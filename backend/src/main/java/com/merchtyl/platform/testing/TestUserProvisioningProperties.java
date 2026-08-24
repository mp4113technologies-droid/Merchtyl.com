package com.merchtyl.platform.testing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "merchtyl.testing.user-provisioning")
public record TestUserProvisioningProperties(
        boolean enabled,
        String key,
        String defaultUserPassword
) {
    public TestUserProvisioningProperties {
        key = key == null ? "" : key;
        defaultUserPassword = defaultUserPassword == null ? "" : defaultUserPassword;
    }
}
