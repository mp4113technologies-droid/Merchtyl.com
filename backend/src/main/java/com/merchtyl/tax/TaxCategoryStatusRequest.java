package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record TaxCategoryStatusRequest(@NotNull Boolean active, @NotNull Long version) {
}
