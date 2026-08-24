package com.merchtyl.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Refresh-token request. Treat the token value as a secret.")
public record RefreshRequest(
        @Schema(description = "Opaque refresh token returned by login or refresh.", example = "<refresh-token>")
        @NotBlank @Size(max = 512) String refreshToken
) {
}
