package com.merchtyl.common;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConstraintErrorMapperTest {
    private final DatabaseConstraintErrorMapper mapper = new DatabaseConstraintErrorMapper();

    @ParameterizedTest
    @CsvSource({
            "uq_tenants_tenant_code,TENANT_CODE_ALREADY_EXISTS,tenantCode",
            "uq_security_users_email,USER_EMAIL_ALREADY_EXISTS,email",
            "uq_stores_code,STORE_CODE_ALREADY_EXISTS,code",
            "uq_product_barcodes_tenant_barcode_lower,BARCODE_ALREADY_EXISTS,barcode",
            "uq_products_tenant_sku_lower,SKU_ALREADY_EXISTS,sku",
            "uq_registers_store_code,REGISTER_CODE_ALREADY_EXISTS,code",
            "uq_register_sessions_open_operator,REGISTER_SESSION_ALREADY_ACTIVE,registerId",
            "uq_platform_pricing_plans_code,PRICING_PLAN_CODE_ALREADY_EXISTS,code",
            "uq_pricing_version_capability,PRICING_PLAN_CAPABILITY_ALREADY_EXISTS,capability",
            "uq_tenant_subscriptions_tenant,MERCHANT_ACTIVE_SUBSCRIPTION_ALREADY_EXISTS,pricingPlanId",
            "uq_platform_invoices_number,INVOICE_NUMBER_ALREADY_EXISTS,invoiceNumber"
    })
    void mapsKnownConstraintsAcrossMajorDomains(String constraint, String code, String field) {
        var result = mapper.analyze(integrity("23505", constraint)).domainError();

        assertThat(result.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(result.code()).isEqualTo(code);
        assertThat(result.field()).isEqualTo(field);
        assertThat(result.message()).doesNotContain(constraint, "SQL", "table");
    }

    @Test
    void mapsOwnerEmailContextAndUnknownSqlStatesSafely() {
        var owner = mapper.analyze(integrity("23505", "uq_security_users_email"), "/api/v1/platform/tenants").domainError();
        assertThat(owner.code()).isEqualTo("OWNER_EMAIL_ALREADY_EXISTS");
        assertThat(owner.field()).isEqualTo("ownerEmail");

        assertThat(mapper.analyze(integrity("23505", "secret_unique")).domainError().code()).isEqualTo("RESOURCE_ALREADY_EXISTS");
        assertThat(mapper.analyze(integrity("23503", "secret_fk")).domainError().code()).isEqualTo("RELATED_RESOURCE_INVALID");
        assertThat(mapper.analyze(integrity("23502", null)).domainError().status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(mapper.analyze(integrity("23514", "secret_check")).domainError().message()).doesNotContain("secret_check");
    }

    private static DataIntegrityViolationException integrity(String sqlState, String constraint) {
        SQLException sql = new SQLException("raw database detail", sqlState);
        return new DataIntegrityViolationException("persistence failed",
                new ConstraintViolationException("write failed", sql, constraint));
    }
}
