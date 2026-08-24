package com.merchtyl.security;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "User creation request. Requires user administration permission.")
public record UserCreateRequest(
        @Schema(description = "User email address.", example = "cashier@example.test")
        @NotBlank @Email @Size(max = 320) String email,
        @Schema(description = "Display name for UI use.", example = "Front Cashier")
        @NotBlank @Size(max = 160) String displayName,
        @Schema(description = "Initial password. The example is a placeholder and not a real password.", format = "password", example = "<password>")
        @NotBlank @Size(min = 8, max = 128) String password,
        @Schema(description = "Roles assigned to the user.")
        @NotEmpty List<RoleName> roles,
        @Schema(description = "Stores the user can access.")
        List<UUID> storeIds,
        @Schema(description = "Registers the user can access.")
        List<UUID> registerIds,
        @Schema(description = "Whether the user can authenticate.", example = "true")
        Boolean enabled,
        @Schema(description = "Whether the user account is locked.", example = "false")
        Boolean locked
) {
}
