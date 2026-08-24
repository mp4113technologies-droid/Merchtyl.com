package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record TaxGroupComponentStatusRequest(@NotNull Boolean active, @NotNull Long version) {
}
