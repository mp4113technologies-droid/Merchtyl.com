package com.merchtyl.config;

import com.merchtyl.platform.testing.TestUserProvisioningProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTest {
    private final ProductionConfigurationValidator validator = new ProductionConfigurationValidator();

    @Test
    void acceptsRestrictedTlsProductionConfiguration() {
        assertThatCode(() -> runner(environment("jdbc:postgresql://db.example/app?sslmode=verify-full"),
                new CorsProperties("https://app.example", ""), false).run(null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsDatabaseConnectionsWithoutTls() {
        assertThatThrownBy(() -> runner(environment("jdbc:postgresql://db.example/app"),
                new CorsProperties("https://app.example", ""), false).run(null))
                .hasMessageContaining("must require PostgreSQL TLS");
    }

    @Test
    void rejectsWildcardCorsAndProductionTestProvisioning() {
        assertThatThrownBy(() -> runner(environment("jdbc:postgresql://db.example/app?sslmode=require"),
                new CorsProperties("https://*.example", ""), false).run(null))
                .hasMessageContaining("without paths or wildcards");
        assertThatThrownBy(() -> runner(environment("jdbc:postgresql://db.example/app?sslmode=require"),
                new CorsProperties("https://app.example", ""), true).run(null))
                .hasMessageContaining("must be false in production");
    }

    private ApplicationRunner runner(MockEnvironment environment, CorsProperties cors, boolean testProvisioning) {
        return validator.validateProductionConfiguration(environment, cors,
                new TestUserProvisioningProperties(testProvisioning, "", ""));
    }

    private static MockEnvironment environment(String databaseUrl) {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", databaseUrl)
                .withProperty("spring.datasource.username", "application_user")
                .withProperty("spring.datasource.password", "a-unique-production-password")
                .withProperty("merchtyl.jwt.secret", "a-unique-production-jwt-secret-at-least-32-characters")
                .withProperty("merchtyl.portal.public-base-domain", "example.com")
                .withProperty("merchtyl.portal.platform-base-url", "https://app.example")
                .withProperty("merchtyl.email.frontend-base-url", "https://app.example");
    }
}
