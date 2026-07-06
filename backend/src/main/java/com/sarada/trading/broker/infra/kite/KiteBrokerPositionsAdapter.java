package com.sarada.trading.broker.infra.kite;

import com.sarada.trading.positions.domain.BrokerPositionsPort;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.Position;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kite implementation of the positions module's read-only reconciliation port.
 * Fetches the NET position book; never touches order APIs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KiteBrokerPositionsAdapter implements BrokerPositionsPort {

    private final KiteClientFactory clientFactory;

    @Override
    public Optional<Map<String, Integer>> openNetQuantities() {
        KiteConnect kite = clientFactory.authenticated().orElse(null);
        if (kite == null) {
            return Optional.empty();
        }
        try {
            List<Position> net = kite.getPositions().get("net");
            Map<String, Integer> bySymbol = new HashMap<>();
            if (net != null) {
                for (Position p : net) {
                    if (p.netQuantity != 0) {
                        bySymbol.merge(p.tradingSymbol, p.netQuantity, Integer::sum);
                    }
                }
            }
            return Optional.of(bySymbol);
        } catch (Exception | com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException e) {
            log.warn("Broker position fetch for reconciliation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
