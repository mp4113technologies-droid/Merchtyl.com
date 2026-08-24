package com.merchtyl.lottery;

import com.merchtyl.sales.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LotterySaleResponse(
        UUID id,
        UUID operatorId,
        String operatorCode,
        String operatorName,
        String operatorReference,
        String ticketReference,
        LotteryGameType gameType,
        BigDecimal amount,
        String currencyCode,
        PaymentMethod paymentMethod,
        UUID storeId,
        String storeCode,
        String storeName,
        UUID registerId,
        String registerCode,
        String registerName,
        UUID deviceId,
        String deviceIdentifier,
        String deviceDisplayName,
        UUID cashierId,
        String cashierEmail,
        String cashierDisplayName,
        UUID registerSessionId,
        LotterySaleStatus status,
        UUID operationId,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static LotterySaleResponse from(LotterySale sale) {
        return new LotterySaleResponse(
                sale.getId(),
                sale.getOperator().getId(),
                sale.getOperator().getCode(),
                sale.getOperator().getName(),
                sale.getOperatorReference(),
                sale.getTicketReference(),
                sale.getGameType(),
                sale.getAmount(),
                sale.getCurrencyCode(),
                sale.getPaymentMethod(),
                sale.getStore().getId(),
                sale.getStore().getCode(),
                sale.getStore().getName(),
                sale.getRegister().getId(),
                sale.getRegister().getCode(),
                sale.getRegister().getName(),
                sale.getDevice().getId(),
                sale.getDevice().getDeviceIdentifier(),
                sale.getDevice().getDisplayName(),
                sale.getCashier().getId(),
                sale.getCashier().getEmail(),
                sale.getCashier().getDisplayName(),
                sale.getRegisterSession() == null ? null : sale.getRegisterSession().getId(),
                sale.getStatus(),
                sale.getOperationId(),
                sale.getOccurredAt(),
                sale.getCreatedAt(),
                sale.getUpdatedAt(),
                sale.getVersion());
    }
}
