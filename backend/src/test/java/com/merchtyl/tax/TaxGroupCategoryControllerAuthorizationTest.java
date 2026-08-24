package com.merchtyl.tax;

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

@SpringBootTest(classes = TaxGroupCategoryControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class TaxGroupCategoryControllerAuthorizationTest {
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000001201");
    private static final UUID GROUP_COMPONENT_ID = UUID.fromString("00000000-0000-0000-0000-000000001202");
    private static final UUID COMPONENT_ID = UUID.fromString("00000000-0000-0000-0000-000000001203");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000001204");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000001205");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000001206");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TaxGroupService taxGroupService;
    @MockBean
    TaxGroupComponentService taxGroupComponentService;
    @MockBean
    TaxCategoryService taxCategoryService;
    @MockBean
    ProductTaxCategoryAssignmentService assignmentService;

    @Test
    void taxViewerCannotCreateCategory() throws Exception {
        mockMvc.perform(post("/api/v1/tax/categories")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson()))
                .andExpect(status().isForbidden());

        verify(taxCategoryService, never()).create(any(), any());
    }

    @Test
    void taxManagerCanCreateProductAssignment() throws Exception {
        when(assignmentService.create(any(), any())).thenReturn(assignmentResponse());

        mockMvc.perform(post("/api/v1/tax/product-category-assignments")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("TAX_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "00000000-0000-0000-0000-000000001205",
                                  "taxCategoryId": "00000000-0000-0000-0000-000000001204",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.taxCategoryId").value(CATEGORY_ID.toString()));
    }

    @Test
    void taxViewerCanListGroupsComponentsCategoriesAndAssignments() throws Exception {
        when(taxGroupService.search(any())).thenReturn(new PageResponse<>(List.of(groupResponse()), 0, 20, 1, 1, true, true));
        when(taxGroupComponentService.search(any())).thenReturn(new PageResponse<>(List.of(groupComponentResponse()), 0, 20, 1, 1, true, true));
        when(taxCategoryService.search(any())).thenReturn(new PageResponse<>(List.of(categoryResponse()), 0, 20, 1, 1, true, true));
        when(assignmentService.search(any())).thenReturn(new PageResponse<>(List.of(assignmentResponse()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/tax/groups").with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("CA-HST"));
        mockMvc.perform(get("/api/v1/tax/group-components").with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tax/categories").with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].treatment").value("STANDARD"));
        mockMvc.perform(get("/api/v1/tax/product-category-assignments").with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW"))))
                .andExpect(status().isOk());
    }

    @Test
    void groupStatusPatchRequiresManagePermission() throws Exception {
        mockMvc.perform(patch("/api/v1/tax/groups/{id}/status", GROUP_ID)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("TAX_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false,
                                  "version": 0
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(taxGroupService, never()).updateStatus(any(), any(), any());
    }

    private static String categoryJson() {
        return """
                {
                  "taxGroupId": "00000000-0000-0000-0000-000000001201",
                  "code": "STANDARD",
                  "name": "Standard taxable",
                  "treatment": "STANDARD",
                  "description": "Standard taxable products",
                  "active": true
                }
                """;
    }

    private static TaxGroupResponse groupResponse() {
        return new TaxGroupResponse(GROUP_ID, "CA-HST", "CA HST", null, true, now(), now(), 0);
    }

    private static TaxGroupComponentResponse groupComponentResponse() {
        return new TaxGroupComponentResponse(GROUP_COMPONENT_ID, GROUP_ID, COMPONENT_ID, 0, true, now(), now(), 0);
    }

    private static TaxCategoryResponse categoryResponse() {
        return new TaxCategoryResponse(CATEGORY_ID, GROUP_ID, "STANDARD", "Standard taxable", TaxTreatment.STANDARD, null, true, now(), now(), 0);
    }

    private static ProductTaxCategoryAssignmentResponse assignmentResponse() {
        return new ProductTaxCategoryAssignmentResponse(ASSIGNMENT_ID, PRODUCT_ID, CATEGORY_ID, true, now(), now(), 0);
    }

    private static Instant now() {
        return Instant.parse("2026-07-22T12:00:00Z");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({TaxGroupController.class, TaxGroupComponentController.class, TaxCategoryController.class, ProductTaxCategoryAssignmentController.class, AuthorizationService.class, TestSecurityConfig.class})
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
