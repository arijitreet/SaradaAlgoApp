package com.sarada.trading.marketdata.application;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.error.DomainException;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.marketdata.domain.InstrumentEntity;
import com.sarada.trading.marketdata.infra.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Picks the contract to trade: nearest Tuesday weekly expiry (Nifty weekly options
 * expire on Tuesday) with rollover on expiry day after the configured cutoff,
 * and the nearest ITM strike.
 *
 *  - BUY_CE → highest strike strictly below spot
 *  - BUY_PE → lowest strike strictly above spot
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OptionSelector {

    private final InstrumentRepository instruments;
    private final InstrumentService instrumentService;
    private final AppProperties props;

    public InstrumentEntity select(SignalType type, BigDecimal spot) {
        return select(type, spot, 0);
    }

    /**
     * @param strikeOffset 0 = nearest ITM/ATM, 1 = 1 strike OTM, 2 = 2 strikes OTM, etc.
     *                     OTM direction: CE → higher strike, PE → lower strike.
     */
    public InstrumentEntity select(SignalType type, BigDecimal spot, int strikeOffset) {
        LocalDate expiry = resolveExpiry();
        BigDecimal step = BigDecimal.valueOf(props.trading().strikeStep());
        BigDecimal strike = nearestItmStrike(type, spot);

        // Apply OTM offset before the search fallback loop.
        if (strikeOffset > 0) {
            BigDecimal shift = step.multiply(BigDecimal.valueOf(strikeOffset));
            strike = type == SignalType.BUY_CE ? strike.add(shift) : strike.subtract(shift);
        }

        // The exact strike may be missing from the dump (band edges) — step outward.
        for (int attempt = 0; attempt < 3; attempt++) {
            BigDecimal candidate = type == SignalType.BUY_CE
                    ? strike.subtract(step.multiply(BigDecimal.valueOf(attempt)))
                    : strike.add(step.multiply(BigDecimal.valueOf(attempt)));
            Optional<InstrumentEntity> found = instruments
                    .findByNameAndInstrumentTypeAndExpiryAndStrike("NIFTY", type.optionType(), expiry, candidate);
            if (found.isPresent()) {
                return found.get();
            }
        }

        throw new DomainException("No tradable " + type.optionType() + " contract found near strike " + strike
                + " (offset=" + strikeOffset + ") for expiry " + expiry
                + " — refresh instruments via Kite re-login");
    }

    BigDecimal nearestItmStrike(SignalType type, BigDecimal spot) {
        BigDecimal step = BigDecimal.valueOf(props.trading().strikeStep());
        BigDecimal floor = spot.divide(step, 0, RoundingMode.FLOOR).multiply(step);
        if (type == SignalType.BUY_CE) {
            // highest strike strictly below spot
            return floor.compareTo(spot) < 0 ? floor : floor.subtract(step);
        }
        // lowest strike strictly above spot
        BigDecimal ceil = spot.divide(step, 0, RoundingMode.CEILING).multiply(step);
        return ceil.compareTo(spot) > 0 ? ceil : ceil.add(step);
    }

    LocalDate resolveExpiry() {
        ZonedDateTime now = ZonedDateTime.now(props.zone());
        LocalDate from = now.toLocalDate().plusDays(props.trading().expiryRolloverDays());
        LocalDate nearest = instrumentService.nearestExpiry(from);

        if (nearest == null) {
            throw new DomainException(
                    "Option universe not loaded — re-authenticate with Kite to refresh instruments");
        }

        // Expiry-day rollover: past the cutoff, trade next week's contract.
        LocalTime cutoff = props.trading().expiryRolloverCutoff();
        if (nearest.equals(now.toLocalDate()) && now.toLocalTime().isAfter(cutoff)) {
            LocalDate next = instrumentService.nearestExpiry(nearest.plusDays(1));
            if (next != null) {
                log.info("Expiry rollover: {} → {}", nearest, next);
                nearest = next;
            }
        }
        return nearest;
    }
}
