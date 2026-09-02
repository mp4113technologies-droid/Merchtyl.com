package com.merchtyl.foodmenu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FoodMenuIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Flyway flyway;

    @Test
    void createsMadeToOrderItemWithExactPayloadAndPersistedOwnership() throws Exception {
        String token = register("menu-owner@foodmenu.test");
        JsonNode store = createFoodStore(token, "MONKEY");
        JsonNode category = createCategory(token, store.get("id").asText(), "Appetizers");

        String response = mockMvc.perform(post("/api/v1/stores/{storeId}/food-menu/items", store.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(category.get("id").asText())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeId").value(store.get("id").asText()))
                .andExpect(jsonPath("$.categoryId").value(category.get("id").asText()))
                .andExpect(jsonPath("$.displayName").value("Monkey fingers"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.price").value(10.0))
                .andExpect(jsonPath("$.displayOrder").value(1))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andExpect(jsonPath("$.madeToOrder").value(true))
                .andReturn().getResponse().getContentAsString();

        UUID itemId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        var row = jdbcTemplate.queryForMap("""
                SELECT item.store_id, item.tenant_id, item.category_id, item.price,
                       item.display_order, item.available, item.description, item.image_url,
                       item.linked_product, product.restaurant_menu_managed,
                       item.tenant_id = store.tenant_id AS ownership_matches
                  FROM food_menu_items item
                  JOIN products product ON product.id = item.product_id
                  JOIN stores store ON store.id = item.store_id
                 WHERE item.id = ?
                """, itemId);
        assertThat(row.get("store_id")).isEqualTo(UUID.fromString(store.get("id").asText()));
        assertThat(row.get("tenant_id")).isNotNull();
        assertThat(row.get("ownership_matches")).isEqualTo(true);
        assertThat(row.get("category_id")).isEqualTo(UUID.fromString(category.get("id").asText()));
        assertThat(row.get("description")).isNull();
        assertThat(row.get("image_url")).isNull();
        assertThat(row.get("linked_product")).isEqualTo(false);
        assertThat(row.get("restaurant_menu_managed")).isEqualTo(true);
    }

    @Test
    void rejectsCategoryFromAnotherStoreWithoutWritingMenuItem() throws Exception {
        String token = register("cross-store@foodmenu.test");
        JsonNode requestedStore = createFoodStore(token, "REQUESTED");
        JsonNode otherStore = createFoodStore(token, "OTHER");
        JsonNode category = createCategory(token, otherStore.get("id").asText(), "Other store category");

        mockMvc.perform(post("/api/v1/stores/{storeId}/food-menu/items", requestedStore.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(category.get("id").asText())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message").value("Food category not found"));
    }

    @Test
    void rejectsMissingCategoryAndInvalidRequestBeforeDatabaseWrite() throws Exception {
        String token = register("validation@foodmenu.test");
        JsonNode store = createFoodStore(token, "VALIDATION");

        mockMvc.perform(post("/api/v1/stores/{storeId}/food-menu/items", store.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Food category not found"));

        mockMvc.perform(post("/api/v1/stores/{storeId}/food-menu/items", store.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(UUID.randomUUID().toString()).replace("\"price\": 10", "\"price\": -1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void madeToOrderMigrationIsApplied() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("91");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM information_schema.columns
                 WHERE table_schema = current_schema()
                   AND ((table_name = 'products' AND column_name = 'restaurant_menu_managed')
                     OR (table_name = 'food_menu_items' AND column_name IN ('description', 'linked_product')))
                """, Integer.class)).isEqualTo(3);
    }

    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"CorrectHorseBattery2026!","displayName":"Menu Owner"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private JsonNode createFoodStore(String token, String code) throws Exception {
        String body = mockMvc.perform(post("/api/v1/stores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s Restaurant","legalName":"%s Foods LLC",
                                 "countryCode":"CA","administrativeAreaCode":"NB","address":"1 Main Street",
                                 "phone":"+1-506-555-0100","email":"%s@foodmenu.test","currencyCode":"CAD",
                                 "locale":"en-CA","timezone":"America/Moncton","pricesIncludeTax":false,
                                 "negativeStockAllowed":false,"active":true,"capabilities":["FOOD_SERVICE"]}
                                """.formatted(code, code, code, code.toLowerCase())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode createCategory(String token, String storeId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/stores/{storeId}/food-menu/categories", storeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"displayOrder\":1,\"active\":true,\"imageUrl\":\"\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String itemJson(String categoryId) {
        return """
                {"categoryId":"%s","displayName":"Monkey fingers","description":"","price":10,
                 "displayOrder":1,"available":true,"imageUrl":""}
                """.formatted(categoryId);
    }
}
