package com.merchtyl.catalogue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MERCHTYL_RUN_LOCAL_PG", matches = "true")
class MerchantNamespaceMigrationIntegrationTest {
    @Test
    void v125NormalizesExistingDataAndFlywayNeverRunsItTwice() {
        var dataSource = new DriverManagerDataSource(
                requiredEnvironment("SPRING_DATASOURCE_URL"),
                requiredEnvironment("SPRING_DATASOURCE_USERNAME"),
                requiredEnvironment("SPRING_DATASOURCE_PASSWORD"));
        Flyway throughV124 = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("124"))
                .cleanDisabled(false).load();
        throughV124.clean();
        throughV124.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        UUID adviam = tenant(jdbc, "Adviam", "2026-01-01T00:00:00Z");
        UUID advani = tenant(jdbc, "Advani", "2026-01-02T00:00:00Z");
        UUID advanced = tenant(jdbc, "Advanced Retail", "2026-01-03T00:00:00Z");
        UUID anusaya = tenant(jdbc, "Anusaya", "2026-01-04T00:00:00Z");
        for (UUID tenantId : List.of(adviam, advani, advanced, anusaya)) seedCatalogue(jdbc, tenantId);

        Flyway current = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(1);

        assertThat(code(jdbc, adviam)).isEqualTo("ADV01");
        assertThat(code(jdbc, advani)).isEqualTo("ADV02");
        assertThat(code(jdbc, advanced)).isEqualTo("ADV03");
        assertThat(code(jdbc, anusaya)).isEqualTo("ANU01");
        assertNamespaces(jdbc);
        assertThat(jdbc.queryForObject("select count(*) from categories where system_type='LOTTERY'", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("select namespace,next_value from tenant_identifier_sequences where tenant_id=? and namespace in ('CATEGORY','BRAND') order by namespace", adviam))
                .containsExactly(Map.of("namespace", "BRAND", "next_value", 2L), Map.of("namespace", "CATEGORY", "next_value", 3L));
        assertThat(jdbc.queryForObject("select next_value from tenant_product_reference_sequences where tenant_id=?", Long.class, adviam)).isEqualTo(2L);

        List<Map<String, Object>> beforeRestart = snapshot(jdbc);
        long successfulMigrations = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Long.class);
        assertThat(current.migrate().migrationsExecuted).isZero();
        assertThat(snapshot(jdbc)).isEqualTo(beforeRestart);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Long.class)).isEqualTo(successfulMigrations);
    }

    private UUID tenant(JdbcTemplate jdbc, String name, String createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into tenants(id,tenant_code,merchant_slug,legal_name,display_name,status,country_code,
                                    default_currency_code,primary_timezone,created_at,updated_at)
                values (?,?,?, ?,?,'ACTIVE','CA','CAD','America/Moncton',?::timestamptz,?::timestamptz)
                """, id, "TEST-" + id, "test-" + id, name + " Inc.", name, createdAt, createdAt);
        return id;
    }

    private void seedCatalogue(JdbcTemplate jdbc, UUID tenantId) {
        UUID category = UUID.randomUUID(), brand = UUID.randomUUID(), product = UUID.randomUUID();
        jdbc.update("insert into categories(id,tenant_id,code,name) values (?,?,?,?)", category, tenantId, "LEGACY-CAT-" + category, "Beverages");
        jdbc.update("insert into brands(id,tenant_id,code,name) values (?,?,?,?)", brand, tenantId, "LEGACY-BR-" + brand, "Cola");
        jdbc.update("""
                insert into products(id,tenant_id,product_reference,sku,name,sellable_type,cost,price,category_id,brand_id,
                                     created_at,updated_at,version)
                values (?,?,?,'COLA-001','Cola','STANDARD_PRODUCT',1,2,?,?,now(),now(),0)
                """, product, tenantId, "PRD-000001", category, brand);
        jdbc.update("""
                insert into product_variants(id,tenant_id,product_id,sku,name,cost,price,created_at,updated_at,version)
                values (?,?,?,'COLA-500ML-001','500 ml',1,2,now(),now(),0)
                """, UUID.randomUUID(), tenantId, product);
    }

    private String code(JdbcTemplate jdbc, UUID tenantId) {
        return jdbc.queryForObject("select merchant_code from tenants where id=?", String.class, tenantId);
    }

    private void assertNamespaces(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("select count(*) from categories c join tenants t on t.id=c.tenant_id where c.code not like t.merchant_code||'-CAT-%'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from brands b join tenants t on t.id=b.tenant_id where b.code not like t.merchant_code||'-BR-%'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from products p join tenants t on t.id=p.tenant_id where p.product_reference not like t.merchant_code||'-PRD-%'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from product_variants v join tenants t on t.id=v.tenant_id where v.sku not like t.merchant_code||'-%'", Long.class)).isZero();
    }

    private List<Map<String, Object>> snapshot(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                select t.merchant_code,c.code category_code,b.code brand_code,p.product_reference,p.sku product_sku,v.sku variant_sku
                from tenants t join categories c on c.tenant_id=t.id and c.system_type is null
                join brands b on b.tenant_id=t.id join products p on p.tenant_id=t.id
                join product_variants v on v.product_id=p.id order by t.merchant_code
                """);
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be configured");
        return value;
    }
}
