package com.merchtyl.config;

import com.merchtyl.platform.testing.TestUserProvisioningProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@Configuration
@Profile({"prod", "production"})
public class ProductionConfigurationValidator {
    @Bean
    ApplicationRunner validateProductionConfiguration(
            Environment environment,
            CorsProperties cors,
            TestUserProvisioningProperties testProvisioning) {
        return args -> {
            String databaseUrl = required(environment, "spring.datasource.url", "SPRING_DATASOURCE_URL");
            String databaseUsername = required(environment, "spring.datasource.username", "SPRING_DATASOURCE_USERNAME");
            String databasePassword = required(environment, "spring.datasource.password", "SPRING_DATASOURCE_PASSWORD");
            requireDatabaseTls(databaseUrl);
            rejectDefaultCredential(databaseUsername, "SPRING_DATASOURCE_USERNAME");
            rejectDefaultCredential(databasePassword, "SPRING_DATASOURCE_PASSWORD");

            required(environment, "merchtyl.jwt.secret", "MERCHTYL_JWT_SECRET");
            required(environment, "merchtyl.portal.public-base-domain", "MERCHTYL_PUBLIC_BASE_DOMAIN");
            required(environment, "merchtyl.portal.platform-base-url", "MERCHTYL_PLATFORM_BASE_URL");
            required(environment, "merchtyl.email.frontend-base-url", "MERCHTYL_FRONTEND_BASE_URL");

            List<String> origins = cors.exactOrigins();
            if (origins.isEmpty()) {
                throw new IllegalStateException("Production requires MERCHTYL_CORS_ALLOWED_ORIGINS");
            }
            for (String origin : origins) {
                requireRestrictedHttpsOrigin(origin);
            }
            for (String pattern : cors.originPatterns()) {
                if (pattern.contains("*")) {
                    throw new IllegalStateException("Production CORS origin patterns must not contain wildcards");
                }
                requireRestrictedHttpsOrigin(pattern);
            }
            if (testProvisioning.enabled()) {
                throw new IllegalStateException("MERCHTYL_TEST_USER_PROVISIONING_ENABLED must be false in production");
            }
        };
    }

    private static String required(Environment environment, String property, String variable) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Production requires " + variable);
        }
        return value.trim();
    }

    private static void requireDatabaseTls(String url) {
        String normalized = url.toLowerCase(Locale.ROOT);
        boolean tls = normalized.contains("sslmode=require")
                || normalized.contains("sslmode=verify-ca")
                || normalized.contains("sslmode=verify-full")
                || normalized.matches(".*[?&]ssl=true(?:&.*)?$");
        if (!tls) {
            throw new IllegalStateException("SPRING_DATASOURCE_URL must require PostgreSQL TLS in production (sslmode=require or verify-full)");
        }
    }

    private static void rejectDefaultCredential(String value, String variable) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("postgres") || normalized.equals("password") || normalized.equals("admin")
                || normalized.startsWith("replace_with_") || normalized.startsWith("change-me")) {
            throw new IllegalStateException(variable + " must not use a default or placeholder credential in production");
        }
    }

    private static void requireRestrictedHttpsOrigin(String origin) {
        URI parsed;
        try {
            parsed = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Production CORS origins must be valid HTTPS origins", exception);
        }
        if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null
                || parsed.getUserInfo() != null || parsed.getPath() != null && !parsed.getPath().isEmpty()
                || parsed.getQuery() != null || parsed.getFragment() != null || origin.contains("*")) {
            throw new IllegalStateException("Production CORS origins must be explicit HTTPS origins without paths or wildcards");
        }
    }
}
