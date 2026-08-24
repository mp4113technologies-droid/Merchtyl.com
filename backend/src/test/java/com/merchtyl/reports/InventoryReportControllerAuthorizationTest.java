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

@SpringBootTest(classes = InventoryReportControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class InventoryReportControllerAuthorizationTest {
    @Autowired
    MockMvc mockMvc;

    @MockBean
    InventoryReportService inventoryReportService;

    @Test
    void inventoryReportRequiresInventoryViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/reports/inventory")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW"))))
                .andExpect(status().isForbidden());

        verify(inventoryReportService, never()).summarize(any());
    }

    @Test
    void viewerCanRequestInventoryReportWithFilters() throws Exception {
        when(inventoryReportService.summarize(any())).thenReturn(response());

        mockMvc.perform(get("/api/v1/reports/inventory")
                        .param("storeId", "00000000-0000-0000-0000-000000000301")
                        .param("categoryId", "00000000-0000-0000-0000-000000000302")
                        .param("productId", "00000000-0000-0000-0000-000000000303")
                        .param("dateFrom", "2026-07-01")
                        .param("dateTo", "2026-07-31")
                        .param("lowStockThreshold", "8")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("INVENTORY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStock").value(14.0000))
                .andExpect(jsonPath("$.inventoryValue").value(21.40))
                .andExpect(jsonPath("$.damagedCount").value(1));

        ArgumentCaptor<InventoryReportRequest> request = ArgumentCaptor.forClass(InventoryReportRequest.class);
        verify(inventoryReportService).summarize(request.capture());
        assertThat(request.getValue().storeId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000301"));
        assertThat(request.getValue().categoryId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000302"));
        assertThat(request.getValue().productId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000303"));
        assertThat(request.getValue().dateFrom()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(request.getValue().dateTo()).isEqualTo(LocalDate.parse("2026-07-31"));
        assertThat(request.getValue().lowStockThreshold()).isEqualByComparingTo("8");
    }

    private static InventoryReportResponse response() {
        return new InventoryReportResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                UUID.fromString("00000000-0000-0000-0000-000000000302"),
                UUID.fromString("00000000-0000-0000-0000-000000000303"),
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31"),
                new BigDecimal("8.0000"),
                new BigDecimal("14.0000"),
                new BigDecimal("21.40"),
                3,
                1,
                1,
                1,
                1,
                1,
                new BigDecimal("3.0000"),
                new BigDecimal("2.0000"),
                new BigDecimal("1.0000"),
                new BigDecimal("3.75"),
                new BigDecimal("1.60"),
                new BigDecimal("1.50"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
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
    @Import({InventoryReportController.class, AuthorizationService.class, TestSecurityConfig.class})
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
