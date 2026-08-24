package com.merchtyl.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.security.RefreshTokenRepository;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.RoleRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.UserRole;
import com.merchtyl.security.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthFlowIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    UserRoleRepository userRoleRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetSecurityData() {
        refreshTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void successfulLoginReturnsAccessAndRefreshTokens() throws Exception {
        createUser("owner@auth.test", "OwnerDev!2026", RoleName.OWNER, true);

        JsonNode response = login("owner@auth.test", "OwnerDev!2026");

        assertThat(response.get("accessToken").asText()).isNotBlank();
        assertThat(response.get("refreshToken").asText()).isNotBlank();
        assertThat(response.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(response.get("roles").get(0).asText()).isEqualTo("OWNER");
    }

    @Test
    void invalidPasswordReturnsSecureAuthenticationError() throws Exception {
        createUser("manager@auth.test", "ManagerDev!2026", RoleName.MANAGER, true);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "manager@auth.test",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("bad_credentials"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void disabledAccountCannotLogin() throws Exception {
        createUser("cashier@auth.test", "CashierDev!2026", RoleName.CASHIER, false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "cashier@auth.test",
                                  "password": "CashierDev!2026"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("bad_credentials"));
    }

    @Test
    void refreshRotatesRefreshToken() throws Exception {
        createUser("owner@auth.test", "OwnerDev!2026", RoleName.OWNER, true);
        JsonNode login = login("owner@auth.test", "OwnerDev!2026");

        JsonNode refreshed = refresh(login.get("refreshToken").asText());

        assertThat(refreshed.get("accessToken").asText()).isNotBlank();
        assertThat(refreshed.get("refreshToken").asText()).isNotBlank();
        assertThat(refreshed.get("refreshToken").asText()).isNotEqualTo(login.get("refreshToken").asText());

        JsonNode refreshedAgain = refresh(refreshed.get("refreshToken").asText());
        assertThat(refreshedAgain.get("refreshToken").asText()).isNotEqualTo(refreshed.get("refreshToken").asText());
    }

    @Test
    void reusingRevokedRefreshTokenIsRejectedAndRevokesActiveTokensForUser() throws Exception {
        createUser("owner@auth.test", "OwnerDev!2026", RoleName.OWNER, true);
        JsonNode login = login("owner@auth.test", "OwnerDev!2026");
        JsonNode refreshed = refresh(login.get("refreshToken").asText());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(login.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("bad_credentials"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshed.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("bad_credentials"));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        createUser("cashier@auth.test", "CashierDev!2026", RoleName.CASHIER, true);
        JsonNode login = login("cashier@auth.test", "CashierDev!2026");
        String refreshToken = login.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserEndpointReturnsAuthenticatedUser() throws Exception {
        createUser("manager@auth.test", "ManagerDev!2026", RoleName.MANAGER, true);
        JsonNode login = login("manager@auth.test", "ManagerDev!2026");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("manager@auth.test"))
                .andExpect(jsonPath("$.displayName").value("manager"))
                .andExpect(jsonPath("$.roles[0]").value("MANAGER"));
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(refreshToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private String refreshJson(String refreshToken) {
        return """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);
    }

    private User createUser(String email, String password, RoleName roleName, boolean enabled) {
        User user = new User(email, email.substring(0, email.indexOf('@')), passwordEncoder.encode(password));
        if (!enabled) {
            user.disable();
        }
        User saved = userRepository.save(user);
        var role = roleRepository.findByName(roleName).orElseThrow();
        userRoleRepository.save(new UserRole(saved, role));
        return saved;
    }
}
