package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record TaxGroupStatusRequest(@NotNull Boolean active, @NotNull Long version) {
}
