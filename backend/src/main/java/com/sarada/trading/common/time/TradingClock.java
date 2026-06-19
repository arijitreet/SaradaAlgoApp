package com.sarada.trading.common.time;

import com.sarada.trading.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.*;

/** Single source of truth for IST session-time decisions. */
@Component
public class TradingClock {

    private final AppProperties props;

    public TradingClock(AppProperties props) {
        this.props = props;
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(props.zone());
    }

    public LocalDate tradingDay() {
        return now().toLocalDate();
    }

    public LocalTime localTime(Instant instant) {
        return instant.atZone(props.zone()).toLocalTime();
    }

    /** True between session-start (09:20) and session-end (15:05), weekdays. */
    public boolean isWithinSession() {
        ZonedDateTime now = now();
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(props.trading().sessionStart())
                && !time.isAfter(props.trading().sessionEnd());
    }

    public boolean isAfterSessionEnd() {
        return now().toLocalTime().isAfter(props.trading().sessionEnd());
    }

    /** Does this candle open-time match the day's first candle (09:15)? */
    public boolean isFirstCandle(Instant candleOpenTime) {
        return localTime(candleOpenTime).equals(props.trading().firstCandleStart());
    }

    public LocalDate dayOf(Instant instant) {
        return instant.atZone(props.zone()).toLocalDate();
    }
}
