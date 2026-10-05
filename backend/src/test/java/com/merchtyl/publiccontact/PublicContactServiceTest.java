package com.merchtyl.publiccontact;

import com.merchtyl.common.ServiceUnavailableException;
import com.merchtyl.email.EmailMessage;
import com.merchtyl.email.EmailProperties;
import com.merchtyl.email.EmailProvider;
import com.merchtyl.email.EmailSendResult;
import com.merchtyl.email.EmailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicContactServiceTest {
    private final EmailSender sender = mock(EmailSender.class);
    private final EmailProperties email = new EmailProperties("console", "default@merchtyl.test", "Merchtyl", "", "", null, null);
    private final PublicContactProperties contact = new PublicContactProperties("sales-team@merchtyl.test", "website@merchtyl.test");
    private final PublicGeographyService geography = mock(PublicGeographyService.class);
    private final PublicContactService service = new PublicContactService(sender, email, contact, geography,
            Clock.fixed(Instant.parse("2026-10-04T16:30:00Z"), ZoneOffset.UTC));

    @Test
    void sendsEscapedContactEmailToConfiguredRecipientWithVisitorReplyTo() {
        when(sender.send(org.mockito.ArgumentMatchers.any())).thenReturn(EmailSendResult.accepted(EmailProvider.CONSOLE, "accepted"));
        geography("CA", "NB", "Canada", "New Brunswick", "Province / Territory");

        PublicContactResponse response = service.submit(request("Ada <script>", ""));

        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(message.capture());
        assertThat(response.success()).isTrue();
        assertThat(message.getValue().to()).extracting(recipient -> recipient.email())
                .containsExactly("sales-team@merchtyl.test");
        assertThat(message.getValue().fromAddress()).isEqualTo("website@merchtyl.test");
        assertThat(message.getValue().replyTo()).isEqualTo("visitor@example.test");
        assertThat(message.getValue().subject()).isEqualTo("New Merchtyl Quote Request — Analytical Engines");
        assertThat(message.getValue().htmlBody()).contains("Ada &lt;script&gt;").doesNotContain("Ada <script>");
        assertThat(message.getValue().htmlBody()).contains("background:#0B1F33", "New quote request",
                "Canada &middot; New Brunswick", "mailto:visitor@example.test", "tel:+15065550100",
                "Reply to lead", "Inventory &amp; Reporting", "Please contact me.");
        assertThat(message.getValue().textBody()).contains("NEW MERCHTYL QUOTE REQUEST",
                "Canada — New Brunswick", "INTERESTED IN", "- Inventory & Reporting",
                "Submitted: 2026-10-04T16:30:00Z");
    }

    @Test
    void silentlyAcceptsHoneypotWithoutSending() {
        PublicContactResponse response = service.submit(request("Ada Lovelace", "https://spam.invalid"));

        assertThat(response.success()).isTrue();
        verify(sender, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void convertsProviderFailureToSafeServiceFailure() {
        geography("CA", "NB", "Canada", "New Brunswick", "Province / Territory");
        when(sender.send(org.mockito.ArgumentMatchers.any())).thenReturn(
                EmailSendResult.failed(EmailProvider.RESEND, "PROVIDER_FAILURE", "private provider detail", true));

        assertThatThrownBy(() -> service.submit(request("Ada Lovelace", "")))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageNotContaining("private provider detail");
    }

    @Test
    void rendersUnitedStatesFriendlyNames() {
        geography("US", "NY", "United States", "New York", "State");
        when(sender.send(org.mockito.ArgumentMatchers.any())).thenReturn(EmailSendResult.accepted(EmailProvider.CONSOLE, "accepted"));
        service.submit(request("Ada Lovelace", "", "US", "NY"));
        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().htmlBody()).contains("United States &middot; New York");
        assertThat(message.getValue().textBody()).contains("United States — New York");
    }

    @Test
    void omitsBlankOptionalSectionsAndRendersOneNeed() {
        geography("CA", "BC", "Canada", "British Columbia", "Province / Territory");
        when(sender.send(org.mockito.ArgumentMatchers.any())).thenReturn(EmailSendResult.accepted(EmailProvider.CONSOLE, "accepted"));
        PublicContactRequest request = new PublicContactRequest("Ada Lovelace", "Analytical Engines",
                "visitor@example.test", "", "convenience", "CA", "BC", "1", "", List.of("Retail POS"),
                "", false, "");
        service.submit(request);

        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().htmlBody())
                .contains("Convenience Store", "Canada &middot; British Columbia", "Retail POS")
                .doesNotContain(">Phone<", "Registers per Store", ">Message<", "Live Demo");
        assertThat(message.getValue().textBody())
                .contains("- Retail POS")
                .doesNotContain("Phone:", "Registers per Store:", "\nMESSAGE\n", "- Live Demo");
    }

    @Test
    void escapesSpecialCharactersAndPreservesLongMessageLineBreaks() {
        geography("US", "CA", "United States", "California", "State");
        when(sender.send(org.mockito.ArgumentMatchers.any())).thenReturn(EmailSendResult.accepted(EmailProvider.CONSOLE, "accepted"));
        String business = "A & B <Markets> " + "x".repeat(120);
        String longMessage = "First <script>alert(1)</script> & line\n" + "z".repeat(1500);
        PublicContactRequest request = new PublicContactRequest("Ada & <Lead>", business,
                "visitor@example.test", "+1 (212) 555-0100", "retail", "US", "CA", "2–3", "3–5",
                List.of("Retail POS", "Lottery", "Inventory and reporting"), longMessage, false, "");
        service.submit(request);

        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().htmlBody())
                .contains("A &amp; B &lt;Markets&gt;", "First &lt;script&gt;alert(1)&lt;/script&gt; &amp; line<br>")
                .doesNotContain("<script>alert(1)</script>");
        assertThat(message.getValue().textBody()).contains(longMessage, "- Retail POS", "- Lottery", "- Inventory & Reporting");
    }

    @Test
    void fallsBackToLeadNameInSubjectWhenBusinessIsBlank() {
        geography("CA", "NB", "Canada", "New Brunswick", "Province / Territory");
        when(sender.send(org.mockito.ArgumentMatchers.any())).thenReturn(EmailSendResult.accepted(EmailProvider.CONSOLE, "accepted"));
        PublicContactRequest request = new PublicContactRequest("Ada Lovelace", "", "visitor@example.test", "",
                "other", "CA", "NB", "", "", List.of(), "", false, "");
        service.submit(request);

        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().subject()).isEqualTo("New Merchtyl Quote Request — Ada Lovelace");
        assertThat(message.getValue().htmlBody()).contains(">Ada Lovelace</div>");
    }

    private static PublicContactRequest request(String name, String website) {
        return request(name, website, "CA", "NB");
    }

    private static PublicContactRequest request(String name, String website, String countryCode, String regionCode) {
        return new PublicContactRequest(name, "Analytical Engines", "visitor@example.test", "+1 506 555 0100",
                "retail", countryCode, regionCode, "1", "2", List.of("Retail POS", "Inventory and reporting"),
                "Please contact me.", true, website);
    }

    private void geography(String countryCode, String regionCode, String countryName, String regionName,
            String regionLabel) {
        when(geography.validate(countryCode, regionCode)).thenReturn(
                new PublicGeographyService.GeographySelection(countryCode, countryName, regionCode, regionName, regionLabel));
    }
}
