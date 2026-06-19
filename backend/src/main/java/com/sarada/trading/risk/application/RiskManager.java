package com.sarada.trading.risk.application;

import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.risk.domain.TradeStatsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Pre-trade gate. Every signal passes through here before any order is placed:
 *   • inside session window (09:20–15:05 IST)
 *   • daily trade budget (max 5) not exhausted
 *   • no position already open (one concurrent position per strategy v1)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskManager {

    private final TradeStatsPort tradeStats;
    private final TradingClock clock;
    private final AppProperties props;
    private final AuditService audit;

    public record Decision(boolean approved, String reason) {
        static Decision approve() {
            return new Decision(true, "OK");
        }

        static Decision reject(String reason) {
            return new Decision(false, reason);
        }
    }

    public Decision evaluateEntry(TradeSignal signal) {
        if (!clock.isWithinSession()) {
            return rejected(signal, "Outside session window "
                    + props.trading().sessionStart() + "–" + props.trading().sessionEnd());
        }
        int tradesToday = tradeStats.tradesOpenedOn(clock.tradingDay());
        if (tradesToday >= props.trading().maxTradesPerDay()) {
            return rejected(signal, "Daily trade limit reached (" + tradesToday + "/"
                    + props.trading().maxTradesPerDay() + ")");
        }
        if (tradeStats.hasOpenPosition()) {
            return rejected(signal, "A position is already open");
        }
        audit.log("RISK", "ENTRY_APPROVED", signal.type() + " @ " + signal.triggerPrice());
        return Decision.approve();
    }

    private Decision rejected(TradeSignal signal, String reason) {
        log.info("Signal {} rejected: {}", signal.type(), reason);
        audit.log("RISK", "ENTRY_REJECTED", signal.type() + " — " + reason);
        return Decision.reject(reason);
    }

    public int tradesRemaining() {
        return Math.max(0, props.trading().maxTradesPerDay()
                - tradeStats.tradesOpenedOn(clock.tradingDay()));
    }
}
