package com.merchtyl.cash;

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

@SpringBootTest(classes = CashMovementControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class CashMovementControllerAuthorizationTest {
    private static final UUID MOVEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000900");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000904");
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");
    private static final String CREATE_JSON = """
            {
              "registerSessionId": "00000000-0000-0000-0000-000000000903",
              "type": "CASH_OUT",
              "amount": 25.00,
              "reason": "Petty cash",
              "occurredAt": "2026-07-27T12:00:00Z"
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CashMovementService cashMovementService;

    @Test
    void createRequiresCashMovementCreatePermission() throws Exception {
        mockMvc.perform(post("/api/v1/cash-movements")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("CASH_MOVEMENT_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isForbidden());

        verify(cashMovementService, never()).create(any(), any());
    }

    @Test
    void creatorCanRecordCashMovement() throws Exception {
        when(cashMovementService.create(any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/cash-movements")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("CASH_MOVEMENT_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(MOVEMENT_ID.toString()))
                .andExpect(jsonPath("$.type").value("CASH_OUT"))
                .andExpect(jsonPath("$.direction").value("OUT"));
    }

    @Test
    void historyRequiresCashMovementViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/cash-movements")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("CASH_MOVEMENT_CREATE"))))
                .andExpect(status().isForbidden());

        verify(cashMovementService, never()).search(any());
    }

    @Test
    void viewerCanReadCashMovementHistory() throws Exception {
        when(cashMovementService.search(any())).thenReturn(new PageResponse<>(
                List.of(response()),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/cash-movements")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("CASH_MOVEMENT_VIEW")))
                        .param("registerSessionId", SESSION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(MOVEMENT_ID.toString()))
                .andExpect(jsonPath("$.content[0].reason").value("Petty cash"));
    }

    private static CashMovementResponse response() {
        return new CashMovementResponse(
                MOVEMENT_ID,
                STORE_ID,
                REGISTER_ID,
                SESSION_ID,
                CashMovementType.CASH_OUT,
                CashLedgerDirection.OUT,
                new BigDecimal("25.00"),
                "USD",
                "Petty cash",
                null,
                USER_ID,
                NOW,
                null,
                null,
                null,
                NOW,
                NOW,
                0);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({CashMovementController.class, AuthorizationService.class, TestSecurityConfig.class})
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
