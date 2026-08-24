package com.merchtyl.email;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ResendEmailSenderTest {
    @Test
    void mapsSuccessfulSendToProviderMessageId() {
        ResendEmailSender sender = new ResendEmailSender(properties(), options -> new CreateEmailResponse("resend-message-123"));

        EmailSendResult result = sender.send(message());

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(EmailProvider.RESEND);
        assertThat(result.providerMessageId()).isEqualTo("resend-message-123");
    }

    @Test
    void logsExternalEmailProviderCallWithoutEmailBodyOrSecrets(CapturedOutput output) {
        ResendEmailSender sender = new ResendEmailSender(properties(), options -> new CreateEmailResponse("resend-message-123"));

        sender.send(message());

        assertThat(output).contains("external_api_call provider=RESEND");
        assertThat(output).contains("operation=send_email");
        assertThat(output).contains("http_status=202");
        assertThat(output).doesNotContain("<p>Hello</p>");
        assertThat(output).doesNotContain("test-key");
    }

    @Test
    void classifiesRateLimitAsRetryable() {
        ResendEmailSender sender = new ResendEmailSender(properties(), options -> {
            throw new ResendException("rate limited raw provider body", 429, "rate_limit");
        });

        EmailSendResult result = sender.send(message());

        assertThat(result.success()).isFalse();
        assertThat(result.failureCode()).isEqualTo("RESEND_429");
        assertThat(result.failureMessage()).isEqualTo("Email provider rate limit reached");
        assertThat(result.failureMessage()).doesNotContain("raw provider body");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void classifiesUnauthorizedAsNonRetryable() {
        ResendEmailSender sender = new ResendEmailSender(properties(), options -> {
            throw new ResendException("invalid secret key", 403, "forbidden");
        });

        EmailSendResult result = sender.send(message());

        assertThat(result.failureCode()).isEqualTo("RESEND_403");
        assertThat(result.failureMessage()).isEqualTo("Email provider authentication is unauthorized");
        assertThat(result.failureMessage()).doesNotContain("secret");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void classifiesUnverifiedSenderDomainAsSafeActionableFailure() {
        ResendEmailSender sender = new ResendEmailSender(properties(), options -> {
            throw new ResendException("The gmail.com domain is not verified. Please, add and verify your domain.", 403, "validation_error");
        });

        EmailSendResult result = sender.send(message());

        assertThat(result.failureCode()).isEqualTo("RESEND_403");
        assertThat(result.failureMessage()).isEqualTo("Configured sender domain is not verified with the email provider");
        assertThat(result.failureMessage()).doesNotContain("gmail.com");
        assertThat(result.retryable()).isFalse();
    }

    private static EmailProperties properties() {
        return new EmailProperties("resend", "no-reply@notifications.example.test", "Merchtyl", "support@example.test", "https://app.example.test",
                new EmailProperties.Retry(5, 30, 2),
                new EmailProperties.Resend(true, "test-key"));
    }

    private static EmailMessage message() {
        return new EmailMessage(
                List.of(new EmailRecipient("owner@example.test", "Owner")),
                "Subject",
                "<p>Hello</p>",
                "Hello",
                "no-reply@notifications.example.test",
                "Merchtyl",
                "support@example.test",
                EmailTemplateCode.MERCHANT_OWNER_ACTIVATION,
                "correlation",
                Map.of());
    }
}
