package com.merchtyl.catalogue;

import java.util.UUID;

public interface CatalogueReferenceRepository<T extends CatalogueReference> {
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
