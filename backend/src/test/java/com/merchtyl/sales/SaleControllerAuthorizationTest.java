package com.merchtyl.sales;

import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.idempotency.IdempotencyState;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SaleControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class SaleControllerAuthorizationTest {
    private static final UUID SALE_ID = UUID.fromString("00000000-0000-0000-0000-000000000900");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000904");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000905");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000906");
    private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000907");
    private static final String DRAFT_JSON = """
            {
              "registerSessionId": "00000000-0000-0000-0000-000000000903",
              "saleChannel": "POS"
            }
            """;
    private static final String ITEM_JSON = """
            {
              "productId": "00000000-0000-0000-0000-000000000906",
              "quantity": 2.0000
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SaleService saleService;

    @Test
    void createDraftRequiresSaleCreatePermission() throws Exception {
        mockMvc.perform(post("/api/v1/sales/drafts")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("SALE_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_JSON))
                .andExpect(status().isForbidden());

        verify(saleService, never()).createDraft(any(), any());
    }

    @Test
    void creatorCanCreateDraftAndMutateCart() throws Exception {
        when(saleService.createDraft(any(), any())).thenReturn(response(SaleStatus.DRAFT));
        when(saleService.addItem(any(), any(), any())).thenReturn(response(SaleStatus.DRAFT));
        when(saleService.updateQuantity(any(), any(), any(), any())).thenReturn(response(SaleStatus.DRAFT));
        when(saleService.removeItem(any(), any(), any())).thenReturn(response(SaleStatus.DRAFT));
        when(saleService.hold(any(), any())).thenReturn(response(SaleStatus.HELD));
        when(saleService.resume(any(), any())).thenReturn(response(SaleStatus.DRAFT));
        when(saleService.cancel(any(), any())).thenReturn(response(SaleStatus.CANCELLED));
        when(saleService.recalculate(any(), any())).thenReturn(response(SaleStatus.DRAFT));
        when(saleService.recordPayment(any(), any(), any())).thenReturn(response(SaleStatus.DRAFT));
        when(saleService.completeIdempotently(any(), any(), any())).thenReturn(new IdempotencyResult(
                IdempotencyState.COMPLETED,
                200,
                MediaType.APPLICATION_JSON_VALUE,
                "{\"status\":\"COMPLETED\"}",
                false));

        mockMvc.perform(post("/api/v1/sales/drafts")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DRAFT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/sales/{id}/items", SALE_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ITEM_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_ID.toString()));

        mockMvc.perform(patch("/api/v1/sales/{id}/items/{itemId}/quantity", SALE_ID, ITEM_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3.0000}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/sales/{id}/items/{itemId}", SALE_ID, ITEM_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sales/{id}/hold", SALE_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"));

        mockMvc.perform(post("/api/v1/sales/{id}/resume", SALE_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/sales/{id}/cancel", SALE_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/sales/{id}/recalculate", SALE_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sales/{id}/payments", SALE_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CASH\",\"amount\":11.50,\"cashTendered\":20.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments[0].method").value("CASH"));

        mockMvc.perform(post("/api/v1/sales/{id}/complete", SALE_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE")))
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "complete-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getRequiresSaleViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/sales/{id}", SALE_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE"))))
                .andExpect(status().isForbidden());

        verify(saleService, never()).get(any());
    }

    @Test
    void saleViewerCanReadSale() throws Exception {
        when(saleService.get(SALE_ID)).thenReturn(response(SaleStatus.DRAFT));

        mockMvc.perform(get("/api/v1/sales/{id}", SALE_ID)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("SALE_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SALE_ID.toString()));
    }

    private static SaleResponse response(SaleStatus status) {
        Instant now = Instant.parse("2026-07-27T12:00:00Z");
        return new SaleResponse(
                SALE_ID,
                STORE_ID,
                REGISTER_ID,
                SESSION_ID,
                USER_ID,
                null,
                status,
                LocalDate.parse("2026-07-27"),
                "POS",
                "USD",
                false,
                new BigDecimal("10.00"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("1.50"),
                new BigDecimal("11.50"),
                status == SaleStatus.HELD ? now : null,
                status == SaleStatus.CANCELLED ? now : null,
                status == SaleStatus.COMPLETED ? USER_ID : null,
                status == SaleStatus.COMPLETED ? now : null,
                List.of(new SaleItemResponse(
                        ITEM_ID,
                        PRODUCT_ID,
                        1,
                        "SKU-1",
                        "Coffee",
                        new BigDecimal("2.0000"),
                        new BigDecimal("5.00"),
                        BigDecimal.ZERO.setScale(2),
                        status == SaleStatus.COMPLETED ? new BigDecimal("1.0000") : null,
                        status == SaleStatus.COMPLETED ? new BigDecimal("5.0000") : null,
                        status == SaleStatus.COMPLETED ? "TRACK_INVENTORY" : null,
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("10.00"),
                        new BigDecimal("1.50"),
                        new BigDecimal("11.50"),
                        0)),
                List.of(new PaymentResponse(
                        PAYMENT_ID,
                        PaymentMethod.CASH,
                        new BigDecimal("11.50"),
                        "USD",
                        new BigDecimal("20.00"),
                        new BigDecimal("8.50"),
                        null,
                        null,
                        USER_ID,
                        now,
                        now,
                        0)),
                new BigDecimal("11.50"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("8.50"),
                true,
                now,
                now,
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
    @Import({SaleController.class, AuthorizationService.class, TestSecurityConfig.class})
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
