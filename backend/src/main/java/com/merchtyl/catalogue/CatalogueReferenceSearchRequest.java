package com.merchtyl.catalogue;

public record CatalogueReferenceSearchRequest(
        String code,
        String name,
        Boolean active,
        int page,
        int size
) {
}
