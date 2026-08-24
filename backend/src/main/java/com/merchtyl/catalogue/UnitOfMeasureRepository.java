package com.merchtyl.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, UUID>, JpaSpecificationExecutor<UnitOfMeasure>, CatalogueReferenceRepository<UnitOfMeasure> {
}
