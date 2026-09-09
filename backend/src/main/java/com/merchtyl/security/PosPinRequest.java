package com.merchtyl.security;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PosPinRequest(
        @NotNull @Pattern(regexp = "\\d{6}", message = "POS PIN must be exactly 6 digits") String pin) {
}
