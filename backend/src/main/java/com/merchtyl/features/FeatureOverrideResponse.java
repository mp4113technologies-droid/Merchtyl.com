package com.merchtyl.features;

import java.time.Instant;
import java.util.UUID;

public record FeatureOverrideResponse(
        UUID id,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static FeatureOverrideResponse from(TenantFeature feature) {
        return new FeatureOverrideResponse(
                feature.getId(),
                feature.isEnabled(),
                feature.getCreatedAt(),
                feature.getUpdatedAt(),
                feature.getVersion());
    }

    static FeatureOverrideResponse from(StoreFeature feature) {
        return new FeatureOverrideResponse(
                feature.getId(),
                feature.isEnabled(),
                feature.getCreatedAt(),
                feature.getUpdatedAt(),
                feature.getVersion());
    }

    static FeatureOverrideResponse from(RegisterFeature feature) {
        return new FeatureOverrideResponse(
                feature.getId(),
                feature.isEnabled(),
                feature.getCreatedAt(),
                feature.getUpdatedAt(),
                feature.getVersion());
    }
}
