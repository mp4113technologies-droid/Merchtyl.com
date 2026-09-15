package com.merchtyl.eod;

import com.merchtyl.registersession.RegisterSessionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDayRegisterSessionBlockingTest {
    @Test
    void onlyFullyClosedOrForceClosedSessionsAreTerminal() {
        assertThat(BusinessDayService.isBlockingRegisterSessionStatus(RegisterSessionStatus.OPEN)).isTrue();
        assertThat(BusinessDayService.isBlockingRegisterSessionStatus(RegisterSessionStatus.CLOSING)).isTrue();
        assertThat(BusinessDayService.isBlockingRegisterSessionStatus(RegisterSessionStatus.CLOSED)).isFalse();
        assertThat(BusinessDayService.isBlockingRegisterSessionStatus(RegisterSessionStatus.FORCE_CLOSED)).isFalse();
    }
}
