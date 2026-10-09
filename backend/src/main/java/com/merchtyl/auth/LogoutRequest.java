package com.merchtyl.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Logout request that revokes a refresh token.")
public record LogoutRequest(
        @Schema(description = "Opaque refresh token to revoke.", example = "<refresh-token>")
        @Size(max = 512) String refreshToken
) {
}
