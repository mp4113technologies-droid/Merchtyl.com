package com.merchtyl.common;

import java.util.Map;

final class DomainErrorCatalog {
    private static final Map<String, Entry> ERRORS = Map.ofEntries(
            entry("EMAIL_ALREADY_REGISTERED", "This email address is already associated with another user. Please use a different email address."),
            entry("USER_EMAIL_ALREADY_EXISTS", "This email address is already associated with another user. Please use a different email address."),
            entry("EMAIL_ALREADY_IN_USE", "This email address is already associated with another user. Please use a different email address."),
            entry("OWNER_EMAIL_ALREADY_EXISTS", "This email address is already associated with another user. Please use a different email address."),
            entry("USER_NOT_FOUND", "We couldn't find this user."),
            entry("USER_LOCKED", "This user account is locked. Unlock the account before continuing."),
            entry("ACCOUNT_LOCKED", "This user account is locked. Unlock the account before continuing."),
            entry("ACCESS_DENIED", "You don't have permission to perform this action."),
            entry("STORE_ACCESS_DENIED", "You don't have access to this store."),
            entry("PRODUCT_STORE_ACCESS_DENIED", "You don't have access to this store."),
            entry("REGISTER_ACCESS_DENIED", "You don't have access to this register."),
            entry("REGISTER_NOT_ASSIGNED", "This register isn't assigned to your account."),
            entry("LOGIN_FAILED", "Email or password is incorrect."),
            entry("BARCODE_ALREADY_IN_USE", "This barcode is already assigned to another product. Please enter a different barcode."),
            entry("BARCODE_ALREADY_EXISTS", "This barcode is already assigned to another product. Please enter a different barcode."),
            entry("SKU_ALREADY_IN_USE", "This SKU is already being used by another product."),
            entry("SKU_ALREADY_EXISTS", "This SKU is already being used by another product."),
            entry("STORE_CODE_ALREADY_EXISTS", "A store with this code already exists. Please choose a different store code."),
            entry("REGISTER_CODE_ALREADY_EXISTS", "A register with this code already exists in this store."),
            entry("REGISTER_SESSION_ALREADY_ACTIVE", "This register already has an active session. Resume or close the existing session before opening another one."),
            entry("REGISTER_ALREADY_OPEN", "This register already has an active session. Resume or close the existing session before opening another one."),
            entry("BUSINESS_DAY_ALREADY_OPEN", "A business day has already been opened for this date. Refresh the page to see the current status."),
            entry("BUSINESS_DAY_ALREADY_EXISTS", "A business day has already been opened for this date. Refresh the page to see the current status."),
            entry("PREVIOUS_BUSINESS_DAY_STILL_OPEN", "The previous business day is still open. Close it before opening today's business day."),
            entry("PREVIOUS_BUSINESS_DAY_OPEN", "The previous business day is still open. Close it before opening today's business day."),
            entry("BUSINESS_DAY_HAS_OPEN_REGISTER_SESSIONS", "This business day can't be closed while registers are still open. Close the open register sessions first."),
            entry("OPEN_REGISTER_SESSION", "This business day can't be closed while registers are still open. Close the open register sessions first."),
            entry("BUSINESS_DAY_NOT_OPEN", "Open the business day before starting register activity."),
            entry("SUBSCRIPTION_CAPABILITY_REQUIRED", "This feature isn't included in the current subscription."),
            entry("SUBSCRIPTION_CAPABILITY_NOT_AVAILABLE", "This feature isn't included in the current subscription."),
            entry("STORE_CAPABILITY_REQUIRED", "This feature isn't enabled for this store."),
            entry("FOOD_SERVICE_NOT_ENABLED", "Restaurant / Kitchen POS isn't enabled for this store."),
            entry("PRICING_PLAN_CODE_ALREADY_EXISTS", "A pricing plan with this code already exists."),
            entry("PRICING_PLAN_NOT_FOUND", "We couldn't find this pricing plan."),
            entry("PRICING_PLAN_NOT_ACTIVE", "This pricing plan isn't active."),
            entry("TENANT_CODE_ALREADY_EXISTS", "A merchant with this code already exists."),
            entry("BUSINESS_NUMBER_ALREADY_EXISTS", "This business number is already associated with another merchant."),
            entry("PAYMENT_AMOUNT_INVALID", "Enter a valid payment amount."),
            entry("CONCURRENT_MODIFICATION", "This information was updated by someone else. Refresh the page and try again."),
            entry("RECORD_UPDATED_BY_ANOTHER_USER", "This information was updated by someone else. Refresh the page and try again."),
            entry("RESOURCE_ALREADY_EXISTS", "This information already exists. Please review your entries and try again."),
            entry("RELATED_RESOURCE_INVALID", "One of the selected items is no longer available. Refresh the page and try again."),
            entry("REQUEST_CONFLICT", "We couldn't complete this action because the information conflicts with the current state. Refresh and try again."),
            entry("VALIDATION_FAILED", "Please review the highlighted fields and correct the information."),
            entry("UNEXPECTED_ERROR", "Something went wrong while completing this action. Please try again.")
    );

    private DomainErrorCatalog() {}

    static Entry resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        int separator = raw.indexOf(':');
        String candidate = separator > 0 ? raw.substring(0, separator).trim() : raw.trim();
        if (!candidate.matches("[A-Z][A-Z0-9_]+")) return null;
        Entry known = ERRORS.get(candidate);
        if (known != null) return new Entry(candidate, known.message());
        if (separator > 0) return new Entry(candidate, raw.substring(separator + 1).trim());
        return new Entry(candidate, "We couldn't complete this action. Please review the information and try again.");
    }

    static String message(String code, String fallback) {
        Entry entry = ERRORS.get(code);
        return entry == null ? fallback : entry.message();
    }

    private static Map.Entry<String, Entry> entry(String code, String message) {
        return Map.entry(code, new Entry(code, message));
    }

    record Entry(String code, String message) {}
}
