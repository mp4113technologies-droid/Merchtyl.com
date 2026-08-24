package com.merchtyl.email;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRendererTest {
    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    @Test
    void rendersHtmlAndTextWithEscapedValues() {
        RenderedEmailTemplate rendered = renderer.render(EmailTemplateCode.MERCHANT_OWNER_ACTIVATION, Map.of(
                "ownerName", "<Owner>",
                "merchantOperatingName", "Test & Shop",
                "activationUrl", "https://app.test/activate-owner?token=raw",
                "expiresAt", "2026-08-04T00:00:00Z"));

        assertThat(rendered.subject()).isEqualTo("Activate your Merchtyl merchant account");
        assertThat(rendered.htmlBody()).contains("&lt;Owner&gt;");
        assertThat(rendered.htmlBody()).contains("Test &amp; Shop");
        assertThat(rendered.htmlBody()).doesNotContain("<Owner>");
        assertThat(rendered.textBody()).contains("https://app.test/activate-owner?token=raw");
    }
}
