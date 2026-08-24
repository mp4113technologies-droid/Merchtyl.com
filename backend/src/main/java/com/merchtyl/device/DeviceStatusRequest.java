package com.merchtyl.device;

import jakarta.validation.constraints.NotNull;

public record DeviceStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
