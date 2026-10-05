package com.merchtyl.publiccontact;

import com.merchtyl.common.BadRequestException;
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
    private final PublicContactService service = new PublicContactService(sender, email, contact,
            Clock.fixed(Instant.parse("2026-10-04T16:30:00Z"), ZoneOffset.UTC));

    @Test
    void sendsEscapedContactEmailToConfiguredRecipientWithVisitorReplyTo() {
        when(sender.send(org.mockito.ArgumentMatchers.any())).thenReturn(EmailSendResult.accepted(EmailProvider.CONSOLE, "accepted"));

        PublicContactResponse response = service.submit(request("Ada <script>", ""));

        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(message.capture());
        assertThat(response.success()).isTrue();
        assertThat(message.getValue().to()).extracting(recipient -> recipient.email())
                .containsExactly("sales-team@merchtyl.test");
        assertThat(message.getValue().fromAddress()).isEqualTo("website@merchtyl.test");
        assertThat(message.getValue().replyTo()).isEqualTo("visitor@example.test");
        assertThat(message.getValue().htmlBody()).contains("Ada &lt;script&gt;").doesNotContain("Ada <script>");
        assertThat(message.getValue().textBody()).contains("Submitted:\n2026-10-04T16:30:00Z");
    }

    @Test
    void silentlyAcceptsHoneypotWithoutSending() {
        PublicContactResponse response = service.submit(request("Ada Lovelace", "https://spam.invalid"));

        assertThat(response.success()).isTrue();
        verify(sender, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void convertsProviderFailureToSafeServiceFailure() {
        when(sender.send(org.mockito.ArgumentMatchers.any())).thenReturn(
                EmailSendResult.failed(EmailProvider.RESEND, "PROVIDER_FAILURE", "private provider detail", true));

        assertThatThrownBy(() -> service.submit(request("Ada Lovelace", "")))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageNotContaining("private provider detail");
    }

    @Test
    void rejectsProvinceOutsideFormContract() {
        PublicContactRequest invalid = new PublicContactRequest("Ada Lovelace", "Analytical Engines", "visitor@example.test",
                "", "retail", "Atlantis", "1", "1", List.of(), "Hello", false, "");

        assertThatThrownBy(() -> service.submit(invalid)).isInstanceOf(BadRequestException.class);
        verify(sender, never()).send(org.mockito.ArgumentMatchers.any());
    }

    private static PublicContactRequest request(String name, String website) {
        return new PublicContactRequest(name, "Analytical Engines", "visitor@example.test", "+1 506 555 0100",
                "retail", "New Brunswick", "1", "2", List.of("Retail POS", "Inventory and reporting"),
                "Please contact me.", true, website);
    }
}
