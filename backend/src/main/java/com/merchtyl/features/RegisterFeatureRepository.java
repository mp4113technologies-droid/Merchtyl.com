package com.merchtyl.features;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegisterFeatureRepository extends JpaRepository<RegisterFeature, UUID> {
    List<RegisterFeature> findByRegister_IdAndFeatureDefinitionIn(UUID registerId, Collection<FeatureDefinition> definitions);

    Optional<RegisterFeature> findByRegister_IdAndFeatureDefinition(UUID registerId, FeatureDefinition definition);
}
