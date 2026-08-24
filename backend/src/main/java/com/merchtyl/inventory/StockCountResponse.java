package com.merchtyl.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockCountResponse(
        UUID id,
        UUID storeId,
        String reference,
        String notes,
        StockCountStatus status,
        UUID createdByUserId,
        UUID reviewedByUserId,
        Instant reviewedAt,
        String reviewNotes,
        UUID postedByUserId,
        Instant postedAt,
        String postNotes,
        List<StockCountLineResponse> lines,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static StockCountResponse from(StockCount count) {
        return new StockCountResponse(
                count.getId(),
                count.getStore().getId(),
                count.getReference(),
                count.getNotes(),
                count.getStatus(),
                count.getCreatedByUser() == null ? null : count.getCreatedByUser().getId(),
                count.getReviewedByUser() == null ? null : count.getReviewedByUser().getId(),
                count.getReviewedAt(),
                count.getReviewNotes(),
                count.getPostedByUser() == null ? null : count.getPostedByUser().getId(),
                count.getPostedAt(),
                count.getPostNotes(),
                count.getLines().stream().map(StockCountLineResponse::from).toList(),
                count.getCreatedAt(),
                count.getUpdatedAt(),
                count.getVersion());
    }
}
