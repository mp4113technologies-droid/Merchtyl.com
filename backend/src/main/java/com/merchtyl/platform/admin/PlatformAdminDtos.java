package com.merchtyl.platform.admin;

import com.merchtyl.security.RoleName;
import com.merchtyl.auth.PasswordPolicyService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlatformAdminDtos {
    private PlatformAdminDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotNull RoleName role) {}

    public record ActivateRequest(
            @NotBlank String token,
            @Schema(description = PasswordPolicyService.REQUIREMENTS_MESSAGE, format = "password", minLength = 8, maxLength = 20)
            @NotBlank @Size(min = 8, max = 20, message = PasswordPolicyService.REQUIREMENTS_MESSAGE) String password) {}
    public record StatusRequest(@NotNull Boolean enabled, @NotNull Long version) {}
    public record ActorSummary(UUID id, String name) {}
    public record Response(UUID id, String firstName, String lastName, String email, RoleName role,
                           String status, boolean locked, Instant lastLoginAt, Instant createdAt,
                           ActorSummary createdBy, long version) {}
    public record Page(List<Response> content, int page, int size, long totalElements, int totalPages) {}
}
