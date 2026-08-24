package com.merchtyl.email;

public record RenderedEmailTemplate(
        String subject,
        String htmlBody,
        String textBody
) {
}
