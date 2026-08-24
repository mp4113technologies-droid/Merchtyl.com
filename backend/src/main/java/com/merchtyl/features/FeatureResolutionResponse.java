package com.merchtyl.features;

import java.util.UUID;

public record FeatureResolutionResponse(
        FeatureDefinitionResponse definition,
        boolean enabled,
        FeatureResolutionSource source,
        UUID storeId,
        UUID registerId,
        FeatureOverrideResponse tenantOverride,
        FeatureOverrideResponse storeOverride,
        FeatureOverrideResponse registerOverride
) {
}
