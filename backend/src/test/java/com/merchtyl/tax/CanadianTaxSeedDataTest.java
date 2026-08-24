package com.merchtyl.tax;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanadianTaxSeedDataTest {
    @Test
    void seedIncludesCanadianJurisdictionsTypesMetadataAndRules() throws Exception {
        String seed = Files.readString(Path.of("src/main/resources/db/migration/V19__seed_canadian_tax_configuration.sql"));

        assertThat(seed).contains("Canada Revenue Agency GST/HST rates");
        assertThat(seed).contains("Province of British Columbia PST");
        assertThat(seed).contains("Province of Manitoba RST");
        assertThat(seed).contains("Government of Saskatchewan PST");
        assertThat(seed).contains("Revenu Quebec GST/QST rates");
        assertThat(seed).contains("Merchtyl seed data");

        assertThat(seed).contains("'GST'", "'HST'", "'PST'", "'RST'", "'QST'");
        assertThat(seed).contains("'STANDARD'", "'ZERO_RATED'", "'EXEMPT'", "'OUT_OF_SCOPE'");

        for (String areaCode : List.of("AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT")) {
            assertThat(seed).contains("'CA-" + areaCode + "'");
            assertThat(seed).contains("'CA_" + areaCode);
            assertThat(seed).contains("'CA_" + areaCode + "_STANDARD'");
        }
    }
}
