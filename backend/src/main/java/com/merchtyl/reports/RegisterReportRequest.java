package com.merchtyl.reports;

import com.merchtyl.registersession.RegisterSessionStatus;

import java.time.LocalDate;
import java.util.UUID;

public record RegisterReportRequest(
        UUID storeId,
        UUID registerId,
        UUID cashierId,
        RegisterSessionStatus status,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
