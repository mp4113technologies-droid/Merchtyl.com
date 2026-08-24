package com.merchtyl.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirstLoginPasswordChangeRequest(
        @NotBlank String passwordChangeToken,
        @NotBlank @Size(min = 12, max = 512) String newPassword,
        @NotBlank @Size(min = 12, max = 512) String confirmPassword
) {
}
