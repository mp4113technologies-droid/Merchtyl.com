package com.merchtyl.reports;

import java.time.LocalDate;
import java.util.UUID;

public record LotteryReportRequest(
        UUID operatorId,
        UUID storeId,
        UUID registerId,
        UUID cashierId,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
