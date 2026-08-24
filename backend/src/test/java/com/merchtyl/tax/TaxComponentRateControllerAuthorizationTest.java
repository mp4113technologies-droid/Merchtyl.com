package com.merchtyl.tax;

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

@SpringBootTest(classes = TaxComponentRateControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class TaxComponentRateControllerAuthorizationTest {
    private static final UUID TYPE_ID = UUID.fromString("00000000-0000-0000-0000-000000001101");
    private static final UUID COMPONENT_ID = UUID.fromString("00000000-0000-0000-0000-000000001102");
    private static final UUID JURISDICTION_ID = UUID.fromString("00000000-0000-0000-0000-000000001103");
    private static final UUID RATE_ID = UUID.fromString("00000000-0000-0000-0000-000000001104");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TaxTypeService taxTypeService;

    @MockBean
    TaxComponentService taxComponentService;

    @MockBean
    TaxRateService taxRateService;

    @Test
    void taxViewerCannotCreateRate() throws Exception {
        mockMvc.perform(post("/api/v1/tax/rates")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rateJson()))
                .andExpect(status().isForbidden());

        verify(taxRateService, never()).create(any(), any());
    }

    @Test
    void taxManagerCanCreateRate() throws Exception {
        when(taxRateService.create(any(), any())).thenReturn(rateResponse());

        mockMvc.perform(post("/api/v1/tax/rates")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("TAX_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RATE_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.percentageRate").value(15.000000));
    }

    @Test
    void taxViewerCanListTypesComponentsAndRates() throws Exception {
        when(taxTypeService.search(any())).thenReturn(new PageResponse<>(List.of(typeResponse()), 0, 20, 1, 1, true, true));
        when(taxComponentService.search(any())).thenReturn(new PageResponse<>(List.of(componentResponse()), 0, 20, 1, 1, true, true));
        when(taxRateService.search(any())).thenReturn(new PageResponse<>(List.of(rateResponse()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/tax/types").with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("GST"));
        mockMvc.perform(get("/api/v1/tax/components").with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("GST"));
        mockMvc.perform(get("/api/v1/tax/rates").with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void rateStatusPatchRequiresManagePermission() throws Exception {
        mockMvc.perform(patch("/api/v1/tax/rates/{id}/status", RATE_ID)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "RETIRED",
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(taxRateService, never()).updateStatus(any(), any(), any());
    }

    private static String rateJson() {
        return """
                {
                  "taxComponentId": "00000000-0000-0000-0000-000000001102",
                  "percentageRate": 15.000000,
                  "effectiveFrom": "2026-01-01",
                  "effectiveTo": "2026-12-31",
                  "includedInPrice": false,
                  "compoundOnPreviousTax": false,
                  "calculationOrder": 0,
                  "status": "ACTIVE",
                  "source": "Revenue bulletin",
                  "sourceReference": "https://example.test/tax",
                  "verifiedBy": "Tax Admin",
                  "verifiedAt": "2026-07-22T12:00:00Z"
                }
                """;
    }

    private static TaxTypeResponse typeResponse() {
        return new TaxTypeResponse(TYPE_ID, "GST", "GST", null, true, Instant.parse("2026-07-22T12:00:00Z"), Instant.parse("2026-07-22T12:00:00Z"), 0);
    }

    private static TaxComponentResponse componentResponse() {
        return new TaxComponentResponse(COMPONENT_ID, TYPE_ID, JURISDICTION_ID, "GST", "GST", null, true, Instant.parse("2026-07-22T12:00:00Z"), Instant.parse("2026-07-22T12:00:00Z"), 0);
    }

    private static TaxRateResponse rateResponse() {
        return new TaxRateResponse(
                RATE_ID,
                COMPONENT_ID,
                new BigDecimal("15.000000"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                false,
                false,
                0,
                TaxRateStatus.ACTIVE,
                "Revenue bulletin",
                "https://example.test/tax",
                "Tax Admin",
                Instant.parse("2026-07-22T12:00:00Z"),
                Instant.parse("2026-07-22T12:00:00Z"),
                Instant.parse("2026-07-22T12:00:00Z"),
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
    @Import({TaxTypeController.class, TaxComponentController.class, TaxRateController.class, AuthorizationService.class, TestSecurityConfig.class})
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
