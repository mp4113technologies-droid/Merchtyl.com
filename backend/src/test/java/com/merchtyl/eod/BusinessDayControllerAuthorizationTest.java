package com.merchtyl.eod;

import com.merchtyl.common.PageResponse;
import com.merchtyl.idempotency.IdempotencyService;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = BusinessDayControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class BusinessDayControllerAuthorizationTest {
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID DAY_ID = UUID.fromString("00000000-0000-0000-0000-000000001002");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    BusinessDayService businessDayService;

    @MockBean
    IdempotencyService idempotencyService;

    @Test
    void cashierCannotOpenBusinessDay() throws Exception {
        mockMvc.perform(post("/api/v1/business-days/open")
                        .contentType("application/json")
                        .content("{\"storeId\":\"" + STORE_ID + "\"}")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE"))))
                .andExpect(status().isForbidden());

        verify(businessDayService, never()).open(any(), any());
    }

    @Test
    void managerCanListBusinessDaysWithFilters() throws Exception {
        when(businessDayService.search(eq(STORE_ID), eq(LocalDate.parse("2026-07-01")), eq(LocalDate.parse("2026-07-31")), eq(BusinessDayStatus.CLOSED), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/business-days")
                        .param("storeId", STORE_ID.toString())
                        .param("dateFrom", "2026-07-01")
                        .param("dateTo", "2026-07-31")
                        .param("status", "CLOSED")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("BUSINESS_DAY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(DAY_ID.toString()))
                .andExpect(jsonPath("$.content[0].status").value("CLOSED"));
    }

    @Test
    void closeRequiresClosePermissionBeforeIdempotencyProcessing() throws Exception {
        mockMvc.perform(post("/api/v1/business-days/{id}/close", DAY_ID)
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "close-1")
                        .contentType("application/json")
                        .content("{\"version\":0,\"confirmationAccepted\":true}")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("BUSINESS_DAY_VIEW"))))
                .andExpect(status().isForbidden());

        verify(idempotencyService, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void managerCanPreviewBusinessDayClose() throws Exception {
        when(businessDayService.previewClosing(DAY_ID)).thenReturn(preview());

        mockMvc.perform(get("/api/v1/business-days/{id}/preview", DAY_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("BUSINESS_DAY_CLOSE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDayId").value(DAY_ID.toString()))
                .andExpect(jsonPath("$.grossSales").value(100.00))
                .andExpect(jsonPath("$.cashVariance").value(-1.00));
    }

    private static BusinessDayResponse response() {
        return new BusinessDayResponse(
                DAY_ID,
                STORE_ID,
                "MAIN",
                "Main Store",
                LocalDate.parse("2026-07-29"),
                "America/Los_Angeles",
                BusinessDayStatus.CLOSED,
                Instant.parse("2026-07-29T08:00:00Z"),
                UUID.fromString("00000000-0000-0000-0000-000000001003"),
                "Manager One",
                Instant.parse("2026-07-29T23:00:00Z"),
                UUID.fromString("00000000-0000-0000-0000-000000001003"),
                "Manager One",
                Instant.parse("2026-07-29T23:05:00Z"),
                UUID.fromString("00000000-0000-0000-0000-000000001003"),
                "Manager One",
                null,
                null,
                1);
    }

    private static EndOfDayClosingPreviewResponse preview() {
        return new EndOfDayClosingPreviewResponse(
                DAY_ID,
                STORE_ID,
                "MAIN",
                "Main Store",
                LocalDate.parse("2026-07-29"),
                BusinessDayStatus.OPEN,
                0,
                new BigDecimal("100.00"),
                new BigDecimal("80.00"),
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("6.00"),
                2,
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                new BigDecimal("20.00"),
                new BigDecimal("4.0000"),
                new BigDecimal("2.0000"),
                new BigDecimal("125.00"),
                new BigDecimal("124.00"),
                new BigDecimal("-1.00"),
                new BigDecimal("0.50"),
                true,
                true,
                "USD",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({BusinessDayController.class, AuthorizationService.class, TestSecurityConfig.class})
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
