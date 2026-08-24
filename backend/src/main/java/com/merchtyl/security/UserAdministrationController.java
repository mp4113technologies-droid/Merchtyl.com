package com.merchtyl.security;

import com.merchtyl.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Merchant Users", description = "Tenant-scoped employee APIs. Owners see only managers and cashiers in their tenant; managers see only users with active assignment overlap in stores they manage; cashiers are denied.")
public class UserAdministrationController {
    private final UserAdministrationService userAdministrationService;

    public UserAdministrationController(UserAdministrationService userAdministrationService) {
        this.userAdministrationService = userAdministrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_CREATE)")
    UserResponse create(@Valid @RequestBody UserCreateRequest request, Authentication authentication) {
        return userAdministrationService.create(request, authentication);
    }

    @GetMapping
    @Operation(summary = "List merchant employees", description = "Returns paginated merchant employee profiles from the canonical security user aggregate, including safe authentication status and store assignments. Owners receive tenant STORE_MANAGER and CASHIER users; manager results require active manageable-store overlap.")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_VIEW) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_VIEW_ASSIGNED_STORE_USERS)")
    PageResponse<UserResponse> list(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID registerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean locked,
            @RequestParam(required = false) UUID createdByUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return userAdministrationService.search(new UserSearchRequest(
                email,
                displayName,
                role,
                storeId,
                registerId,
                status,
                enabled,
                locked,
                createdByUserId,
                search,
                page,
                size), authentication);
    }

    @GetMapping("/assignable-stores")
    @Operation(summary = "List stores assignable for employee management", description = "Tenant owners receive all active tenant stores. Store managers receive only active stores they actively manage. Cashiers receive 403 Forbidden.")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_ASSIGN_STORE) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_VIEW_ASSIGNED_STORE_USERS) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_VIEW)")
    List<AssignedStoreResponse> assignableStores(Authentication authentication) {
        return userAdministrationService.assignableStores(authentication);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_VIEW) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_VIEW_ASSIGNED_STORE_USERS)")
    UserResponse get(@PathVariable UUID id, Authentication authentication) {
        return userAdministrationService.get(id, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_UPDATE)")
    UserResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequest request,
            Authentication authentication) {
        return userAdministrationService.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_DISABLE) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_REACTIVATE)")
    UserResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UserStatusRequest request,
            Authentication authentication) {
        return userAdministrationService.updateStatus(id, request, authentication);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_DISABLE)")
    UserResponse disable(@PathVariable UUID id, @Valid @RequestBody UserStatusRequest request, Authentication authentication) {
        return userAdministrationService.updateStatus(id, new UserStatusRequest(false, request.version()), authentication);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_REACTIVATE)")
    UserResponse reactivate(@PathVariable UUID id, @Valid @RequestBody UserStatusRequest request, Authentication authentication) {
        return userAdministrationService.updateStatus(id, new UserStatusRequest(true, request.version()), authentication);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_UPDATE)")
    UserResponse resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody UserPasswordResetRequest request,
            Authentication authentication) {
        return userAdministrationService.resetPassword(id, request, authentication);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_UPDATE)")
    UserResponse updateRoles(
            @PathVariable UUID id,
            @Valid @RequestBody UserRolesRequest request,
            Authentication authentication) {
        return userAdministrationService.replaceRolesAndAssignments(id, request, authentication);
    }

    @GetMapping("/{id}/store-assignments")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_VIEW) || @authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_VIEW_ASSIGNED_STORE_USERS)")
    List<UserStoreAssignmentResponse> storeAssignments(@PathVariable UUID id, Authentication authentication) {
        return userAdministrationService.storeAssignments(id, authentication);
    }

    @PostMapping("/{id}/store-assignments")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_ASSIGN_STORE)")
    List<UserStoreAssignmentResponse> addStoreAssignments(
            @PathVariable UUID id,
            @Valid @RequestBody UserStoreAssignmentRequest request,
            Authentication authentication) {
        return userAdministrationService.addStoreAssignments(id, request, authentication);
    }

    @PutMapping("/{id}/store-assignments")
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_ASSIGN_STORE)")
    List<UserStoreAssignmentResponse> replaceStoreAssignments(
            @PathVariable UUID id,
            @Valid @RequestBody UserStoreAssignmentRequest request,
            Authentication authentication) {
        return userAdministrationService.replaceStoreAssignments(id, request, authentication);
    }

    @DeleteMapping("/{id}/store-assignments/{storeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).USER_REMOVE_STORE_ASSIGNMENT)")
    void removeStoreAssignment(@PathVariable UUID id, @PathVariable UUID storeId, Authentication authentication) {
        userAdministrationService.removeStoreAssignment(id, storeId, authentication);
    }
}
