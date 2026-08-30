package com.merchtyl.platform.billing;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {}

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record PlanRequest(
            @NotBlank @Size(max = 80) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String code,
            @NotBlank @Size(max = 180) String name,
            @Size(max = 2000) String description,
            @NotBlank String status,
            @NotBlank String billingInterval,
            @NotNull @DecimalMin("0") BigDecimal basePrice,
            @NotNull @DecimalMin("0") BigDecimal oneTimeOnboardingFee,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
            @Min(0) int trialDays,
            @Min(0) Integer includedStores,
            @Min(0) Integer includedRegisters,
            @Min(0) Integer includedUsers,
            @DecimalMin("0") BigDecimal additionalStorePrice,
            @DecimalMin("0") BigDecimal additionalRegisterPrice,
            @DecimalMin("0") BigDecimal additionalUserPrice,
            List<CapabilityPrice> capabilityPrices,
            @NotBlank String taxBehavior,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    public record PlanResponse(
            UUID id, String code, String name, String description, String status, String billingInterval,
            BigDecimal basePrice, BigDecimal oneTimeOnboardingFee, String currency, int trialDays, Integer includedStores,
            Integer includedRegisters, Integer includedUsers, BigDecimal additionalStorePrice,
            BigDecimal additionalRegisterPrice, BigDecimal additionalUserPrice, String taxBehavior,
            List<CapabilityPrice> capabilityPrices,
            LocalDate effectiveFrom, LocalDate effectiveTo, long activeMerchants, Instant createdAt,
            Instant updatedAt, long version) {}

    public record SubscriptionRequest(
            @NotNull UUID pricingPlanId,
            @NotBlank String status,
            @NotBlank String billingInterval,
            @NotNull LocalDate startDate,
            LocalDate trialEndDate,
            @DecimalMin("0") BigDecimal customBasePrice,
            @DecimalMin("0") BigDecimal customOnboardingFee,
            @DecimalMin("0") BigDecimal customAdditionalStorePrice,
            @DecimalMin("0") BigDecimal customAdditionalRegisterPrice,
            @DecimalMin("0") BigDecimal customAdditionalUserPrice,
            @Size(max = 180) String discountName,
            String discountType,
            @DecimalMin("0") @DecimalMax("100") BigDecimal discountValue,
            @Size(max = 2000) String pricingNotes,
            @Min(0) Integer paymentTermsDays) {}

    public record SubscriptionActionRequest(@NotBlank String action, boolean atPeriodEnd, @Size(max = 1000) String reason) {}

    public record SubscriptionResponse(
            UUID id, UUID tenantId, String merchantName, UUID pricingPlanId, String planCode, String planName,
            String status, String billingInterval, LocalDate subscriptionStartDate, LocalDate currentPeriodStart,
            LocalDate currentPeriodEnd, LocalDate nextBillingDate, LocalDate trialEndDate,
            boolean cancelAtPeriodEnd, Instant cancelledAt, String cancellationReason,
            BigDecimal standardBasePrice, BigDecimal merchantBasePrice, String currency,
            Integer includedStoresSnapshot, BigDecimal additionalStorePriceSnapshot,
            BigDecimal onboardingFeeSnapshot, Instant onboardingFeeInvoicedAt,
            int currentBillableStores, int additionalBillableStores, BigDecimal estimatedMonthlyPrice,
            List<CapabilityCharge> capabilityCharges,
            BigDecimal customAdditionalStorePrice, BigDecimal customAdditionalRegisterPrice,
            BigDecimal customAdditionalUserPrice, String discountName, String discountType,
            BigDecimal discountValue, String pricingNotes, Integer paymentTermsDays, long version) {}

    public record CapabilityPrice(@NotNull CommercialCapability capability, CapabilityInclusionType inclusionType,
                                  BillingUnit billingUnit, @DecimalMin("0") BigDecimal monthlyPricePerStore) {}
    public record CapabilityCharge(CommercialCapability capability, String description, int storeCount, BigDecimal monthlyPricePerStore, BigDecimal monthlyTotal) {}
    public record PricingPreview(String currency, BigDecimal baseSubscription, int storeCount, int includedStores,
                                 int additionalStoreCount, BigDecimal additionalStoreMonthlyPrice,
                                 List<CapabilityCharge> capabilityCharges, BigDecimal estimatedMonthlySubscription) {}
    public record PricingVersionRequest(@NotNull PlanRequest pricing, @NotBlank String effectivePolicy,
                                        LocalDate effectiveDate, @NotBlank String existingSubscriberPolicy,
                                        boolean confirmCapabilityRemoval, long expectedPlanVersion) {}
    public record PricingVersionResponse(UUID id, UUID pricingPlanId, int versionNumber, String status,
                                         LocalDate effectiveFrom, LocalDate effectiveTo, String subscriberPolicy,
                                         PlanRequest pricing, boolean usedForBilling, Instant createdAt, long version) {}
    public record CapabilityDefinition(CommercialCapability capability, String displayName,
                                       List<BillingUnit> supportedBillingUnits) {}

    public record InvoiceLine(
            UUID id, String lineType, String description, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal discount, BigDecimal taxAmount, BigDecimal lineSubtotal, BigDecimal lineTotal) {}

    public record InvoiceResponse(
            UUID id, String invoiceNumber, UUID tenantId, String merchantName, UUID subscriptionId,
            UUID pricingPlanId, String planCode, LocalDate billingPeriodStart, LocalDate billingPeriodEnd,
            LocalDate issueDate, LocalDate dueDate, String currency, BigDecimal subtotal,
            BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal total, BigDecimal amountPaid,
            BigDecimal amountOutstanding, String status, String billingEmail, String billingAddress,
            String taxLabel, BigDecimal taxRate, String notes, Instant issuedAt, Instant sentAt,
            Instant paidAt, Instant voidedAt, List<InvoiceLine> lines) {}

    public record InvoiceGenerateRequest(LocalDate periodStart, LocalDate periodEnd, @Size(max = 2000) String notes) {}

    public record PaymentRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull LocalDate paymentDate,
            @NotBlank String paymentMethod,
            @Size(max = 180) String reference,
            @Size(max = 1000) String notes) {}

    public record BillingContactRequest(
            @Size(max = 180) String contactName,
            @NotBlank @Email @Size(max = 320) String billingEmail,
            @Size(max = 40) String billingPhone,
            @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @Size(max = 120) String city,
            @Size(max = 120) String provinceState,
            @Size(max = 32) String postalCode,
            @Pattern(regexp = "^[A-Za-z]{2}$") String countryCode,
            UUID taxRuleId) {}

    public record BillingSettingsRequest(
            @Size(max = 255) String legalName,
            @Size(max = 2000) String billingAddress,
            @Email @Size(max = 320) String supportEmail,
            @Email @Size(max = 320) String invoiceSenderEmail,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String defaultCurrency,
            @Min(0) int defaultPaymentTermsDays,
            @NotBlank @Size(max = 20) String invoicePrefix,
            @Size(max = 120) String taxRegistrationNumber,
            UUID defaultTaxRuleId,
            @Size(max = 2000) String invoiceFooter,
            @Size(max = 4000) String paymentInstructions,
            boolean billingEnforcementEnabled) {}

    public record BillingSettingsResponse(
            UUID id, String legalName, String billingAddress, String supportEmail, String invoiceSenderEmail,
            String defaultCurrency, int defaultPaymentTermsDays, String invoicePrefix,
            String taxRegistrationNumber, UUID defaultTaxRuleId, String invoiceFooter,
            String paymentInstructions, boolean billingEnforcementEnabled, long version) {}

    public record TaxRuleRequest(
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 120) String label,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal rate,
            @Size(max = 120) String registrationNumber,
            @Pattern(regexp = "^[A-Za-z]{2}$") String countryCode,
            @Size(max = 120) String provinceState,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active) {}

    public record Overview(
            long activeSubscriptions, long trialSubscriptions, BigDecimal monthlyRecurringRevenue,
            long invoicesThisMonth, BigDecimal outstandingBalance, long pastDueInvoices,
            BigDecimal paidThisMonth, long subscriptionsCancelling, String currency) {}
}
