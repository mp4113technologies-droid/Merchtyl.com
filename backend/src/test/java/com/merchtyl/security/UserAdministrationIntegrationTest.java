package com.merchtyl.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UserAdministrationIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired UserRegisterAssignmentRepository registerAssignments;

    @Test
    void sameEmailUpdatePreservesExistingRegisterAndUpdatesStoreAssignments() throws Exception {
        String token = registerAndGetToken("owner@user-update.test", "Owner");
        JsonNode storeOne = createStore(token, "one");
        JsonNode storeTwo = createStore(token, "two");
        JsonNode register = createRegister(token, storeOne.get("id").asText(), "front");
        JsonNode user = createUser(token, "test4@gmail.com", storeOne.get("id").asText(), register.get("id").asText());

        mockMvc.perform(put("/api/v1/users/{id}", user.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(user, " TEST4@gmail.com ", storeOne.get("id").asText(),
                                storeTwo.get("id").asText(), register.get("id").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test4@gmail.com"))
                .andExpect(jsonPath("$.displayName").value("Updated User"))
                .andExpect(jsonPath("$.storeIds.length()").value(2))
                .andExpect(jsonPath("$.registerIds[0]").value(register.get("id").asText()));

        User persisted = userRepository.findById(java.util.UUID.fromString(user.get("id").asText())).orElseThrow();
        assertThat(registerAssignments.findByUser(persisted))
                .extracting(assignment -> assignment.getRegister().getId())
                .containsExactly(java.util.UUID.fromString(register.get("id").asText()));
    }

    @Test
    void oneUserUpdateReconcilesRegisterAssignmentsAsAnUnorderedSet() throws Exception {
        String token = registerAndGetToken("owner-registers@user-update.test", "Owner");
        JsonNode store = createStore(token, "register-set");
        JsonNode firstRegister = createRegister(token, store.get("id").asText(), "first");
        JsonNode secondRegister = createRegister(token, store.get("id").asText(), "second");
        JsonNode user = createUser(token, "test3@adviam.com", store.get("id").asText(), firstRegister.get("id").asText());

        JsonNode keptAndAdded = update(token, user, List.of(store.get("id").asText()),
                List.of(firstRegister.get("id").asText(), secondRegister.get("id").asText()));
        assertThat(keptAndAdded.get("registerIds").size()).isEqualTo(2);

        JsonNode reordered = update(token, keptAndAdded, List.of(store.get("id").asText()),
                List.of(secondRegister.get("id").asText(), firstRegister.get("id").asText()));
        assertThat(reordered.get("registerIds").size()).isEqualTo(2);

        JsonNode removed = update(token, reordered, List.of(store.get("id").asText()),
                List.of(firstRegister.get("id").asText()));
        assertThat(removed.get("registerIds")).extracting(JsonNode::asText)
                .containsExactly(firstRegister.get("id").asText());

        JsonNode replaced = update(token, removed, List.of(store.get("id").asText()),
                List.of(secondRegister.get("id").asText()));
        assertThat(replaced.get("registerIds")).extracting(JsonNode::asText)
                .containsExactly(secondRegister.get("id").asText());
        assertThat(replaced.get("version").asLong()).isGreaterThan(user.get("version").asLong());
    }

    @Test
    void emailCanChangeToUnusedButNotAnotherUsersEmailIgnoringCase() throws Exception {
        String token = registerAndGetToken("owner2@user-update.test", "Owner");
        JsonNode store = createStore(token, "email-store");
        JsonNode first = createUser(token, "first@gmail.com", store.get("id").asText(), null);
        JsonNode second = createUser(token, "second@gmail.com", store.get("id").asText(), null);

        String changedBody = mockMvc.perform(put("/api/v1/users/{id}", second.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(second, " unused@gmail.com ", List.of(store.get("id").asText()), List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("unused@gmail.com"))
                .andReturn().getResponse().getContentAsString();
        JsonNode changed = objectMapper.readTree(changedBody);

        mockMvc.perform(put("/api/v1/users/{id}", changed.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(changed, " FIRST@GMAIL.COM ", List.of(store.get("id").asText()), List.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User email is already registered"));

        assertThat(userRepository.findById(java.util.UUID.fromString(first.get("id").asText())).orElseThrow().getEmail())
                .isEqualTo("first@gmail.com");
    }

    @Test
    void staleVersionAndCrossTenantUpdatesRemainRejected() throws Exception {
        String ownerToken = registerAndGetToken("owner3@user-update.test", "Owner");
        JsonNode ownerStore = createStore(ownerToken, "owner-store");
        JsonNode user = createUser(ownerToken, "tenant-user@gmail.com", ownerStore.get("id").asText(), null);

        mockMvc.perform(put("/api/v1/users/{id}", user.get("id").asText())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(user, user.get("email").asText(), List.of(ownerStore.get("id").asText()), List.of())
                                .replace("\"version\":0", "\"version\":99")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User was modified by another transaction"));

        String otherTenantToken = registerAndGetToken("other-owner@user-update.test", "Other Owner");
        mockMvc.perform(put("/api/v1/users/{id}", user.get("id").asText())
                        .header("Authorization", "Bearer " + otherTenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(user, user.get("email").asText(), List.of(ownerStore.get("id").asText()), List.of())))
                .andExpect(status().isNotFound());
    }

    private String registerAndGetToken(String email, String displayName) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"CorrectHorse2026!","displayName":"%s"}
                                """.formatted(email, displayName)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private JsonNode createStore(String token, String code) throws Exception {
        String body = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"Store %s","legalName":"Store %s LLC","countryCode":"US",
                                 "administrativeAreaCode":"CA","address":"100 Main St","phone":"+1-555-0100",
                                 "email":"%s@store.test","currencyCode":"USD","locale":"en-US",
                                 "timezone":"America/Los_Angeles","pricesIncludeTax":true,
                                 "negativeStockAllowed":false,"active":true}
                                """.formatted(code, code, code, code)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode createRegister(String token, String storeId, String code) throws Exception {
        String body = mockMvc.perform(post("/api/v1/registers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":"%s","code":"%s","name":"Front Register",
                                 "locationDescription":"Front","active":true,"type":"RETAIL"}
                                """.formatted(storeId, code)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode createUser(String token, String email, String storeId, String registerId) throws Exception {
        String registers = registerId == null ? "" : "\"" + registerId + "\"";
        String body = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","displayName":"Test User","password":"CashierPass2026!",
                                 "roles":["CASHIER"],"storeIds":["%s"],"registerIds":[%s],
                                 "enabled":true,"locked":false}
                                """.formatted(email, storeId, registers)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode update(String token, JsonNode user, List<String> storeIds, List<String> registerIds) throws Exception {
        String body = mockMvc.perform(put("/api/v1/users/{id}", user.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(user, user.get("email").asText(), storeIds, registerIds)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String updateJson(JsonNode user, String email, String storeOne, String storeTwo, String registerId) {
        return updateJson(user, email, List.of(storeOne, storeTwo), List.of(registerId));
    }

    private String updateJson(JsonNode user, String email, java.util.List<String> storeIds, java.util.List<String> registerIds) {
        String stores = storeIds.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
        String registers = registerIds.stream().map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
        return """
                {"email":"%s","displayName":"Updated User","locked":false,"roles":["CASHIER"],
                 "storeIds":[%s],"registerIds":[%s],"version":%d}
                """.formatted(email, stores, registers, user.get("version").asLong());
    }
}
