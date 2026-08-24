package com.merchtyl.returns;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReturnResponse(
        UUID id,
        UUID originalSaleId,
        UUID storeId,
        UUID registerId,
        UUID registerSessionId,
        UUID createdBy,
        LocalDate businessDate,
        Instant occurredAt,
        String currencyCode,
        String reason,
        BigDecimal totalQuantity,
        BigDecimal subtotalAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        boolean fullReturn,
        List<ReturnItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static ReturnResponse from(Return returnRecord, boolean fullReturn) {
        return new ReturnResponse(
                returnRecord.getId(),
                returnRecord.getOriginalSale().getId(),
                returnRecord.getStore().getId(),
                returnRecord.getRegister().getId(),
                returnRecord.getRegisterSession().getId(),
                returnRecord.getCreatedBy().getId(),
                returnRecord.getBusinessDate(),
                returnRecord.getOccurredAt(),
                returnRecord.getCurrencyCode(),
                returnRecord.getReason(),
                returnRecord.getTotalQuantity(),
                returnRecord.getSubtotalAmount(),
                returnRecord.getTaxAmount(),
                returnRecord.getTotalAmount(),
                fullReturn,
                returnRecord.getItems().stream().map(ReturnItemResponse::from).toList(),
                returnRecord.getCreatedAt(),
                returnRecord.getUpdatedAt(),
                returnRecord.getVersion());
    }
}
