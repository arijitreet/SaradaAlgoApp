package com.sarada.trading.broker.infra.kite;

import com.sarada.trading.broker.domain.BrokerGateway;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.PermissionException;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.TokenException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Live order routing through Zerodha. MARKET + MIS + DAY validity, then polls
 * the order book briefly to capture the fill price.
 *
 * Troubleshooting tip: set logging.level.com.sarada.trading.broker=DEBUG in
 * application.yml to see per-poll order status lines.
 */
@Slf4j
@Component
@Profile("live")
@RequiredArgsConstructor
public class KiteBrokerGateway implements BrokerGateway {

    private static final int FILL_POLL_ATTEMPTS = 20;
    private static final long FILL_POLL_INTERVAL_MS = 400;

    private final KiteClientFactory clientFactory;

    @Override
    public String mode() {
        return "LIVE";
    }

    @Override
    public OrderResult placeMarketOrder(OrderRequest request) {
        Optional<KiteConnect> maybeKite = clientFactory.authenticated();
        if (maybeKite.isEmpty()) {
            log.warn("[ORDER] No active Kite session — order NOT sent to broker. "
                    + "symbol={} side={} qty={} — complete Kite OAuth login via Settings",
                    request.tradingsymbol(), request.side(), request.quantity());
            return OrderResult.rejected("Broker not authenticated — complete Kite login first");
        }
        KiteConnect kite = maybeKite.get();

        OrderParams params = new OrderParams();
        params.exchange = request.exchange();
        params.tradingsymbol = request.tradingsymbol();
        params.transactionType = request.side() == OrderRequest.Side.BUY
                ? Constants.TRANSACTION_TYPE_BUY : Constants.TRANSACTION_TYPE_SELL;
        params.quantity = request.quantity();
        params.product = Constants.PRODUCT_MIS;
        params.validity = Constants.VALIDITY_DAY;

        // Log params before the network call so a failure is always diagnosable.
        log.info("[ORDER] → Kite placeOrder exchange={} symbol={} side={} qty={} product=MIS validity=DAY",
                params.exchange, params.tradingsymbol, params.transactionType, params.quantity);

        try {
            // Zerodha rejects pure MARKET orders in the NFO segment (InputException 400:
            // "Market orders without market protection are not allowed"). Use an aggressive
            // LIMIT order instead: 2% above LTP for buys, 2% below for sells. Fills
            // almost instantly at prevailing price while satisfying the protection rule.
            String instrument = request.exchange() + ":" + request.tradingsymbol();
            Map<String, com.zerodhatech.models.LTPQuote> ltpMap =
                    kite.getLTP(new String[]{instrument});
            com.zerodhatech.models.LTPQuote ltpQuote = ltpMap.get(instrument);
            if (ltpQuote == null || ltpQuote.lastPrice <= 0) {
                log.error("[ORDER] LTP fetch returned nothing for {} — order aborted", instrument);
                return OrderResult.rejected("Could not fetch LTP for " + instrument + " — order not sent to broker");
            }
            BigDecimal ltp = BigDecimal.valueOf(ltpQuote.lastPrice);
            BigDecimal buffer = request.side() == OrderRequest.Side.BUY
                    ? new BigDecimal("1.02") : new BigDecimal("0.98");
            // Round to NFO tick size (₹0.05) so the price is exchange-valid.
            BigDecimal tick = new BigDecimal("0.05");
            BigDecimal limitPrice = ltp.multiply(buffer)
                    .divide(tick, 0, RoundingMode.HALF_UP)
                    .multiply(tick);

            params.orderType = Constants.ORDER_TYPE_LIMIT;
            params.price = limitPrice.doubleValue();
            log.info("[ORDER] LTP={} limitPrice={} ({}% buffer) type=LIMIT",
                    ltp, limitPrice, request.side() == OrderRequest.Side.BUY ? "+2" : "-2");

            Order placed = kite.placeOrder(params, Constants.VARIETY_REGULAR);
            log.info("[ORDER] Kite accepted — brokerOrderId={} symbol={}",
                    placed.orderId, request.tradingsymbol());
            return awaitFill(kite, placed.orderId);

        } catch (TokenException te) {
            // 403 — token expired or revoked on Zerodha's side; app-side expiry clock was stale.
            // The HTTP request was made but Zerodha rejected it before creating any order,
            // which is why the order will NOT appear in Kite's order book.
            log.warn("[ORDER] Token rejected by Zerodha (HTTP 403) — access token has expired. "
                    + "symbol={} kiteError='{}' — user must re-login via Settings",
                    request.tradingsymbol(), te.message);
            return OrderResult.rejected("Access token expired — re-login via Settings (TokenException: " + te.message + ")");

        } catch (PermissionException pe) {
            // 403 — request reached Zerodha but the source IP is not on the account's whitelist.
            // Since April 1 2026, SEBI mandates order-placement API calls originate from a
            // registered static IP. Rejected at the permission gateway BEFORE any order is
            // created, so it never appears in the Kite order book.
            log.warn("[ORDER] Permission denied by Zerodha (HTTP 403) — source IP not whitelisted. "
                    + "symbol={} kiteError='{}' — register this IP in the Kite developer console "
                    + "(Profile → IP whitelist) at developers.kite.trade",
                    request.tradingsymbol(), pe.message);
            return OrderResult.rejected("Order blocked — server IP not whitelisted in Kite app. "
                    + "Register it at developers.kite.trade → Profile (" + pe.message + ")");

        } catch (KiteException ke) {
            // Other Kite API errors (InputException 400, OrderException, NetworkException, etc.)
            log.error("[ORDER] Kite API error — symbol={} errorType={} httpCode={} kiteMessage='{}'",
                    request.tradingsymbol(), ke.getClass().getSimpleName(), ke.code, ke.message);
            return OrderResult.rejected(ke.getClass().getSimpleName() + " [" + ke.code + "]: " + ke.message);

        } catch (Exception e) {
            // Local JVM exception — order was NOT sent to broker (no network call was made,
            // or the call failed at the transport layer before Zerodha could process anything).
            log.error("[ORDER] Local exception placing order — symbol={} — order was NOT sent to Kite",
                    request.tradingsymbol(), e);
            String msg = e.getMessage();
            return OrderResult.rejected("Local error (not sent to broker): "
                    + e.getClass().getSimpleName() + (msg != null ? ": " + msg : ""));
        }
    }

    /**
     * Polls getOrderHistory until the order reaches a terminal state (COMPLETE /
     * REJECTED / CANCELLED) or we exhaust attempts.
     *
     * Throws checked exceptions so they propagate to the typed catch blocks in
     * placeMarketOrder rather than being silently swallowed here.
     */
    private OrderResult awaitFill(KiteConnect kite, String orderId)
            throws Exception, KiteException {
        for (int attempt = 0; attempt < FILL_POLL_ATTEMPTS; attempt++) {
            List<Order> history = kite.getOrderHistory(orderId);
            if (history == null || history.isEmpty()) {
                log.debug("[ORDER] Poll {}/{} — empty history for orderId={}, retrying",
                        attempt + 1, FILL_POLL_ATTEMPTS, orderId);
                Thread.sleep(FILL_POLL_INTERVAL_MS);
                continue;
            }
            Order latest = history.get(history.size() - 1);
            log.debug("[ORDER] Poll {}/{} — orderId={} status='{}' message='{}'",
                    attempt + 1, FILL_POLL_ATTEMPTS, orderId, latest.status, latest.statusMessage);

            if (Constants.ORDER_COMPLETE.equalsIgnoreCase(latest.status)) {
                BigDecimal fillPrice = parseFillPrice(latest.averagePrice, orderId);
                log.info("[ORDER] COMPLETE — orderId={} avgFillPrice={}", orderId, fillPrice);
                return new OrderResult(orderId, true, fillPrice, "COMPLETE");
            }
            if ("REJECTED".equalsIgnoreCase(latest.status) || "CANCELLED".equalsIgnoreCase(latest.status)) {
                // Genuine exchange/RMS rejection — order DID reach Kite and will appear in order book.
                log.warn("[ORDER] {} by exchange/RMS — orderId={} reason='{}'",
                        latest.status, orderId, latest.statusMessage);
                return new OrderResult(orderId, false, null, latest.statusMessage);
            }
            Thread.sleep(FILL_POLL_INTERVAL_MS);
        }
        long waitedMs = (long) FILL_POLL_ATTEMPTS * FILL_POLL_INTERVAL_MS;
        log.warn("[ORDER] Fill confirmation timed out after {}ms — orderId={} — order may still fill; check Kite app",
                waitedMs, orderId);
        return new OrderResult(orderId, false, null, "Fill confirmation timed out after " + waitedMs + "ms");
    }

    /**
     * SDK v3.x returns averagePrice as a String; it can be null, "", or "0" for
     * orders that just transitioned to COMPLETE before the fill price is populated.
     */
    private static BigDecimal parseFillPrice(String averagePrice, String orderId) {
        if (averagePrice == null || averagePrice.isBlank()) {
            log.warn("[ORDER] averagePrice is blank for orderId={} — defaulting to ZERO; verify fill price in Kite app", orderId);
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(averagePrice);
        } catch (NumberFormatException nfe) {
            log.warn("[ORDER] Could not parse averagePrice='{}' for orderId={} — defaulting to ZERO", averagePrice, orderId);
            return BigDecimal.ZERO;
        }
    }
}
