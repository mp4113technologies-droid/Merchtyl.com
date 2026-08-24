package com.merchtyl.product;

import com.merchtyl.security.AuthorizationService;
import com.merchtyl.common.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ProductControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("catalogue")
class ProductControllerAuthorizationTest {
    private static final String PRODUCT_JSON = """
            {
              "sku": "COFFEE-12OZ",
              "name": "House Coffee",
              "description": "Fresh brewed",
              "sellableType": "STANDARD_PRODUCT",
              "cost": 1.25,
              "price": 3.25,
              "active": true,
              "inventoryTrackingEnabled": true,
              "decimalQuantityAllowed": false,
              "storeIds": ["00000000-0000-0000-0000-000000000701"],
              "imageUrl": "https://cdn.example.test/coffee.png",
              "variants": [
                {
                  "sku": "COFFEE-LARGE",
                  "name": "Large",
                  "cost": 1.50,
                  "price": 4.00,
                  "active": true
                }
              ],
              "barcodes": [
                {
                  "barcode": "012345678905",
                  "variantSku": "COFFEE-LARGE",
                  "primaryBarcode": true,
                  "active": true
                }
              ]
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ProductService productService;

    @Test
    void cashierCannotCreateProductByCallingProtectedApiDirectly() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(user("cashier").authorities(
                                new SimpleGrantedAuthority("PRODUCT_VIEW"),
                                new SimpleGrantedAuthority("SALE_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isForbidden());

        verify(productService, never()).create(any(), any());
    }

    @Test
    void productManagerCanCreateProduct() throws Exception {
        UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000601");
        when(productService.create(any(), any())).thenReturn(new ProductResponse(
                productId,
                "COFFEE-12OZ",
                "House Coffee",
                "Fresh brewed",
                SellableType.STANDARD_PRODUCT,
                null,
                new BigDecimal("1.2500"),
                new BigDecimal("3.2500"),
                null,
                null,
                true,
                true,
                false,
                "https://cdn.example.test/coffee.png",
                null,
                List.of(),
                List.of(),
                Set.of(ProductCapability.TRACK_INVENTORY),
                Instant.parse("2026-07-21T12:00:00Z"),
                Instant.parse("2026-07-21T12:00:00Z"),
                0));

        mockMvc.perform(post("/api/v1/products")
                        .with(user("manager").authorities(
                                new SimpleGrantedAuthority(AuthorizationService.TENANT_SCOPE_AUTHORITY),
                                new SimpleGrantedAuthority("PRODUCT_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.sku").value("COFFEE-12OZ"));
    }

    @Test
    void productCreateRequiresTenantScopeAlongsidePermission() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("PRODUCT_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void productListRequiresProductViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE"))))
                .andExpect(status().isForbidden());

        verify(productService, never()).search(any(), any());
    }

    @Test
    void productViewerCanListProducts() throws Exception {
        when(productService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/products")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({ProductController.class, AuthorizationService.class, TestSecurityConfig.class})
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
