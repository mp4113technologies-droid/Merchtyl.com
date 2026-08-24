package com.merchtyl.supplier;

import com.merchtyl.common.PageResponse;
import com.merchtyl.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SupplierControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class SupplierControllerAuthorizationTest {
    private static final UUID SUPPLIER_ID = UUID.fromString("00000000-0000-0000-0000-000000001101");
    private static final UUID PRODUCT_SUPPLIER_ID = UUID.fromString("00000000-0000-0000-0000-000000001102");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000001103");
    private static final String SUPPLIER_JSON = """
            {
              "code": "ACME",
              "name": "ACME Foods",
              "contactName": "Jane Buyer",
              "email": "jane@example.test",
              "address": "10 Warehouse Road",
              "active": true
            }
            """;
    private static final String PRODUCT_SUPPLIER_JSON = """
            {
              "productId": "00000000-0000-0000-0000-000000001103",
              "supplierId": "00000000-0000-0000-0000-000000001101",
              "supplierSku": "ACME-SKU",
              "preferred": true,
              "active": true
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SupplierService supplierService;

    @MockBean
    ProductSupplierService productSupplierService;

    @Test
    void productViewerCannotCreateSupplier() throws Exception {
        mockMvc.perform(post("/api/v1/suppliers")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUPPLIER_JSON))
                .andExpect(status().isForbidden());

        verify(supplierService, never()).create(any(), any());
    }

    @Test
    void productManagerCanCreateSupplier() throws Exception {
        when(supplierService.create(any(), any())).thenReturn(supplierResponse());

        mockMvc.perform(post("/api/v1/suppliers")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("PRODUCT_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUPPLIER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SUPPLIER_ID.toString()))
                .andExpect(jsonPath("$.code").value("ACME"));
    }

    @Test
    void productViewerCanListSuppliersAndProductSuppliers() throws Exception {
        when(supplierService.search(any())).thenReturn(supplierPage());
        when(productSupplierService.search(any())).thenReturn(productSupplierPage());

        mockMvc.perform(get("/api/v1/suppliers")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("ACME"));
        mockMvc.perform(get("/api/v1/product-suppliers")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].supplierSku").value("ACME-SKU"));
    }

    @Test
    void supplierStatusPatchRequiresManagePermission() throws Exception {
        mockMvc.perform(patch("/api/v1/suppliers/{id}/status", SUPPLIER_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(supplierService, never()).updateStatus(any(), any(), any());
    }

    @Test
    void productSupplierCreateRequiresManagePermission() throws Exception {
        mockMvc.perform(post("/api/v1/product-suppliers")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_SUPPLIER_JSON))
                .andExpect(status().isForbidden());

        verify(productSupplierService, never()).create(any(), any());
    }

    private static PageResponse<SupplierResponse> supplierPage() {
        return new PageResponse<>(List.of(supplierResponse()), 0, 20, 1, 1, true, true);
    }

    private static PageResponse<ProductSupplierResponse> productSupplierPage() {
        return new PageResponse<>(List.of(productSupplierResponse()), 0, 20, 1, 1, true, true);
    }

    private static SupplierResponse supplierResponse() {
        return new SupplierResponse(
                SUPPLIER_ID,
                "ACME",
                "ACME Foods",
                "Jane Buyer",
                "555-0100",
                "jane@example.test",
                "10 Warehouse Road",
                "Net 30",
                true,
                Instant.parse("2026-07-22T12:00:00Z"),
                Instant.parse("2026-07-22T12:00:00Z"),
                0);
    }

    private static ProductSupplierResponse productSupplierResponse() {
        return new ProductSupplierResponse(
                PRODUCT_SUPPLIER_ID,
                PRODUCT_ID,
                SUPPLIER_ID,
                "ACME-SKU",
                true,
                true,
                Instant.parse("2026-07-22T12:00:00Z"),
                Instant.parse("2026-07-22T12:00:00Z"),
                0);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({SupplierController.class, ProductSupplierController.class, AuthorizationService.class, TestSecurityConfig.class})
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
