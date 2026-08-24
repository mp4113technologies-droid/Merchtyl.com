package com.merchtyl.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DeviceRegisterRequest(
        @NotNull UUID storeId,
        @NotNull UUID registerId,
        @NotBlank @Size(max = 128) String deviceIdentifier,
        @NotBlank @Size(max = 180) String displayName,
        @NotBlank @Size(max = 64) String deviceType
) {
}
