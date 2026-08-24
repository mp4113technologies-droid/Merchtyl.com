package com.merchtyl.returns;

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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

@SpringBootTest(classes = ReturnControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class ReturnControllerAuthorizationTest {
    private static final UUID RETURN_ID = UUID.fromString("00000000-0000-0000-0000-000000000950");
    private static final UUID SALE_ID = UUID.fromString("00000000-0000-0000-0000-000000000951");
    private static final UUID SALE_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000952");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000953");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000954");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000955");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000956");
    private static final String CREATE_JSON = """
            {
              "originalSaleId": "00000000-0000-0000-0000-000000000951",
              "reason": "Customer changed mind",
              "items": [
                {
                  "originalSaleItemId": "00000000-0000-0000-0000-000000000952",
                  "quantity": 1.0000
                }
              ]
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ReturnService returnService;

    @Test
    void createRequiresReturnCreatePermission() throws Exception {
        mockMvc.perform(post("/api/v1/returns")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("RETURN_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isForbidden());

        verify(returnService, never()).create(any(), any());
    }

    @Test
    void creatorCanCreateReturn() throws Exception {
        when(returnService.create(any(), any())).thenReturn(response(false));

        mockMvc.perform(post("/api/v1/returns")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("RETURN_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalSaleId").value(SALE_ID.toString()))
                .andExpect(jsonPath("$.items[0].originalSaleItemId").value(SALE_ITEM_ID.toString()));

        mockMvc.perform(post("/api/v1/sales/{saleId}/returns", SALE_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("RETURN_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Customer changed mind",
                                  "items": [
                                    {
                                      "originalSaleItemId": "00000000-0000-0000-0000-000000000952",
                                      "quantity": 1.0000
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalSaleId").value(SALE_ID.toString()));
    }

    @Test
    void readRequiresReturnViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/returns/{id}", RETURN_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("RETURN_CREATE"))))
                .andExpect(status().isForbidden());

        verify(returnService, never()).get(any());
    }

    @Test
    void viewerCanReadAndSearchReturns() throws Exception {
        when(returnService.get(RETURN_ID)).thenReturn(response(true));
        when(returnService.search(any(), any(), any(Integer.class), any(Integer.class))).thenReturn(new com.merchtyl.common.PageResponse<>(
                List.of(response(false)), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/returns/{id}", RETURN_ID)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("RETURN_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullReturn").value(true));

        mockMvc.perform(get("/api/v1/returns")
                        .param("originalSaleId", SALE_ID.toString())
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("RETURN_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(RETURN_ID.toString()));

        mockMvc.perform(get("/api/v1/sales/{saleId}/returns", SALE_ID)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("RETURN_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(RETURN_ID.toString()));
    }

    private static ReturnResponse response(boolean fullReturn) {
        Instant now = Instant.parse("2026-07-28T14:00:00Z");
        return new ReturnResponse(
                RETURN_ID,
                SALE_ID,
                STORE_ID,
                REGISTER_ID,
                SESSION_ID,
                USER_ID,
                LocalDate.parse("2026-07-28"),
                now,
                "USD",
                "Customer changed mind",
                new BigDecimal("1.0000"),
                new BigDecimal("5.00"),
                new BigDecimal("0.75"),
                new BigDecimal("5.75"),
                fullReturn,
                List.of(new ReturnItemResponse(
                        UUID.fromString("00000000-0000-0000-0000-000000000957"),
                        SALE_ITEM_ID,
                        UUID.fromString("00000000-0000-0000-0000-000000000958"),
                        1,
                        "SKU-1",
                        "Coffee",
                        new BigDecimal("1.0000"),
                        "Customer changed mind",
                        new BigDecimal("2.0000"),
                        new BigDecimal("5.0000"),
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("10.00"),
                        new BigDecimal("1.50"),
                        new BigDecimal("11.50"),
                        new BigDecimal("2.0000"),
                        new BigDecimal("5.0000"),
                        "ALLOW_RETURN",
                        null,
                        new BigDecimal("5.00"),
                        new BigDecimal("0.75"),
                        new BigDecimal("5.75"),
                        0)),
                now,
                now,
                0);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({ReturnController.class, SaleReturnController.class, SecurityTestConfiguration.class})
    static class TestApplication {
    }

    @TestConfiguration
    static class SecurityTestConfiguration {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(registry -> registry.anyRequest().authenticated())
                    .build();
        }

        @Bean
        AuthorizationService authorizationService() {
            return new AuthorizationService();
        }
    }
}
