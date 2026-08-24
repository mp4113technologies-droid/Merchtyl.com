package com.merchtyl.platform.admin;

import com.merchtyl.audit.AuditRecordResponse;
import com.merchtyl.audit.AuditSearchRequest;
import com.merchtyl.audit.AuditService;
import com.merchtyl.common.PageResponse;
import com.merchtyl.platform.admin.PlatformDtos.LifecycleRequest;
import com.merchtyl.platform.admin.PlatformDtos.MerchantGeographyValidationRequest;
import com.merchtyl.platform.admin.PlatformDtos.MerchantGeographyValidationResponse;
import com.merchtyl.platform.admin.PlatformDtos.MerchantOnboardingRequest;
import com.merchtyl.platform.admin.PlatformDtos.OnboardingResponse;
import com.merchtyl.platform.admin.PlatformDtos.OwnerActivationRequest;
import com.merchtyl.platform.admin.PlatformDtos.OwnerActivationStatusResponse;
import com.merchtyl.platform.admin.PlatformDtos.OwnerInviteResponse;
import com.merchtyl.platform.admin.PlatformDtos.OwnerInvitationResendRequest;
import com.merchtyl.platform.admin.PlatformDtos.OwnerInvitationResendResponse;
import com.merchtyl.platform.admin.PlatformDtos.PlatformDashboardResponse;
import com.merchtyl.platform.admin.PlatformDtos.PlatformLoginRequest;
import com.merchtyl.platform.admin.PlatformDtos.PlatformSettingsResponse;
import com.merchtyl.platform.admin.PlatformDtos.PlatformUserCreateRequest;
import com.merchtyl.platform.admin.PlatformDtos.PlatformUserResponse;
import com.merchtyl.platform.admin.PlatformDtos.PlatformUserStatusRequest;
import com.merchtyl.platform.admin.PlatformDtos.PlatformUserUpdateRequest;
import com.merchtyl.platform.admin.PlatformDtos.SubscriptionResponse;
import com.merchtyl.platform.admin.PlatformDtos.SubscriptionUpdateRequest;
import com.merchtyl.platform.admin.PlatformDtos.TenantDeleteRequest;
import com.merchtyl.platform.admin.PlatformDtos.TenantDeletionEligibilityResponse;
import com.merchtyl.platform.admin.PlatformDtos.TenantDetailResponse;
import com.merchtyl.platform.admin.PlatformDtos.TenantStatusHistoryResponse;
import com.merchtyl.platform.admin.PlatformDtos.TenantSummaryResponse;
import com.merchtyl.platform.admin.PlatformDtos.TenantUpdateRequest;
import com.merchtyl.platform.admin.PlatformDtos.VersionRequest;
import com.merchtyl.auth.AuthResponse;
import com.merchtyl.auth.AdminPasswordResetRequest;
import com.merchtyl.auth.PasswordResetService;
import com.merchtyl.email.EmailDeliveryResponse;
import com.merchtyl.security.PermissionCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "Platform Administration", description = "Platform-scoped tenant onboarding, lifecycle, subscription, and support administration.")
public class PlatformAdministrationController {
    private final PlatformAdministrationService platformService;
    private final AuditService auditService;
    private final PasswordResetService passwordResetService;

    public PlatformAdministrationController(PlatformAdministrationService platformService, AuditService auditService,
                                            PasswordResetService passwordResetService) {
        this.platformService = platformService;
        this.auditService = auditService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Login as a platform administrator")
    AuthResponse platformLogin(@Valid @RequestBody PlatformLoginRequest request) {
        return platformService.login(request);
    }

    @PostMapping("/owner-invitations/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Activate invited merchant owner account")
    void activateOwner(@Valid @RequestBody OwnerActivationRequest request) {
        platformService.activateOwner(request);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_DASHBOARD_VIEW)")
    PlatformDashboardResponse dashboard() {
        return platformService.dashboard();
    }

    @GetMapping("/settings")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_DASHBOARD_VIEW)")
    PlatformSettingsResponse settings() {
        return platformService.settings();
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    @Tag(name = "Merchant Onboarding")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_CREATE)")
    TenantDetailResponse createTenant(@Valid @RequestBody MerchantOnboardingRequest request, Authentication authentication) {
        return platformService.createMerchant(request, authentication);
    }

    @PostMapping("/tenants/validate-geography")
    @Tag(name = "Merchant Geography")
    @Operation(summary = "Validate merchant country, province or state, currency, timezone, and tax-region defaults")
    @PreAuthorize("@authorizationService.hasAnyPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_CREATE, T(com.merchtyl.security.PermissionCode).TENANT_UPDATE, T(com.merchtyl.security.PermissionCode).REFERENCE_DATA_VIEW)")
    MerchantGeographyValidationResponse validateTenantGeography(
            @Valid @RequestBody MerchantGeographyValidationRequest request,
            Authentication authentication) {
        return platformService.validateMerchantGeography(request, authentication);
    }

    @GetMapping("/tenants")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_VIEW)")
    PageResponse<TenantSummaryResponse> tenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return platformService.listTenants(page, size);
    }

    @GetMapping("/tenants/{tenantId}")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_VIEW)")
    TenantDetailResponse tenant(@PathVariable UUID tenantId) {
        return platformService.getTenant(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/send-password-reset")
    @Operation(summary = "Send a secure password reset link to a merchant user")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_USER_SEND_PASSWORD_RESET)")
    EmailDeliveryResponse sendPasswordReset(@PathVariable UUID tenantId, @PathVariable UUID userId,
                                            @Valid @RequestBody AdminPasswordResetRequest request,
                                            Authentication authentication) {
        return passwordResetService.adminSend(tenantId, userId, request, authentication);
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unlock a merchant user without changing their password")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_USER_UNLOCK)")
    void unlockUser(@PathVariable UUID tenantId, @PathVariable UUID userId,
                    @Valid @RequestBody AdminPasswordResetRequest request, Authentication authentication) {
        passwordResetService.unlock(tenantId, userId, request, authentication);
    }

    @GetMapping("/tenants/{tenantId}/owner-invitation")
    @Operation(summary = "Get merchant owner activation invitation and latest delivery status")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_VIEW)")
    OwnerActivationStatusResponse ownerInvitation(@PathVariable UUID tenantId) {
        return platformService.ownerInvitation(tenantId);
    }

    @PutMapping("/tenants/{tenantId}")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_UPDATE)")
    TenantDetailResponse updateTenant(
            @PathVariable UUID tenantId,
            @Valid @RequestBody TenantUpdateRequest request,
            Authentication authentication) {
        return platformService.updateTenant(tenantId, request, authentication);
    }

    @PostMapping("/tenants/{tenantId}/activate")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_ACTIVATE)")
    TenantDetailResponse activate(@PathVariable UUID tenantId, @Valid @RequestBody VersionRequest request, Authentication authentication) {
        return platformService.activate(tenantId, request, authentication);
    }

    @PostMapping("/tenants/{tenantId}/suspend")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_SUSPEND)")
    TenantDetailResponse suspend(@PathVariable UUID tenantId, @Valid @RequestBody LifecycleRequest request, Authentication authentication) {
        return platformService.suspend(tenantId, request, authentication);
    }

    @PostMapping("/tenants/{tenantId}/reactivate")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_REACTIVATE)")
    TenantDetailResponse reactivate(@PathVariable UUID tenantId, @Valid @RequestBody LifecycleRequest request, Authentication authentication) {
        return platformService.reactivate(tenantId, request, authentication);
    }

    @PostMapping("/tenants/{tenantId}/close")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_CLOSE)")
    TenantDetailResponse close(@PathVariable UUID tenantId, @Valid @RequestBody LifecycleRequest request, Authentication authentication) {
        return platformService.close(tenantId, request, authentication);
    }

    @PostMapping("/tenants/{tenantId}/reopen")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_REOPEN)")
    TenantDetailResponse reopen(@PathVariable UUID tenantId, @Valid @RequestBody LifecycleRequest request, Authentication authentication) {
        return platformService.reopen(tenantId, request, authentication);
    }

    @GetMapping("/tenants/{tenantId}/deletion-eligibility")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_DELETION_ELIGIBILITY_VIEW)")
    TenantDeletionEligibilityResponse deletionEligibility(@PathVariable UUID tenantId, Authentication authentication) {
        return platformService.deletionEligibility(tenantId, authentication);
    }

    @DeleteMapping("/tenants/{tenantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_DELETE_EMPTY)")
    void deleteEmptyTenant(@PathVariable UUID tenantId, @Valid @RequestBody TenantDeleteRequest request, Authentication authentication) {
        platformService.deleteEmptyTenant(tenantId, request, authentication);
    }

    @GetMapping("/tenants/{tenantId}/status-history")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_VIEW)")
    List<TenantStatusHistoryResponse> statusHistory(@PathVariable UUID tenantId) {
        return platformService.statusHistory(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/owners/invite")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_OWNER_INVITE)")
    OwnerInviteResponse inviteOwner(@PathVariable UUID tenantId, Authentication authentication) {
        return platformService.resendInvitation(tenantId, authentication);
    }

    @PostMapping("/tenants/{tenantId}/owners/resend-invitation")
    @Operation(summary = "Invalidate any unused activation link and send a new merchant owner activation email")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_OWNER_RESEND_INVITE)")
    OwnerInvitationResendResponse resendOwnerInvitation(
            @PathVariable UUID tenantId,
            @Valid @RequestBody OwnerInvitationResendRequest request,
            Authentication authentication) {
        return platformService.resendActivationEmail(tenantId, request, authentication);
    }

    @PostMapping("/tenants/{tenantId}/owners/{ownerId}/resend-temporary-credentials")
    @Operation(summary = "Generate and email new temporary credentials for a merchant owner")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_OWNER_RESEND_TEMPORARY_CREDENTIALS)")
    OwnerActivationStatusResponse resendTemporaryCredentials(
            @PathVariable UUID tenantId,
            @PathVariable UUID ownerId,
            @Valid @RequestBody OwnerInvitationResendRequest request,
            Authentication authentication) {
        return platformService.resendTemporaryCredentials(tenantId, ownerId, request, authentication);
    }

    @PostMapping("/tenants/{tenantId}/owners/{ownerId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_OWNER_DISABLE)")
    void disableOwner(@PathVariable UUID tenantId, @PathVariable UUID ownerId, Authentication authentication) {
        platformService.disableOwner(tenantId, ownerId, authentication);
    }

    @GetMapping("/tenants/{tenantId}/onboarding")
    @Tag(name = "Merchant Onboarding")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).TENANT_VIEW)")
    OnboardingResponse onboarding(@PathVariable UUID tenantId) {
        return platformService.onboarding(tenantId);
    }

    @PutMapping("/tenants/{tenantId}/subscription")
    @Tag(name = "Merchant Subscriptions")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).SUBSCRIPTION_UPDATE)")
    SubscriptionResponse updateSubscription(
            @PathVariable UUID tenantId,
            @Valid @RequestBody SubscriptionUpdateRequest request,
            Authentication authentication) {
        return platformService.updateSubscription(tenantId, request, authentication);
    }

    @GetMapping("/users")
    @Tag(name = "Platform Users")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_USER_VIEW)")
    List<PlatformUserResponse> platformUsers() {
        return platformService.listPlatformUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Tag(name = "Platform Users")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_USER_CREATE)")
    PlatformUserResponse createPlatformUser(@Valid @RequestBody PlatformUserCreateRequest request, Authentication authentication) {
        return platformService.createPlatformUser(request, authentication);
    }

    @GetMapping("/users/{platformUserId}")
    @Tag(name = "Platform Users")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_USER_VIEW)")
    PlatformUserResponse platformUser(@PathVariable UUID platformUserId) {
        return platformService.getPlatformUser(platformUserId);
    }

    @PutMapping("/users/{platformUserId}")
    @Tag(name = "Platform Users")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_USER_UPDATE)")
    PlatformUserResponse updatePlatformUser(
            @PathVariable UUID platformUserId,
            @Valid @RequestBody PlatformUserUpdateRequest request,
            Authentication authentication) {
        return platformService.updatePlatformUser(platformUserId, request, authentication);
    }

    @PostMapping("/users/{platformUserId}/disable")
    @Tag(name = "Platform Users")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_USER_DISABLE)")
    PlatformUserResponse disablePlatformUser(
            @PathVariable UUID platformUserId,
            @Valid @RequestBody PlatformUserStatusRequest request,
            Authentication authentication) {
        return platformService.updatePlatformUserStatus(platformUserId, request, authentication);
    }

    @GetMapping("/audit-events")
    @Tag(name = "Platform Audit")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_AUDIT_VIEW)")
    PageResponse<AuditRecordResponse> auditEvents(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditService.search(new AuditSearchRequest(action, entityType, entityId, actorUserId, null, null, createdFrom, createdTo, page, size));
    }
}
