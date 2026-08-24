package com.merchtyl.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LotteryReportChartPoint(
        LocalDate date,
        BigDecimal sales,
        BigDecimal payouts,
        BigDecimal reversals,
        BigDecimal referrals,
        BigDecimal settlement
) {
}
