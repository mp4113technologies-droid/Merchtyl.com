package com.merchtyl.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credentials used to request JWT access and refresh tokens.")
public record LoginRequest(
        @Schema(description = "User email address.", example = "manager@example.test")
        @Email @NotBlank @Size(max = 320) String email,
        @Schema(description = "User password. The example is a placeholder and not a real password.", format = "password", example = "<password>")
        @NotBlank @Size(max = 128) String password
) {
}
