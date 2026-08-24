package com.merchtyl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "merchtyl.platform")
public record PlatformAdministrationProperties(
        Bootstrap bootstrap,
        OwnerInvitation ownerInvitation,
        SupportAccess supportAccess
) {
    public PlatformAdministrationProperties {
        bootstrap = bootstrap == null ? new Bootstrap(false, "", "", "") : bootstrap;
        ownerInvitation = ownerInvitation == null ? new OwnerInvitation(48, 3, 60, 10) : ownerInvitation;
        supportAccess = supportAccess == null ? new SupportAccess(false, 30) : supportAccess;
    }

    public Duration ownerInvitationExpiry() {
        return Duration.ofHours(Math.max(1, ownerInvitation.expiryHours()));
    }

    public int ownerInvitationResendMaxPerHour() {
        return Math.max(1, ownerInvitation.resendMaxPerHour());
    }

    public Duration ownerInvitationResendMinInterval() {
        return Duration.ofSeconds(Math.max(1, ownerInvitation.resendMinIntervalSeconds()));
    }

    public int ownerInvitationResendMaxTotal() {
        return Math.max(1, ownerInvitation.resendMaxTotal());
    }

    public Duration supportAccessDefaultDuration() {
        return Duration.ofMinutes(Math.max(1, supportAccess.defaultMinutes()));
    }

    public record Bootstrap(
            boolean enabled,
            String email,
            String name,
            String password
    ) {
    }

    public record OwnerInvitation(
            long expiryHours,
            int resendMaxPerHour,
            long resendMinIntervalSeconds,
            int resendMaxTotal
    ) {
    }

    public record SupportAccess(
            boolean enabled,
            long defaultMinutes
    ) {
    }
}
