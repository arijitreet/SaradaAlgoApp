package com.sarada.trading.marketdata.application;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.error.DomainException;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.marketdata.domain.ActiveTradesPort;
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

    private static final int MAX_STRIKE_SHIFTS = 3;

    private final InstrumentRepository instruments;
    private final InstrumentService instrumentService;
    private final ActiveTradesPort activeTrades;
    private final AppProperties props;

    public InstrumentEntity select(SignalType type, BigDecimal spot) {
        return select(type, spot, 0);
    }

    /**
     * Resolves the contract exactly like {@link #select}, then — if that exact contract is
     * already ACTIVE or was already taken today (opened, whether or not since exited) —
     * shifts one strike deeper ITM (CE: next lower strike, PE: next higher strike) and
     * re-checks, up to {@value #MAX_STRIKE_SHIFTS} times. Empty means every attempt still
     * conflicted; the caller must skip the trade.
     */
    public Optional<InstrumentEntity> selectAvoidingConflicts(
            SignalType type, BigDecimal spot, int strikeOffset, LocalDate tradingDay, String strategyId) {
        InstrumentEntity contract = select(type, spot, strikeOffset);
        BigDecimal step = BigDecimal.valueOf(props.trading().strikeStep());

        int shifts = 0;
        while (activeTrades.isContractInUseToday(contract.getTradingsymbol(), tradingDay)) {
            if (shifts >= MAX_STRIKE_SHIFTS) {
                log.warn("STRIKE SHIFT EXHAUSTED: {} resolved {} but all {} deeper-ITM strikes are "
                                + "also active/taken today — skipping trade",
                        strategyId, contract.getTradingsymbol(), MAX_STRIKE_SHIFTS);
                return Optional.empty();
            }
            BigDecimal nextStrike = type == SignalType.BUY_CE
                    ? contract.getStrike().subtract(step)
                    : contract.getStrike().add(step);
            LocalDate expiry = contract.getExpiry();
            Optional<InstrumentEntity> found = instruments
                    .findByNameAndInstrumentTypeAndExpiryAndStrike("NIFTY", type.optionType(), expiry, nextStrike);
            if (found.isEmpty()) {
                throw new DomainException("No tradable " + type.optionType()
                        + " contract found at shifted strike " + nextStrike + " for expiry " + expiry);
            }
            InstrumentEntity shifted = found.get();
            log.info("STRIKE SHIFTED: {} resolved {} -> conflict with active/taken contract -> entered {}",
                    strategyId, contract.getTradingsymbol(), shifted.getTradingsymbol());
            contract = shifted;
            shifts++;
        }
        return Optional.of(contract);
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
