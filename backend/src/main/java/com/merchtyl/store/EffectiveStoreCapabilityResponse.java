package com.merchtyl.store;

public record EffectiveStoreCapabilityResponse(
        StoreCapability capability,
        boolean subscriptionEnabled,
        boolean storeEnabled,
        boolean deploymentEnabled,
        boolean userAuthorized,
        boolean enabled) {
}
