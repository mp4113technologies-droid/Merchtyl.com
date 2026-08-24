package com.merchtyl.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "merchtyl.email")
public record EmailProperties(
        String provider,
        String fromAddress,
        String fromName,
        String replyTo,
        String frontendBaseUrl,
        Retry retry,
        Resend resend
) {
    public EmailProperties {
        provider = blankToDefault(provider, "console");
        fromAddress = blankToDefault(fromAddress, "");
        fromName = blankToDefault(fromName, "Merchtyl");
        replyTo = blankToDefault(replyTo, "");
        frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
        retry = retry == null ? new Retry(5, 30, 2) : retry;
        resend = resend == null ? new Resend(false, "") : resend;
    }

    public EmailProvider resolvedProvider() {
        return "resend".equalsIgnoreCase(provider) ? EmailProvider.RESEND : EmailProvider.CONSOLE;
    }

    public boolean consoleProvider() {
        return resolvedProvider() == EmailProvider.CONSOLE;
    }

    public String activationUrl(String rawToken) {
        String base = frontendBaseUrl == null ? "" : frontendBaseUrl.replaceAll("/+$", "");
        return base + "/activate-owner?token=" + rawToken;
    }

    public String passwordResetUrl(String rawToken) {
        String base = frontendBaseUrl == null ? "" : frontendBaseUrl.replaceAll("/+$", "");
        return base + "/reset-password?token=" + rawToken;
    }

    public int maxAttempts() {
        return Math.max(1, retry.maxAttempts());
    }

    public long retryDelaySeconds(int attemptNumber) {
        long delay = Math.max(1, retry.initialSeconds());
        int multiplier = Math.max(1, retry.multiplier());
        for (int i = 1; i < Math.max(1, attemptNumber); i++) {
            delay = Math.multiplyExact(delay, multiplier);
        }
        return delay;
    }

    public record Retry(
            int maxAttempts,
            int initialSeconds,
            int multiplier
    ) {
    }

    public record Resend(
            boolean enabled,
            String apiKey
    ) {
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
