package com.merchtyl.catalogue;

import jakarta.validation.constraints.Size;

public record CatalogueReferenceRequest(
        @Size(max = 64) String code,
        @jakarta.validation.constraints.NotBlank @Size(max = 180) String name,
        @Size(max = 1000) String description,
        boolean active
) {
}
