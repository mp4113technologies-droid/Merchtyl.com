package com.merchtyl.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminActivationUrlTest {
    @Test
    void usesNormalizedFrontendBaseUrlAndCanonicalRoute() {
        EmailProperties properties = new EmailProperties("console", "", "Merchtyl", "",
                "https://merchtyl.example///", null, null);

        assertThat(properties.platformAdminActivationUrl("secret-token"))
                .isEqualTo("https://merchtyl.example/activate-platform-admin?token=secret-token");
    }
}
