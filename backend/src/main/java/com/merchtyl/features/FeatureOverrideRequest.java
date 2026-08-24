package com.merchtyl.features;

public record FeatureOverrideRequest(
        Boolean enabled,
        Long version
) {
}
