package com.merchtyl.email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;

public class ResendEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final EmailProperties properties;
    private final ResendEmailClient client;

    public ResendEmailSender(EmailProperties properties) {
        this(properties, sdkClient(properties.resend().apiKey()));
    }

    ResendEmailSender(EmailProperties properties, ResendEmailClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        long started = System.nanoTime();
        try {
            CreateEmailOptions.Builder builder = CreateEmailOptions.builder()
                    .from(fromValue(message))
                    .to(message.to().stream().map(EmailRecipient::email).toList())
                    .subject(message.subject())
                    .html(message.htmlBody())
                    .text(message.textBody());
            if (message.replyTo() != null && !message.replyTo().isBlank()) {
                builder.replyTo(List.of(message.replyTo()));
            }
            CreateEmailResponse response = client.send(builder.build());
            log.info("external_api_call provider=RESEND operation=send_email duration_ms={} http_status={} success=true",
                    durationMs(started),
                    202);
            return EmailSendResult.accepted(EmailProvider.RESEND, response == null ? null : response.getId());
        } catch (ResendException exception) {
            int status = exception.getStatusCode() == null ? 0 : exception.getStatusCode();
            log.warn("external_api_call provider=RESEND operation=send_email duration_ms={} http_status={} success=false failure_code={}",
                    durationMs(started),
                    status,
                    failureCode(status, exception.getErrorName()));
            return EmailSendResult.failed(
                    EmailProvider.RESEND,
                    failureCode(status, exception.getErrorName()),
                    sanitizedMessage(status, exception),
                    status == 429 || status >= 500);
        } catch (RuntimeException exception) {
            boolean retryable = isTransient(exception);
            log.warn("external_api_call provider=RESEND operation=send_email duration_ms={} http_status={} success=false failure_code={}",
                    durationMs(started),
                    0,
                    retryable ? "NETWORK_TRANSIENT" : "RESEND_SEND_FAILED");
            return EmailSendResult.failed(
                    EmailProvider.RESEND,
                    retryable ? "NETWORK_TRANSIENT" : "RESEND_SEND_FAILED",
                    retryable ? "Email provider is temporarily unavailable" : "Email provider request failed",
                    retryable);
        }
    }

    @Override
    public EmailProvider provider() {
        return EmailProvider.RESEND;
    }

    private String fromValue(EmailMessage message) {
        String fromAddress = message.fromAddress() == null || message.fromAddress().isBlank()
                ? properties.fromAddress()
                : message.fromAddress();
        String fromName = message.fromName() == null || message.fromName().isBlank()
                ? properties.fromName()
                : message.fromName();
        if (fromName == null || fromName.isBlank()) {
            return fromAddress;
        }
        return fromName.replaceAll("[\\r\\n<>]", "") + " <" + fromAddress + ">";
    }

    private static ResendEmailClient sdkClient(String apiKey) {
        Resend resend = new Resend(apiKey);
        return options -> resend.emails().send(options);
    }

    private static String failureCode(int status, String errorName) {
        if (status > 0) {
            return "RESEND_" + status;
        }
        if (errorName != null && !errorName.isBlank()) {
            return "RESEND_" + errorName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        }
        return "RESEND_ERROR";
    }

    private static String sanitizedMessage(int status, ResendException exception) {
        String rawMessage = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        if ((status == 400 || status == 403) && rawMessage.contains("domain") && rawMessage.contains("not verified")) {
            return "Configured sender domain is not verified with the email provider";
        }
        return switch (status) {
            case 400 -> "Email provider rejected the request";
            case 401 -> "Email provider authentication is missing";
            case 403 -> "Email provider authentication is unauthorized";
            case 429 -> "Email provider rate limit reached";
            default -> status >= 500 ? "Email provider service is temporarily unavailable" : "Email provider request failed";
        };
    }

    private static boolean isTransient(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof ConnectException) {
                return true;
            }
            String name = current.getClass().getName().toLowerCase(Locale.ROOT);
            if (name.contains("timeout") || name.contains("connect")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static long durationMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
