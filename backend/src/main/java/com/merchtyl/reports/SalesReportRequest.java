package com.merchtyl.reports;

import java.time.LocalDate;
import java.util.UUID;

public record SalesReportRequest(
        UUID storeId,
        UUID registerId,
        UUID cashierId,
        UUID categoryId,
        UUID productId,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
