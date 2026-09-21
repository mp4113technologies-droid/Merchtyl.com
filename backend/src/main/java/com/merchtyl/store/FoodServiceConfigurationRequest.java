package com.merchtyl.store;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FoodServiceConfigurationRequest(
        boolean autoPrintAsapKitchenTickets,
        boolean autoPrintScheduledKitchenTickets,
        @Min(0) @Max(1440) int scheduledKitchenPrintLeadMinutes) {
}
