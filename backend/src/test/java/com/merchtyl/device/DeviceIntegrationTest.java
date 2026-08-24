package com.merchtyl.device;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class DeviceIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DeviceRepository deviceRepository;

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
        deviceRepository.deleteAll();
        registerRepository.deleteAll();
        storeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCanRegisterFetchAndHeartbeatDeviceWithAuditRecord() throws Exception {
        String token = registerAndGetToken("owner@devices.test", "Owner");
        JsonNode store = createStore(token, "main", "Main Store");
        JsonNode register = createRegister(token, store.get("id").asText(), "front-1", "Front Register");
        auditRecordRepository.deleteAll();

        String body = mockMvc.perform(post("/api/v1/devices/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceRegisterJson(store.get("id").asText(), register.get("id").asText(), "browser:abc-123", "Front iPad", "browser")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeId").value(store.get("id").asText()))
                .andExpect(jsonPath("$.registerId").value(register.get("id").asText()))
                .andExpect(jsonPath("$.deviceIdentifier").value("browser:abc-123"))
                .andExpect(jsonPath("$.displayName").value("Front iPad"))
                .andExpect(jsonPath("$.deviceType").value("BROWSER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode device = objectMapper.readTree(body);
        String deviceId = device.get("id").asText();

        mockMvc.perform(get("/api/v1/devices/{id}", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deviceId))
                .andExpect(jsonPath("$.deviceIdentifier").value("browser:abc-123"));

        mockMvc.perform(post("/api/v1/devices/{id}/heartbeat", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deviceId))
                .andExpect(jsonPath("$.version").value(1));

        var auditRecords = auditRecordRepository.findAll();
        assertThat(auditRecords).hasSize(1);
        assertThat(auditRecords.getFirst().getAction()).isEqualTo(AuditAction.DEVICE_REGISTERED.name());
        assertThat(auditRecords.getFirst().getEntityType()).isEqualTo("DEVICE");
        assertThat(auditRecords.getFirst().getEntityId().toString()).isEqualTo(deviceId);
        assertThat(auditRecords.getFirst().getStoreId().toString()).isEqualTo(store.get("id").asText());
        assertThat(auditRecords.getFirst().getRegisterId().toString()).isEqualTo(register.get("id").asText());
        assertThat(auditRecords.getFirst().getAfterSnapshot()).contains("\"deviceIdentifier\":\"browser:abc-123\"");
    }

    @Test
    void listSupportsPaginationAndFiltering() throws Exception {
        String token = registerAndGetToken("owner@devices.test", "Owner");
        JsonNode store = createStore(token, "main", "Main Store");
        JsonNode register = createRegister(token, store.get("id").asText(), "front-1", "Front Register");
        createDevice(token, store.get("id").asText(), register.get("id").asText(), "browser:front-1", "Front Browser", "browser");
        JsonNode secondStore = createStore(token, "second", "Second Store");
        JsonNode secondRegister = createRegister(token, secondStore.get("id").asText(), "front-1", "Second Front Register");
        createDevice(token, secondStore.get("id").asText(), secondRegister.get("id").asText(), "browser:second-1", "Second Browser", "browser");

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token)
                        .param("storeId", store.get("id").asText())
                        .param("registerId", register.get("id").asText())
                        .param("deviceType", "browser")
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].storeId").value(store.get("id").asText()))
                .andExpect(jsonPath("$.content[0].registerId").value(register.get("id").asText()))
                .andExpect(jsonPath("$.content[0].deviceType").value("BROWSER"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void duplicateDeviceIdentifierIsRejected() throws Exception {
        String token = registerAndGetToken("owner@devices.test", "Owner");
        JsonNode store = createStore(token, "main", "Main Store");
        JsonNode register = createRegister(token, store.get("id").asText(), "front-1", "Front Register");
        createDevice(token, store.get("id").asText(), register.get("id").asText(), "browser:front-1", "Front Browser", "browser");

        mockMvc.perform(post("/api/v1/devices/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceRegisterJson(store.get("id").asText(), register.get("id").asText(), "browser:front-1", "Duplicate", "browser")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Device identifier already exists"));
    }

    @Test
    void registerRejectsRegisterFromAnotherStore() throws Exception {
        String token = registerAndGetToken("owner@devices.test", "Owner");
        JsonNode store = createStore(token, "main", "Main Store");
        JsonNode secondStore = createStore(token, "second", "Second Store");
        JsonNode secondRegister = createRegister(token, secondStore.get("id").asText(), "front-1", "Second Front Register");

        mockMvc.perform(post("/api/v1/devices/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceRegisterJson(store.get("id").asText(), secondRegister.get("id").asText(), "browser:mismatch", "Mismatch Browser", "browser")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("registerId must belong to storeId"));
    }

    @Test
    void updateAndStatusPatchRequireCurrentVersion() throws Exception {
        String token = registerAndGetToken("owner@devices.test", "Owner");
        JsonNode store = createStore(token, "main", "Main Store");
        JsonNode register = createRegister(token, store.get("id").asText(), "front-1", "Front Register");
        JsonNode device = createDevice(token, store.get("id").asText(), register.get("id").asText(), "browser:front-1", "Front Browser", "browser");
        String deviceId = device.get("id").asText();

        String updatedBody = mockMvc.perform(put("/api/v1/devices/{id}", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceUpdateJson(store.get("id").asText(), register.get("id").asText(), "browser:front-renamed", "Renamed Browser", "browser", true, device.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceIdentifier").value("browser:front-renamed"))
                .andExpect(jsonPath("$.displayName").value("Renamed Browser"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode updated = objectMapper.readTree(updatedBody);

        mockMvc.perform(put("/api/v1/devices/{id}", deviceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceUpdateJson(store.get("id").asText(), register.get("id").asText(), "browser:stale", "Stale Browser", "browser", true, 0)))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/devices/{id}/status", deviceId)
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
                        AuditAction.DEVICE_REGISTERED.name(),
                        AuditAction.DEVICE_UPDATED.name(),
                        AuditAction.DEVICE_STATUS_CHANGED.name());
    }

    @Test
    void cashierCanViewAndHeartbeatButCannotRegisterDevices() throws Exception {
        String ownerToken = registerAndGetToken("owner@devices.test", "Owner");
        JsonNode store = createStore(ownerToken, "main", "Main Store");
        JsonNode register = createRegister(ownerToken, store.get("id").asText(), "front-1", "Front Register");
        JsonNode device = createDevice(ownerToken, store.get("id").asText(), register.get("id").asText(), "browser:front-1", "Front Browser", "browser");
        String cashierToken = registerAndGetToken("cashier@devices.test", "Cashier");

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + cashierToken)
                        .param("storeId", store.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(post("/api/v1/devices/{id}/heartbeat", device.get("id").asText())
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/devices/register")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceRegisterJson(store.get("id").asText(), register.get("id").asText(), "browser:cashier", "Cashier Browser", "browser")))
                .andExpect(status().isForbidden());
    }

    private JsonNode createDevice(String token, String storeId, String registerId, String deviceIdentifier, String displayName, String deviceType) throws Exception {
        String body = mockMvc.perform(post("/api/v1/devices/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deviceRegisterJson(storeId, registerId, deviceIdentifier, displayName, deviceType)))
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

    private JsonNode createStore(String token, String code, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
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
                                """.formatted(code, name, code, code)))
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

    private String deviceRegisterJson(String storeId, String registerId, String deviceIdentifier, String displayName, String deviceType) {
        return """
                {
                  "storeId": "%s",
                  "registerId": "%s",
                  "deviceIdentifier": "%s",
                  "displayName": "%s",
                  "deviceType": "%s"
                }
                """.formatted(storeId, registerId, deviceIdentifier, displayName, deviceType);
    }

    private String deviceUpdateJson(
            String storeId,
            String registerId,
            String deviceIdentifier,
            String displayName,
            String deviceType,
            boolean active,
            long version) {
        return """
                {
                  "storeId": "%s",
                  "registerId": "%s",
                  "deviceIdentifier": "%s",
                  "displayName": "%s",
                  "deviceType": "%s",
                  "active": %s,
                  "version": %d
                }
                """.formatted(storeId, registerId, deviceIdentifier, displayName, deviceType, active, version);
    }
}
