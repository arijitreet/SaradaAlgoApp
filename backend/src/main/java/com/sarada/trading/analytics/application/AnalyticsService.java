package com.sarada.trading.analytics.application;

import com.sarada.trading.analytics.domain.DailyStats;
import com.sarada.trading.analytics.infra.DailyStatsRepository;
import com.sarada.trading.common.events.DomainEvents;
import com.sarada.trading.common.time.TradingClock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated performance: daily stats are folded incrementally as positions
 * close; weekly/monthly summaries and the equity curve are derived on read.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final DailyStatsRepository dailyStats;
    private final TradingClock clock;

    @Async("appExecutor")
    @EventListener
    @Transactional
    public void onPositionClosed(DomainEvents.PositionClosed event) {
        LocalDate day = clock.tradingDay();
        DailyStats stats = dailyStats.findById(day).orElseGet(() -> new DailyStats(day));
        stats.applyTrade(event.realizedPnl());
        dailyStats.save(stats);
    }

    public record Summary(String period, LocalDate from, LocalDate to, int trades, int wins,
                          int losses, BigDecimal winRatePct, BigDecimal grossPnl,
                          BigDecimal bestTrade, BigDecimal worstTrade, BigDecimal avgPnlPerTrade) {}

    public Summary summary(String period) {
        LocalDate today = clock.tradingDay();
        LocalDate from = switch (period) {
            case "weekly" -> today.with(java.time.DayOfWeek.MONDAY);
            case "monthly" -> today.withDayOfMonth(1);
            default -> today;
        };
        List<DailyStats> rows = dailyStats.findByTradingDayBetweenOrderByTradingDayAsc(from, today);

        int trades = rows.stream().mapToInt(DailyStats::getTrades).sum();
        int wins = rows.stream().mapToInt(DailyStats::getWins).sum();
        int losses = rows.stream().mapToInt(DailyStats::getLosses).sum();
        BigDecimal gross = rows.stream().map(DailyStats::getGrossPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal best = rows.stream().map(DailyStats::getBestTrade)
                .reduce(BigDecimal.ZERO, BigDecimal::max);
        BigDecimal worst = rows.stream().map(DailyStats::getWorstTrade)
                .reduce(BigDecimal.ZERO, BigDecimal::min);
        BigDecimal winRate = trades == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(wins * 100L).divide(BigDecimal.valueOf(trades), 1, RoundingMode.HALF_UP);
        BigDecimal avg = trades == 0 ? BigDecimal.ZERO
                : gross.divide(BigDecimal.valueOf(trades), 2, RoundingMode.HALF_UP);

        return new Summary(period, from, today, trades, wins, losses, winRate, gross, best, worst, avg);
    }

    public record EquityPoint(LocalDate day, BigDecimal dayPnl, BigDecimal cumulativePnl) {}

    public List<EquityPoint> equityCurve(int days) {
        LocalDate today = clock.tradingDay();
        List<DailyStats> rows = dailyStats
                .findByTradingDayBetweenOrderByTradingDayAsc(today.minusDays(days), today);
        List<EquityPoint> curve = new ArrayList<>(rows.size());
        BigDecimal cumulative = BigDecimal.ZERO;
        for (DailyStats row : rows) {
            cumulative = cumulative.add(row.getGrossPnl());
            curve.add(new EquityPoint(row.getTradingDay(), row.getGrossPnl(), cumulative));
        }
        return curve;
    }

    public List<DailyStats> dailyBreakdown(int days) {
        LocalDate today = clock.tradingDay();
        return dailyStats.findByTradingDayBetweenOrderByTradingDayAsc(today.minusDays(days), today);
    }
}
