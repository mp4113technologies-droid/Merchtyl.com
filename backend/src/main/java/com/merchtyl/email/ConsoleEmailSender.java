package com.merchtyl.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

public class ConsoleEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    private final EmailProperties properties;

    public ConsoleEmailSender(EmailProperties properties) {
        this.properties = properties;
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        String recipients = message.to().stream()
                .map(EmailRecipient::email)
                .collect(Collectors.joining(","));
        String localActivationUrl = message.metadata() == null ? null : message.metadata().get("activationUrl");
        log.info("console_email provider=CONSOLE recipient={} subject={} template={} activation_url={}",
                recipients,
                safe(message.subject()),
                message.templateCode(),
                properties.consoleProvider() ? safe(localActivationUrl) : "[hidden]");
        return EmailSendResult.accepted(EmailProvider.CONSOLE, "console-" + System.currentTimeMillis());
    }

    @Override
    public EmailProvider provider() {
        return EmailProvider.CONSOLE;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ");
    }
}
