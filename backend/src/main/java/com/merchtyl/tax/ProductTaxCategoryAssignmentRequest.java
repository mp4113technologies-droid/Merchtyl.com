package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProductTaxCategoryAssignmentRequest(
        @NotNull UUID productId,
        @NotNull UUID taxCategoryId,
        boolean active
) {
}
