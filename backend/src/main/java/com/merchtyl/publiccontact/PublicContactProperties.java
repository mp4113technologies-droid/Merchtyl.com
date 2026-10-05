package com.merchtyl.publiccontact;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "merchtyl.contact")
public record PublicContactProperties(String emailTo, String emailFrom) {
    public PublicContactProperties {
        emailTo = emailTo == null ? "" : emailTo.trim();
        emailFrom = emailFrom == null ? "" : emailFrom.trim();
    }
}
