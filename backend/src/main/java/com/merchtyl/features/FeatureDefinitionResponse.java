package com.merchtyl.features;

import java.time.Instant;
import java.util.UUID;

public record FeatureDefinitionResponse(
        UUID id,
        FeatureCode code,
        String name,
        String description,
        boolean defaultEnabled,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static FeatureDefinitionResponse from(FeatureDefinition definition) {
        return new FeatureDefinitionResponse(
                definition.getId(),
                definition.getCode(),
                definition.getName(),
                definition.getDescription(),
                definition.isDefaultEnabled(),
                definition.getCreatedAt(),
                definition.getUpdatedAt(),
                definition.getVersion());
    }
}
