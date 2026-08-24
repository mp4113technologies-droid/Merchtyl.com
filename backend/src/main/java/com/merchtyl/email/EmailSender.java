package com.merchtyl.email;

public interface EmailSender {
    EmailSendResult send(EmailMessage message);

    EmailProvider provider();
}
