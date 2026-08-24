package com.merchtyl.security;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Password reset request. Requires user administration permission.")
public record UserPasswordResetRequest(
        @Schema(description = "Replacement password. The example is a placeholder and not a real password.", format = "password", example = "<password>")
        @NotBlank @Size(min = 8, max = 128) String newPassword,
        @Schema(description = "Optimistic-lock version. Stale values return 409 Conflict.", example = "3")
        @NotNull Long version
) {
}
