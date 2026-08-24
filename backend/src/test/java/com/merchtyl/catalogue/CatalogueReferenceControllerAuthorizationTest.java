package com.merchtyl.catalogue;

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

@SpringBootTest(classes = CatalogueReferenceControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class CatalogueReferenceControllerAuthorizationTest {
    private static final UUID REFERENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final String REFERENCE_JSON = """
            {
              "code": "GROCERY",
              "name": "Grocery",
              "description": "General grocery items",
              "active": true
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    CategoryService categoryService;

    @MockBean
    BrandService brandService;

    @MockBean
    UnitOfMeasureService unitOfMeasureService;

    @Test
    void categoryViewerCannotCreateCategory() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFERENCE_JSON))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).create(any(), any());
    }

    @Test
    void productManagerCanCreateCategory() throws Exception {
        when(categoryService.create(any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/categories")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("PRODUCT_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REFERENCE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(REFERENCE_ID.toString()))
                .andExpect(jsonPath("$.code").value("GROCERY"));
    }

    @Test
    void productViewerCanListAllReferenceTypes() throws Exception {
        when(categoryService.search(any())).thenReturn(page());
        when(brandService.search(any())).thenReturn(page());
        when(unitOfMeasureService.search(any())).thenReturn(page());

        mockMvc.perform(get("/api/v1/categories")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("GROCERY"));
        mockMvc.perform(get("/api/v1/brands")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/units")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW"))))
                .andExpect(status().isOk());
    }

    @Test
    void statusPatchRequiresManagePermission() throws Exception {
        mockMvc.perform(patch("/api/v1/categories/{id}/status", REFERENCE_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("PRODUCT_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).updateStatus(any(), any(), any());
    }

    private static PageResponse<CatalogueReferenceResponse> page() {
        return new PageResponse<>(List.of(response()), 0, 20, 1, 1, true, true);
    }

    private static CatalogueReferenceResponse response() {
        return new CatalogueReferenceResponse(
                REFERENCE_ID,
                "GROCERY",
                "Grocery",
                "General grocery items",
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
    @Import({CategoryController.class, BrandController.class, UnitOfMeasureController.class, AuthorizationService.class, TestSecurityConfig.class})
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
