package com.merchtyl.common;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public final class DatabaseConstraintErrorMapper {
    private static final Map<String, DomainError> CONSTRAINTS = Map.ofEntries(
            unique("uq_tenants_tenant_code", "TENANT_CODE_ALREADY_EXISTS", "A merchant with this tenant code already exists.", "tenantCode"),
            unique("uq_security_users_email", "EMAIL_ALREADY_REGISTERED", "This email address is already associated with another user. Please use a different email address.", "email"),
            unique("uq_security_users_email_lower", "EMAIL_ALREADY_REGISTERED", "This email address is already associated with another user. Please use a different email address.", "email"),
            unique("uq_user_accounts_email", "EMAIL_ALREADY_REGISTERED", "This email address is already associated with another user. Please use a different email address.", "email"),
            unique("uq_platform_users_email", "EMAIL_ALREADY_REGISTERED", "This email address is already associated with another user. Please use a different email address.", "email"),
            unique("uq_stores_code", "STORE_CODE_ALREADY_EXISTS", "A store with this code already exists.", "code"),
            unique("uq_stores_code_lower", "STORE_CODE_ALREADY_EXISTS", "A store with this code already exists. Please choose a different store code.", "code"),
            unique("uq_store_capabilities_store", "STORE_CAPABILITY_ALREADY_ASSIGNED", "This capability is already assigned to the store.", "capability"),
            unique("store_capabilities_pkey", "STORE_CAPABILITY_ALREADY_ASSIGNED", "This capability is already assigned to the store.", "capability"),
            unique("uq_product_barcodes_tenant_barcode_lower", "BARCODE_ALREADY_IN_USE", "This barcode is already assigned to another product. Please enter a different barcode.", "barcode"),
            unique("uq_product_barcodes_barcode_lower", "BARCODE_ALREADY_IN_USE", "This barcode is already assigned to another product. Please enter a different barcode.", "barcode"),
            unique("uq_products_tenant_sku_lower", "SKU_ALREADY_IN_USE", "This SKU is already being used by another product.", "sku"),
            unique("uq_products_sku_lower", "SKU_ALREADY_IN_USE", "This SKU is already being used by another product.", "sku"),
            unique("uq_product_variants_tenant_sku_lower", "SKU_ALREADY_IN_USE", "This SKU is already being used by another product.", "sku"),
            unique("uq_registers_store_code", "REGISTER_CODE_ALREADY_EXISTS", "A register with this code already exists in the store.", "code"),
            unique("uq_registers_store_code_lower", "REGISTER_CODE_ALREADY_EXISTS", "A register with this code already exists in this store.", "code"),
            unique("uq_register_sessions_open_operator", "REGISTER_SESSION_ALREADY_ACTIVE", "This operator already has an active register session.", "registerId"),
            unique("uq_business_days_store_active", "BUSINESS_DAY_ALREADY_OPEN", "This store already has an open business day.", "businessDate"),
            unique("uq_business_days_store_date", "BUSINESS_DAY_ALREADY_EXISTS", "A business day already exists for this store and date.", "businessDate"),
            unique("uq_food_menu_items_store_product", "FOOD_MENU_ITEM_ALREADY_EXISTS", "This menu item already exists in the store menu.", "productId"),
            unique("uq_platform_pricing_plans_code", "PRICING_PLAN_CODE_ALREADY_EXISTS", "A pricing plan with this code already exists.", "code"),
            unique("uq_platform_pricing_plan_versions", "PRICING_PLAN_VERSION_CONFLICT", "This pricing plan version already exists.", "effectiveFrom"),
            unique("uq_platform_pricing_plan_one_active_version", "PRICING_PLAN_VERSION_CONFLICT", "This pricing plan already has an active pricing version.", "effectiveFrom"),
            unique("uq_pricing_version_capability", "PRICING_PLAN_CAPABILITY_ALREADY_EXISTS", "This capability is already configured for the pricing version.", "capability"),
            unique("uq_subscription_capability", "SUBSCRIPTION_CAPABILITY_ALREADY_EXISTS", "This capability is already configured for the merchant subscription.", "capability"),
            unique("uq_tenant_subscriptions_tenant", "MERCHANT_ACTIVE_SUBSCRIPTION_ALREADY_EXISTS", "This merchant already has a subscription.", "pricingPlanId"),
            unique("uq_platform_invoices_number", "INVOICE_NUMBER_ALREADY_EXISTS", "An invoice with this number already exists.", "invoiceNumber"),
            unique("uq_receipts_number", "RECEIPT_NUMBER_ALREADY_EXISTS", "A receipt with this number already exists.", "receiptNumber"),
            check("ck_platform_pricing_plan_amounts", "PRICING_PLAN_AMOUNT_INVALID", "Pricing amounts and included quantities must not be negative.", "basePrice"),
            check("ck_plan_version_register_pricing", "REGISTER_PRICING_INVALID", "Included register quantities and additional register prices must not be negative.", "includedRegisters"),
            check("ck_payments_amount_positive", "PAYMENT_AMOUNT_INVALID", "Payment amount must be greater than zero.", "amount"),
            check("ck_cash_movements_amount_positive", "CASH_MOVEMENT_AMOUNT_INVALID", "Cash movement amount must be greater than zero.", "amount"),
            check("ck_pricing_capability_paid", "PRICING_PLAN_CAPABILITY_INVALID", "Paid add-ons require a billing unit and price.", "capabilityPrices")
    );

    public Analysis analyze(Throwable failure) {
        return analyze(failure, null);
    }

    public Analysis analyze(Throwable failure, String requestPath) {
        SQLException sqlException = findSqlException(failure);
        String sqlState = sqlException == null ? null : sqlException.getSQLState();
        String constraint = constraintName(failure, sqlException);
        DomainError mapped = constraint == null ? null : CONSTRAINTS.get(constraint);
        if ("uq_security_users_email".equals(constraint) && requestPath != null && requestPath.endsWith("/platform/tenants")) {
            mapped = new DomainError(HttpStatus.CONFLICT, "OWNER_EMAIL_ALREADY_EXISTS",
                    "An account with this owner email already exists.", "ownerEmail", true);
        }
        if (mapped == null) mapped = fallback(sqlState);
        return new Analysis(mapped, sqlState, constraint, sqlException == null ? null : sqlException.getMessage(),
                sqlException == null ? null : sqlException.getClass().getName());
    }

    private static DomainError fallback(String sqlState) {
        if ("23505".equals(sqlState)) return new DomainError(HttpStatus.CONFLICT, "RESOURCE_ALREADY_EXISTS",
                "This information already exists. Please review your entries and try again.", null, true);
        if ("23503".equals(sqlState)) return new DomainError(HttpStatus.CONFLICT, "RELATED_RESOURCE_INVALID",
                "One of the selected items is no longer available. Refresh the page and try again.", null, false);
        if ("23502".equals(sqlState)) return new DomainError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_DATA_INTEGRITY_ERROR",
                "The requested operation could not be completed.", null, false);
        if ("23514".equals(sqlState)) return new DomainError(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Please review the highlighted fields and correct the information.", null, false);
        return new DomainError(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR",
                "Something went wrong while completing this action. Please try again.", null, false);
    }

    private static String constraintName(Throwable failure, SQLException sqlException) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName();
            }
            String postgresConstraint = postgresConstraint(current);
            if (postgresConstraint != null) return postgresConstraint;
        }
        return postgresConstraint(sqlException);
    }

    private static String postgresConstraint(Throwable failure) {
        if (failure == null || !"org.postgresql.util.PSQLException".equals(failure.getClass().getName())) return null;
        try {
            Method serverError = failure.getClass().getMethod("getServerErrorMessage");
            Object details = serverError.invoke(failure);
            if (details == null) return null;
            Object constraint = details.getClass().getMethod("getConstraint").invoke(details);
            return constraint == null ? null : constraint.toString();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static SQLException findSqlException(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException) return sqlException;
        }
        return null;
    }

    private static Map.Entry<String, DomainError> unique(String constraint, String code, String message, String field) {
        return Map.entry(constraint, new DomainError(HttpStatus.CONFLICT, code, message, field, true));
    }

    private static Map.Entry<String, DomainError> check(String constraint, String code, String message, String field) {
        return Map.entry(constraint, new DomainError(HttpStatus.BAD_REQUEST, code, message, field, true));
    }

    public record DomainError(HttpStatus status, String code, String message, String field, boolean expected) {
        public List<ApiError.FieldViolation> violations() {
            return field == null ? List.of() : List.of(new ApiError.FieldViolation(field, code, message));
        }
    }

    public record Analysis(DomainError domainError, String sqlState, String constraintName, String technicalDetail,
                           String databaseExceptionClass) {}
}
