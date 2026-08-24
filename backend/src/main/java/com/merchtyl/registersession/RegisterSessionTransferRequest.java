package com.merchtyl.registersession;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegisterSessionTransferRequest(
        @NotNull UUID newOperatorUserId,
        @NotBlank String reason,
        @NotNull Long version) {
}
