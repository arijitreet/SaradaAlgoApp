package com.sarada.trading.strategy.application;

import com.sarada.trading.common.time.TradingClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * Per-strategy intraday active windows (IST). A signal is only accepted when the
 * current time falls inside the firing strategy's allowed window(s).
 *
 *  • Global:  no signals before 09:30 (the 09:15–09:30 open is skipped for every strategy).
 *  • Mean Reversion:            11:30–13:30 (midday range).
 *  • Multi-Confluence Trend:    09:30–11:30 and 13:30–15:00 (trend/momentum windows).
 *  • First-Candle Breakout & Supertrend:  global skip only; otherwise the full session.
 */
@Component
@RequiredArgsConstructor
public class StrategyTimeWindows {

    private static final LocalTime OPEN_SKIP_END = LocalTime.of(9, 30);
    private static final LocalTime MORNING_START = LocalTime.of(9, 30);
    private static final LocalTime MORNING_END = LocalTime.of(11, 30);
    private static final LocalTime MIDDAY_START = LocalTime.of(11, 30);
    private static final LocalTime MIDDAY_END = LocalTime.of(13, 30);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 30);
    private static final LocalTime AFTERNOON_END = LocalTime.of(15, 0);

    private final TradingClock clock;

    /** True when the strategy is allowed to fire at the current time. */
    public boolean isActive(String strategyId) {
        return isActiveAt(strategyId, clock.now().toLocalTime());
    }

    boolean isActiveAt(String strategyId, LocalTime now) {
        if (now.isBefore(OPEN_SKIP_END)) {
            return false;   // global 09:15–09:30 skip
        }
        return switch (strategyId) {
            case MeanReversionStrategy.ID -> within(now, MIDDAY_START, MIDDAY_END);
            case MultiConfluenceTrendStrategy.ID ->
                    within(now, MORNING_START, MORNING_END) || within(now, AFTERNOON_START, AFTERNOON_END);
            default -> true;   // FCB, Supertrend — full session after the open skip
        };
    }

    /** Human-readable active window(s) for the dashboard. */
    public String windowLabel(String strategyId) {
        return switch (strategyId) {
            case MeanReversionStrategy.ID -> "11:30–13:30";
            case MultiConfluenceTrendStrategy.ID -> "09:30–11:30, 13:30–15:00";
            default -> "09:30–close";
        };
    }

    private boolean within(LocalTime t, LocalTime start, LocalTime end) {
        return !t.isBefore(start) && t.isBefore(end);
    }
}
