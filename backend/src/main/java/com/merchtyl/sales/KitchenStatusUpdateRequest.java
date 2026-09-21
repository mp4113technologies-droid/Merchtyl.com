package com.merchtyl.sales;

import jakarta.validation.constraints.NotNull;

public record KitchenStatusUpdateRequest(@NotNull KitchenOrderStatus status) {
}
