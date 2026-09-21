package com.merchtyl.store;

import java.util.UUID;

public record FoodServiceConfigurationResponse(UUID storeId, boolean restaurantPosEnabled, String kitchenDisplayName,
        String timezone, boolean autoPrintAsapKitchenTickets, boolean autoPrintScheduledKitchenTickets,
        int scheduledKitchenPrintLeadMinutes) {
    static FoodServiceConfigurationResponse from(Store store) {
        return new FoodServiceConfigurationResponse(store.getId(), true, store.getKitchenDisplayName(), store.getTimezone(),
                store.isAutoPrintAsapKitchenTickets(), store.isAutoPrintScheduledKitchenTickets(),
                store.getScheduledKitchenPrintLeadMinutes());
    }
}
