package com.merchtyl.registersession;

import com.merchtyl.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/register-sessions")
public class RegisterSessionController {
    private final RegisterSessionService registerSessionService;

    public RegisterSessionController(RegisterSessionService registerSessionService) {
        this.registerSessionService = registerSessionService;
    }

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_SESSION_OPEN)")
    @Operation(summary = "Open a register session", description = "deviceId is optional when register device enforcement is disabled and required when it is enabled. No MAC-address verification is performed.")
    @ApiResponse(responseCode = "400", description = "REGISTER_DEVICE_REQUIRED when device enforcement is enabled and deviceId is absent.")
    RegisterSessionResponse open(@Valid @RequestBody RegisterSessionOpenRequest request, Authentication authentication) {
        return registerSessionService.open(request, authentication);
    }

    @GetMapping("/current")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_SESSION_VIEW)")
    ResponseEntity<RegisterSessionResponse> current(
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) String deviceIdentifier,
            Authentication authentication) {
        RegisterSessionResponse current = registerSessionService.current(deviceId, deviceIdentifier, authentication);
        return current == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(current);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_SESSION_CLOSE)")
    RegisterSessionResponse close(
            @org.springframework.web.bind.annotation.PathVariable UUID id,
            @Valid @RequestBody RegisterSessionCloseRequest request,
            Authentication authentication) {
        return registerSessionService.close(id, request, authentication);
    }

    @PostMapping("/{id}/start-closing")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_SESSION_CLOSE)")
    RegisterSessionResponse startClosing(@org.springframework.web.bind.annotation.PathVariable UUID id,
            @Valid @RequestBody RegisterSessionTransitionRequest request, Authentication authentication) {
        return registerSessionService.startClosing(id, request, authentication);
    }

    @PostMapping("/{id}/cancel-closing")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_SESSION_CLOSE)")
    RegisterSessionResponse cancelClosing(@org.springframework.web.bind.annotation.PathVariable UUID id,
            @Valid @RequestBody RegisterSessionTransitionRequest request, Authentication authentication) {
        return registerSessionService.cancelClosing(id, request, authentication);
    }

    @PostMapping("/{id}/force-close")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_SESSION_FORCE_CLOSE)")
    RegisterSessionResponse forceClose(
            @org.springframework.web.bind.annotation.PathVariable UUID id,
            @Valid @RequestBody RegisterSessionForceCloseRequest request,
            Authentication authentication) {
        return registerSessionService.forceClose(id, request, authentication);
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_SESSION_TRANSFER)")
    @Operation(summary = "Transfer an open register session", description = "Explicitly transfers the current operator while preserving opener and operator history.")
    RegisterSessionResponse transfer(
            @org.springframework.web.bind.annotation.PathVariable UUID id,
            @Valid @RequestBody RegisterSessionTransferRequest request,
            Authentication authentication) {
        return registerSessionService.transfer(id, request, authentication);
    }

    @PostMapping("/{id}/override")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_SESSION_OVERRIDE)")
    @Operation(summary = "Override an open register session", description = "An authorized Owner or Manager explicitly becomes current operator. The original opener and prior sales remain unchanged; the displaced operator's refresh tokens are revoked.")
    RegisterSessionResponse override(
            @org.springframework.web.bind.annotation.PathVariable UUID id,
            @Valid @RequestBody RegisterSessionOverrideRequest request,
            Authentication authentication) {
        return registerSessionService.override(id, request, authentication);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_SESSION_RELEASE)")
    @Operation(summary = "Release an open register session to a cashier", description = "Returns control to an eligible cashier without closing the session or changing opening cash, sales, or reconciliation history.")
    RegisterSessionResponse release(
            @org.springframework.web.bind.annotation.PathVariable UUID id,
            @Valid @RequestBody RegisterSessionReleaseRequest request,
            Authentication authentication) {
        return registerSessionService.release(id, request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_SESSION_VIEW)")
    PageResponse<RegisterSessionResponse> search(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID registerId,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) UUID assignedCashierId,
            @RequestParam(required = false) RegisterSessionStatus status,
            @RequestParam(required = false) Instant openedFrom,
            @RequestParam(required = false) Instant openedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return registerSessionService.search(new RegisterSessionSearchRequest(
                storeId,
                registerId,
                deviceId,
                assignedCashierId,
                status,
                openedFrom,
                openedTo,
                page,
                size), authentication);
    }
}
