package com.merchtyl.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockAdjustmentResponse(
        UUID id,
        UUID storeId,
        String reason,
        String notes,
        StockAdjustmentApprovalStatus approvalStatus,
        UUID approvedByUserId,
        Instant approvedAt,
        String approvalNotes,
        List<StockAdjustmentLineResponse> lines,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static StockAdjustmentResponse from(StockAdjustment adjustment) {
        return new StockAdjustmentResponse(
                adjustment.getId(),
                adjustment.getStore().getId(),
                adjustment.getReason(),
                adjustment.getNotes(),
                adjustment.getApprovalStatus(),
                adjustment.getApprovedByUser() == null ? null : adjustment.getApprovedByUser().getId(),
                adjustment.getApprovedAt(),
                adjustment.getApprovalNotes(),
                adjustment.getLines().stream().map(StockAdjustmentLineResponse::from).toList(),
                adjustment.getCreatedAt(),
                adjustment.getUpdatedAt(),
                adjustment.getVersion());
    }
}
