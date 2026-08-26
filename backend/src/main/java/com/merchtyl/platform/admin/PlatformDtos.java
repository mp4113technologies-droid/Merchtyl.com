package com.merchtyl.platform.admin;

import com.merchtyl.security.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlatformDtos {
    private PlatformDtos() {
    }

    public record PlatformLoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 128) String password
    ) {
    }

    public record MerchantOnboardingRequest(
            @Size(max = 64) String tenantCode,
            @NotBlank @Size(max = 255) String legalBusinessName,
            @NotBlank @Size(max = 180) String operatingName,
            @NotBlank @Size(max = 2) String countryCode,
            @Size(max = 32) String administrativeDivisionCode,
            @NotBlank @Size(max = 64) String primaryTimezone,
            @NotBlank @Size(max = 3) String defaultCurrencyCode,
            @Size(max = 64) String defaultTaxRegionCode,
            @Size(max = 1000) String currencyOverrideReason,
            @Size(max = 80) String businessNumber,
            @Size(max = 120) String industryType,
            @Min(0) Integer estimatedStoreCount,
            @Size(max = 2000) String notes,
            @Size(max = 80) String subscriptionPlan,
            Instant trialStartsAt,
            Instant trialEndsAt,
            @Min(1) Integer maximumStores,
            @Min(1) Integer maximumUsers,
            Map<String, Boolean> features,
            @Size(max = 120) String ownerFirstName,
            @Size(max = 120) String ownerLastName,
            @Email @Size(max = 320) String ownerEmail,
            @Size(max = 40) String ownerPhone,
            MerchantOwnerRequest owner,
            MerchantSubscriptionRequest subscription
    ) {
    }

    public record MerchantOwnerRequest(
            @NotBlank @Size(max = 120) String firstName,
            @NotBlank @Size(max = 120) String lastName,
            @NotBlank @Email @Size(max = 320) String email,
            @Size(max = 40) String phone
    ) {
    }

    public record MerchantSubscriptionRequest(
            @NotBlank @Size(max = 80) String planCode,
            Instant trialStartsAt,
            Instant trialEndsAt,
            @Min(1) Integer maximumStores,
            @Min(1) Integer maximumUsers,
            Map<String, Boolean> features
    ) {
    }

    public record MerchantGeographyValidationRequest(
            @NotBlank @Size(max = 2) String countryCode,
            @NotBlank @Size(max = 32) String administrativeDivisionCode,
            @NotBlank @Size(max = 3) String currencyCode,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Size(max = 64) String taxRegionCode,
            @Size(max = 1000) String currencyOverrideReason
    ) {
    }

    public record MerchantGeographyValidationResponse(
            boolean valid,
            NamedCode country,
            NamedCode administrativeDivision,
            NamedCode currency,
            String timezone,
            NamedCode taxRegion,
            List<String> warnings
    ) {
    }

    public record NamedCode(String code, String name) {
    }

    public record TenantSummaryResponse(
            UUID id,
            String tenantCode,
            String legalName,
            String displayName,
            TenantStatus status,
            String countryCode,
            String administrativeDivisionCode,
            String defaultCurrencyCode,
            String primaryTimezone,
            String defaultTaxRegionCode,
            String primaryOwnerEmail,
            String subscriptionPlan,
            OnboardingStage onboardingStage,
            long storeCount,
            long userCount,
            Instant createdAt,
            Instant activatedAt,
            Instant suspendedAt,
            UUID suspendedByPlatformUserId,
            String suspensionReason,
            Instant closedAt,
            UUID closedByPlatformUserId,
            String closureReason,
            Instant reactivatedAt,
            UUID reactivatedByPlatformUserId,
            long version
    ) {
    }

    public record TenantDetailResponse(
            TenantSummaryResponse tenant,
            MerchantProfileResponse merchantProfile,
            SubscriptionResponse subscription,
            OnboardingResponse onboarding
    ) {
    }

    public record MerchantProfileResponse(
            UUID id,
            UUID tenantId,
            String legalBusinessName,
            String operatingName,
            String businessNumber,
            String contactName,
            String contactEmail,
            String contactPhone,
            String billingAddress,
            String countryCode,
            String administrativeDivisionCode,
            String defaultCurrencyCode,
            String primaryTimezone,
            String defaultTaxRegionCode,
            String postalCode,
            String industryType,
            Integer estimatedStoreCount,
            String notes,
            long version
    ) {
    }

    public record SubscriptionResponse(
            UUID id,
            UUID tenantId,
            String planCode,
            String status,
            Instant startsAt,
            Instant trialEndsAt,
            Instant renewsAt,
            Instant cancelledAt,
            Integer maximumStores,
            Integer maximumUsers,
            Map<String, Boolean> features,
            long version
    ) {
    }

    public record SubscriptionUpdateRequest(
            @NotBlank @Size(max = 80) String planCode,
            @NotBlank @Size(max = 64) String status,
            @NotNull Instant startsAt,
            Instant trialEndsAt,
            Instant renewsAt,
            Instant cancelledAt,
            @Min(1) Integer maximumStores,
            @Min(1) Integer maximumUsers,
            Map<String, Boolean> features,
            @NotNull Long version
    ) {
    }

    public record LifecycleRequest(
            @NotBlank @Size(max = 1000) String reason,
            @Size(max = 2000) String notes,
            @Size(max = 180) String confirmation,
            Long version
    ) {
    }

    public record TenantDeleteRequest(
            @NotBlank @Size(max = 180) String confirmation,
            @Size(max = 1000) String reason,
            Long version
    ) {
    }

    public record VersionRequest(@NotNull Long version) {
    }

    public record TenantUpdateRequest(
            @NotBlank @Size(max = 255) String legalName,
            @NotBlank @Size(max = 180) String displayName,
            @NotBlank @Size(max = 2) String countryCode,
            @NotBlank @Size(max = 32) String administrativeDivisionCode,
            @NotBlank @Size(max = 3) String defaultCurrencyCode,
            @NotBlank @Size(max = 64) String primaryTimezone,
            @NotBlank @Size(max = 64) String defaultTaxRegionCode,
            @Size(max = 1000) String reason,
            @NotNull Long version
    ) {
    }

    public record OwnerInviteResponse(
            UUID invitationId,
            UUID tenantId,
            UUID ownerUserId,
            String email,
            String status,
            Instant expiresAt,
            Instant acceptedAt,
            String activationUrl,
            String deliveryStatus,
            String emailProvider
    ) {
    }

    public record OwnerInvitationResendRequest(
            @NotBlank @Size(max = 1000) String reason,
            @Size(max = 2000) String notes
    ) {
    }

    public record OwnerActivationStatusResponse(
            UUID tenantId,
            UUID ownerId,
            String ownerName,
            String ownerEmail,
            String ownerAccountStatus,
            String invitationStatus,
            UUID invitationId,
            Instant invitationCreatedAt,
            Instant invitationExpiresAt,
            String emailProvider,
            String latestEmailDeliveryStatus,
            Instant latestAttemptAt,
            Instant emailSentAt,
            int attemptCount,
            String sanitizedFailureMessage,
            Instant activationCompletedAt,
            Instant temporaryCredentialsIssuedAt,
            Instant temporaryCredentialsExpiresAt,
            String credentialsDeliveryStatus,
            Instant firstLoginAt,
            Instant passwordChangedAt,
            int failedLoginAttempts,
            Instant lastFailedLoginAt,
            Instant lockedAt,
            String lockReason,
            boolean temporaryCredentialsExpired,
            String activationUrl,
            boolean canResend,
            boolean canRetry,
            UUID retryDeliveryId,
            boolean canCopyActivationLink,
            boolean canResendTemporaryCredentials
    ) {
    }

    public record OwnerInvitationDeliverySummary(
            UUID deliveryId,
            String provider,
            String providerMessageId,
            String status,
            int attemptCount,
            Instant lastAttemptAt
    ) {
    }

    public record OwnerInvitationResendResponse(
            UUID tenantId,
            UUID ownerId,
            String ownerEmail,
            String invitationStatus,
            Instant invitationExpiresAt,
            OwnerInvitationDeliverySummary delivery
    ) {
    }

    public record OwnerActivationRequest(
            @NotBlank @Size(max = 512) String token,
            @NotBlank @Size(min = 8, max = 20, message = com.merchtyl.auth.PasswordPolicyService.REQUIREMENTS_MESSAGE) String password
    ) {
    }

    public record OnboardingResponse(
            UUID tenantId,
            OnboardingStage currentStage,
            Instant completedAt,
            List<OnboardingStageResponse> stages
    ) {
    }

    public record OnboardingStageResponse(
            OnboardingStage stage,
            Instant completedAt
    ) {
    }

    public record PlatformDashboardResponse(
            long totalActiveMerchants,
            long pendingOnboardings,
            long suspendedMerchants,
            long closedMerchants,
            long merchantsRequiringAttention,
            long activeStores,
            long activeMerchantUsers,
            long trialSubscriptions,
            List<TenantSummaryResponse> recentOnboardingActivity,
            List<TenantStatusHistoryResponse> recentLifecycleActivity,
            long failedInvitations,
            boolean supportAccessEnabled,
            long supportAccessDefaultMinutes
    ) {
    }

    public record TenantStatusHistoryResponse(
            UUID id,
            UUID tenantId,
            String tenantCodeSnapshot,
            TenantStatus previousStatus,
            TenantStatus newStatus,
            String reason,
            String notes,
            UUID changedByPlatformUserId,
            Instant changedAt,
            String correlationId
    ) {
    }

    public record TenantDeletionBlockerResponse(
            String type,
            long count,
            String message
    ) {
    }

    public record TenantDeletionEligibilityResponse(
            boolean eligible,
            TenantStatus merchantStatus,
            List<TenantDeletionBlockerResponse> blockers,
            String recommendedAction
    ) {
    }

    public record PlatformUserResponse(
            UUID id,
            String email,
            String displayName,
            RoleName role,
            boolean enabled,
            boolean locked,
            boolean passwordChangeRequired,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        static PlatformUserResponse from(PlatformUserAccount account) {
            return new PlatformUserResponse(
                    account.id(),
                    account.email(),
                    account.displayName(),
                    account.role(),
                    account.enabled(),
                    account.locked(),
                    account.passwordChangeRequired(),
                    account.createdAt(),
                    account.updatedAt(),
                    account.version());
        }
    }

    public record PlatformUserCreateRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 160) String displayName,
            @NotBlank @Size(min = 8, max = 20, message = com.merchtyl.auth.PasswordPolicyService.REQUIREMENTS_MESSAGE) String password,
            @NotNull RoleName role,
            Boolean enabled
    ) {
    }

    public record PlatformUserUpdateRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 160) String displayName,
            @NotNull RoleName role,
            boolean locked,
            @NotNull Long version
    ) {
    }

    public record PlatformUserStatusRequest(
            @NotNull Boolean enabled,
            @NotNull Long version
    ) {
    }

    public record PlatformSettingsResponse(
            boolean bootstrapEnabled,
            long ownerInvitationExpiryHours,
            boolean supportAccessEnabled,
            long supportAccessDefaultMinutes,
            List<String> tenantStatuses,
            List<String> onboardingStages,
            List<String> subscriptionStatuses,
            LocalDate serverDate
    ) {
    }
}
