package com.merchtyl.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Initial account registration request.")
public record RegisterRequest(
        @Schema(description = "User email address.", example = "owner@example.test")
        @Email @NotBlank @Size(max = 320) String email,
        @Schema(description = PasswordPolicyService.REQUIREMENTS_MESSAGE, format = "password", example = "<password>", minLength = 8, maxLength = 20)
        @NotBlank @Size(min = 8, max = 20, message = PasswordPolicyService.REQUIREMENTS_MESSAGE) String password,
        @Schema(description = "Display name for UI use.", example = "Store Owner")
        @NotBlank @Size(max = 160) String displayName
) {
}
