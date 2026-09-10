package com.merchtyl.catalogue;

import java.util.UUID;

public interface CatalogueReferenceRepository<T extends CatalogueReference> {
    java.util.Optional<T> findByCodeIgnoreCase(String code);
    java.util.Optional<T> findByNameIgnoreCase(String name);
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
