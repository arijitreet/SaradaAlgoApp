package com.sarada.trading.marketdata.infra;

import com.sarada.trading.marketdata.domain.InstrumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    @Query("""
            select min(i.expiry) from InstrumentEntity i
            where i.name = :name and i.instrumentType in ('CE','PE') and i.expiry >= :from
            """)
    Optional<LocalDate> findNearestExpiry(@Param("name") String name, @Param("from") LocalDate from);

    /** Returns up to 10 distinct upcoming expiry dates so the caller can pick the right weekday. */
    @Query("""
            select distinct i.expiry from InstrumentEntity i
            where i.name = :name and i.instrumentType in ('CE','PE') and i.expiry >= :from
            order by i.expiry
            """)
    List<LocalDate> findUpcomingExpiries(@Param("name") String name, @Param("from") LocalDate from,
                                         org.springframework.data.domain.Pageable pageable);

    Optional<InstrumentEntity> findByNameAndInstrumentTypeAndExpiryAndStrike(
            String name, String instrumentType, LocalDate expiry, BigDecimal strike);

    List<InstrumentEntity> findByNameAndExpiry(String name, LocalDate expiry);
}
