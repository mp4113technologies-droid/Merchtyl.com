package com.merchtyl.email;

import com.merchtyl.common.BadRequestException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class EmailTemplateRenderer {
    public RenderedEmailTemplate render(EmailTemplateCode templateCode, Map<String, String> values) {
        String baseName = switch (templateCode) {
            case MERCHANT_OWNER_ACTIVATION -> "merchant-owner-activation";
            case MERCHANT_OWNER_INVITATION_RESEND -> "merchant-owner-invitation-resend";
            case MERCHANT_OWNER_TEMPORARY_CREDENTIALS -> "merchant-owner-temporary-credentials";
            case MERCHANT_OWNER_TEMPORARY_CREDENTIALS_RESEND -> "merchant-owner-temporary-credentials";
            case PASSWORD_RESET -> "password-reset";
            case MERCHANT_SUSPENDED -> "merchant-suspended";
            case MERCHANT_REACTIVATED -> "merchant-reactivated";
            case TEST_EMAIL -> "test-email";
        };
        String html = renderResource("templates/email/" + baseName + ".html", values, true);
        String text = renderResource("templates/email/" + baseName + ".txt", values, false);
        return new RenderedEmailTemplate(subject(templateCode), html, text);
    }

    private String renderResource(String path, Map<String, String> values, boolean html) {
        String template = read(path);
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            rendered = rendered.replace("{{" + entry.getKey() + "}}", html ? HtmlUtils.htmlEscape(value) : value);
        }
        return rendered;
    }

    private static String read(String path) {
        try (var reader = new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException exception) {
            throw new BadRequestException("Email template is unavailable: " + path);
        }
    }

    private static String subject(EmailTemplateCode templateCode) {
        return switch (templateCode) {
            case MERCHANT_OWNER_ACTIVATION -> "Activate your Merchtyl merchant account";
            case MERCHANT_OWNER_INVITATION_RESEND -> "Your new Merchtyl activation link";
            case MERCHANT_OWNER_TEMPORARY_CREDENTIALS -> "Your temporary Merchtyl login details";
            case MERCHANT_OWNER_TEMPORARY_CREDENTIALS_RESEND -> "Your new temporary Merchtyl login details";
            case PASSWORD_RESET -> "Reset your Merchtyl password";
            case MERCHANT_SUSPENDED -> "Your Merchtyl merchant account has been suspended";
            case MERCHANT_REACTIVATED -> "Your Merchtyl merchant account has been reactivated";
            case TEST_EMAIL -> "Merchtyl test email";
        };
    }
}
