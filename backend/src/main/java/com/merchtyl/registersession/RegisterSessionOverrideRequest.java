package com.merchtyl.registersession;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterSessionOverrideRequest(
        @NotBlank String reason,
        @NotNull Long version
) {}
