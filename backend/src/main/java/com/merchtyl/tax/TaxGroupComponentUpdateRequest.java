package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record TaxGroupComponentUpdateRequest(
        @NotNull UUID taxGroupId,
        @NotNull UUID taxComponentId,
        @PositiveOrZero int calculationOrder,
        boolean active,
        @NotNull Long version
) {
}
