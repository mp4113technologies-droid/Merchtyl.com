package com.merchtyl.tax;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CountryUpdateRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$") String code,
        @NotBlank @Size(max = 180) String name,
        boolean active,
        @NotNull Long version
) {
}
