package com.merchtyl.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesBindingTest {
    @Test
    void bindsCanonicalRecordConstructorWithNestedResetProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "merchtyl.security.login.max-failed-attempts", "3",
                "merchtyl.security.password-reset.token-expiry-minutes", "30",
                "merchtyl.security.password-reset.forgot-max-per-hour", "5",
                "merchtyl.security.password-reset.admin-max-per-hour", "5"
        )));

        SecurityProperties properties = Binder.get(environment)
                .bind("merchtyl.security", Bindable.of(SecurityProperties.class))
                .orElseThrow(() -> new AssertionError("Security properties did not bind"));

        assertThat(properties.login().maxFailedAttempts()).isEqualTo(3);
        assertThat(properties.passwordReset().tokenExpiryMinutes()).isEqualTo(30);
    }
}
