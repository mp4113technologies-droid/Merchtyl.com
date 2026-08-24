package com.merchtyl.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SupplierUpdateRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 180) String contactName,
        @Size(max = 40) String phone,
        @Email @Size(max = 320) String email,
        @Size(max = 1000) String address,
        @Size(max = 2000) String notes,
        boolean active,
        @NotNull Long version
) {
}
