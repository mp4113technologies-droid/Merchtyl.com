package com.merchtyl.publiccontact;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PublicContactService {
    private static final Logger log = LoggerFactory.getLogger(PublicContactService.class);
    private final EmailSender emailSender;
    private final EmailProperties emailProperties;
    private final PublicContactProperties contactProperties;
    private final PublicGeographyService geographyService;
    private final Clock clock;

    @Autowired
    public PublicContactService(EmailSender emailSender, EmailProperties emailProperties,
            PublicContactProperties contactProperties, PublicGeographyService geographyService) {
        this(emailSender, emailProperties, contactProperties, geographyService, Clock.systemUTC());
    }

    PublicContactService(EmailSender emailSender, EmailProperties emailProperties,
            PublicContactProperties contactProperties, PublicGeographyService geographyService, Clock clock) {
        this.emailSender = emailSender;
        this.emailProperties = emailProperties;
        this.contactProperties = contactProperties;
        this.geographyService = geographyService;
        this.clock = clock;
    }

    public PublicContactResponse submit(PublicContactRequest request) {
        log.info("contact_event event=CONTACT_FORM_SUBMITTED correlation_id={}", MDC.get(CorrelationIdFilter.MDC_KEY));
        if (text(request.website()) != null) {
            log.info("contact_event event=CONTACT_FORM_SPAM_DISCARDED correlation_id={}", MDC.get(CorrelationIdFilter.MDC_KEY));
            return PublicContactResponse.accepted();
        }
        PublicGeographyService.GeographySelection geography =
                geographyService.validate(request.countryCode(), request.regionCode());
        if (contactProperties.emailTo().isBlank()) {
            log.error("contact_event event=CONTACT_EMAIL_FAILED reason=recipient_not_configured correlation_id={}",
                    MDC.get(CorrelationIdFilter.MDC_KEY));
            throw new ServiceUnavailableException("Contact recipient is not configured");
        }

        String subjectName = text(request.business()) == null ? request.name().trim() : request.business().trim();
        String subject = "New Merchtyl Quote Request — " + subjectName;
        Instant submittedAt = Instant.now(clock);
        EmailMessage message = new EmailMessage(
                List.of(new EmailRecipient(contactProperties.emailTo(), "Merchtyl Sales")),
                subject,
                html(request, geography, submittedAt),
                plainText(request, geography, submittedAt),
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

    private static String plainText(PublicContactRequest request,
            PublicGeographyService.GeographySelection geography, Instant submittedAt) {
        StringBuilder body = new StringBuilder()
                .append("NEW MERCHTYL QUOTE REQUEST\n\n")
                .append("BUSINESS\n")
                .append(displayBusiness(request)).append('\n')
                .append(businessType(request.businessType())).append('\n')
                .append(geography.countryName()).append(" — ").append(geography.regionName()).append("\n\n")
                .append("CONTACT\n")
                .append("Name: ").append(request.name().trim()).append('\n')
                .append("Email: ").append(request.email().trim()).append('\n');
        appendLine(body, "Phone", request.phone());
        if (text(request.stores()) != null || text(request.registers()) != null) {
            body.append("\nSTORE SETUP\n");
            appendLine(body, "Number of Stores", request.stores());
            appendLine(body, "Registers per Store", request.registers());
        }
        if ((request.needs() != null && !request.needs().isEmpty()) || request.demo()) {
            body.append("\nINTERESTED IN\n");
            if (request.needs() != null) request.needs().forEach(need -> body.append("- ").append(needLabel(need)).append('\n'));
            if (request.demo()) body.append("- Live Demo\n");
        }
        if (text(request.message()) != null) {
            body.append("\nMESSAGE\n").append(request.message().trim()).append("\n");
        }
        return body.append("\nSubmitted from merchtyl.com\n")
                .append("Submitted: ").append(submittedAt).append('\n')
                .append("Location: ").append(geography.countryName()).append(" — ").append(geography.regionName()).append('\n')
                .toString();
    }

    private static String html(PublicContactRequest request,
            PublicGeographyService.GeographySelection geography, Instant submittedAt) {
        String business = escape(displayBusiness(request));
        String email = escape(request.email().trim());
        String location = escape(geography.countryName()) + " &middot; " + escape(geography.regionName());
        StringBuilder body = new StringBuilder(4096);
        body.append("<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>")
                .append("<body style=\"margin:0;padding:0;background:#f4f7fb;color:#0B1F33;font-family:Arial,Helvetica,sans-serif\">")
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background:#f4f7fb\"><tr><td align=\"center\" style=\"padding:28px 12px\">")
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"max-width:640px;background:#ffffff;border:1px solid #e3eaf2;border-radius:12px\">")
                .append("<tr><td style=\"padding:28px 32px;background:#0B1F33;border-radius:12px 12px 0 0\">")
                .append("<div style=\"font-size:13px;line-height:18px;font-weight:700;letter-spacing:2px;color:#69a9ff\">MERCHTYL</div>")
                .append("<h1 style=\"margin:10px 0 0;font-size:26px;line-height:32px;color:#ffffff\">New quote request</h1>")
                .append("<p style=\"margin:8px 0 0;font-size:15px;line-height:22px;color:#d5e5f7\">A new lead submitted the pricing form on merchtyl.com.</p>")
                .append("</td></tr><tr><td style=\"padding:28px 32px\">")
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background:#eef5ff;border-left:4px solid #0568EE;border-radius:8px\"><tr><td style=\"padding:20px\">")
                .append(sectionLabel("Business"))
                .append("<div style=\"margin-top:8px;font-size:22px;line-height:28px;font-weight:700;color:#0B1F33\">").append(business).append("</div>")
                .append("<div style=\"margin-top:4px;font-size:15px;line-height:22px;color:#44566A\">").append(escape(businessType(request.businessType()))).append("</div>")
                .append("<div style=\"margin-top:2px;font-size:15px;line-height:22px;color:#44566A\">").append(location).append("</div>")
                .append("</td></tr></table>")
                .append(sectionHeading("Contact"))
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\">")
                .append(detailRow("Name", escape(request.name())))
                .append("<tr><td style=\"padding:7px 0;color:#68798c;font-size:13px;width:155px\">Email</td><td style=\"padding:7px 0;font-size:15px\"><a href=\"mailto:").append(email).append("\" style=\"color:#0568EE;text-decoration:none\">").append(email).append("</a></td></tr>");
        if (text(request.phone()) != null) {
            String phone = escape(request.phone());
            String telephone = escape(request.phone().replaceAll("[^0-9+]", ""));
            body.append("<tr><td style=\"padding:7px 0;color:#68798c;font-size:13px;width:155px\">Phone</td><td style=\"padding:7px 0;font-size:15px\"><a href=\"tel:")
                    .append(telephone).append("\" style=\"color:#0568EE;text-decoration:none\">").append(phone).append("</a></td></tr>");
        }
        body.append("</table>");
        if (text(request.stores()) != null || text(request.registers()) != null) {
            body.append(sectionHeading("Store setup"))
                    .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"><tr>");
            if (text(request.stores()) != null) body.append(metric("Number of Stores", request.stores()));
            if (text(request.registers()) != null) body.append(metric("Registers per Store", request.registers()));
            body.append("</tr></table>");
        }
        if ((request.needs() != null && !request.needs().isEmpty()) || request.demo()) {
            body.append(sectionHeading("Interested in")).append("<div style=\"font-size:0\">");
            if (request.needs() != null) request.needs().forEach(need -> body.append(badge(needLabel(need))));
            if (request.demo()) body.append(badge("Live Demo"));
            body.append("</div>");
        }
        if (text(request.message()) != null) {
            body.append(sectionHeading("Message"))
                    .append("<div style=\"padding:16px 18px;background:#f6f8fb;border:1px solid #e3eaf2;border-radius:8px;font-size:15px;line-height:23px;color:#273b50\">")
                    .append(escape(request.message()).replace("\n", "<br>"))
                    .append("</div>");
        }
        body.append("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"margin-top:26px\"><tr><td bgcolor=\"#0568EE\" style=\"border-radius:8px\">")
                .append("<a href=\"mailto:").append(email).append("\" style=\"display:inline-block;padding:13px 20px;color:#ffffff;font-size:15px;font-weight:700;text-decoration:none\">Reply to lead</a>")
                .append("</td></tr></table>")
                .append("<div style=\"margin-top:28px;padding-top:18px;border-top:1px solid #e3eaf2;font-size:12px;line-height:18px;color:#7a8999\">Submitted from merchtyl.com<br>")
                .append(escape(submittedAt.toString())).append("<br>").append(location).append("</div>")
                .append("</td></tr><tr><td style=\"padding:18px 32px;background:#f8fafc;border-radius:0 0 12px 12px;font-size:12px;line-height:18px;color:#68798c;text-align:center\">Merchtyl &middot; merchtyl.com</td></tr>")
                .append("</table></td></tr></table></body></html>");
        return body.toString();
    }

    private static String sectionHeading(String label) {
        return "<div style=\"margin:26px 0 9px;font-size:12px;line-height:18px;font-weight:700;letter-spacing:1.2px;text-transform:uppercase;color:#68798c\">"
                + escape(label) + "</div>";
    }

    private static String sectionLabel(String label) {
        return "<div style=\"font-size:12px;line-height:18px;font-weight:700;letter-spacing:1.2px;text-transform:uppercase;color:#0568EE\">"
                + escape(label) + "</div>";
    }

    private static String detailRow(String label, String value) {
        return "<tr><td style=\"padding:7px 0;color:#68798c;font-size:13px;width:155px\">" + escape(label)
                + "</td><td style=\"padding:7px 0;font-size:15px;color:#0B1F33\">" + value + "</td></tr>";
    }

    private static String metric(String label, String value) {
        return "<td valign=\"top\" width=\"50%\" style=\"padding:14px 16px;background:#f6f8fb;border:1px solid #e3eaf2\"><div style=\"font-size:12px;line-height:18px;color:#68798c\">"
                + escape(label) + "</div><div style=\"margin-top:4px;font-size:20px;line-height:26px;font-weight:700;color:#0B1F33\">"
                + escape(value) + "</div></td>";
    }

    private static String badge(String value) {
        return "<span style=\"display:inline-block;margin:0 8px 8px 0;padding:7px 11px;background:#eef5ff;border:1px solid #c9ddfb;border-radius:16px;color:#174f91;font-size:13px;line-height:18px\">"
                + escape(value) + "</span>";
    }

    private static void appendLine(StringBuilder body, String label, String value) {
        String clean = text(value);
        if (clean != null) body.append(label).append(": ").append(clean).append('\n');
    }

    private static String businessType(String value) {
        return switch (value) {
            case "convenience" -> "Convenience Store";
            case "retail" -> "Retail Store";
            case "takeout" -> "Takeout or Quick Service";
            case "retail_restaurant" -> "Retail + Restaurant";
            default -> "Other";
        };
    }

    private static String displayBusiness(PublicContactRequest request) {
        return text(request.business()) == null ? request.name().trim() : request.business().trim();
    }

    private static String needLabel(String value) {
        return "Inventory and reporting".equals(value) ? "Inventory & Reporting" : value;
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value.trim());
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
