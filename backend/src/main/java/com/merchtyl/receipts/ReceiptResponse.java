package com.merchtyl.receipts;

import java.time.Instant;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        UUID saleId,
        String receiptNumber,
        Instant generatedAt,
        int reprintCount,
        Instant lastReprintedAt,
        ReceiptDocumentDto document,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static ReceiptResponse from(Receipt receipt, ReceiptDocumentDto document) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getSale().getId(),
                receipt.getReceiptNumber(),
                receipt.getGeneratedAt(),
                receipt.getReprintCount(),
                receipt.getLastReprintedAt(),
                document,
                receipt.getCreatedAt(),
                receipt.getUpdatedAt(),
                receipt.getVersion());
    }
}
