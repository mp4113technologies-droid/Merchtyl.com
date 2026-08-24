package com.merchtyl.email;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Configuration
public class EmailConfiguration {
    @Bean
    EmailSender emailSender(EmailProperties properties) {
        if (properties.resolvedProvider() == EmailProvider.RESEND) {
            validateResendProvider(properties);
            return new ResendEmailSender(properties);
        }
        return new ConsoleEmailSender(properties);
    }

    @Bean
    ApplicationRunner emailConfigurationValidator(EmailProperties properties, Environment environment) {
        return args -> {
            boolean prod = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
            if (prod && properties.resolvedProvider() != EmailProvider.RESEND) {
                throw new IllegalStateException("Production email provider must be RESEND");
            }
            if (properties.resolvedProvider() == EmailProvider.RESEND) {
                validateResendProvider(properties);
            }
        };
    }

    private static void validateResendProvider(EmailProperties properties) {
        if (!properties.resend().enabled()) {
            throw new IllegalStateException("Resend email provider requires RESEND_ENABLED=true");
        }
        if (properties.resend().apiKey() == null || properties.resend().apiKey().isBlank()) {
            throw new IllegalStateException("Resend email provider requires RESEND_API_KEY");
        }
        if (properties.fromAddress() == null || properties.fromAddress().isBlank()) {
            throw new IllegalStateException("Resend email provider requires MERCHTYL_EMAIL_FROM_ADDRESS");
        }
        if (properties.frontendBaseUrl() == null || properties.frontendBaseUrl().isBlank()) {
            throw new IllegalStateException("Resend email provider requires MERCHTYL_FRONTEND_BASE_URL");
        }
    }
}
