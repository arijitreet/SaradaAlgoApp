package com.sarada.trading.marketdata.infra;

import com.sarada.trading.marketdata.domain.CandleEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    List<CandleEntity> findByInstrumentTokenAndIntervalMinutesAndOpenTimeGreaterThanEqualOrderByOpenTimeAsc(
            long instrumentToken, int intervalMinutes, Instant from);

    List<CandleEntity> findByInstrumentTokenAndIntervalMinutesOrderByOpenTimeDesc(
            long instrumentToken, int intervalMinutes, Pageable pageable);

    boolean existsByInstrumentTokenAndIntervalMinutesAndOpenTime(
            long instrumentToken, int intervalMinutes, Instant openTime);
}
