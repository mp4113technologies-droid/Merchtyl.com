package com.merchtyl.platform.admin;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.auth.PasswordPolicyService;
import com.merchtyl.config.PlatformAdministrationProperties;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Component
@Order(10)
public class PlatformBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrapRunner.class);

    private final PlatformAdministrationProperties properties;
    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final PasswordPolicyService passwordPolicyService;
    private final UserRepository tenantUserRepository;

    public PlatformBootstrapRunner(
            PlatformAdministrationProperties properties,
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            PasswordPolicyService passwordPolicyService,
            UserRepository tenantUserRepository) {
        this.properties = properties;
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.passwordPolicyService = passwordPolicyService;
        this.tenantUserRepository = tenantUserRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.bootstrap().enabled()) {
            return;
        }
        platformUserRepository.acquireBootstrapLock();
        if (platformUserRepository.existsSuperAdmin()) {
            log.info("Platform Super Admin already exists; bootstrap skipped");
            return;
        }

        String email = required(properties.bootstrap().email(), "MERCHTYL_BOOTSTRAP_SUPERADMIN_EMAIL").toLowerCase(Locale.ROOT);
        String name = required(properties.bootstrap().name(), "MERCHTYL_BOOTSTRAP_SUPERADMIN_NAME");
        String password = required(properties.bootstrap().password(), "MERCHTYL_BOOTSTRAP_SUPERADMIN_PASSWORD");
        PlatformUserAccount existingPlatformUser = platformUserRepository.findByEmail(email).orElse(null);
        if (existingPlatformUser != null) {
            throw new IllegalStateException("Configured bootstrap email already belongs to a non-Super-Admin platform account");
        }
        if (tenantUserRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("Configured bootstrap email already belongs to a merchant account");
        }
        passwordPolicyService.validate(password);

        PlatformUserAccount created = platformUserRepository.create(
                email,
                name,
                passwordEncoder.encode(password),
                RoleName.PLATFORM_SUPER_ADMIN,
                true,
                true);
        auditService.record(new CreateAuditRecordCommand(
                created.id(),
                AuditAction.PLATFORM_SUPER_ADMIN_CREATED,
                "PLATFORM_USER",
                created.id(),
                null,
                null,
                null,
                Map.of("email", created.email(), "role", created.role(), "passwordChangeRequired", true),
                "environment bootstrap"));
        log.info("Initial Platform Super Admin created for configured email={}", created.email());
    }

    private static String required(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(envName + " is required when bootstrap is enabled");
        }
        return value.trim();
    }

}
