package com.merchtyl.tax;

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
import org.springframework.boot.test.context.TestConfiguration;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TaxCalculationControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class TaxCalculationControllerAuthorizationTest {
    @Autowired
    MockMvc mockMvc;

    @MockBean
    TaxEngine taxEngine;

    @Test
    void taxViewerCanCalculateTax() throws Exception {
        when(taxEngine.calculate(any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/tax/calculate")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netAmount").value(100.00))
                .andExpect(jsonPath("$.taxAmount").value(15.00))
                .andExpect(jsonPath("$.components[0].taxComponentCode").value("HST"));
    }

    @Test
    void calculationRequiresTaxViewPermission() throws Exception {
        mockMvc.perform(post("/api/v1/tax/calculate")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isForbidden());

        verify(taxEngine, never()).calculate(any(), any());
    }

    private static String requestJson() {
        return """
                {
                  "transactionDate": "2026-07-22",
                  "saleChannel": "POS",
                  "unitPrice": 100.00,
                  "quantity": 1,
                  "discountAmount": 10.00,
                  "pricesIncludeTax": false,
                  "currencyCode": "CAD"
                }
                """;
    }

    private static TaxCalculationResponse response() {
        return new TaxCalculationResponse(
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 7, 22),
                "POS",
                "CAD",
                BigDecimal.ONE,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                false,
                new BigDecimal("100.00"),
                new BigDecimal("15.00"),
                new BigDecimal("115.00"),
                false,
                false,
                false,
                IncludedPriceBehavior.USE_RATE_SETTING,
                TaxRoundingStrategy.HALF_UP,
                List.of(new TaxComponentCalculationResponse(
                        null,
                        "HST",
                        "HST",
                        null,
                        new BigDecimal("15.000000"),
                        new BigDecimal("100.00"),
                        new BigDecimal("15.00"),
                        false,
                        false,
                        0,
                        LocalDate.of(2026, 1, 1),
                        null,
                        "HST used 15%.")),
                List.of("Calculated tax."),
                new TaxRuleEvaluationResponse(List.of(), List.of(), List.of(), false, false, false, IncludedPriceBehavior.USE_RATE_SETTING, TaxRoundingStrategy.HALF_UP, List.of()));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({TaxCalculationController.class, AuthorizationService.class, TestSecurityConfig.class})
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
