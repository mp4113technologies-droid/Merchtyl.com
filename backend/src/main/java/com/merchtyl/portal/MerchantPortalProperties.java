package com.merchtyl.portal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "merchtyl.portal")
public record MerchantPortalProperties(String publicBaseDomain, String platformBaseUrl) {
    public MerchantPortalProperties {
        publicBaseDomain = clean(publicBaseDomain, "localhost");
        platformBaseUrl = clean(platformBaseUrl, "http://localhost:5173");
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().replaceAll("^https?://|/+$", "");
    }
}
