package com.merchtyl.email;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailConfigurationTest {
    private final EmailConfiguration configuration = new EmailConfiguration();

    @Test
    void productionRequiresResendProvider() {
        EmailProperties properties = new EmailProperties("console", "", "Merchtyl", "", "",
                new EmailProperties.Retry(5, 30, 2),
                new EmailProperties.Resend(false, ""));
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        environment.setActiveProfiles("prod");

        ApplicationRunner runner = configuration.emailConfigurationValidator(properties, environment);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production email provider must be RESEND");
    }

    @Test
    void resendProviderRequiresKeyWithoutExposingKeyValue() {
        EmailProperties properties = new EmailProperties("resend", "no-reply@notifications.example.test", "Merchtyl", "", "https://app.example.test",
                new EmailProperties.Retry(5, 30, 2),
                new EmailProperties.Resend(true, ""));
        MockEnvironment environment = new MockEnvironment();

        ApplicationRunner runner = configuration.emailConfigurationValidator(properties, environment);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESEND_API_KEY")
                .hasMessageNotContaining("secret");
    }

    @Test
    void resendProviderRequiresKeyBeforeCreatingSender() {
        EmailProperties properties = new EmailProperties("resend", "no-reply@notifications.example.test", "Merchtyl", "", "https://app.example.test",
                new EmailProperties.Retry(5, 30, 2),
                new EmailProperties.Resend(true, ""));

        assertThatThrownBy(() -> configuration.emailSender(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESEND_API_KEY");
    }

    @Test
    void resendProviderCreatesResendSenderWhenConfigured() {
        EmailProperties properties = new EmailProperties("resend", "no-reply@notifications.example.test", "Merchtyl", "", "https://app.example.test",
                new EmailProperties.Retry(5, 30, 2),
                new EmailProperties.Resend(true, "configured-key"));

        EmailSender sender = configuration.emailSender(properties);

        assertThat(sender).isInstanceOf(ResendEmailSender.class);
    }

    @Test
    void consoleProviderCreatesConsoleSenderForLocalTesting() {
        EmailProperties properties = new EmailProperties("console", "", "Merchtyl", "", "",
                new EmailProperties.Retry(5, 30, 2),
                new EmailProperties.Resend(false, ""));

        EmailSender sender = configuration.emailSender(properties);

        assertThat(sender).isInstanceOf(ConsoleEmailSender.class);
    }

    @Test
    void resendProviderRequiresFrontendBaseUrl() {
        EmailProperties properties = new EmailProperties("resend", "no-reply@notifications.example.test", "Merchtyl", "", "",
                new EmailProperties.Retry(5, 30, 2),
                new EmailProperties.Resend(true, "configured-key"));
        MockEnvironment environment = new MockEnvironment();

        ApplicationRunner runner = configuration.emailConfigurationValidator(properties, environment);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MERCHTYL_FRONTEND_BASE_URL")
                .hasMessageNotContaining("configured-key");
    }
}
