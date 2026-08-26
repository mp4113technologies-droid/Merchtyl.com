package com.merchtyl.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @Schema(description = PasswordPolicyService.REQUIREMENTS_MESSAGE, format = "password", minLength = 8, maxLength = 20)
        @NotBlank @Size(min = 8, max = 20, message = PasswordPolicyService.REQUIREMENTS_MESSAGE) String newPassword,
        @NotBlank @Size(min = 8, max = 20, message = PasswordPolicyService.REQUIREMENTS_MESSAGE) String confirmPassword) {
}
