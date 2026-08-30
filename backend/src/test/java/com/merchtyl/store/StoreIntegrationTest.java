package com.merchtyl.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditRecordRepository;
import com.merchtyl.security.RefreshTokenRepository;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.UserRoleRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class StoreIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

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
        auditRecordRepository.deleteAll();
        storeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCanCreateAndFetchStoreWithAuditRecord() throws Exception {
        String token = registerAndGetToken("owner@stores.test", "Owner");

        String body = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storeJson("main", "Main Store", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MAIN"))
                .andExpect(jsonPath("$.name").value("Main Store"))
                .andExpect(jsonPath("$.countryCode").value("US"))
                .andExpect(jsonPath("$.administrativeAreaCode").value("CA"))
                .andExpect(jsonPath("$.email").value("main@example.test"))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.locale").value("en-US"))
                .andExpect(jsonPath("$.timezone").value("America/Los_Angeles"))
                .andExpect(jsonPath("$.pricesIncludeTax").value(true))
                .andExpect(jsonPath("$.negativeStockAllowed").value(false))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode store = objectMapper.readTree(body);
        String storeId = store.get("id").asText();

        mockMvc.perform(get("/api/v1/stores/{id}", storeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(storeId))
                .andExpect(jsonPath("$.code").value("MAIN"));

        var auditRecords = auditRecordRepository.findAll();
        assertThat(auditRecords).hasSize(1);
        assertThat(auditRecords.getFirst().getAction()).isEqualTo(AuditAction.STORE_CREATED.name());
        assertThat(auditRecords.getFirst().getEntityType()).isEqualTo("STORE");
        assertThat(auditRecords.getFirst().getEntityId().toString()).isEqualTo(storeId);
        assertThat(auditRecords.getFirst().getStoreId().toString()).isEqualTo(storeId);
        assertThat(auditRecords.getFirst().getActorUserId()).isNotNull();
        assertThat(auditRecords.getFirst().getAfterSnapshot()).contains("\"code\":\"MAIN\"");
    }

    @Test
    void listSupportsPaginationAndFiltering() throws Exception {
        String token = registerAndGetToken("owner@stores.test", "Owner");
        createStore(token, "main", "Main Store", true);
        createStore(token, "warehouse", "Warehouse", false);
        createStore(token, "annex", "Annex Store", true);

        mockMvc.perform(get("/api/v1/stores")
                        .header("Authorization", "Bearer " + token)
                        .param("countryCode", "us")
                        .param("currencyCode", "usd")
                        .param("active", "true")
                        .param("name", "store")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("ANNEX"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void updateAndStatusPatchRequireCurrentVersion() throws Exception {
        String token = registerAndGetToken("owner@stores.test", "Owner");
        JsonNode created = createStore(token, "main", "Main Store", true);
        String storeId = created.get("id").asText();

        String updatedBody = mockMvc.perform(put("/api/v1/stores/{id}", storeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("main-renamed", "Renamed Store", created.get("version").asLong(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MAIN-RENAMED"))
                .andExpect(jsonPath("$.name").value("Renamed Store"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode updated = objectMapper.readTree(updatedBody);

        mockMvc.perform(put("/api/v1/stores/{id}", storeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("main-stale", "Stale Store", 0, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        mockMvc.perform(patch("/api/v1/stores/{id}/status", storeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false,
                                  "version": %d
                                }
                                """.formatted(updated.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(2));

        assertThat(auditRecordRepository.findAll())
                .extracting(record -> record.getAction())
                .contains(
                        AuditAction.STORE_CREATED.name(),
                        AuditAction.STORE_UPDATED.name(),
                        AuditAction.STORE_STATUS_CHANGED.name());
    }

    @Test
    void duplicateStoreCodeIsRejectedCaseInsensitively() throws Exception {
        String token = registerAndGetToken("owner@stores.test", "Owner");
        createStore(token, "main", "Main Store", true);

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storeJson("MAIN", "Duplicate Main Store", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Store code already exists"));
    }

    @Test
    void validationRejectsInvalidStorePayload() throws Exception {
        String token = registerAndGetToken("owner@stores.test", "Owner");

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storeJson("main", "Main Store", true).replace(
                                "\"timezone\": \"America/Los_Angeles\"",
                                "\"timezone\": \"Mars/Base\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    void cashierCanViewButCannotManageStores() throws Exception {
        String ownerToken = registerAndGetToken("owner@stores.test", "Owner");
        createStore(ownerToken, "main", "Main Store", true);
        String cashierToken = registerAndGetToken("cashier@stores.test", "Cashier");

        mockMvc.perform(get("/api/v1/stores")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storeJson("cashier", "Cashier Store", true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportsCapabilityCombinationsAndEnforcesFoodServiceApi() throws Exception {
        String token = registerAndGetToken("operations@stores.test", "Operations Owner");
        JsonNode retail = createStore(token, "retail", "Retail Store", true);
        String foodBody = storeJson("food", "Restaurant", true)
                .replace("\"capabilities\": [\"RETAIL\"]", "\"capabilities\": [\"FOOD_SERVICE\"], \"kitchenDisplayName\": \"Joe's Kitchen\"");
        String bothBody = storeJson("both", "Market", true)
                .replace("\"capabilities\": [\"RETAIL\"]", "\"capabilities\": [\"RETAIL\", \"FOOD_SERVICE\"]");

        JsonNode food = objectMapper.readTree(mockMvc.perform(post("/api/v1/stores").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(foodBody)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.capabilities[0]").value("FOOD_SERVICE"))
                .andExpect(jsonPath("$.kitchenDisplayName").value("Joe's Kitchen")).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/stores").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(bothBody)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.foodServiceEnabled").value(true))
                .andExpect(jsonPath("$.kitchenDisplayName").value("Market Kitchen"));

        mockMvc.perform(get("/api/v1/stores/{id}/food-service/configuration", retail.get("id").asText())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/stores/{id}/food-service/configuration", food.get("id").asText())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantPosEnabled").value(true));
    }

    @Test
    void rejectsStoreWithoutCapabilities() throws Exception {
        String token = registerAndGetToken("nocap@stores.test", "No Cap Owner");
        mockMvc.perform(post("/api/v1/stores").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storeJson("none", "No Operations", true).replace("[\"RETAIL\"]", "[]")))
                .andExpect(status().isBadRequest());
    }

    private JsonNode createStore(String token, String code, String name, boolean active) throws Exception {
        String body = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storeJson(code, name, active)))
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

    private String storeJson(String code, String name, boolean active) {
        return """
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
                  "active": %s
                  ,"capabilities": ["RETAIL"]
                }
                """.formatted(code, name, code, code, active);
    }

    private String updateJson(String code, String name, long version, boolean active) {
        return """
                {
                  "code": "%s",
                  "name": "%s",
                  "legalName": "%s LLC",
                  "countryCode": "US",
                  "administrativeAreaCode": "CA",
                  "address": "200 Market Street, San Francisco, CA",
                  "phone": "+1-555-0200",
                  "email": "%s@example.test",
                  "currencyCode": "USD",
                  "locale": "en-US",
                  "timezone": "America/Los_Angeles",
                  "pricesIncludeTax": false,
                  "negativeStockAllowed": true,
                  "active": %s,
                  "capabilities": ["RETAIL"],
                  "version": %d
                }
                """.formatted(code, name, code, code, active, version);
    }
}
