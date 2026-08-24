package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record ProductTaxCategoryAssignmentStatusRequest(@NotNull Boolean active, @NotNull Long version) {
}
