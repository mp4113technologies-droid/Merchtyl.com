package com.merchtyl.features;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditRecordRepository;
import com.merchtyl.register.RegisterRepository;
import com.merchtyl.security.RefreshTokenRepository;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.UserRoleRepository;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FeatureIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TenantFeatureRepository tenantFeatureRepository;

    @Autowired
    StoreFeatureRepository storeFeatureRepository;

    @Autowired
    RegisterFeatureRepository registerFeatureRepository;

    @Autowired
    RegisterRepository registerRepository;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    AuditRecordRepository auditRecordRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    UserRoleRepository userRoleRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void resetData() {
        registerFeatureRepository.deleteAll();
        storeFeatureRepository.deleteAll();
        tenantFeatureRepository.deleteAll();
        auditRecordRepository.deleteAll();
        registerRepository.deleteAll();
        storeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCanResolveAndUpdateDeploymentFeatureOverrideWithAudit() throws Exception {
        String token = registerAndGetToken("owner@features.test", "Owner");

        mockMvc.perform(get("/api/v1/features/definitions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8));

        mockMvc.perform(get("/api/v1/features/resolution")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.definition.code == 'AGE_VERIFICATION')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.definition.code == 'AGE_VERIFICATION')].source").value("DEFAULT"));

        String disabledBody = mockMvc.perform(put("/api/v1/features/AGE_VERIFICATION/deployment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.source").value("TENANT"))
                .andExpect(jsonPath("$.tenantOverride.enabled").value(false))
                .andExpect(jsonPath("$.tenantOverride.version").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode disabled = objectMapper.readTree(disabledBody);
        long version = disabled.get("tenantOverride").get("version").asLong();

        mockMvc.perform(put("/api/v1/features/AGE_VERIFICATION/deployment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "version": %d
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.tenantOverride.version").value(1));

        mockMvc.perform(put("/api/v1/features/AGE_VERIFICATION/deployment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        assertThat(auditRecordRepository.findAll())
                .extracting(record -> record.getAction())
                .contains(AuditAction.FEATURE_OVERRIDE_UPDATED.name());
    }

    @Test
    void resolutionUsesRegisterThenStoreThenDeploymentThenDefaultPrecedence() throws Exception {
        String token = registerAndGetToken("owner@features.test", "Owner");
        String storeId = createStore(token, "main", "Main Store").get("id").asText();
        String registerId = createRegister(token, storeId, "front-1", "Front Register").get("id").asText();

        mockMvc.perform(put("/api/v1/features/LOTTERY_SALES/deployment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("TENANT"));

        mockMvc.perform(put("/api/v1/features/LOTTERY_SALES/stores/{storeId}", storeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("STORE"))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(get("/api/v1/features/resolution")
                        .header("Authorization", "Bearer " + token)
                        .param("storeId", storeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.definition.code == 'LOTTERY_SALES')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.definition.code == 'LOTTERY_SALES')].source").value("STORE"));

        mockMvc.perform(put("/api/v1/features/LOTTERY_SALES/registers/{registerId}", registerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("REGISTER"))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/v1/features/resolution")
                        .header("Authorization", "Bearer " + token)
                        .param("storeId", storeId)
                        .param("registerId", registerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.definition.code == 'LOTTERY_SALES')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.definition.code == 'LOTTERY_SALES')].source").value("REGISTER"));
    }

    private JsonNode createStore(String token, String code, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "%s",
                                  "legalName": "%s LLC",
                                  "countryCode": "us",
                                  "administrativeAreaCode": "ca",
                                  "address": "100 Market Street, San Francisco, CA",
                                  "phone": "+1-555-0100",
                                  "email": "%s@example.test",
                                  "currencyCode": "usd",
                                  "locale": "en_US",
                                  "timezone": "America/Los_Angeles",
                                  "pricesIncludeTax": true,
                                  "negativeStockAllowed": false,
                                  "active": true
                                }
                                """.formatted(code, name, code, code)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode createRegister(String token, String storeId, String code, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/registers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeId": "%s",
                                  "code": "%s",
                                  "name": "%s",
                                  "locationDescription": "Front counter",
                                  "active": true
                                }
                                """.formatted(storeId, code, name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private String registerAndGetToken(String email, String displayName) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "CorrectHorseBattery2026!",
                                  "displayName": "%s"
                                }
                                """.formatted(email, displayName)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
