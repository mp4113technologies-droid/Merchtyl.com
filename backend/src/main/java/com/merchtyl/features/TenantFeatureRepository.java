package com.merchtyl.features;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantFeatureRepository extends JpaRepository<TenantFeature, UUID> {
    List<TenantFeature> findByFeatureDefinitionIn(Collection<FeatureDefinition> definitions);

    Optional<TenantFeature> findByFeatureDefinition(FeatureDefinition definition);
}
