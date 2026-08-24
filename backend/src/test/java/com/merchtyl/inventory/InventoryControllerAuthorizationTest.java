package com.merchtyl.inventory;

import com.merchtyl.common.PageResponse;
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

@SpringBootTest(classes = InventoryControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class InventoryControllerAuthorizationTest {
    private static final UUID BALANCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000900");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final String CHANGE_JSON = """
            {
              "storeId": "00000000-0000-0000-0000-000000000901",
              "productId": "00000000-0000-0000-0000-000000000902",
              "transactionType": "PURCHASE",
              "quantityDelta": 5.0000,
              "referenceType": "purchase_order",
              "reason": "Receiving"
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    InventoryService inventoryService;

    @MockBean
    StockAdjustmentService stockAdjustmentService;

    @MockBean
    StockCountService stockCountService;

    @Test
    void inventoryViewerCannotRecordStockChange() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/transactions")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("INVENTORY_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHANGE_JSON))
                .andExpect(status().isForbidden());

        verify(inventoryService, never()).recordStockChange(any(), any());
    }

    @Test
    void inventoryManagerCanRecordStockChange() throws Exception {
        when(inventoryService.recordStockChange(any(), any())).thenReturn(transactionResponse());

        mockMvc.perform(post("/api/v1/inventory/transactions")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("INVENTORY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHANGE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.transactionType").value("PURCHASE"))
                .andExpect(jsonPath("$.quantityDelta").value(5.0000));
    }

    @Test
    void inventoryCurrentStockRequiresViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/balances/current")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_VIEW")))
                        .param("storeId", STORE_ID.toString())
                        .param("productId", PRODUCT_ID.toString()))
                .andExpect(status().isForbidden());

        verify(inventoryService, never()).currentStock(any(), any());
    }

    @Test
    void inventoryViewerCanReadCurrentStockAndHistory() throws Exception {
        when(inventoryService.currentStock(STORE_ID, PRODUCT_ID)).thenReturn(balanceResponse());
        when(inventoryService.searchTransactions(any())).thenReturn(new PageResponse<>(
                List.of(transactionResponse()),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/inventory/balances/current")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("INVENTORY_VIEW")))
                        .param("storeId", STORE_ID.toString())
                        .param("productId", PRODUCT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(5.0000));

        mockMvc.perform(get("/api/v1/inventory/transactions")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("INVENTORY_VIEW")))
                        .param("storeId", STORE_ID.toString())
                        .param("productId", PRODUCT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(TRANSACTION_ID.toString()));
    }

    @Test
    void inventoryViewerCannotCreateAdjustment() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("INVENTORY_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentJson()))
                .andExpect(status().isForbidden());

        verify(stockAdjustmentService, never()).create(any(), any());
    }

    @Test
    void inventoryManagerCanCreateAdjustment() throws Exception {
        when(stockAdjustmentService.create(any(), any())).thenReturn(adjustmentResponse());

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("INVENTORY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000904"))
                .andExpect(jsonPath("$.reason").value("Cycle count"))
                .andExpect(jsonPath("$.lines[0].adjustmentType").value("INCREASE"));
    }

    @Test
    void inventoryViewerCanListAdjustments() throws Exception {
        when(stockAdjustmentService.search(any())).thenReturn(new PageResponse<>(
                List.of(adjustmentResponse()),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/inventory/adjustments")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("INVENTORY_VIEW")))
                        .param("storeId", STORE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reason").value("Cycle count"));
    }

    @Test
    void inventoryViewerCannotCreateCount() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/counts")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("INVENTORY_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockCountJson()))
                .andExpect(status().isForbidden());

        verify(stockCountService, never()).create(any(), any());
    }

    @Test
    void inventoryManagerCanCreateReviewAndPostCount() throws Exception {
        when(stockCountService.create(any(), any())).thenReturn(stockCountResponse(StockCountStatus.DRAFT));
        when(stockCountService.review(any(), any(), any())).thenReturn(stockCountResponse(StockCountStatus.IN_REVIEW));
        when(stockCountService.postIdempotently(any(), any(), any(), any())).thenReturn(new IdempotencyResult(
                IdempotencyState.COMPLETED,
                200,
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {"id":"00000000-0000-0000-0000-000000000906","status":"POSTED"}
                        """,
                false));

        mockMvc.perform(post("/api/v1/inventory/counts")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("INVENTORY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockCountJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000906"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/inventory/counts/00000000-0000-0000-0000-000000000906/review")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("INVENTORY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewNotes\":\"Ready\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        mockMvc.perform(post("/api/v1/inventory/counts/00000000-0000-0000-0000-000000000906/post")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("INVENTORY_MANAGE")))
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "post-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postNotes\":\"Posted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));
    }

    @Test
    void inventoryViewerCannotListOwnerManagerStockCounts() throws Exception {
        when(stockCountService.search(any())).thenReturn(new PageResponse<>(
                List.of(stockCountResponse(StockCountStatus.DRAFT)),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/inventory/counts")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("INVENTORY_VIEW")))
                        .param("storeId", STORE_ID.toString()))
                .andExpect(status().isForbidden());
    }

    private static InventoryBalanceResponse balanceResponse() {
        return new InventoryBalanceResponse(
                BALANCE_ID,
                STORE_ID,
                PRODUCT_ID,
                new BigDecimal("5.0000"),
                Instant.parse("2026-07-23T12:00:00Z"),
                Instant.parse("2026-07-23T12:00:00Z"),
                Instant.parse("2026-07-23T12:00:00Z"),
                0L);
    }

    private static InventoryTransactionResponse transactionResponse() {
        return new InventoryTransactionResponse(
                TRANSACTION_ID,
                BALANCE_ID,
                STORE_ID,
                PRODUCT_ID,
                InventoryTransactionType.PURCHASE,
                new BigDecimal("5.0000"),
                new BigDecimal("5.0000"),
                "PURCHASE_ORDER",
                null,
                "Receiving",
                null,
                Instant.parse("2026-07-23T12:00:00Z"),
                Instant.parse("2026-07-23T12:00:00Z"),
                0);
    }

    private static String adjustmentJson() {
        return """
                {
                  "storeId": "00000000-0000-0000-0000-000000000901",
                  "reason": "Cycle count",
                  "notes": "Back room count",
                  "approvalNotes": "Approved by manager",
                  "lines": [
                    {
                      "productId": "00000000-0000-0000-0000-000000000902",
                      "adjustmentType": "INCREASE",
                      "quantity": 2.0000
                    }
                  ]
                }
                """;
    }

    private static String stockCountJson() {
        return """
                {
                  "storeId": "00000000-0000-0000-0000-000000000901",
                  "reference": "Cycle count A",
                  "notes": "Back room count",
                  "lines": [
                    {
                      "productId": "00000000-0000-0000-0000-000000000902"
                    }
                  ]
                }
                """;
    }

    private static StockAdjustmentResponse adjustmentResponse() {
        return new StockAdjustmentResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000904"),
                STORE_ID,
                "Cycle count",
                "Back room count",
                StockAdjustmentApprovalStatus.APPROVED,
                null,
                Instant.parse("2026-07-23T12:00:00Z"),
                "Approved by manager",
                List.of(new StockAdjustmentLineResponse(
                        UUID.fromString("00000000-0000-0000-0000-000000000905"),
                        PRODUCT_ID,
                        StockAdjustmentType.INCREASE,
                        new BigDecimal("2.0000"),
                        new BigDecimal("2.0000"),
                        new BigDecimal("7.0000"),
                        TRANSACTION_ID,
                        Instant.parse("2026-07-23T12:00:00Z"),
                        Instant.parse("2026-07-23T12:00:00Z"),
                        0)),
                Instant.parse("2026-07-23T12:00:00Z"),
                Instant.parse("2026-07-23T12:00:00Z"),
                0);
    }

    private static StockCountResponse stockCountResponse(StockCountStatus status) {
        return new StockCountResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000906"),
                STORE_ID,
                "Cycle count A",
                "Back room count",
                status,
                null,
                null,
                status == StockCountStatus.DRAFT ? null : Instant.parse("2026-07-23T12:00:00Z"),
                status == StockCountStatus.DRAFT ? null : "Ready",
                null,
                status == StockCountStatus.POSTED ? Instant.parse("2026-07-23T12:05:00Z") : null,
                status == StockCountStatus.POSTED ? "Posted" : null,
                List.of(new StockCountLineResponse(
                        UUID.fromString("00000000-0000-0000-0000-000000000907"),
                        PRODUCT_ID,
                        new BigDecimal("10.0000"),
                        status == StockCountStatus.DRAFT ? null : new BigDecimal("7.0000"),
                        status == StockCountStatus.DRAFT ? null : new BigDecimal("-3.0000"),
                        0L,
                        status == StockCountStatus.POSTED ? new BigDecimal("7.0000") : null,
                        status == StockCountStatus.POSTED ? TRANSACTION_ID : null,
                        Instant.parse("2026-07-23T12:00:00Z"),
                        Instant.parse("2026-07-23T12:00:00Z"),
                        0)),
                Instant.parse("2026-07-23T12:00:00Z"),
                Instant.parse("2026-07-23T12:00:00Z"),
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
    @Import({InventoryController.class, AuthorizationService.class, TestSecurityConfig.class})
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
