package com.merchtyl.eod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.MerchtylApplication;
import com.merchtyl.security.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(classes = MerchtylApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MERCHTYL_RUN_LOCAL_PG", matches = "true")
class BusinessDayRecoveryIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    private UUID tenantId;

    @Test
    void ownerClosesPreviousOpenBusinessDayWithVersionZeroRecoveryPayload() throws Exception {
        registerOwner();
        String storeId = createStore().toString();
        LocalDate previousDate = LocalDate.now().minusDays(1);

        String openedBody = mockMvc.perform(post("/api/v1/business-days/open")
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":"%s","businessDate":"%s","overrideOpenPrevious":false}
                                """.formatted(storeId, previousDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();
        JsonNode opened = objectMapper.readTree(openedBody);
        String dayId = opened.get("id").asText();

        mockMvc.perform(post("/api/v1/business-days/{id}/close", dayId)
                        .with(owner())
                        .header("Idempotency-Key", "previous-day-recovery-close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"managerNotes":"Closed from previous business day recovery action.",
                                 "varianceExplanation":"","confirmationAccepted":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value(previousDate.toString()))
                .andExpect(jsonPath("$.signOff.varianceExplanation").doesNotExist());

        String stateBody = mockMvc.perform(get("/api/v1/business-days/operational-state")
                        .with(owner())
                        .param("storeId", storeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("HISTORICAL_CLOSED"))
                .andExpect(jsonPath("$.availableAction").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(stateBody).get("currentBusinessDay").isNull()).isTrue();
    }

    private UUID createStore() {
        UUID storeId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into stores (id, code, name, country_code, address, currency_code, locale, timezone, tenant_id)
                values (?, 'RECOVERY', 'Recovery Store', 'CA', '100 Main Street', 'CAD', 'en-CA',
                        'America/Moncton', ?)
                """, storeId, tenantId);
        jdbcTemplate.update("insert into store_capabilities (store_id, capability) values (?, 'RETAIL')", storeId);
        return storeId;
    }

    private void registerOwner() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@business-day-recovery.test","password":"RecoveryTest2026!","displayName":"Recovery Owner"}
                                """))
                .andExpect(status().isCreated());
        tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into tenants (id, tenant_code, merchant_slug, legal_name, display_name, status, country_code,
                                     default_currency_code, primary_timezone, activated_at)
                values (?, ?, 'recovery-merchant', ?, ?, 'ACTIVE', 'CA', 'CAD', 'America/Moncton', now())
                """, tenantId, "RECOVERY-" + tenantId, "Recovery Merchant Inc.", "Recovery Merchant");
        var owner = userRepository.findByEmailIgnoreCase("owner@business-day-recovery.test").orElseThrow();
        owner.assignTenant(tenantId);
        userRepository.saveAndFlush(owner);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor owner() {
        return user("owner@business-day-recovery.test").authorities(
                new SimpleGrantedAuthority("ACCOUNT_SCOPE_TENANT"),
                new SimpleGrantedAuthority("ROLE_OWNER"),
                new SimpleGrantedAuthority("STORE_CREATE"),
                new SimpleGrantedAuthority("BUSINESS_DAY_OPEN"),
                new SimpleGrantedAuthority("BUSINESS_DAY_CLOSE"),
                new SimpleGrantedAuthority("BUSINESS_DAY_VIEW"));
    }
}
