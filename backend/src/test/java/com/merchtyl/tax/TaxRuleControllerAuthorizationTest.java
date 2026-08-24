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

@SpringBootTest(classes = TaxRuleControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class TaxRuleControllerAuthorizationTest {
    private static final UUID RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000001501");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000001201");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TaxRuleService taxRuleService;

    @Test
    void taxViewerCanListAndEvaluateRules() throws Exception {
        when(taxRuleService.search(any())).thenReturn(new PageResponse<>(List.of(ruleResponse()), 0, 20, 1, 1, true, true));
        when(taxRuleService.evaluate(any(), any())).thenReturn(evaluationResponse());

        mockMvc.perform(get("/api/v1/tax/rules").with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("STANDARD"));

        mockMvc.perform(post("/api/v1/tax/rules/evaluate")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerExempt": false,
                                  "transactionDate": "2026-07-22",
                                  "saleChannel": "POS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedTaxGroupIds[0]").value(GROUP_ID.toString()));
    }

    @Test
    void taxViewerCannotCreateOrDeactivateRules() throws Exception {
        mockMvc.perform(post("/api/v1/tax/rules")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/tax/rules/{id}/status", RULE_ID)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(taxRuleService, never()).create(any(), any());
        verify(taxRuleService, never()).updateStatus(any(), any(), any());
    }

    @Test
    void taxManagerCanCreateRules() throws Exception {
        when(taxRuleService.create(any(), any())).thenReturn(ruleResponse());

        mockMvc.perform(post("/api/v1/tax/rules")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("TAX_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("STANDARD"));
    }

    private static String ruleJson() {
        return """
                {
                  "code": "STANDARD",
                  "name": "Standard",
                  "priority": 10,
                  "effectiveFrom": "2026-01-01",
                  "effectiveTo": null,
                  "active": true,
                  "conditions": [],
                  "actions": [
                    {
                      "actionType": "APPLY_TAX_GROUP",
                      "taxGroupId": "00000000-0000-0000-0000-000000001201"
                    }
                  ]
                }
                """;
    }

    private static TaxRuleResponse ruleResponse() {
        return new TaxRuleResponse(
                RULE_ID,
                "STANDARD",
                "Standard",
                null,
                10,
                LocalDate.of(2026, 1, 1),
                null,
                true,
                List.of(),
                List.of(new TaxRuleActionResponse(UUID.fromString("00000000-0000-0000-0000-000000001502"), TaxRuleActionType.APPLY_TAX_GROUP, GROUP_ID, null, null)),
                Instant.parse("2026-07-22T12:00:00Z"),
                Instant.parse("2026-07-22T12:00:00Z"),
                0);
    }

    private static TaxRuleEvaluationResponse evaluationResponse() {
        return new TaxRuleEvaluationResponse(
                List.of(GROUP_ID),
                List.of(),
                List.of(),
                false,
                false,
                false,
                IncludedPriceBehavior.USE_RATE_SETTING,
                TaxRoundingStrategy.HALF_UP,
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
    @Import({TaxRuleController.class, AuthorizationService.class, TestSecurityConfig.class})
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
