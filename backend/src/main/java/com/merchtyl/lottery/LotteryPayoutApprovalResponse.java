package com.merchtyl.lottery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LotteryPayoutApprovalResponse(
        UUID id,
        LotteryPayoutApprovalType approvalType,
        UUID approvedBy,
        String approvedByEmail,
        String approvedByDisplayName,
        Instant approvedAt,
        BigDecimal payoutAmount,
        BigDecimal thresholdAmount,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static LotteryPayoutApprovalResponse from(LotteryPayoutApproval approval) {
        return new LotteryPayoutApprovalResponse(
                approval.getId(),
                approval.getApprovalType(),
                approval.getApprovedBy().getId(),
                approval.getApprovedBy().getEmail(),
                approval.getApprovedBy().getDisplayName(),
                approval.getApprovedAt(),
                approval.getPayoutAmount(),
                approval.getThresholdAmount(),
                approval.getNotes(),
                approval.getCreatedAt(),
                approval.getUpdatedAt(),
                approval.getVersion());
    }
}
