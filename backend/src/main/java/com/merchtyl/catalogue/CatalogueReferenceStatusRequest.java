package com.merchtyl.catalogue;

import jakarta.validation.constraints.NotNull;

public record CatalogueReferenceStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
