package com.merchtyl.publiccontact;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ServiceUnavailableException;
import com.merchtyl.email.EmailMessage;
import com.merchtyl.email.EmailProperties;
import com.merchtyl.email.EmailRecipient;
import com.merchtyl.email.EmailSendResult;
import com.merchtyl.email.EmailSender;
import com.merchtyl.email.EmailTemplateCode;
import com.merchtyl.platform.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PublicContactService {
    private static final Logger log = LoggerFactory.getLogger(PublicContactService.class);
    private static final List<String> PROVINCES = List.of("Alberta", "British Columbia", "Manitoba", "New Brunswick",
            "Newfoundland and Labrador", "Northwest Territories", "Nova Scotia", "Nunavut", "Ontario",
            "Prince Edward Island", "Quebec", "Saskatchewan", "Yukon");

    private final EmailSender emailSender;
    private final EmailProperties emailProperties;
    private final PublicContactProperties contactProperties;
    private final Clock clock;

    @Autowired
    public PublicContactService(EmailSender emailSender, EmailProperties emailProperties,
            PublicContactProperties contactProperties) {
        this(emailSender, emailProperties, contactProperties, Clock.systemUTC());
    }

    PublicContactService(EmailSender emailSender, EmailProperties emailProperties,
            PublicContactProperties contactProperties, Clock clock) {
        this.emailSender = emailSender;
        this.emailProperties = emailProperties;
        this.contactProperties = contactProperties;
        this.clock = clock;
    }

    public PublicContactResponse submit(PublicContactRequest request) {
        log.info("contact_event event=CONTACT_FORM_SUBMITTED correlation_id={}", MDC.get(CorrelationIdFilter.MDC_KEY));
        if (text(request.website()) != null) {
            log.info("contact_event event=CONTACT_FORM_SPAM_DISCARDED correlation_id={}", MDC.get(CorrelationIdFilter.MDC_KEY));
            return PublicContactResponse.accepted();
        }
        if (text(request.province()) != null && !PROVINCES.contains(request.province().trim())) {
            throw new BadRequestException("VALIDATION_FAILED: Unsupported province or territory");
        }
        if (contactProperties.emailTo().isBlank()) {
            log.error("contact_event event=CONTACT_EMAIL_FAILED reason=recipient_not_configured correlation_id={}",
                    MDC.get(CorrelationIdFilter.MDC_KEY));
            throw new ServiceUnavailableException("Contact recipient is not configured");
        }

        String subjectName = text(request.business()) == null ? request.name().trim() : request.business().trim();
        String subject = "New Merchtyl Website Inquiry — " + subjectName;
        Map<String, String> fields = fields(request);
        EmailMessage message = new EmailMessage(
                List.of(new EmailRecipient(contactProperties.emailTo(), "Merchtyl Sales")),
                subject,
                html(fields),
                plainText(fields),
                contactProperties.emailFrom().isBlank() ? emailProperties.fromAddress() : contactProperties.emailFrom(),
                emailProperties.fromName(),
                request.email().trim().toLowerCase(Locale.ROOT),
                EmailTemplateCode.PUBLIC_CONTACT,
                MDC.get(CorrelationIdFilter.MDC_KEY),
                Map.of("source", "merchtyl.com"));
        EmailSendResult result = emailSender.send(message);
        if (!result.success()) {
            log.warn("contact_event event=CONTACT_EMAIL_FAILED provider={} failure_code={} correlation_id={}",
                    result.provider(), result.failureCode(), MDC.get(CorrelationIdFilter.MDC_KEY));
            throw new ServiceUnavailableException("Contact email was not accepted");
        }
        log.info("contact_event event=CONTACT_EMAIL_ACCEPTED provider={} correlation_id={}",
                result.provider(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return PublicContactResponse.accepted();
    }

    private Map<String, String> fields(PublicContactRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        put(fields, "Name", request.name());
        put(fields, "Business", request.business());
        put(fields, "Email", request.email());
        put(fields, "Phone", request.phone());
        put(fields, "Business Type", request.businessType());
        put(fields, "Province or Territory", request.province());
        put(fields, "Number of Stores", request.stores());
        put(fields, "Registers per Store", request.registers());
        if (request.needs() != null && !request.needs().isEmpty()) put(fields, "Needs", String.join(", ", request.needs()));
        put(fields, "Live Demo", request.demo() ? "Yes" : "No");
        put(fields, "Message", request.message());
        put(fields, "Submitted", Instant.now(clock).toString());
        put(fields, "Source", "merchtyl.com");
        return fields;
    }

    private static void put(Map<String, String> fields, String label, String value) {
        String clean = text(value);
        if (clean != null) fields.put(label, clean);
    }

    private static String plainText(Map<String, String> fields) {
        StringBuilder body = new StringBuilder("New inquiry from merchtyl.com\n\n");
        fields.forEach((label, value) -> body.append(label).append(":\n").append(value).append("\n\n"));
        return body.toString();
    }

    private static String html(Map<String, String> fields) {
        StringBuilder body = new StringBuilder("<h1>New inquiry from merchtyl.com</h1><dl>");
        fields.forEach((label, value) -> body.append("<dt><strong>").append(HtmlUtils.htmlEscape(label))
                .append("</strong></dt><dd style=\"margin:0 0 16px\">")
                .append(HtmlUtils.htmlEscape(value).replace("\n", "<br>"))
                .append("</dd>"));
        return body.append("</dl>").toString();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
