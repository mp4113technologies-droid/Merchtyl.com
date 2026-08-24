package com.merchtyl.platform.admin;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.auth.PasswordPolicyService;
import com.merchtyl.config.PlatformAdministrationProperties;
import com.merchtyl.security.RoleName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Component
public class PlatformBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrapRunner.class);

    private final PlatformAdministrationProperties properties;
    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final PasswordPolicyService passwordPolicyService;

    public PlatformBootstrapRunner(
            PlatformAdministrationProperties properties,
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            PasswordPolicyService passwordPolicyService) {
        this.properties = properties;
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.passwordPolicyService = passwordPolicyService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.bootstrap().enabled()) {
            return;
        }
        if (platformUserRepository.existsSuperAdmin()) {
            log.info("Platform Super Admin bootstrap skipped because an active Super Admin already exists");
            return;
        }

        String email = required(properties.bootstrap().email(), "MERCHTYL_BOOTSTRAP_ADMIN_EMAIL").toLowerCase(Locale.ROOT);
        String name = required(properties.bootstrap().name(), "MERCHTYL_BOOTSTRAP_ADMIN_NAME");
        String password = required(properties.bootstrap().password(), "MERCHTYL_BOOTSTRAP_ADMIN_PASSWORD");
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
        log.info("Platform Super Admin bootstrap created account email={}", created.email());
    }

    private static String required(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(envName + " is required when bootstrap is enabled");
        }
        return value.trim();
    }

}
