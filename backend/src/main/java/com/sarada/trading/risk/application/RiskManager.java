package com.sarada.trading.risk.application;

import com.sarada.trading.common.audit.AuditService;
import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.market.TradeSignal;
import com.sarada.trading.common.time.TradingClock;
import com.sarada.trading.common.ws.WsPublisher;
import com.sarada.trading.risk.domain.TradeStatsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskManager {

    private final TradeStatsPort tradeStats;
    private final TradingClock clock;
    private final AppProperties props;
    private final AuditService audit;
    private final WsPublisher ws;

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
                    + props.trading().maxTradesPerDay() + " across all strategies)");
        }
        int strategyCap = props.strategy().perDayCap(signal.strategyId());
        if (strategyCap > 0) {
            int strategyCount = tradeStats.tradesOpenedOnByStrategy(clock.tradingDay(), signal.strategyId());
            if (strategyCount >= strategyCap) {
                return rejected(signal, "ENTRY BLOCKED: " + signal.strategyId()
                        + " daily cap (" + strategyCap + ") reached");
            }
        }
        BigDecimal lockThreshold = props.trading().dailyProfitLockAmount();
        if (lockThreshold != null && lockThreshold.signum() > 0) {
            BigDecimal lockedPnl = tradeStats.profitLockExcessAmount(clock.tradingDay(), lockThreshold);
            if (lockedPnl != null) {
                ws.feed("RISK", "Trading paused for today",
                        "Daily profit target achieved (P₹" + lockedPnl + ") — no new entries until tomorrow");
                return rejected(signal, "TRADING HALTED FOR DAY: profit lock hit (P&L="
                        + lockedPnl + " > threshold=" + lockThreshold + ")");
            }
        }
        int maxConcurrent = props.trading().maxConcurrentTrades();
        TradeStatsPort.SlotReservation reservation =
                tradeStats.tryReserveSlot(signal.strategyId(), maxConcurrent);
        if (!reservation.approved()) {
            if (reservation.blockedBySameStrategy()) {
                String tradeRef = reservation.activeTradeId() != null
                        ? "tradeId=" + reservation.activeTradeId() : "entry in flight";
                return rejected(signal, "ENTRY BLOCKED: strategy=" + signal.strategyId()
                        + " already has an active trade (" + tradeRef + ")");
            }
            return rejected(signal, "Maximum concurrent trades reached ("
                    + maxConcurrent + "/" + maxConcurrent + ")");
        }
        audit.log("RISK", "ENTRY_APPROVED", signal.type() + " @ " + signal.triggerPrice());
        return Decision.approve();
    }

    /** Releases a slot reserved by {@link #evaluateEntry} when the entry pipeline aborts
     *  before a position is actually opened (e.g. order rejected, pipeline exception). */
    public void releaseReservedSlot(String strategyId) {
        tradeStats.releaseSlot(strategyId);
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
