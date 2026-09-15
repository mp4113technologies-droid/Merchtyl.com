package com.merchtyl.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record LotterySalesReportRequest(UUID storeId, UUID registerId, UUID cashierId,
                                 LocalDate dateFrom, LocalDate dateTo, String type, String source) {}

record LotterySalesActivityRow(Instant occurredAt, String register, String cashier, String type,
                               String source, String description, BigDecimal amount, String receiptNumber) {}

record LotterySalesReportResponse(UUID storeId, UUID registerId, UUID cashierId,
                                  LocalDate dateFrom, LocalDate dateTo, String type, String source,
                                  BigDecimal physicalTicketSales, BigDecimal manualLotterySold,
                                  BigDecimal totalLotterySold, BigDecimal lotteryWins,
                                  BigDecimal netLottery, String currencyCode,
                                  List<LotterySalesActivityRow> activities, Instant generatedAt) {}
