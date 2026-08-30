package com.merchtyl.store;

import java.util.UUID;

public record FoodServiceConfigurationResponse(UUID storeId, boolean restaurantPosEnabled, String kitchenDisplayName) {
    static FoodServiceConfigurationResponse from(Store store) {
        return new FoodServiceConfigurationResponse(store.getId(), true, store.getKitchenDisplayName());
    }
}
