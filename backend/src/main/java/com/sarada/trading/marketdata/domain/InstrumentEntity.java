package com.sarada.trading.marketdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "instruments")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentEntity {

    @Id
    @Column(name = "instrument_token")
    private Long instrumentToken;

    @Column(nullable = false)
    private String tradingsymbol;

    private String name;

    @Column(nullable = false)
    private String exchange;

    private String segment;

    @Column(name = "instrument_type")
    private String instrumentType;

    private BigDecimal strike;

    private LocalDate expiry;

    @Column(name = "lot_size")
    private Integer lotSize;

    @Column(name = "tick_size")
    private BigDecimal tickSize;

    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt;
}
