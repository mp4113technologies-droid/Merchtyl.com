package com.merchtyl.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Arrays;

@Validated
@ConfigurationProperties(prefix = "merchtyl.security")
public record SecurityProperties(
        @Valid Cors cors,
        @Valid RateLimit rateLimit,
        @Valid TemporaryPassword temporaryPassword,
        @Valid Login login,
        @Valid PasswordReset passwordReset
) {
    public SecurityProperties(Cors cors, RateLimit rateLimit, TemporaryPassword temporaryPassword) {
        this(cors, rateLimit, temporaryPassword, null, null);
    }

    @ConstructorBinding
    public SecurityProperties {
        cors = cors == null ? new Cors(List.of(), List.of()) : cors;
        rateLimit = rateLimit == null ? new RateLimit(true, 20, Duration.ofMinutes(1)) : rateLimit;
        temporaryPassword = temporaryPassword == null ? new TemporaryPassword(20, 24, 10) : temporaryPassword;
        login = login == null ? new Login(3, true) : login;
        passwordReset = passwordReset == null ? new PasswordReset(30, 5, 5) : passwordReset;
    }

    public record Cors(List<String> allowedOrigins, List<String> allowedOriginPatterns) {
        public Cors {
            allowedOrigins = clean(allowedOrigins);
            allowedOriginPatterns = clean(allowedOriginPatterns);
        }

        public Cors(List<String> allowedOrigins) {
            this(allowedOrigins, List.of());
        }

        private static List<String> clean(List<String> values) {
            return values == null ? List.of() : values.stream()
                    .flatMap(value -> Arrays.stream(value.split(",")))
                    .map(String::trim)
                    .filter(origin -> !origin.isBlank())
                    .distinct()
                    .toList();
        }
    }

    public record RateLimit(
            boolean enabled,
            @Min(1) int authMaxAttempts,
            @NotNull Duration authWindow
    ) {
        public RateLimit {
            authWindow = authWindow == null ? Duration.ofMinutes(1) : authWindow;
            if (authWindow.isZero() || authWindow.isNegative()) {
                throw new IllegalArgumentException("authWindow must be positive");
            }
        }
    }

    public record TemporaryPassword(
            @Min(16) int length,
            @Min(1) long expiryHours,
            @Min(1) long passwordChangeTokenMinutes
    ) {
        public TemporaryPassword {
            length = Math.max(16, length);
            expiryHours = Math.max(1, expiryHours);
            passwordChangeTokenMinutes = Math.max(1, passwordChangeTokenMinutes);
        }

        public Duration expiry() {
            return Duration.ofHours(expiryHours);
        }

        public Duration passwordChangeTokenTtl() {
            return Duration.ofMinutes(passwordChangeTokenMinutes);
        }
    }

    public record Login(@Min(1) int maxFailedAttempts, boolean accountLockEmailEnabled) {
    }

    public record PasswordReset(@Min(1) long tokenExpiryMinutes, @Min(1) int forgotMaxPerHour, @Min(1) int adminMaxPerHour) {
        public Duration tokenTtl() {
            return Duration.ofMinutes(tokenExpiryMinutes);
        }
    }
}
