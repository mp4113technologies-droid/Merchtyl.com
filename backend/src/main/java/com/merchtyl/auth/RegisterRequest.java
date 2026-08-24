package com.merchtyl.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Initial account registration request.")
public record RegisterRequest(
        @Schema(description = "User email address.", example = "owner@example.test")
        @Email @NotBlank @Size(max = 320) String email,
        @Schema(description = "User password. The example is a placeholder and not a real password.", format = "password", example = "<password>")
        @NotBlank @Size(min = 8, max = 128) String password,
        @Schema(description = "Display name for UI use.", example = "Store Owner")
        @NotBlank @Size(max = 160) String displayName
) {
}
