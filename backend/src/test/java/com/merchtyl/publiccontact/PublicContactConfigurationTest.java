package com.merchtyl.publiccontact;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicContactConfigurationTest {
    private final PublicContactConfiguration configuration = new PublicContactConfiguration();

    @Test
    void productionRequiresConfiguredRecipient() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> configuration.publicContactConfigurationValidator(
                new PublicContactProperties("", ""), environment).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MERCHTYL_CONTACT_EMAIL_TO");
    }

    @Test
    void localConsoleDevelopmentDoesNotRequireRecipientAtStartup() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThatCode(() -> configuration.publicContactConfigurationValidator(
                new PublicContactProperties("", ""), environment).run(null)).doesNotThrowAnyException();
    }
}
