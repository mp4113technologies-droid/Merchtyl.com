package com.merchtyl.cash;

import jakarta.validation.constraints.NotBlank;

public record CashMovementReversalRequest(
        @NotBlank String reason
) {
}
