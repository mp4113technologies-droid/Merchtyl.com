package com.merchtyl.platform.testing;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@Profile({"dev", "local", "test"})
@ConditionalOnProperty(prefix = "merchtyl.testing.user-provisioning", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/testing")
@Tag(name = "Testing Helpers", description = "Development and test-only user provisioning helpers. Not available in production.")
public class TestUserProvisioningController {
    private static final String KEY_HEADER = "X-Merchtyl-Test-Key";

    private final TestUserProvisioningProperties properties;
    private final TestUserProvisioningService service;
    private final TestProvisioningRateLimiter rateLimiter;
    private final AuditService auditService;

    public TestUserProvisioningController(
            TestUserProvisioningProperties properties,
            TestUserProvisioningService service,
            TestProvisioningRateLimiter rateLimiter,
            AuditService auditService) {
        this.properties = properties;
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    @PostMapping("/users")
    @Operation(
            summary = "Provision one or more test users",
            description = "Development/test only. Requires X-Merchtyl-Test-Key. Does not return passwords or keys.")
    public ResponseEntity<?> provisionUser(
            @Parameter(description = "Configured non-production test provisioning key")
            @RequestHeader(name = KEY_HEADER, required = false) String key,
            @RequestBody TestUserProvisioningDtos.ProvisionUserRequest request,
            HttpServletRequest servletRequest) {
        requireAccess(key, servletRequest);
        var users = service.provision(request);
        if (users.size() == 1) {
            return ResponseEntity.ok(users.get(0));
        }
        return ResponseEntity.ok(new TestUserProvisioningDtos.BatchProvisionUsersResponse(users));
    }

    @PostMapping("/users/batch")
    @Operation(
            summary = "Provision multiple test users",
            description = "Development/test only. Requires X-Merchtyl-Test-Key. Batch entries are processed in one transaction.")
    public TestUserProvisioningDtos.BatchProvisionUsersResponse provisionBatch(
            @Parameter(description = "Configured non-production test provisioning key")
            @RequestHeader(name = KEY_HEADER, required = false) String key,
            @RequestBody TestUserProvisioningDtos.BatchProvisionUsersRequest request,
            HttpServletRequest servletRequest) {
        requireAccess(key, servletRequest);
        return new TestUserProvisioningDtos.BatchProvisionUsersResponse(service.provisionBatch(request));
    }

    @PostMapping("/users/cleanup")
    @Operation(
            summary = "Deactivate helper-created test users",
            description = "Development/test only. Requires testProvisionedOnly=true. Normal users are never deleted.")
    public TestUserProvisioningDtos.CleanupResponse cleanup(
            @Parameter(description = "Configured non-production test provisioning key")
            @RequestHeader(name = KEY_HEADER, required = false) String key,
            @RequestBody TestUserProvisioningDtos.CleanupRequest request,
            HttpServletRequest servletRequest) {
        requireAccess(key, servletRequest);
        return service.cleanup(request);
    }

    @DeleteMapping("/users")
    @Operation(
            summary = "Deactivate helper-created test users",
            description = "DELETE alias for cleanup. Development/test only.")
    public TestUserProvisioningDtos.CleanupResponse cleanupDelete(
            @Parameter(description = "Configured non-production test provisioning key")
            @RequestHeader(name = KEY_HEADER, required = false) String key,
            @RequestBody TestUserProvisioningDtos.CleanupRequest request,
            HttpServletRequest servletRequest) {
        requireAccess(key, servletRequest);
        return service.cleanup(request);
    }

    private void requireAccess(String providedKey, HttpServletRequest request) {
        if (!service.enabled()) {
            throw new NotFoundException("Testing helper is disabled");
        }
        String source = request == null ? "unknown" : request.getRemoteAddr();
        if (!rateLimiter.allow(source)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many provisioning attempts");
        }
        if (providedKey == null || !safeEquals(providedKey, properties.key())) {
            auditService.record(new CreateAuditRecordCommand(
                    null,
                    AuditAction.INVALID_TEST_KEY_ATTEMPT,
                    "TEST_PROVISIONING",
                    null,
                    null,
                    null,
                    null,
                    Map.of("source", source),
                    "Invalid test provisioning key"));
            throw new ForbiddenOperationException("Invalid test provisioning key");
        }
    }

    private boolean safeEquals(String provided, String configured) {
        if (configured == null || configured.isBlank()) {
            return false;
        }
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        byte[] configuredBytes = configured.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(providedBytes, configuredBytes);
    }
}
