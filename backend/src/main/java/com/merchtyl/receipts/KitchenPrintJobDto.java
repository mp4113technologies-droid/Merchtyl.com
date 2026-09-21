package com.merchtyl.receipts;

import java.time.Instant;
import java.util.UUID;

public record KitchenPrintJobDto(UUID id, UUID storeId, UUID orderId, PrintDocumentType type, Instant scheduledAt,
        KitchenPrintJobStatus status, int attemptCount, KitchenPrintOrigin origin, Instant lastAttemptAt,
        Instant printedAt, String lastError, long version) {
    static KitchenPrintJobDto from(KitchenPrintJob job) { return new KitchenPrintJobDto(job.getId(), job.getStoreId(),
            job.getSaleId(), job.getType(), job.getScheduledAt(), job.getStatus(), job.getAttemptCount(), job.getOrigin(),
            job.getLastAttemptAt(), job.getPrintedAt(), job.getLastError(), job.getVersion()); }
}
