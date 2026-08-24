package com.merchtyl.lottery;

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
import java.time.LocalDate;
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

@SpringBootTest(classes = LotterySettlementControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class LotterySettlementControllerAuthorizationTest {
    private static final UUID SETTLEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000930");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LotterySettlementService lotterySettlementService;

    @Test
    void listRequiresLotteryViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/lottery/settlements")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(lotterySettlementService, never()).search(any());
    }

    @Test
    void viewerCanListSettlements() throws Exception {
        when(lotterySettlementService.search(any())).thenReturn(new PageResponse<>(
                List.of(response(LotterySettlementStatus.CALCULATED)),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/lottery/settlements")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CALCULATED"))
                .andExpect(jsonPath("$.content[0].expectedSettlement").value(34.00));
    }

    @Test
    void approveRequiresSettlementApprovePermission() throws Exception {
        mockMvc.perform(post("/api/v1/lottery/settlements/{id}/approve", SETTLEMENT_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(lotterySettlementService, never()).approve(any(), any(), any());
    }

    @Test
    void managerCanApproveSettlement() throws Exception {
        when(lotterySettlementService.approve(any(), any(), any())).thenReturn(response(LotterySettlementStatus.APPROVED));

        mockMvc.perform(post("/api/v1/lottery/settlements/{id}/approve", SETTLEMENT_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_SETTLEMENT_APPROVE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0,
                                  "notes": "Reviewed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void postRequiresSettlementPostPermission() throws Exception {
        mockMvc.perform(post("/api/v1/lottery/settlements/{id}/post", SETTLEMENT_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_SETTLEMENT_APPROVE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(lotterySettlementService, never()).post(any(), any(), any());
    }

    @Test
    void reopenUsesSettlementApprovePermission() throws Exception {
        when(lotterySettlementService.reopen(any(), any(), any())).thenReturn(response(LotterySettlementStatus.REOPENED));

        mockMvc.perform(post("/api/v1/lottery/settlements/{id}/reopen", SETTLEMENT_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_SETTLEMENT_APPROVE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0,
                                  "reason": "Correction"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REOPENED"));
    }

    private static LotterySettlementResponse response(LotterySettlementStatus status) {
        return new LotterySettlementResponse(
                SETTLEMENT_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000901"),
                "STATE",
                "State Lottery",
                UUID.fromString("00000000-0000-0000-0000-000000000902"),
                "CA",
                "California",
                UUID.fromString("00000000-0000-0000-0000-000000000903"),
                "MAIN",
                "Main Store",
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-07"),
                new BigDecimal("150.00"),
                new BigDecimal("70.00"),
                new BigDecimal("50.00"),
                new BigDecimal("30.00"),
                new BigDecimal("26.00"),
                new BigDecimal("34.00"),
                "USD",
                Instant.parse("2026-08-08T12:00:00Z"),
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-08-08T12:00:00Z"),
                Instant.parse("2026-08-08T12:00:00Z"),
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
    @Import({LotterySettlementController.class, AuthorizationService.class, TestSecurityConfig.class})
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
