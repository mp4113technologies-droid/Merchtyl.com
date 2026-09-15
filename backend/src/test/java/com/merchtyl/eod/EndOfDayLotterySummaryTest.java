package com.merchtyl.eod;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EndOfDayLotterySummaryTest {
    @Test
    void netLotteryIsSoldMinusWinsAndMayBeNegative() {
        EndOfDayLotterySummary positive = summary("500.00", "180.00");
        EndOfDayLotterySummary negative = summary("200.00", "350.00");

        assertThat(positive.getNetLottery()).isEqualByComparingTo("320.00");
        assertThat(negative.getNetLottery()).isEqualByComparingTo("-150.00");
    }

    private static EndOfDayLotterySummary summary(String sold, String wins) {
        return new EndOfDayLotterySummary(null, true, new BigDecimal(sold), new BigDecimal(wins),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0, 0, 0, 0, "{}", "{}", "{}");
    }
}
