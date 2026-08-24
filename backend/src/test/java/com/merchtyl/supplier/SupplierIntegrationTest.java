package com.merchtyl.supplier;

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
class SupplierIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    SupplierRepository supplierRepository;

    @Autowired
    ProductSupplierRepository productSupplierRepository;

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
        productSupplierRepository.deleteAll();
        supplierRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCanCreateAndFetchSupplierWithAuditRecord() throws Exception {
        String token = registerAndGetToken("owner@suppliers.test", "Owner");

        String body = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson("acme", "ACME Foods", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ACME"))
                .andExpect(jsonPath("$.name").value("ACME Foods"))
                .andExpect(jsonPath("$.contactName").value("Jane Buyer"))
                .andExpect(jsonPath("$.email").value("orders@acme.example"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode supplier = objectMapper.readTree(body);
        String supplierId = supplier.get("id").asText();

        mockMvc.perform(get("/api/v1/suppliers/{id}", supplierId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supplierId))
                .andExpect(jsonPath("$.code").value("ACME"));

        var auditRecords = auditRecordRepository.findAll();
        assertThat(auditRecords).hasSize(1);
        assertThat(auditRecords.getFirst().getAction()).isEqualTo(AuditAction.SUPPLIER_CREATED.name());
        assertThat(auditRecords.getFirst().getEntityType()).isEqualTo("SUPPLIER");
        assertThat(auditRecords.getFirst().getEntityId().toString()).isEqualTo(supplierId);
        assertThat(auditRecords.getFirst().getActorUserId()).isNotNull();
        assertThat(auditRecords.getFirst().getAfterSnapshot()).contains("\"code\":\"ACME\"");
    }

    @Test
    void listSupportsPaginationAndFiltering() throws Exception {
        String token = registerAndGetToken("owner@suppliers.test", "Owner");
        createSupplier(token, "acme", "ACME Foods", true);
        createSupplier(token, "fresh", "Fresh Goods", false);
        createSupplier(token, "global", "Global Foods", true);

        mockMvc.perform(get("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .param("active", "true")
                        .param("name", "foods")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("ACME"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void updateAndStatusPatchRequireCurrentVersion() throws Exception {
        String token = registerAndGetToken("owner@suppliers.test", "Owner");
        JsonNode created = createSupplier(token, "acme", "ACME Foods", true);
        String supplierId = created.get("id").asText();

        String updatedBody = mockMvc.perform(put("/api/v1/suppliers/{id}", supplierId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("acme-renamed", "Renamed Supplier", created.get("version").asLong(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ACME-RENAMED"))
                .andExpect(jsonPath("$.name").value("Renamed Supplier"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode updated = objectMapper.readTree(updatedBody);

        mockMvc.perform(put("/api/v1/suppliers/{id}", supplierId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("acme-stale", "Stale Supplier", 0, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        mockMvc.perform(patch("/api/v1/suppliers/{id}/status", supplierId)
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
                        AuditAction.SUPPLIER_CREATED.name(),
                        AuditAction.SUPPLIER_UPDATED.name(),
                        AuditAction.SUPPLIER_STATUS_CHANGED.name());
    }

    @Test
    void duplicateSupplierCodeIsRejectedCaseInsensitively() throws Exception {
        String token = registerAndGetToken("owner@suppliers.test", "Owner");
        createSupplier(token, "acme", "ACME Foods", true);

        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson("ACME", "Duplicate Supplier", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Supplier code already exists"));
    }

    @Test
    void validationRejectsInvalidSupplierPayload() throws Exception {
        String token = registerAndGetToken("owner@suppliers.test", "Owner");

        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson("bad code", "Bad Supplier", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    void cashierCanViewButCannotManageSuppliers() throws Exception {
        String ownerToken = registerAndGetToken("owner@suppliers.test", "Owner");
        createSupplier(ownerToken, "acme", "ACME Foods", true);
        String cashierToken = registerAndGetToken("cashier@suppliers.test", "Cashier");

        mockMvc.perform(get("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson("cashier", "Cashier Supplier", true)))
                .andExpect(status().isForbidden());
    }

    private JsonNode createSupplier(String token, String code, String name, boolean active) throws Exception {
        String body = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson(code, name, active)))
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

    private String supplierJson(String code, String name, boolean active) {
        return """
                {
                  "code": "%s",
                  "name": "%s",
                  "contactName": "Jane Buyer",
                  "phone": "555-0100",
                  "email": "orders@%s.example",
                  "address": "10 Warehouse Road",
                  "notes": "Net 30",
                  "active": %s
                }
                """.formatted(code, name, code, active);
    }

    private String updateJson(String code, String name, long version, boolean active) {
        return """
                {
                  "code": "%s",
                  "name": "%s",
                  "contactName": "Updated Contact",
                  "phone": "555-0200",
                  "email": "updated@%s.example",
                  "address": "20 Warehouse Road",
                  "notes": "Updated terms",
                  "active": %s,
                  "version": %d
                }
                """.formatted(code, name, code, active, version);
    }
}
