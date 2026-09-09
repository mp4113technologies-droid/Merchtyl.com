package com.merchtyl.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CanonicalReferenceDataValidator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CanonicalReferenceDataValidator.class);
    private final JdbcTemplate jdbc;

    public CanonicalReferenceDataValidator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireCodes("countries", "code", List.of("CA", "US"));
        requireCodes("currencies", "code", List.of("CAD", "USD"));
        requireCodes("units_of_measure", "code", List.of("EA", "KG", "G", "L", "ML"));
        requireCodes("security_roles", "name", List.of("OWNER", "MANAGER", "CASHIER", "KITCHEN"));
        requireCodes("feature_definitions", "code", List.of("LOTTERY_SALES", "FOOD_SALES", "KITCHEN_DISPLAY"));
        requirePositive("security_permissions", "canonical permissions");
        requirePositive("security_role_permissions", "role-permission grants");
        requirePositive("tax_rates", "effective tax rates");
        log.info("Reference database migrations completed and canonical metadata validated");
    }

    private void requireCodes(String table, String column, List<String> expected) {
        String placeholders = String.join(",", expected.stream().map(ignored -> "?").toList());
        Long count = jdbc.queryForObject(
                "select count(distinct " + column + ") from " + table + " where " + column + " in (" + placeholders + ")",
                Long.class, expected.toArray());
        if (count == null || count != expected.size()) {
            throw new IllegalStateException("Required canonical metadata is missing from " + table);
        }
    }

    private void requirePositive(String table, String description) {
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        if (count == null || count == 0) {
            throw new IllegalStateException("Required " + description + " are missing from " + table);
        }
    }
}
