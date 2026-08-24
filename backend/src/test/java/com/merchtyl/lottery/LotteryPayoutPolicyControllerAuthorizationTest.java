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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LotteryPayoutPolicyControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class LotteryPayoutPolicyControllerAuthorizationTest {
    private static final UUID POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000910");
    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID JURISDICTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final String POLICY_JSON = """
            {
              "operatorId": "00000000-0000-0000-0000-000000000901",
              "jurisdictionId": "00000000-0000-0000-0000-000000000902",
              "storeId": "00000000-0000-0000-0000-000000000903",
              "maximumCashPayout": 2500.00,
              "cashierApprovalLimit": 200.00,
              "managerApprovalThreshold": 500.00,
              "operatorReferralThreshold": 2500.00,
              "protectedRegisterFloat": 150.00,
              "allowCashPayout": true,
              "allowStoreCredit": true,
              "requireTicketValidation": true,
              "requireAgeVerification": true,
              "requireCustomerIdentification": true,
              "allowAlternateRegister": false,
              "effectiveFrom": "2026-08-01",
              "status": "ACTIVE"
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LotteryPayoutPolicyService lotteryPayoutPolicyService;

    @Test
    void listRequiresLotteryViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/lottery/payout-policies")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(lotteryPayoutPolicyService, never()).search(any());
    }

    @Test
    void viewerCanListPolicies() throws Exception {
        when(lotteryPayoutPolicyService.search(any())).thenReturn(new PageResponse<>(
                List.of(response(LotteryPayoutPolicyStatus.ACTIVE, 0)),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/lottery/payout-policies")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].operatorName").value("State Lottery"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void createRequiresLotteryManagePermission() throws Exception {
        mockMvc.perform(post("/api/v1/lottery/payout-policies")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POLICY_JSON))
                .andExpect(status().isForbidden());

        verify(lotteryPayoutPolicyService, never()).create(any(), any());
    }

    @Test
    void managerCanCreatePolicy() throws Exception {
        when(lotteryPayoutPolicyService.create(any(), any())).thenReturn(response(LotteryPayoutPolicyStatus.ACTIVE, 0));

        mockMvc.perform(post("/api/v1/lottery/payout-policies")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POLICY_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(POLICY_ID.toString()))
                .andExpect(jsonPath("$.maximumCashPayout").value(2500.00));
    }

    @Test
    void statusPatchRequiresLotteryManagePermission() throws Exception {
        mockMvc.perform(patch("/api/v1/lottery/payout-policies/{id}/status", POLICY_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "RETIRED",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(lotteryPayoutPolicyService, never()).updateStatus(any(), any(), any());
    }

    private static LotteryPayoutPolicyResponse response(LotteryPayoutPolicyStatus status, long version) {
        return new LotteryPayoutPolicyResponse(
                POLICY_ID,
                OPERATOR_ID,
                "STATE",
                "State Lottery",
                JURISDICTION_ID,
                "CA",
                "California",
                STORE_ID,
                "MAIN",
                "Main Store",
                new BigDecimal("2500.00"),
                new BigDecimal("200.00"),
                new BigDecimal("500.00"),
                new BigDecimal("2500.00"),
                new BigDecimal("150.00"),
                true,
                true,
                true,
                true,
                true,
                false,
                LocalDate.of(2026, 8, 1),
                null,
                status,
                Instant.parse("2026-07-28T12:00:00Z"),
                Instant.parse("2026-07-28T12:00:00Z"),
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
    @Import({LotteryPayoutPolicyController.class, AuthorizationService.class, TestSecurityConfig.class})
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
