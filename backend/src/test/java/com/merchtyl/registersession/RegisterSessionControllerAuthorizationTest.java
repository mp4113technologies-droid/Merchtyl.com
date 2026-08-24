package com.merchtyl.registersession;

import com.merchtyl.common.PageResponse;
import com.merchtyl.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RegisterSessionControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class RegisterSessionControllerAuthorizationTest {
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000900");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final UUID CASHIER_ID = UUID.fromString("00000000-0000-0000-0000-000000000904");
    private static final String OPEN_JSON = """
            {
              "storeId": "00000000-0000-0000-0000-000000000901",
              "registerId": "00000000-0000-0000-0000-000000000902",
              "deviceId": "00000000-0000-0000-0000-000000000903",
              "openingCash": 125.50
            }
            """;
    private static final String CLOSE_JSON = """
            {
              "countedCash": 125.00,
              "version": 0
            }
            """;
    private static final String FORCE_CLOSE_JSON = """
            {
              "countedCash": 125.00,
              "reason": "Device failed",
              "version": 0
            }
            """;
    private static final String RELEASE_JSON = """
            {
              "cashierUserId": "00000000-0000-0000-0000-000000000904",
              "reason": "Manager assistance completed",
              "version": 0
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RegisterSessionService registerSessionService;

    @Test
    void openingRequiresRegisterSessionOpenPermission() throws Exception {
        mockMvc.perform(post("/api/v1/register-sessions/open")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPEN_JSON))
                .andExpect(status().isForbidden());

        verify(registerSessionService, never()).open(any(), any());
    }

    @Test
    void cashierWithOpenPermissionCanOpenSession() throws Exception {
        when(registerSessionService.open(any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/register-sessions/open")
                        .with(user("cashier@example.local").authorities(new SimpleGrantedAuthority("REGISTER_SESSION_OPEN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPEN_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.openingCash").value(125.50));
    }

    @Test
    void currentRequiresRegisterSessionViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/register-sessions/current")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("REGISTER_SESSION_OPEN"))))
                .andExpect(status().isForbidden());

        verify(registerSessionService, never()).current(any(), any(), any());
    }

    @Test
    void registerSessionViewerCanFetchCurrentSession() throws Exception {
        when(registerSessionService.current(any(), any(), any())).thenReturn(response());

        mockMvc.perform(get("/api/v1/register-sessions/current")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("REGISTER_SESSION_VIEW")))
                        .param("deviceId", DEVICE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(DEVICE_ID.toString()));
    }

    @Test
    void closeRequiresRegisterSessionClosePermission() throws Exception {
        mockMvc.perform(post("/api/v1/register-sessions/{id}/close", SESSION_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLOSE_JSON))
                .andExpect(status().isForbidden());

        verify(registerSessionService, never()).close(any(), any(), any());
    }

    @Test
    void closerCanCloseSession() throws Exception {
        when(registerSessionService.close(any(), any(), any())).thenReturn(closedResponse());

        mockMvc.perform(post("/api/v1/register-sessions/{id}/close", SESSION_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("REGISTER_SESSION_CLOSE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLOSE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.countedCash").value(125.00));
    }

    @Test
    void forceCloseRequiresForceClosePermission() throws Exception {
        mockMvc.perform(post("/api/v1/register-sessions/{id}/force-close", SESSION_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("REGISTER_SESSION_CLOSE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FORCE_CLOSE_JSON))
                .andExpect(status().isForbidden());

        verify(registerSessionService, never()).forceClose(any(), any(), any());
    }

    @Test
    void managerCanForceCloseSession() throws Exception {
        when(registerSessionService.forceClose(any(), any(), any())).thenReturn(forceClosedResponse());

        mockMvc.perform(post("/api/v1/register-sessions/{id}/force-close", SESSION_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("REGISTER_SESSION_FORCE_CLOSE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FORCE_CLOSE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FORCE_CLOSED"))
                .andExpect(jsonPath("$.forceCloseReason").value("Device failed"));
    }

    @Test
    void releaseRequiresReleasePermission() throws Exception {
        mockMvc.perform(post("/api/v1/register-sessions/{id}/release", SESSION_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("REGISTER_SESSION_TRANSFER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RELEASE_JSON))
                .andExpect(status().isForbidden());

        verify(registerSessionService, never()).release(any(), any(), any());
    }

    @Test
    void authorizedSupervisorCanReleaseSession() throws Exception {
        when(registerSessionService.release(any(), any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/register-sessions/{id}/release", SESSION_ID)
                        .with(user("owner").authorities(new SimpleGrantedAuthority("REGISTER_SESSION_RELEASE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RELEASE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.openingCash").value(125.50))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void registerSessionViewerCanSearchSessionHistory() throws Exception {
        when(registerSessionService.search(any(), any())).thenReturn(new PageResponse<>(
                List.of(closedResponse()),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/register-sessions")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("REGISTER_SESSION_VIEW")))
                        .param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CLOSED"));
    }

    private static RegisterSessionResponse response() {
        Instant timestamp = Instant.parse("2026-07-21T12:00:00Z");
        return new RegisterSessionResponse(
                SESSION_ID,
                STORE_ID,
                REGISTER_ID,
                DEVICE_ID,
                CASHIER_ID,
                "cashier@example.local",
                "Cashier One",
                RegisterSessionStatus.OPEN,
                new BigDecimal("125.50"),
                new BigDecimal("125.50"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                timestamp,
                timestamp,
                timestamp,
                0);
    }

    private static RegisterSessionResponse closedResponse() {
        Instant timestamp = Instant.parse("2026-07-21T12:00:00Z");
        return new RegisterSessionResponse(
                SESSION_ID,
                STORE_ID,
                REGISTER_ID,
                DEVICE_ID,
                CASHIER_ID,
                "cashier@example.local",
                "Cashier One",
                RegisterSessionStatus.CLOSED,
                new BigDecimal("125.50"),
                new BigDecimal("125.00"),
                new BigDecimal("125.00"),
                new BigDecimal("125.00"),
                BigDecimal.ZERO,
                CASHIER_ID,
                "cashier@example.local",
                "Cashier One",
                timestamp,
                null,
                null,
                timestamp,
                timestamp,
                timestamp,
                1);
    }

    private static RegisterSessionResponse forceClosedResponse() {
        Instant timestamp = Instant.parse("2026-07-21T12:00:00Z");
        return new RegisterSessionResponse(
                SESSION_ID,
                STORE_ID,
                REGISTER_ID,
                DEVICE_ID,
                CASHIER_ID,
                "cashier@example.local",
                "Cashier One",
                RegisterSessionStatus.FORCE_CLOSED,
                new BigDecimal("125.50"),
                new BigDecimal("125.00"),
                new BigDecimal("125.00"),
                new BigDecimal("125.00"),
                BigDecimal.ZERO,
                CASHIER_ID,
                "manager@example.local",
                "Manager One",
                timestamp,
                "Device failed",
                null,
                timestamp,
                timestamp,
                timestamp,
                1);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({RegisterSessionController.class, AuthorizationService.class, TestSecurityConfig.class})
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
