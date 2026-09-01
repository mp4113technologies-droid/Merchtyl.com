package com.merchtyl.platform.billing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PricingCapabilityMigrationTest {
    @Test
    void legacyCompatibilityConstraintsAllowEveryCommercialCapability() throws IOException {
        String migration;
        try (var stream = getClass().getResourceAsStream("/db/migration/V87__align_legacy_capability_price_constraints.sql")) {
            assertThat(stream).isNotNull();
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(migration).contains("ck_plan_capability_price_capability");
        assertThat(migration).contains("ck_subscription_capability_snapshot_capability");
        for (CommercialCapability capability : CommercialCapability.values()) {
            assertThat(migration).contains("'%s'".formatted(capability.name()));
        }
    }
}
