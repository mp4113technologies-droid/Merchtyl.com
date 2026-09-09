package com.merchtyl.bootstrap;

import com.merchtyl.MerchtylApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MerchtylApplication.class, properties = "merchtyl.platform.bootstrap.enabled=false")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MERCHTYL_RUN_LOCAL_PG", matches = "true")
class ProductionDatabaseBootstrapIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void emptyDatabaseMigratesToCompleteIdempotentReferenceFoundation() {
        assertThat(count("select count(*) from flyway_schema_history where success = true")).isGreaterThanOrEqualTo(101);
        assertThat(count("select count(*) from countries where code in ('CA', 'US')")).isEqualTo(2);
        assertThat(count("""
                select count(*) from administrative_areas area
                join countries country on country.id = area.country_id
                where country.code = 'CA' and area.code in ('AB','BC','MB','NB','NL','NS','NT','NU','ON','PE','QC','SK','YT')
                """)).isEqualTo(13);
        assertThat(count("""
                select count(*) from administrative_areas area
                join countries country on country.id = area.country_id
                where country.code = 'US' and area.type = 'STATE'
                """)).isEqualTo(50);
        assertThat(count("select count(*) from currencies where code in ('CAD', 'USD')")).isEqualTo(2);
        assertThat(count("select count(*) from units_of_measure where code in ('EA','KG','G','L','ML')")).isEqualTo(5);
        assertThat(count("select count(*) from security_roles where name in ('OWNER','MANAGER','CASHIER','KITCHEN')")).isEqualTo(4);
        assertThat(count("select count(*) from security_permissions")).isGreaterThan(100);
        assertThat(count("select count(*) from feature_definitions where code in ('LOTTERY_SALES','FOOD_SALES','KITCHEN_DISPLAY')")).isEqualTo(3);
        assertThat(count("select count(*) from tax_rates where effective_from is not null")).isGreaterThan(0);
        assertThat(count("select count(*) from tenants")).isZero();
        assertThat(count("select count(*) from stores")).isZero();
        long migrationCount = count("select count(*) from flyway_schema_history where success = true");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(count("select count(*) from flyway_schema_history where success = true")).isEqualTo(migrationCount);
        assertThat(count("select count(*) from countries where code in ('CA', 'US')")).isEqualTo(2);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
