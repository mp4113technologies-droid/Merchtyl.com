package com.merchtyl.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevelopmentSecurityDataSeeder implements ApplicationRunner {
    private static final String OWNER_EMAIL = "owner@example.local";
    private static final String MANAGER_EMAIL = "manager@example.local";
    private static final String CASHIER_EMAIL = "cashier@example.local";

    private final SecurityUserService securityUserService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public DevelopmentSecurityDataSeeder(
            SecurityUserService securityUserService,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository) {
        this.securityUserService = securityUserService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser(OWNER_EMAIL, "Development Owner", "OwnerDev!2026", RoleName.OWNER);
        seedUser(MANAGER_EMAIL, "Development Manager", "ManagerDev!2026", RoleName.MANAGER);
        seedUser(CASHIER_EMAIL, "Development Cashier", "CashierDev!2026", RoleName.CASHIER);
    }

    private void seedUser(String email, String displayName, String password, RoleName roleName) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> securityUserService.createUser(new CreateSecurityUserCommand(
                        email,
                        displayName,
                        password,
                        roleName)));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Required development role is missing: " + roleName));
        if (!userRoleRepository.existsByUserAndRole(user, role)) {
            userRoleRepository.save(new UserRole(user, role));
        }
    }
}
