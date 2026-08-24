package com.merchtyl.tax;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CountryRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$") String code,
        @NotBlank @Size(max = 180) String name,
        boolean active
) {
}
