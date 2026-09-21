package com.merchtyl.sales;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PhoneOrderClaimRequest(@NotNull UUID registerSessionId) {
}
