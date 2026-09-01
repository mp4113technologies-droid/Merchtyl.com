package com.merchtyl.register;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditRecordRepository;
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

import java.util.UUID;

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
class RegisterIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

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
        auditRecordRepository.deleteAll();
        registerRepository.deleteAll();
        storeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCanCreateAndFetchRegisterWithAuditRecord() throws Exception {
        String token = registerAndGetToken("owner@registers.test", "Owner");
        String storeId = createStore(token, "main", "Main Store").get("id").asText();
        auditRecordRepository.deleteAll();

        String body = mockMvc.perform(post("/api/v1/registers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(storeId, "front-1", "Front Register", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeId").value(storeId))
                .andExpect(jsonPath("$.code").value("FRONT-1"))
                .andExpect(jsonPath("$.name").value("Front Register"))
                .andExpect(jsonPath("$.locationDescription").value("Front counter"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode register = objectMapper.readTree(body);
        String registerId = register.get("id").asText();

        mockMvc.perform(get("/api/v1/registers/{id}", registerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registerId))
                .andExpect(jsonPath("$.storeId").value(storeId))
                .andExpect(jsonPath("$.code").value("FRONT-1"));

        var auditRecords = auditRecordRepository.findAll();
        assertThat(auditRecords).hasSize(1);
        assertThat(auditRecords.getFirst().getAction()).isEqualTo(AuditAction.REGISTER_CREATED.name());
        assertThat(auditRecords.getFirst().getEntityType()).isEqualTo("REGISTER");
        assertThat(auditRecords.getFirst().getEntityId().toString()).isEqualTo(registerId);
        assertThat(auditRecords.getFirst().getStoreId().toString()).isEqualTo(storeId);
        assertThat(auditRecords.getFirst().getRegisterId().toString()).isEqualTo(registerId);
        assertThat(auditRecords.getFirst().getActorUserId()).isNotNull();
        assertThat(auditRecords.getFirst().getAfterSnapshot()).contains("\"code\":\"FRONT-1\"");
    }

    @Test
    void listSupportsPaginationAndFilteringByStore() throws Exception {
        String token = registerAndGetToken("owner@registers.test", "Owner");
        String mainStoreId = createStore(token, "main", "Main Store").get("id").asText();
        String secondStoreId = createStore(token, "second", "Second Store").get("id").asText();
        createRegister(token, mainStoreId, "front-1", "Front Register", true);
        createRegister(token, mainStoreId, "back-1", "Back Register", false);
        createRegister(token, secondStoreId, "front-1", "Second Front Register", true);

        mockMvc.perform(get("/api/v1/registers")
                        .header("Authorization", "Bearer " + token)
                        .param("storeId", mainStoreId)
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].storeId").value(mainStoreId))
                .andExpect(jsonPath("$.content[0].code").value("FRONT-1"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void registerCodeIsUniqueWithinStoreOnly() throws Exception {
        String token = registerAndGetToken("owner@registers.test", "Owner");
        String mainStoreId = createStore(token, "main", "Main Store").get("id").asText();
        String secondStoreId = createStore(token, "second", "Second Store").get("id").asText();
        createRegister(token, mainStoreId, "front-1", "Front Register", true);

        mockMvc.perform(post("/api/v1/registers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(mainStoreId, "FRONT-1", "Duplicate Front Register", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Register code already exists for this store"));

        mockMvc.perform(post("/api/v1/registers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(secondStoreId, "front-1", "Second Front Register", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeId").value(secondStoreId))
                .andExpect(jsonPath("$.code").value("FRONT-1"));
    }

    @Test
    void createRejectsUnknownStore() throws Exception {
        String token = registerAndGetToken("owner@registers.test", "Owner");
        UUID missingStoreId = UUID.fromString("00000000-0000-0000-0000-000000009999");

        mockMvc.perform(post("/api/v1/registers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(missingStoreId.toString(), "front-1", "Front Register", true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Store not found"));
    }

    @Test
    void updateAndStatusPatchRequireCurrentVersion() throws Exception {
        String token = registerAndGetToken("owner@registers.test", "Owner");
        String storeId = createStore(token, "main", "Main Store").get("id").asText();
        JsonNode created = createRegister(token, storeId, "front-1", "Front Register", true);
        String registerId = created.get("id").asText();

        String updatedBody = mockMvc.perform(put("/api/v1/registers/{id}", registerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(storeId, "front-renamed", "Renamed Register", created.get("version").asLong(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FRONT-RENAMED"))
                .andExpect(jsonPath("$.name").value("Renamed Register"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode updated = objectMapper.readTree(updatedBody);

        mockMvc.perform(put("/api/v1/registers/{id}", registerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(storeId, "front-stale", "Stale Register", 0, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        mockMvc.perform(patch("/api/v1/registers/{id}/status", registerId)
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
                        AuditAction.REGISTER_CREATED.name(),
                        AuditAction.REGISTER_UPDATED.name(),
                        AuditAction.REGISTER_STATUS_CHANGED.name());
    }

    @Test
    void cashierCanViewButCannotManageRegisters() throws Exception {
        String ownerToken = registerAndGetToken("owner@registers.test", "Owner");
        String storeId = createStore(ownerToken, "main", "Main Store").get("id").asText();
        createRegister(ownerToken, storeId, "front-1", "Front Register", true);
        String cashierToken = registerAndGetToken("cashier@registers.test", "Cashier");

        mockMvc.perform(get("/api/v1/registers")
                        .header("Authorization", "Bearer " + cashierToken)
                        .param("storeId", storeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(post("/api/v1/registers")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(storeId, "cashier", "Cashier Register", true)))
                .andExpect(status().isForbidden());
    }

    private JsonNode createRegister(String token, String storeId, String code, String name, boolean active) throws Exception {
        String body = mockMvc.perform(post("/api/v1/registers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(storeId, code, name, active)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode createStore(String token, String code, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storeJson(code, name)))
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

    private String registerJson(String storeId, String code, String name, boolean active) {
        return """
                {
                  "storeId": "%s",
                  "code": "%s",
                  "name": "%s",
                  "locationDescription": "Front counter",
                  "active": %s,
                  "type": "RETAIL"
                }
                """.formatted(storeId, code, name, active);
    }

    private String updateJson(String storeId, String code, String name, long version, boolean active) {
        return """
                {
                  "storeId": "%s",
                  "code": "%s",
                  "name": "%s",
                  "locationDescription": "Checkout lane",
                  "active": %s,
                  "type": "RETAIL",
                  "version": %d
                }
                """.formatted(storeId, code, name, active, version);
    }

    private String storeJson(String code, String name) {
        return """
                {
                  "code": "%s",
                  "name": "%s",
                  "legalName": "%s LLC",
                  "countryCode": "US",
                  "administrativeAreaCode": "CA",
                  "address": "100 Market Street, San Francisco, CA",
                  "phone": "+1-555-0100",
                  "email": "%s@example.test",
                  "currencyCode": "USD",
                  "locale": "en-US",
                  "timezone": "America/Los_Angeles",
                  "pricesIncludeTax": true,
                  "negativeStockAllowed": false,
                  "active": true
                }
                """.formatted(code, name, code, code);
    }
}
