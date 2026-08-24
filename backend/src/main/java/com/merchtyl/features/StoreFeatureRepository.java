package com.merchtyl.features;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreFeatureRepository extends JpaRepository<StoreFeature, UUID> {
    List<StoreFeature> findByStore_IdAndFeatureDefinitionIn(UUID storeId, Collection<FeatureDefinition> definitions);

    Optional<StoreFeature> findByStore_IdAndFeatureDefinition(UUID storeId, FeatureDefinition definition);
}
