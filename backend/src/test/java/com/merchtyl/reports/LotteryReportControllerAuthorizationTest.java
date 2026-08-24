package com.merchtyl.reports;

import com.merchtyl.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LotteryReportControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class LotteryReportControllerAuthorizationTest {
    @Autowired
    MockMvc mockMvc;

    @MockBean
    LotteryReportService lotteryReportService;

    @Test
    void lotteryReportRequiresReportViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/reports/lottery")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW"))))
                .andExpect(status().isForbidden());

        verify(lotteryReportService, never()).summarize(any());
    }

    @Test
    void viewerCanRequestLotteryReportWithFilters() throws Exception {
        when(lotteryReportService.summarize(any())).thenReturn(response());

        mockMvc.perform(get("/api/v1/reports/lottery")
                        .param("operatorId", "00000000-0000-0000-0000-000000000501")
                        .param("storeId", "00000000-0000-0000-0000-000000000502")
                        .param("registerId", "00000000-0000-0000-0000-000000000503")
                        .param("cashierId", "00000000-0000-0000-0000-000000000504")
                        .param("dateFrom", "2026-07-01")
                        .param("dateTo", "2026-07-31")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("REPORT_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales").value(120.00))
                .andExpect(jsonPath("$.payouts").value(40.00))
                .andExpect(jsonPath("$.variance").value(0.00));

        ArgumentCaptor<LotteryReportRequest> request = ArgumentCaptor.forClass(LotteryReportRequest.class);
        verify(lotteryReportService).summarize(request.capture());
        assertThat(request.getValue().operatorId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000501"));
        assertThat(request.getValue().storeId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000502"));
        assertThat(request.getValue().registerId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000503"));
        assertThat(request.getValue().cashierId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000504"));
        assertThat(request.getValue().dateFrom()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(request.getValue().dateTo()).isEqualTo(LocalDate.parse("2026-07-31"));
    }

    private static LotteryReportResponse response() {
        return new LotteryReportResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000501"),
                UUID.fromString("00000000-0000-0000-0000-000000000502"),
                UUID.fromString("00000000-0000-0000-0000-000000000503"),
                UUID.fromString("00000000-0000-0000-0000-000000000504"),
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31"),
                new BigDecimal("120.00"),
                1,
                new BigDecimal("40.00"),
                1,
                new BigDecimal("40.00"),
                1,
                new BigDecimal("10.00"),
                1,
                new BigDecimal("75.00"),
                1,
                new BigDecimal("20.00"),
                1,
                new BigDecimal("12.00"),
                new BigDecimal("58.00"),
                new BigDecimal("58.00"),
                BigDecimal.ZERO.setScale(2),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new LotteryReportChartPoint(
                        LocalDate.parse("2026-07-27"),
                        new BigDecimal("120.00"),
                        new BigDecimal("40.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("75.00"),
                        BigDecimal.ZERO.setScale(2))),
                Instant.parse("2026-07-29T12:00:00Z"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({LotteryReportController.class, AuthorizationService.class, TestSecurityConfig.class})
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
