package com.merchtyl.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsoleEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    public ConsoleEmailSender(EmailProperties properties) {
        // Keep the shared sender constructor shape; console delivery never reads credentials.
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        log.info("console_email provider=CONSOLE recipient=[REDACTED] template={}", message.templateCode());
        return EmailSendResult.accepted(EmailProvider.CONSOLE, "console-" + System.currentTimeMillis());
    }

    @Override
    public EmailProvider provider() {
        return EmailProvider.CONSOLE;
    }

}
