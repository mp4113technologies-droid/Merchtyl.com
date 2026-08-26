package com.merchtyl.security;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Password reset request. Requires user administration permission.")
public record UserPasswordResetRequest(
        @Schema(description = "Password must be 8–20 characters and include uppercase, lowercase, number, and allowed special characters.", format = "password", example = "<password>", minLength = 8, maxLength = 20)
        @NotBlank @Size(min = 8, max = 20, message = com.merchtyl.auth.PasswordPolicyService.REQUIREMENTS_MESSAGE) String newPassword,
        @Schema(description = "Optimistic-lock version. Stale values return 409 Conflict.", example = "3")
        @NotNull Long version
) {
}
