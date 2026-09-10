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
            entry("REGISTER_RECONCILIATION_REQUIRED", "Start register closing before completing reconciliation."),
            entry("REGISTER_SESSION_ALREADY_RECONCILED", "This register session has already been reconciled."),
            entry("REGISTER_SESSION_STATE_CHANGED", "The register session changed. Refresh and try again."),
            entry("POS_PIN_NOT_CONFIGURED", "Set a POS PIN before securing this till."),
            entry("TILL_PIN_TEMPORARILY_LOCKED", "PIN resume is temporarily disabled. Use your password or try again later."),
            entry("TILL_SECURED", "Enter your PIN or password to resume this till."),
            entry("BUSINESS_DAY_ALREADY_OPEN", "A business day has already been opened for this date. Refresh the page to see the current status."),
            entry("BUSINESS_DAY_ALREADY_EXISTS", "A business day has already been opened for this date. Refresh the page to see the current status."),
            entry("PREVIOUS_BUSINESS_DAY_STILL_OPEN", "The previous business day is still open. Close it before opening today's business day."),
            entry("PREVIOUS_BUSINESS_DAY_OPEN", "The previous business day is still open. Close it before opening today's business day."),
            entry("BUSINESS_DAY_HAS_OPEN_REGISTER_SESSIONS", "This business day can't be closed while registers are still open. Close the open register sessions first."),
            entry("BUSINESS_DAY_HAS_UNRECONCILED_REGISTER_SESSIONS", "Complete register reconciliation before closing the business day."),
            entry("BUSINESS_DAY_HAS_UNFINALIZED_SALES", "Complete, cancel, or resolve all draft and held sales before closing the business day."),
            entry("BUSINESS_DAY_CLOSING_BLOCKED", "Resolve the listed closing blockers before closing the business day."),
            entry("BUSINESS_DAY_RECONCILIATION_INCOMPLETE", "Complete register reconciliation before closing the business day."),
            entry("UNFINALIZED_DRAFT_SALE", "Cancel or complete the draft sale before closing the business day."),
            entry("UNFINALIZED_PAID_DRAFT_SALE", "This draft sale has recorded payments and must be reviewed before closing the business day."),
            entry("UNFINALIZED_HELD_SALE", "Resolve the held sale before closing the business day."),
            entry("SALE_HAS_RECORDED_PAYMENTS", "This sale has recorded payments and cannot be cancelled. Review the sale and its payments."),
            entry("SALE_STATE_CHANGED", "The sale changed. Refresh and try again."),
            entry("SALE_NOT_DRAFT", "Only an unresolved draft or held sale can be force closed."),
            entry("SALE_FORCE_CLOSE_REASON_REQUIRED", "Select a force-close reason. Other reasons also require a note."),
            entry("INDUSTRY_TYPE_REQUIRED", "Industry Type is required."),
            entry("INVALID_INDUSTRY_TYPE", "Please select a valid Industry Type."),
            entry("BUSINESS_DAY_STATE_CHANGED", "The business day changed. Refresh the page and try again."),
            entry("VARIANCE_EXPLANATION_REQUIRED", "Explain the cash variance before closing the business day."),
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
            entry("MERCHANT_SLUG_ALREADY_EXISTS", "This merchant portal address is already in use."),
            entry("MERCHANT_SLUG_RESERVED", "This merchant portal address is reserved."),
            entry("MERCHANT_SLUG_INVALID", "Enter a valid merchant portal address."),
            entry("MERCHANT_PORTAL_NOT_FOUND", "We couldn't find this Merchtyl portal."),
            entry("MERCHANT_CONTEXT_MISMATCH", "You don't have access to this merchant portal."),
            entry("BUSINESS_NUMBER_ALREADY_EXISTS", "This business number is already associated with another merchant."),
            entry("PAYMENT_AMOUNT_INVALID", "Enter a valid payment amount."),
            entry("PAYMENT_AMOUNT_EXCEEDS_REMAINING", "Payment amount cannot exceed the remaining balance."),
            entry("SALE_ALREADY_FULLY_PAID", "This sale is already fully paid."),
            entry("INVALID_DISCOUNT_TYPE", "Select a percentage or fixed-amount discount."),
            entry("DISCOUNT_VALUE_INVALID", "Discount must be greater than zero."),
            entry("DISCOUNT_PERCENTAGE_EXCEEDS_MAXIMUM", "Percentage discount cannot exceed 100%."),
            entry("DISCOUNT_EXCEEDS_ELIGIBLE_SUBTOTAL", "Discount cannot exceed the eligible subtotal."),
            entry("DISCOUNT_NOT_ALLOWED", "The items in this order are not eligible for a discount."),
            entry("DISCOUNT_NOT_FOUND", "The selected discount is no longer available."),
            entry("DISCOUNT_INACTIVE", "The selected discount is inactive. Select another discount."),
            entry("DISCOUNT_NAME_EXISTS", "A discount with this name already exists."),
            entry("DISCOUNT_MINIMUM_PURCHASE_NOT_MET", "The minimum purchase amount for this discount has not been met."),
            entry("DISCOUNT_MAXIMUM_PURCHASE_EXCEEDED", "This order exceeds the maximum purchase amount for the selected discount."),
            entry("DISCOUNT_MINIMUM_QUANTITY_NOT_MET", "The minimum item quantity for this discount has not been met."),
            entry("DISCOUNT_NO_ELIGIBLE_ITEMS", "This discount is not available for the items in this order."),
            entry("DISCOUNT_NOT_AVAILABLE_AT_STORE", "This discount is not available at this store."),
            entry("DISCOUNT_NOT_STARTED", "This promotion has not started yet."),
            entry("DISCOUNT_EXPIRED", "This promotion has expired."),
            entry("DISCOUNT_HAS_HISTORICAL_USAGE", "This discount has sales history and can only be deactivated."),
            entry("CONCURRENT_MODIFICATION", "This information was updated by someone else. Refresh the page and try again."),
            entry("RECORD_UPDATED_BY_ANOTHER_USER", "This information was updated by someone else. Refresh the page and try again."),
            entry("RESOURCE_ALREADY_EXISTS", "This information already exists. Please review your entries and try again."),
            entry("RELATED_RESOURCE_INVALID", "One of the selected items is no longer available. Refresh the page and try again."),
            entry("REQUEST_CONFLICT", "We couldn't complete this action because the information conflicts with the current state. Refresh and try again."),
            entry("VALIDATION_FAILED", "Please review the highlighted fields and correct the information."),
            entry("UNEXPECTED_ERROR", "Something went wrong while completing this action. Please try again.")
            ,entry("INITIAL_INVENTORY_INVALID_FILE", "Upload a valid Merchtyl Initial Inventory XLSX workbook.")
            ,entry("INITIAL_INVENTORY_TEMPLATE_VERSION_UNSUPPORTED", "This template version is not supported. Download a new template and try again.")
            ,entry("INITIAL_INVENTORY_TOO_MANY_ROWS", "The workbook exceeds the 5,000 variant row limit.")
            ,entry("INITIAL_INVENTORY_FILE_TOO_LARGE", "The workbook exceeds the 10 MB upload limit.")
            ,entry("INITIAL_INVENTORY_VALIDATION_FAILED", "Correct all workbook errors before confirming the import.")
            ,entry("INITIAL_INVENTORY_IMPORT_EXPIRED", "This validation preview has expired. Validate the workbook again.")
            ,entry("INITIAL_INVENTORY_ALREADY_COMPLETED", "This initial inventory import has already completed.")
            ,entry("STORE_INVENTORY_ALREADY_INITIALIZED", "Initial inventory has already been established for this Store.")
            ,entry("INITIAL_INVENTORY_CONCURRENT_CONFLICT", "Catalog information changed during import. Validate the workbook again.")
            ,entry("INITIAL_INVENTORY_STATE_CHANGED", "Catalog information changed after validation. Validate the workbook again.")
            ,entry("EXISTING_VARIANT_PRICE_MISMATCH", "An existing variant has a different selling price. Existing prices were not changed.")
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
