package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record AdministrativeAreaStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
