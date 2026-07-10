package com.sarada.trading.marketdata.application;

import com.sarada.trading.common.config.AppProperties;
import com.sarada.trading.common.market.SignalType;
import com.sarada.trading.marketdata.domain.ActiveTradesPort;
import com.sarada.trading.marketdata.domain.InstrumentEntity;
import com.sarada.trading.marketdata.infra.InstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Strike-conflict avoidance: before entering, the resolved contract is checked against
 * every ACTIVE trade and every trade already taken today (open or closed); on conflict
 * the strike shifts one step deeper ITM (CE: lower, PE: higher), up to 3 times, else the
 * trade is skipped. SL/target computation and order placement are untouched by this —
 * only which InstrumentEntity gets selected changes.
 */
class OptionSelectorTest {

    private static final LocalDate EXPIRY = LocalDate.of(2026, 7, 9);
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 7);

    private InstrumentRepository instruments;
    private ActiveTradesPort activeTrades;
    private OptionSelector selector;

    @BeforeEach
    void setUp() {
        instruments = Mockito.mock(InstrumentRepository.class);
        InstrumentService instrumentService = Mockito.mock(InstrumentService.class);
        activeTrades = Mockito.mock(ActiveTradesPort.class);
        when(instrumentService.nearestExpiry(Mockito.any())).thenReturn(EXPIRY);

        AppProperties props = new AppProperties("Asia/Kolkata", "PAPER", null, null,
                new AppProperties.Trading("NIFTY 50", "NSE", "NFO", 65, 1, 50,
                        LocalTime.of(9, 20), LocalTime.of(15, 5), LocalTime.of(9, 15),
                        5, 4, 2, 0, LocalTime.of(14, 30), new BigDecimal("6500")),
                null, null);
        selector = new OptionSelector(instruments, instrumentService, activeTrades, props);
    }

    private void stub(String optionType, BigDecimal strike) {
        InstrumentEntity entity = InstrumentEntity.builder()
                .instrumentToken(strike.longValue())
                .tradingsymbol("NIFTY25JUL" + strike.intValue() + optionType)
                .name("NIFTY")
                .exchange("NFO")
                .instrumentType(optionType)
                .strike(strike)
                .expiry(EXPIRY)
                .lotSize(65)
                .build();
        when(instruments.findByNameAndInstrumentTypeAndExpiryAndStrike("NIFTY", optionType, EXPIRY, strike))
                .thenReturn(Optional.of(entity));
    }

    private void inUse(String optionType, BigDecimal strike, boolean flag) {
        when(activeTrades.isContractInUseToday("NIFTY25JUL" + strike.intValue() + optionType, TODAY))
                .thenReturn(flag);
    }

    @Test
    void noConflictReturnsTheOriginallyResolvedContract() {
        stub("CE", new BigDecimal("24200"));
        inUse("CE", new BigDecimal("24200"), false);

        Optional<InstrumentEntity> result = selector.selectAvoidingConflicts(
                SignalType.BUY_CE, new BigDecimal("24210"), 0, TODAY, "first-candle-breakout-v1");

        assertThat(result).isPresent();
        assertThat(result.get().getTradingsymbol()).isEqualTo("NIFTY25JUL24200CE");
    }

    @Test
    void callConflictShiftsToNextLowerStrike() {
        stub("CE", new BigDecimal("24200"));
        stub("CE", new BigDecimal("24150"));
        inUse("CE", new BigDecimal("24200"), true);
        inUse("CE", new BigDecimal("24150"), false);

        Optional<InstrumentEntity> result = selector.selectAvoidingConflicts(
                SignalType.BUY_CE, new BigDecimal("24210"), 0, TODAY, "mean-reversion-v1");

        assertThat(result).isPresent();
        assertThat(result.get().getTradingsymbol()).isEqualTo("NIFTY25JUL24150CE");
    }

    @Test
    void putConflictShiftsToNextHigherStrike() {
        stub("PE", new BigDecimal("24200"));
        stub("PE", new BigDecimal("24250"));
        inUse("PE", new BigDecimal("24200"), true);
        inUse("PE", new BigDecimal("24250"), false);

        Optional<InstrumentEntity> result = selector.selectAvoidingConflicts(
                SignalType.BUY_PE, new BigDecimal("24190"), 0, TODAY, "mean-reversion-v1");

        assertThat(result).isPresent();
        assertThat(result.get().getTradingsymbol()).isEqualTo("NIFTY25JUL24250PE");
    }

    @Test
    void doubleConflictShiftsTwiceInTheSameDirection() {
        stub("CE", new BigDecimal("24200"));
        stub("CE", new BigDecimal("24150"));
        stub("CE", new BigDecimal("24100"));
        inUse("CE", new BigDecimal("24200"), true);
        inUse("CE", new BigDecimal("24150"), true);
        inUse("CE", new BigDecimal("24100"), false);

        Optional<InstrumentEntity> result = selector.selectAvoidingConflicts(
                SignalType.BUY_CE, new BigDecimal("24210"), 0, TODAY, "supertrend-flip-v1");

        assertThat(result).isPresent();
        assertThat(result.get().getTradingsymbol()).isEqualTo("NIFTY25JUL24100CE");
    }

    @Test
    void beyondMaxShiftsSkipsTheTrade() {
        BigDecimal[] strikes = {
                new BigDecimal("24200"), new BigDecimal("24150"),
                new BigDecimal("24100"), new BigDecimal("24050"),
        };
        for (BigDecimal s : strikes) {
            stub("CE", s);
            inUse("CE", s, true);   // original + all 3 shifts conflict
        }

        Optional<InstrumentEntity> result = selector.selectAvoidingConflicts(
                SignalType.BUY_CE, new BigDecimal("24210"), 0, TODAY, "first-candle-breakout-v1");

        assertThat(result).isEmpty();
    }

    @Test
    void aSameDayExitedContractAlsoTriggersTheShift() {
        // ActiveTradesPort itself decides "in use" (OPEN or opened earlier today,
        // regardless of exit) — the selector just reacts uniformly to that boolean.
        stub("CE", new BigDecimal("24200"));
        stub("CE", new BigDecimal("24150"));
        inUse("CE", new BigDecimal("24200"), true);   // exited earlier today, still counts
        inUse("CE", new BigDecimal("24150"), false);

        Optional<InstrumentEntity> result = selector.selectAvoidingConflicts(
                SignalType.BUY_CE, new BigDecimal("24210"), 0, TODAY, "first-candle-breakout-v1");

        assertThat(result).isPresent();
        assertThat(result.get().getTradingsymbol()).isEqualTo("NIFTY25JUL24150CE");
    }
}
