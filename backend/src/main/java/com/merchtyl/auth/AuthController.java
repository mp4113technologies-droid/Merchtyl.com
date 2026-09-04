package com.merchtyl.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import com.merchtyl.portal.MerchantPortalService;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "JWT login, refresh, logout, and current user endpoints.")
public class AuthController {
    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final PasswordPolicyService passwordPolicyService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService, PasswordPolicyService passwordPolicyService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.passwordPolicyService = passwordPolicyService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register the first or next user account", description = "Public endpoint. Returns JWT access and refresh tokens.")
    @ApiResponse(responseCode = "201", description = "User registered and tokens issued.")
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and issue JWT tokens", description = "Public endpoint. Use the returned accessToken with Swagger Authorize.")
    @ApiResponse(responseCode = "200", description = "Credentials accepted and tokens issued.")
    @ApiResponse(responseCode = "401", description = "Invalid email or password.")
    AuthResponse login(@Valid @RequestBody LoginRequest request,
                       @RequestHeader(value = MerchantPortalService.HEADER_NAME, required = false) String merchantSlug) {
        return authService.login(request, merchantSlug);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a one-time password reset link")
    PasswordResetMessage forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                        jakarta.servlet.http.HttpServletRequest httpRequest) {
        return passwordResetService.forgotPassword(request, httpRequest.getRemoteAddr());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reset a password with a one-time token")
    @ApiResponse(responseCode = "204", description = "Password reset. Fresh login is required.")
    @ApiResponse(responseCode = "400", description = "INVALID_RESET_TOKEN, RESET_TOKEN_PURPOSE_INVALID, PASSWORD_CONFIRMATION_MISMATCH, or PASSWORD_POLICY_VIOLATION")
    @ApiResponse(responseCode = "409", description = "RESET_TOKEN_ALREADY_USED or RESET_TOKEN_REVOKED")
    @ApiResponse(responseCode = "410", description = "EXPIRED_RESET_TOKEN")
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request);
    }

    @GetMapping("/password-policy")
    @Operation(summary = "Get public password composition requirements")
    PasswordPolicyResponse passwordPolicy() { return passwordPolicyService.describe(); }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT tokens", description = "Public endpoint. Rotates the supplied refresh token.")
    @ApiResponse(responseCode = "200", description = "Refresh token accepted and replacement tokens issued.")
    AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Logout by revoking a refresh token", description = "Public endpoint.")
    @ApiResponse(responseCode = "204", description = "Refresh token revoked when present.")
    void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
    }

    @PostMapping("/first-login/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Complete mandatory first-login password change",
            description = "Uses a restricted short-lived password-change token returned by login with temporary credentials. Does not accept normal JWTs or refresh tokens.")
    @ApiResponse(responseCode = "204", description = "Password changed. Sign in again with the new password.")
    @ApiResponse(responseCode = "400", description = "Password policy or confirmation validation failed.")
    @ApiResponse(responseCode = "401", description = "Password-change token is invalid, expired, revoked, or already used.")
    void firstLoginChangePassword(@Valid @RequestBody FirstLoginPasswordChangeRequest request) {
        authService.firstLoginChangePassword(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user", description = "Requires a valid JWT.")
    CurrentUserResponse me(Authentication authentication) {
        return authService.currentUser(authentication);
    }
}
