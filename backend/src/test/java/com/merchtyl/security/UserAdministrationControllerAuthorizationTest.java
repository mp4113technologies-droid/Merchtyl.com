package com.merchtyl.security;

import com.merchtyl.common.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = UserAdministrationControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class UserAdministrationControllerAuthorizationTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final String USER_JSON = """
            {
              "email": "cashier@example.local",
              "displayName": "Cashier User",
              "password": "CashierDev!2026",
              "roles": ["CASHIER"],
              "storeIds": [],
              "registerIds": [],
              "enabled": true,
              "locked": false
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UserAdministrationService userAdministrationService;

    @MockBean
    RoleAdministrationService roleAdministrationService;

    @Test
    void userViewerCannotCreateUser() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(user("manager").authorities(
                                new SimpleGrantedAuthority(AuthorizationService.TENANT_SCOPE_AUTHORITY),
                                new SimpleGrantedAuthority("USER_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USER_JSON))
                .andExpect(status().isForbidden());

        verify(userAdministrationService, never()).create(any(), any());
    }

    @Test
    void userManagerCanCreateUser() throws Exception {
        when(userAdministrationService.create(any(), any())).thenReturn(response(true, 0));

        mockMvc.perform(post("/api/v1/users")
                        .with(user("owner").authorities(
                                new SimpleGrantedAuthority(AuthorizationService.TENANT_SCOPE_AUTHORITY),
                                new SimpleGrantedAuthority("USER_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(USER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("cashier@example.local"));
    }

    @Test
    void userListRequiresViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(user("cashier").authorities(
                                new SimpleGrantedAuthority(AuthorizationService.TENANT_SCOPE_AUTHORITY),
                                new SimpleGrantedAuthority("STORE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(userAdministrationService, never()).search(any());
    }

    @Test
    void userViewerCanListUsers() throws Exception {
        when(userAdministrationService.search(any(), any())).thenReturn(new PageResponse<>(
                List.of(response(true, 0)),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/users")
                        .with(user("manager").authorities(
                                new SimpleGrantedAuthority(AuthorizationService.TENANT_SCOPE_AUTHORITY),
                                new SimpleGrantedAuthority("USER_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(USER_ID.toString()));
    }

    @Test
    void mutatingExistingUserRequiresManagePermission() throws Exception {
        mockMvc.perform(patch("/api/v1/users/{id}/status", USER_ID)
                        .with(user("manager").authorities(
                                new SimpleGrantedAuthority(AuthorizationService.TENANT_SCOPE_AUTHORITY),
                                new SimpleGrantedAuthority("USER_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users/{id}/reset-password", USER_ID)
                        .with(user("manager").authorities(
                                new SimpleGrantedAuthority(AuthorizationService.TENANT_SCOPE_AUTHORITY),
                                new SimpleGrantedAuthority("USER_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "NewPassword!2026",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/users/{id}/roles", USER_ID)
                        .with(user("manager").authorities(
                                new SimpleGrantedAuthority(AuthorizationService.TENANT_SCOPE_AUTHORITY),
                                new SimpleGrantedAuthority("USER_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roles": ["MANAGER"],
                                  "storeIds": [],
                                  "registerIds": [],
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(userAdministrationService, never()).updateStatus(any(), any(), any());
        verify(userAdministrationService, never()).resetPassword(any(), any(), any());
        verify(userAdministrationService, never()).replaceRolesAndAssignments(any(), any(), any());
    }

    @Test
    void roleListRequiresRoleViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("USER_VIEW"))))
                .andExpect(status().isForbidden());

        verify(roleAdministrationService, never()).list();
    }

    @Test
    void roleViewerCanListRoles() throws Exception {
        when(roleAdministrationService.list()).thenReturn(List.of(new RoleResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                RoleName.OWNER,
                "Store owner",
                true,
                List.of("USER_VIEW", "USER_MANAGE"),
                0)));

        mockMvc.perform(get("/api/v1/roles")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("ROLE_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("OWNER"));
    }

    private static UserResponse response(boolean enabled, long version) {
        return new UserResponse(
                USER_ID,
                "cashier@example.local",
                "Cashier User",
                enabled,
                false,
                List.of(RoleName.CASHIER),
                List.of(),
                List.of(),
                Instant.parse("2026-07-22T12:00:00Z"),
                Instant.parse("2026-07-22T12:00:00Z"),
                version);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({UserAdministrationController.class, RoleAdministrationController.class, AuthorizationService.class, TestSecurityConfig.class})
    static class TestApplication {
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }
    }
}
