package com.merchtyl.features;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureDefinitionRepository extends JpaRepository<FeatureDefinition, UUID> {
    List<FeatureDefinition> findAllByOrderByCodeAsc();

    Optional<FeatureDefinition> findByCode(FeatureCode code);
}
