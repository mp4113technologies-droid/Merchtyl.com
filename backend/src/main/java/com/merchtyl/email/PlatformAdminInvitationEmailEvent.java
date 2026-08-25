package com.merchtyl.email;

import com.merchtyl.security.RoleName;

import java.time.Instant;
import java.util.UUID;

public record PlatformAdminInvitationEmailEvent(
        UUID actorId, UUID platformUserId, String recipient, String firstName,
        RoleName role, String rawToken, Instant expiresAt) {
}
