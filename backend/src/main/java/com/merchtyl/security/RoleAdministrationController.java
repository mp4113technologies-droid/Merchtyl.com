package com.merchtyl.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleAdministrationController {
    private final RoleAdministrationService roleAdministrationService;

    public RoleAdministrationController(RoleAdministrationService roleAdministrationService) {
        this.roleAdministrationService = roleAdministrationService;
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).ROLE_VIEW)")
    List<RoleResponse> list() {
        return roleAdministrationService.list();
    }
}
