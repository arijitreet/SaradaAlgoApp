package com.sarada.trading.analytics.infra;

import com.sarada.trading.analytics.domain.DailyStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyStatsRepository extends JpaRepository<DailyStats, LocalDate> {

    List<DailyStats> findByTradingDayBetweenOrderByTradingDayAsc(LocalDate from, LocalDate to);
}
