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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LotteryOperatorControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class LotteryOperatorControllerAuthorizationTest {
    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID JURISDICTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final String OPERATOR_JSON = """
            {
              "code": "STATE",
              "name": "State Lottery",
              "jurisdictionId": "00000000-0000-0000-0000-000000000902",
              "supportContact": "support@example.test",
              "settlementFrequency": "WEEKLY",
              "active": true
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LotteryOperatorService lotteryOperatorService;

    @Test
    void listRequiresLotteryViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/lottery/operators")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(lotteryOperatorService, never()).search(any());
    }

    @Test
    void viewerCanListLotteryOperators() throws Exception {
        when(lotteryOperatorService.search(any())).thenReturn(new PageResponse<>(
                List.of(response(true, 0)),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/lottery/operators")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("STATE"));
    }

    @Test
    void createRequiresLotteryManagePermission() throws Exception {
        mockMvc.perform(post("/api/v1/lottery/operators")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPERATOR_JSON))
                .andExpect(status().isForbidden());

        verify(lotteryOperatorService, never()).create(any(), any());
    }

    @Test
    void managerCanCreateLotteryOperator() throws Exception {
        when(lotteryOperatorService.create(any(), any())).thenReturn(response(true, 0));

        mockMvc.perform(post("/api/v1/lottery/operators")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPERATOR_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(OPERATOR_ID.toString()))
                .andExpect(jsonPath("$.settlementFrequency").value("WEEKLY"));
    }

    @Test
    void statusPatchRequiresLotteryManagePermission() throws Exception {
        mockMvc.perform(patch("/api/v1/lottery/operators/{id}/status", OPERATOR_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(lotteryOperatorService, never()).updateStatus(any(), any(), any());
    }

    private static LotteryOperatorResponse response(boolean active, long version) {
        return new LotteryOperatorResponse(
                OPERATOR_ID,
                "STATE",
                "State Lottery",
                JURISDICTION_ID,
                "CA",
                "California",
                "support@example.test",
                SettlementFrequency.WEEKLY,
                active,
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
    @Import({LotteryOperatorController.class, AuthorizationService.class, TestSecurityConfig.class})
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
