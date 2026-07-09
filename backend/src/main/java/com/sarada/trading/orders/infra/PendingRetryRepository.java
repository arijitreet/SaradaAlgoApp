package com.sarada.trading.orders.infra;

import com.sarada.trading.orders.domain.PendingRetryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PendingRetryRepository extends JpaRepository<PendingRetryEntity, Long> {

    List<PendingRetryEntity> findByStatusAndTradingDay(
            PendingRetryEntity.Status status, LocalDate tradingDay);

    List<PendingRetryEntity> findByStatusAndTradingDayBefore(
            PendingRetryEntity.Status status, LocalDate tradingDay);

    boolean existsByStrategyIdAndTradingDayAndStatus(
            String strategyId, LocalDate tradingDay, PendingRetryEntity.Status status);
}
