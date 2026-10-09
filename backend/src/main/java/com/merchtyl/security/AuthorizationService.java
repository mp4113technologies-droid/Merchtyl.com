package com.merchtyl.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;

@Service("authorizationService")
public class AuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);
    public static final String PLATFORM_SCOPE_AUTHORITY = "ACCOUNT_SCOPE_PLATFORM";
    public static final String TENANT_SCOPE_AUTHORITY = "ACCOUNT_SCOPE_TENANT";

    public boolean hasPermission(Authentication authentication, PermissionCode permission) {
        Objects.requireNonNull(permission, "permission is required");
        return hasAuthority(authentication, TENANT_SCOPE_AUTHORITY)
                && hasRawPermission(authentication, permission);
    }

    public boolean hasPlatformPermission(Authentication authentication, PermissionCode permission) {
        return hasAuthority(authentication, PLATFORM_SCOPE_AUTHORITY) && hasRawPermission(authentication, permission);
    }

    public boolean hasTenantPermission(Authentication authentication, PermissionCode permission) {
        return hasPermission(authentication, permission);
    }

    public boolean canCreateProduct(Authentication authentication) {
        boolean allowed = hasTenantPermission(authentication, PermissionCode.PRODUCT_CREATE);
        log.info("product_event event={} actor=[REDACTED] actor_role={} permission={}",
                allowed ? "PRODUCT_CREATE_AUTHORIZED" : "PRODUCT_CREATE_DENIED",
                authentication == null ? null : authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .sorted()
                        .toList(),
                PermissionCode.PRODUCT_CREATE);
        return allowed;
    }

    public boolean hasAnyPermission(Authentication authentication, PermissionCode... permissions) {
        return Arrays.stream(permissions)
                .anyMatch(permission -> hasPermission(authentication, permission));
    }

    public boolean hasAnyPlatformPermission(Authentication authentication, PermissionCode... permissions) {
        return hasAuthority(authentication, PLATFORM_SCOPE_AUTHORITY)
                && Arrays.stream(permissions).anyMatch(permission -> hasRawPermission(authentication, permission));
    }

    private boolean hasRawPermission(Authentication authentication, PermissionCode permission) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.name().equals(authority.getAuthority()));
    }

    private boolean hasAuthority(Authentication authentication, String authorityName) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authorityName.equals(authority.getAuthority()));
    }
}
