package com.merchtyl.store;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record StoreRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 255) String legalName,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$") String countryCode,
        @Size(max = 32) @Pattern(regexp = "^[A-Za-z0-9_-]*$") String administrativeDivisionCode,
        @Size(max = 32) @Pattern(regexp = "^[A-Za-z0-9_-]*$") String administrativeAreaCode,
        @NotBlank @Size(max = 1000) String address,
        @Size(max = 40) String phone,
        @Email @Size(max = 320) String email,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currencyCode,
        @NotBlank @Size(max = 35) String locale,
        @NotBlank @Size(max = 64) String timezone,
        @Size(max = 64) String taxRegionCode,
        boolean pricesIncludeTax,
        boolean negativeStockAllowed,
        boolean active,
        @NotEmpty Set<StoreCapability> capabilities,
        @Size(max = 180) String kitchenDisplayName
) {
}
