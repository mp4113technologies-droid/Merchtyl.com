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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest(classes = SalesReportControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class SalesReportControllerAuthorizationTest {
    @Autowired
    MockMvc mockMvc;

    @MockBean
    SalesReportService salesReportService;

    @Test
    void salesReportRequiresReportViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/reports/sales")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_VIEW"))))
                .andExpect(status().isForbidden());

        verify(salesReportService, never()).summarize(any());
    }

    @Test
    void viewerCanRequestSalesReportWithFilters() throws Exception {
        when(salesReportService.summarize(any())).thenReturn(response());

        mockMvc.perform(get("/api/v1/reports/sales")
                        .param("storeId", "00000000-0000-0000-0000-000000000101")
                        .param("registerId", "00000000-0000-0000-0000-000000000102")
                        .param("cashierId", "00000000-0000-0000-0000-000000000103")
                        .param("categoryId", "00000000-0000-0000-0000-000000000104")
                        .param("productId", "00000000-0000-0000-0000-000000000105")
                        .param("dateFrom", "2026-07-01")
                        .param("dateTo", "2026-07-31")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("REPORT_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossSales").value(100.00))
                .andExpect(jsonPath("$.payments").value(74.00));

        ArgumentCaptor<SalesReportRequest> request = ArgumentCaptor.forClass(SalesReportRequest.class);
        verify(salesReportService).summarize(request.capture());
        assertThat(request.getValue().storeId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000101"));
        assertThat(request.getValue().registerId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000102"));
        assertThat(request.getValue().cashierId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000103"));
        assertThat(request.getValue().categoryId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000104"));
        assertThat(request.getValue().productId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000105"));
        assertThat(request.getValue().dateFrom()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(request.getValue().dateTo()).isEqualTo(LocalDate.parse("2026-07-31"));
    }

    private static SalesReportResponse response() {
        return new SalesReportResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                UUID.fromString("00000000-0000-0000-0000-000000000103"),
                UUID.fromString("00000000-0000-0000-0000-000000000104"),
                UUID.fromString("00000000-0000-0000-0000-000000000105"),
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31"),
                new BigDecimal("100.00"),
                new BigDecimal("70.00"),
                new BigDecimal("10.00"),
                new BigDecimal("22.00"),
                new BigDecimal("6.00"),
                new BigDecimal("74.00"),
                1,
                1,
                List.of(),
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
    @Import({SalesReportController.class, AuthorizationService.class, TestSecurityConfig.class})
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
