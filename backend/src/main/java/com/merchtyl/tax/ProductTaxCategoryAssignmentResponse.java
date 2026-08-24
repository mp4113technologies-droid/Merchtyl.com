package com.merchtyl.tax;

import java.time.Instant;
import java.util.UUID;

public record ProductTaxCategoryAssignmentResponse(
        UUID id,
        UUID productId,
        UUID taxCategoryId,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static ProductTaxCategoryAssignmentResponse from(ProductTaxCategoryAssignment assignment) {
        return new ProductTaxCategoryAssignmentResponse(
                assignment.getId(),
                assignment.getProduct().getId(),
                assignment.getTaxCategory().getId(),
                assignment.isActive(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt(),
                assignment.getVersion());
    }
}
