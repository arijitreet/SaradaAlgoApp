package com.sarada.trading.strategy.infra;

import com.sarada.trading.strategy.domain.SignalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SignalRepository extends JpaRepository<SignalEntity, Long> {

    List<SignalEntity> findByTradingDayOrderByCreatedAtDesc(LocalDate tradingDay);

    Optional<SignalEntity> findFirstByStrategyIdAndTradingDayOrderByCreatedAtDesc(
            String strategyId, LocalDate tradingDay);
}
