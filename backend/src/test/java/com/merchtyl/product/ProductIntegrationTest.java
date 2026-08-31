package com.merchtyl.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditRecordRepository;
import com.merchtyl.catalogue.Brand;
import com.merchtyl.catalogue.BrandRepository;
import com.merchtyl.catalogue.Category;
import com.merchtyl.catalogue.CategoryRepository;
import com.merchtyl.catalogue.UnitOfMeasure;
import com.merchtyl.catalogue.UnitOfMeasureRepository;
import com.merchtyl.security.RefreshTokenRepository;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.UserRoleRepository;
import com.merchtyl.tax.TaxCategory;
import com.merchtyl.tax.TaxCategoryRepository;
import com.merchtyl.tax.TaxTreatment;
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
class ProductIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ProductVariantRepository productVariantRepository;

    @Autowired
    ProductBarcodeRepository productBarcodeRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    BrandRepository brandRepository;

    @Autowired
    UnitOfMeasureRepository unitOfMeasureRepository;

    @Autowired
    TaxCategoryRepository taxCategoryRepository;

    @Autowired
    AuditRecordRepository auditRecordRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    UserRoleRepository userRoleRepository;

    @Autowired
    UserRepository userRepository;

    private Category category;
    private Brand brand;
    private UnitOfMeasure unit;
    private TaxCategory taxCategory;

    @BeforeEach
    void resetData() {
        auditRecordRepository.deleteAll();
        productBarcodeRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        taxCategoryRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();
        unitOfMeasureRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRoleRepository.deleteAll();
        userRepository.deleteAll();
        taxCategory = taxCategoryRepository.saveAndFlush(new TaxCategory(null, "STANDARD", "Standard Tax", TaxTreatment.STANDARD, null, true));

        category = categoryRepository.saveAndFlush(new Category("BEV", "Beverages", null, true));
        brand = brandRepository.saveAndFlush(new Brand("HOUSE", "House Brand", null, true));
        unit = unitOfMeasureRepository.saveAndFlush(new UnitOfMeasure("EA", "Each", null, true));
    }

    @Test
    void ownerCanCreateAndFetchCoreProductWithVariantsBarcodesAndAudit() throws Exception {
        String token = registerAndGetToken("owner@products.test", "Owner");

        String body = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("coffee-12oz", "House Coffee", "012345678905", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("COFFEE-12OZ"))
                .andExpect(jsonPath("$.sellableType").value("STANDARD_PRODUCT"))
                .andExpect(jsonPath("$.categoryId").value(category.getId().toString()))
                .andExpect(jsonPath("$.brandId").value(brand.getId().toString()))
                .andExpect(jsonPath("$.unitOfMeasureId").value(unit.getId().toString()))
                .andExpect(jsonPath("$.inventoryTrackingEnabled").value(true))
                .andExpect(jsonPath("$.decimalQuantityAllowed").value(false))
                .andExpect(jsonPath("$.variants[0].sku").value("COFFEE-LARGE"))
                .andExpect(jsonPath("$.barcodes[0].barcode").value("012345678905"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode product = objectMapper.readTree(body);
        String productId = product.get("id").asText();

        mockMvc.perform(get("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.sku").value("COFFEE-12OZ"));

        var auditRecords = auditRecordRepository.findAll();
        assertThat(auditRecords).hasSize(1);
        assertThat(auditRecords.getFirst().getAction()).isEqualTo(AuditAction.PRODUCT_CREATED.name());
        assertThat(auditRecords.getFirst().getEntityType()).isEqualTo("PRODUCT");
        assertThat(auditRecords.getFirst().getEntityId().toString()).isEqualTo(productId);
        assertThat(auditRecords.getFirst().getActorUserId()).isNotNull();
        assertThat(auditRecords.getFirst().getAfterSnapshot()).contains("\"sku\":\"COFFEE-12OZ\"");
    }

    @Test
    void listSearchesByNameSkuAndBarcodeAndBarcodeLookupReturnsProduct() throws Exception {
        String token = registerAndGetToken("owner@products.test", "Owner");
        createProduct(token, "coffee-12oz", "House Coffee", "012345678905", true);
        createProduct(token, "tea-12oz", "Iced Tea", "987654321098", true);

        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .param("name", "coffee")
                        .param("sku", "coffee-12oz")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("COFFEE-12OZ"));

        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .param("barcode", "987654321098"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("TEA-12OZ"));

        mockMvc.perform(get("/api/v1/products/barcodes/{barcode}", "012345678905")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("COFFEE-12OZ"));
    }

    @Test
    void updateAndStatusPatchRequireCurrentVersion() throws Exception {
        String token = registerAndGetToken("owner@products.test", "Owner");
        JsonNode created = createProduct(token, "coffee-12oz", "House Coffee", "012345678905", true);
        String productId = created.get("id").asText();

        String updatedBody = mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("coffee-renamed", "Renamed Coffee", "012345678906", created.get("version").asLong(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("COFFEE-RENAMED"))
                .andExpect(jsonPath("$.name").value("Renamed Coffee"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode updated = objectMapper.readTree(updatedBody);

        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("coffee-stale", "Stale Coffee", "012345678907", 0, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));

        mockMvc.perform(patch("/api/v1/products/{id}/status", productId)
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
                        AuditAction.PRODUCT_CREATED.name(),
                        AuditAction.PRODUCT_UPDATED.name(),
                        AuditAction.PRODUCT_STATUS_CHANGED.name());
    }

    @Test
    void updateKeepsExistingBarcodeWithoutDatabaseConstraintFailure() throws Exception {
        String token = registerAndGetToken("owner-barcode-edit@products.test", "Owner");
        JsonNode created = createProduct(token, "cola", "Test Cola", "123456789012", true);

        mockMvc.perform(put("/api/v1/products/{id}", created.get("id").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("cola", "Test Cola", "123456789012", created.get("version").asLong(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcodes[0].barcode").value("123456789012"));
    }

    @Test
    void duplicateSkuAndBarcodeAreRejected() throws Exception {
        String token = registerAndGetToken("owner@products.test", "Owner");
        createProduct(token, "coffee-12oz", "House Coffee", "012345678905", true);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("COFFEE-12OZ", "Duplicate Coffee", "012345678906", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("SKU already exists"));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("tea-12oz", "Iced Tea", "012345678905", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Barcode is already assigned to another product or variant."));
    }

    @Test
    void cashierCanViewButCannotManageProducts() throws Exception {
        String ownerToken = registerAndGetToken("owner@products.test", "Owner");
        createProduct(ownerToken, "coffee-12oz", "House Coffee", "012345678905", true);
        String cashierToken = registerAndGetToken("cashier@products.test", "Cashier");

        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("tea-12oz", "Iced Tea", "987654321098", true)))
                .andExpect(status().isForbidden());
    }

    private JsonNode createProduct(String token, String sku, String name, String barcode, boolean active) throws Exception {
        String body = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, name, barcode, active)))
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

    private String productJson(String sku, String name, String barcode, boolean active) {
        return """
                {
                  "sku": "%s",
                  "name": "%s",
                  "description": "Core product",
                  "sellableType": "STANDARD_PRODUCT",
                  "unitOfMeasureId": "%s",
                  "cost": 1.2500,
                  "price": 3.2500,
                  "categoryId": "%s",
                  "brandId": "%s",
                  "active": %s,
                  "inventoryTrackingEnabled": true,
                  "decimalQuantityAllowed": false,
                  "imageUrl": "https://cdn.example.test/%s.png",
                  "taxCategoryId": "%s",
                  "variants": [
                    {
                      "sku": "%s-large",
                      "name": "Large",
                      "description": "Large size",
                      "cost": 1.5000,
                      "price": 4.0000,
                      "active": true
                    }
                  ],
                  "barcodes": [
                    {
                      "barcode": "%s",
                      "variantSku": "%s-large",
                      "primaryBarcode": true,
                      "active": true
                    }
                  ]
                }
                """.formatted(
                sku,
                name,
                unit.getId(),
                category.getId(),
                brand.getId(),
                active,
                sku,
                taxCategory.getId(),
                sku,
                barcode,
                sku);
    }

    private String updateJson(String sku, String name, String barcode, long version, boolean active) {
        return productJson(sku, name, barcode, active)
                .replaceFirst("\\n                \\}", ",\\n                  \"version\": %d\\n                }".formatted(version));
    }
}
