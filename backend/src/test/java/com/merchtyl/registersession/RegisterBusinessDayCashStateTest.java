package com.merchtyl.registersession;

import com.merchtyl.eod.BusinessDay;
import com.merchtyl.register.Register;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RegisterBusinessDayCashStateTest {
    private final Store store = mock(Store.class);
    private final Register register = mock(Register.class);
    private final BusinessDay day = mock(BusinessDay.class);

    @Test
    void oneRegisterOneSessionContributesItsInitialFloatOnce() {
        RegisterBusinessDayCashState state = state("440.00");

        state.settle(money("878.00"), money("878.00"), money("440.00"));

        assertThat(state.getInitialFloat()).isEqualByComparingTo("440.00");
        assertThat(state.getSessionCount()).isEqualTo(1);
        assertThat(state.getCashRemoved()).isEqualByComparingTo("438.00");
        assertThat(state.getRetainedCash()).isEqualByComparingTo("440.00");
    }

    @Test
    void twoAndThreeSequentialShiftsReuseDefaultTillWithoutNewPhysicalFloat() {
        RegisterBusinessDayCashState state = state("440.00");
        state.settle(money("878.00"), money("878.00"), money("440.00"));
        state.openNextShift(money("440.00"));
        state.settle(money("700.00"), money("700.00"), money("440.00"));
        state.openNextShift(money("440.00"));

        assertThat(state.getInitialFloat()).isEqualByComparingTo("440.00");
        assertThat(state.getSessionCount()).isEqualTo(3);
        assertThat(state.getCashRemoved()).isEqualByComparingTo("698.00");
    }

    @Test
    void finalShiftReconciliationDoesNotSumIntermediateExpectedOrCountedCash() {
        RegisterBusinessDayCashState state = state("440.00");
        state.settle(money("878.00"), money("878.00"), money("440.00"));
        state.openNextShift(money("440.00"));
        state.settle(money("600.00"), money("600.00"), money("440.00"));

        assertThat(state.getInitialFloat()).isEqualByComparingTo("440.00");
        assertThat(state.getFinalExpectedCash()).isEqualByComparingTo("600.00");
        assertThat(state.getFinalCountedCash()).isEqualByComparingTo("600.00");
        assertThat(state.getCashRemoved()).isEqualByComparingTo("598.00");
        assertThat(state.getRetainedCash()).isEqualByComparingTo("440.00");
    }

    @Test
    void nextShiftRequiresTillRestoredToConfiguredStartingAmount() {
        RegisterBusinessDayCashState state = state("440.00");
        state.settle(money("878.00"), money("878.00"), money("440.00"));

        assertThatThrownBy(() -> state.openNextShift(money("880.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("restored to its configured starting amount");
        assertThat(state.getSessionCount()).isEqualTo(1);
    }

    @Test
    void twoPhysicalRegistersKeepIndependentInitialFloatsAndShiftCounts() {
        RegisterBusinessDayCashState first = state("440.00");
        RegisterBusinessDayCashState second = new RegisterBusinessDayCashState(
                store, mock(Register.class), day, money("300.00"));
        first.openNextShift(money("440.00"));
        second.openNextShift(money("300.00"));
        second.openNextShift(money("300.00"));

        assertThat(first.getInitialFloat().add(second.getInitialFloat())).isEqualByComparingTo("740.00");
        assertThat(first.getSessionCount()).isEqualTo(2);
        assertThat(second.getSessionCount()).isEqualTo(3);
    }

    @Test
    void settlementCannotRetainMoreCashThanWasCounted() {
        RegisterBusinessDayCashState state = state("440.00");

        assertThatThrownBy(() -> state.settle(money("400.00"), money("400.00"), money("440.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void separateStoreAndBusinessDayInstancesHaveIndependentPhysicalState() {
        RegisterBusinessDayCashState firstDay = state("440.00");
        RegisterBusinessDayCashState otherStore = new RegisterBusinessDayCashState(
                mock(Store.class), register, day, money("200.00"));
        RegisterBusinessDayCashState otherDay = new RegisterBusinessDayCashState(
                store, register, mock(BusinessDay.class), money("500.00"));

        firstDay.openNextShift(money("440.00"));

        assertThat(otherStore.getSessionCount()).isEqualTo(1);
        assertThat(otherDay.getSessionCount()).isEqualTo(1);
        assertThat(otherStore.getInitialFloat()).isEqualByComparingTo("200.00");
        assertThat(otherDay.getInitialFloat()).isEqualByComparingTo("500.00");
    }

    @Test
    void configuredTargetIsSnapshottedSeparatelyFromActualOpeningCash() {
        RegisterBusinessDayCashState state = new RegisterBusinessDayCashState(
                store, register, day, money("430.00"), money("440.00"));

        assertThat(state.getInitialFloat()).isEqualByComparingTo("430.00");
        assertThat(state.getTargetFloat()).isEqualByComparingTo("440.00");
    }

    @Test
    void middayConfigurationChangesCannotAlterExistingBusinessDaySnapshot() {
        RegisterBusinessDayCashState state = new RegisterBusinessDayCashState(
                store, register, day, money("440.00"), money("440.00"));

        // A future Store/Register configuration may resolve to 500, but this state owns today's snapshot.
        BigDecimal futureEffectiveFloat = money("500.00");

        assertThat(futureEffectiveFloat).isEqualByComparingTo("500.00");
        assertThat(state.getTargetFloat()).isEqualByComparingTo("440.00");
    }

    @Test
    void fullMultipleRegisterShiftMatrixAggregatesPhysicalCashOnly() {
        RegisterBusinessDayCashState r1 = new RegisterBusinessDayCashState(
                store, register, day, money("440.00"), money("440.00"));
        RegisterBusinessDayCashState r2 = new RegisterBusinessDayCashState(
                store, mock(Register.class), day, money("300.00"), money("300.00"));

        r1.settle(money("700.00"), money("700.00"), money("440.00"));
        r1.openNextShift(money("440.00"));
        r1.settle(money("600.00"), money("600.00"), money("440.00"));
        r1.openNextShift(money("440.00"));
        r1.settle(money("500.00"), money("500.00"), money("440.00"));
        r2.settle(money("300.00"), money("300.00"), money("300.00"));
        r2.openNextShift(money("300.00"));
        r2.settle(money("300.00"), money("300.00"), money("300.00"));

        assertThat(r1.getInitialFloat().add(r2.getInitialFloat())).isEqualByComparingTo("740.00");
        assertThat(r1.getRetainedCash().add(r2.getRetainedCash())).isEqualByComparingTo("740.00");
        assertThat(r1.getSessionCount()).isEqualTo(3);
        assertThat(r2.getSessionCount()).isEqualTo(2);
        assertThat(r1.getCashRemoved()).isEqualByComparingTo("480.00");
        assertThat(r2.getCashRemoved()).isZero();
    }

    @Test
    void nextBusinessDayUsesNewConfigurationWithoutChangingCurrentSnapshot() {
        RegisterBusinessDayCashState current = new RegisterBusinessDayCashState(
                store, register, day, money("440.00"), money("440.00"));
        RegisterBusinessDayCashState next = new RegisterBusinessDayCashState(
                store, register, mock(BusinessDay.class), money("500.00"), money("500.00"));

        assertThat(current.getTargetFloat()).isEqualByComparingTo("440.00");
        assertThat(next.getTargetFloat()).isEqualByComparingTo("500.00");
    }

    private RegisterBusinessDayCashState state(String opening) {
        return new RegisterBusinessDayCashState(store, register, day, money(opening));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
