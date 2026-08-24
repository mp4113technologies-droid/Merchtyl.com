package com.merchtyl.auth;

import com.merchtyl.security.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Authentication token response. Token values are secrets and examples are placeholders.")
public record AuthResponse(
        @Schema(description = "Authentication outcome.", example = "AUTHENTICATED")
        String authenticationStatus,
        @Schema(description = "JWT access token. Paste this value into Swagger Authorize without adding Bearer.", example = "<jwt-access-token>")
        String accessToken,
        @Schema(description = "Opaque refresh token. Store securely and never log it.", example = "<refresh-token>")
        String refreshToken,
        @Schema(description = "Token type.", example = "Bearer")
        String tokenType,
        @Schema(description = "UTC expiry for the access token.", type = "string", format = "date-time", example = "2026-07-29T13:00:00Z")
        Instant accessTokenExpiresAt,
        @Schema(description = "UTC expiry for the refresh token.", type = "string", format = "date-time", example = "2026-08-12T12:00:00Z")
        Instant refreshTokenExpiresAt,
        @Schema(description = "Authenticated user identifier.", format = "uuid", example = "9fd67c6e-9c69-41fe-b634-6f541e9ed0b8")
        UUID userId,
        @Schema(description = "Authenticated user email.", example = "manager@example.test")
        String email,
        @Schema(description = "Display name for UI use.", example = "Store Manager")
        String displayName,
        @Schema(description = "Assigned role names.")
        List<RoleName> roles,
        @Schema(description = "Short-lived token for mandatory first-login password change. Returned only when authenticationStatus is PASSWORD_CHANGE_REQUIRED.", example = "<password-change-token>")
        String passwordChangeToken,
        @Schema(description = "UTC expiry for the password-change token.", type = "string", format = "date-time", example = "2026-08-04T13:10:00Z")
        Instant passwordChangeTokenExpiresAt,
        @Schema(description = "Password-change token lifetime in seconds.", example = "600")
        Long expiresIn
) {
}
