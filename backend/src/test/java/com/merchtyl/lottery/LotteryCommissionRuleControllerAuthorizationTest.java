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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LotteryCommissionRuleControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class LotteryCommissionRuleControllerAuthorizationTest {
    private static final UUID RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000920");
    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID JURISDICTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final String RULE_JSON = """
            {
              "name": "Sales commission",
              "operatorId": "00000000-0000-0000-0000-000000000901",
              "jurisdictionId": "00000000-0000-0000-0000-000000000902",
              "storeId": "00000000-0000-0000-0000-000000000903",
              "ruleType": "PERCENT_OF_SALES",
              "commissionRatePercent": 5.25,
              "effectiveFrom": "2026-08-01",
              "status": "ACTIVE"
            }
            """;
    private static final String RULE_UPDATE_JSON = """
            {
              "name": "Sales commission",
              "operatorId": "00000000-0000-0000-0000-000000000901",
              "jurisdictionId": "00000000-0000-0000-0000-000000000902",
              "storeId": "00000000-0000-0000-0000-000000000903",
              "ruleType": "PERCENT_OF_SALES",
              "commissionRatePercent": 5.25,
              "effectiveFrom": "2026-08-01",
              "status": "ACTIVE",
              "version": 0
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LotteryCommissionRuleService lotteryCommissionRuleService;

    @Test
    void listRequiresLotteryViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/lottery/commission-rules")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(lotteryCommissionRuleService, never()).search(any());
    }

    @Test
    void viewerCanListRules() throws Exception {
        when(lotteryCommissionRuleService.search(any())).thenReturn(new PageResponse<>(
                List.of(response()),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/lottery/commission-rules")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Sales commission"))
                .andExpect(jsonPath("$.content[0].ruleType").value("PERCENT_OF_SALES"));
    }

    @Test
    void createRequiresCommissionRuleManagePermission() throws Exception {
        mockMvc.perform(post("/api/v1/lottery/commission-rules")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RULE_JSON))
                .andExpect(status().isForbidden());

        verify(lotteryCommissionRuleService, never()).create(any(), any());
    }

    @Test
    void managerCanCreateRule() throws Exception {
        when(lotteryCommissionRuleService.create(any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/lottery/commission-rules")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_COMMISSION_RULE_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RULE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RULE_ID.toString()))
                .andExpect(jsonPath("$.commissionRatePercent").value(5.2500));
    }

    @Test
    void updateRequiresCommissionRuleManagePermission() throws Exception {
        mockMvc.perform(put("/api/v1/lottery/commission-rules/{id}", RULE_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RULE_UPDATE_JSON))
                .andExpect(status().isForbidden());

        verify(lotteryCommissionRuleService, never()).update(any(), any(), any());
    }

    @Test
    void deleteRequiresCommissionRuleManagePermission() throws Exception {
        mockMvc.perform(delete("/api/v1/lottery/commission-rules/{id}?version=0", RULE_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW"))))
                .andExpect(status().isForbidden());

        verify(lotteryCommissionRuleService, never()).delete(any(), any(), any());
    }

    private static LotteryCommissionRuleResponse response() {
        return new LotteryCommissionRuleResponse(
                RULE_ID,
                "Sales commission",
                OPERATOR_ID,
                "STATE",
                "State Lottery",
                JURISDICTION_ID,
                "CA",
                "California",
                STORE_ID,
                "MAIN",
                "Main Store",
                LotteryCommissionRuleType.PERCENT_OF_SALES,
                new BigDecimal("5.2500"),
                null,
                null,
                null,
                LocalDate.of(2026, 8, 1),
                null,
                LotteryCommissionRuleStatus.ACTIVE,
                null,
                Instant.parse("2026-07-28T12:00:00Z"),
                Instant.parse("2026-07-28T12:00:00Z"),
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
    @Import({LotteryCommissionRuleController.class, AuthorizationService.class, TestSecurityConfig.class})
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
