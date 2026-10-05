package com.merchtyl.publiccontact;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Configuration
public class PublicContactConfiguration {
    @Bean
    ApplicationRunner publicContactConfigurationValidator(PublicContactProperties properties, Environment environment) {
        return args -> {
            boolean production = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
            if (production && properties.emailTo().isBlank()) {
                throw new IllegalStateException("Public contact form requires MERCHTYL_CONTACT_EMAIL_TO in production");
            }
        };
    }
}
