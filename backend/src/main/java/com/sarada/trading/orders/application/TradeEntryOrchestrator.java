package com.sarada.trading.orders.application;

import com.sarada.trading.broker.application.FeedSubscriptions;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.market.LivePriceCache;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.marketdata.application.OptionSelector;
import com.sarada.trading.marketdata.domain.InstrumentEntity;
import com.sarada.trading.orders.domain.OrderEntity;
import com.sarada.trading.risk.application.RiskManager;
import com.sarada.trading.strategy.infra.SignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * The entry pipeline, end to end:
 *
 *   SignalGenerated → risk check → option selection (nearest ITM weekly)
 *     → subscribe option feed → MARKET BUY → EntryFilled event
 *
 * Runs async off the candle/tick thread; every outcome is recorded on the
 * persisted signal row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeEntryOrchestrator {

    private static final long PRICE_WAIT_TIMEOUT_MS = 5_000;

    private final RiskManager riskManager;
    private final OptionSelector optionSelector;
    private final FeedSubscriptions feedSubscriptions;
    private final LivePriceCache priceCache;
    private final OrderService orderService;
    private final SignalRepository signals;
    private final AppProperties props;
    private final TradingClock clock;
    private final ApplicationEventPublisher events;

    @Async("appExecutor")
    @EventListener
    public void onSignal(DomainEvents.SignalGenerated event) {
        TradeSignal signal = event.signal();
        RiskManager.Decision decision = riskManager.evaluateEntry(signal);
        if (!decision.approved()) {
            recordOutcome(event.signalId(), false, decision.reason());
            return;
        }

        // From here the concurrent-trade slot is reserved (RiskManager.evaluateEntry) —
        // release it on any path that does NOT end in a published EntryFilled event.
        boolean filled = false;
        try {
            Optional<InstrumentEntity> resolved = optionSelector.selectAvoidingConflicts(
                    signal.type(), signal.triggerPrice(), signal.strikeOffset(),
                    clock.tradingDay(), signal.strategyId());
            if (resolved.isEmpty()) {
                recordOutcome(event.signalId(), false,
                        "Strike shift exhausted — every deeper-ITM contract already active/taken today");
                return;
            }
            InstrumentEntity contract = resolved.get();
            feedSubscriptions.subscribe(contract.getInstrumentToken());
            awaitFirstPrice(contract.getInstrumentToken());

            int quantity = contract.getLotSize() != null
                    ? contract.getLotSize() * props.trading().quantityLots()
                    : props.trading().orderQuantity();

            OrderEntity order = orderService.placeMarket(
                    contract.getInstrumentToken(), contract.getTradingsymbol(),
                    contract.getExchange(), OrderEntity.Side.BUY, quantity);

            if (order.getStatus() != OrderEntity.Status.FILLED) {
                recordOutcome(event.signalId(), false, "Order rejected: " + order.getStatusMessage());
                return;
            }

            recordOutcome(event.signalId(), true, null);
            events.publishEvent(new DomainEvents.EntryFilled(
                    order.getId(), signal, contract.getInstrumentToken(), contract.getTradingsymbol(),
                    contract.getStrike(), contract.getExpiry(), quantity, order.getAvgFillPrice()));
            filled = true;
        } catch (Exception e) {
            log.error("Entry pipeline failed for signal {}: {}", event.signalId(), e.getMessage(), e);
            recordOutcome(event.signalId(), false, "Pipeline error: " + e.getMessage());
        } finally {
            if (!filled) {
                riskManager.releaseReservedSlot(signal.strategyId());
            }
        }
    }

    /** Paper fills need an LTP on the freshly subscribed option token. */
    private void awaitFirstPrice(long instrumentToken) throws InterruptedException {
        long deadline = System.currentTimeMillis() + PRICE_WAIT_TIMEOUT_MS;
        while (priceCache.ltp(instrumentToken).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
    }

    private void recordOutcome(long signalId, boolean accepted, String rejectReason) {
        signals.findById(signalId).ifPresent(entity -> {
            entity.setAccepted(accepted);
            entity.setRejectReason(rejectReason);
            signals.save(entity);
        });
    }
}
