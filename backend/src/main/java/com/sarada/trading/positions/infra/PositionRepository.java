package com.sarada.trading.positions.infra;

import com.sarada.trading.positions.domain.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    Optional<PositionEntity> findFirstByStatusOrderByOpenedAtDesc(PositionEntity.Status status);

    List<PositionEntity> findByStatus(PositionEntity.Status status);

    int countByTradingDay(LocalDate tradingDay);

    List<PositionEntity> findByTradingDayOrderByOpenedAtDesc(LocalDate tradingDay);

    List<PositionEntity> findByTradingDayBetweenAndStatusOrderByClosedAtAsc(
            LocalDate from, LocalDate to, PositionEntity.Status status);

    org.springframework.data.domain.Page<PositionEntity> findByStatusOrderByClosedAtDesc(
            PositionEntity.Status status, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select coalesce(sum(p.realizedPnl), 0) from PositionEntity p
            where p.tradingDay = :day and p.status = 'CLOSED'
            """)
    BigDecimal realizedPnlOn(@Param("day") LocalDate day);

    @Query("""
            select coalesce(sum(p.realizedPnl), 0) from PositionEntity p
            where p.tradingDay = :day and p.strategyId = :strategyId and p.status = 'CLOSED'
            """)
    BigDecimal realizedPnlByStrategyOn(@Param("day") LocalDate day, @Param("strategyId") String strategyId);

    int countByTradingDayAndStrategyId(LocalDate tradingDay, String strategyId);

    List<PositionEntity> findByStrategyIdAndStatus(String strategyId, PositionEntity.Status status);

    List<PositionEntity> findFirst2ByTradingDayAndStatusOrderByClosedAtAsc(
            LocalDate tradingDay, PositionEntity.Status status);

    /** True if this contract is currently OPEN (any day), or was opened at all today
     *  (open or already closed) — used to steer new entries away from a contract
     *  already in play. */
    @Query("""
            select count(p) > 0 from PositionEntity p
            where p.tradingsymbol = :tradingsymbol and (p.status = 'OPEN' or p.tradingDay = :day)
            """)
    boolean existsActiveOrTakenOn(@Param("tradingsymbol") String tradingsymbol, @Param("day") LocalDate day);
}
