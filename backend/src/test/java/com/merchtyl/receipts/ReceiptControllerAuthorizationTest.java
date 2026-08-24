package com.merchtyl.receipts;

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

@SpringBootTest(classes = ReceiptControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class ReceiptControllerAuthorizationTest {
    private static final UUID RECEIPT_ID = UUID.fromString("00000000-0000-0000-0000-000000000940");
    private static final UUID SALE_ID = UUID.fromString("00000000-0000-0000-0000-000000000900");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID CASHIER_ID = UUID.fromString("00000000-0000-0000-0000-000000000904");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ReceiptService receiptService;

    @Test
    void receiptRetrievalRequiresSaleViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/sales/{saleId}/receipt", SALE_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("SALE_CREATE"))))
                .andExpect(status().isForbidden());

        verify(receiptService, never()).getForSale(any(), any());
    }

    @Test
    void saleViewerCanRetrieveAndReprintReceipt() throws Exception {
        when(receiptService.getForSale(any(), any())).thenReturn(response(0));
        when(receiptService.reprintForSale(any(), any())).thenReturn(response(1));

        mockMvc.perform(get("/api/v1/sales/{saleId}/receipt", SALE_ID)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("SALE_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.brandName").value("Merchtyl"))
                .andExpect(jsonPath("$.document.items[0].productName").value("Coffee"));

        mockMvc.perform(post("/api/v1/sales/{saleId}/receipt/reprint", SALE_ID)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("SALE_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reprintCount").value(1));
    }

    private static ReceiptResponse response(int reprintCount) {
        Instant now = Instant.parse("2026-07-27T12:30:00Z");
        ReceiptDocumentDto document = new ReceiptDocumentDto(
                "Merchtyl",
                "Point of sale receipt",
                new ReceiptStoreDto(STORE_ID, "MAIN", "Main Store", null, "100 Market Street", null, null),
                new ReceiptRegisterDto(REGISTER_ID, "FRONT-1", "Front Register"),
                new ReceiptCashierDto(CASHIER_ID, "Cashier One", "cashier@example.test"),
                "RCT-2026-07-27-00000000",
                SALE_ID,
                SALE_ID.toString(),
                LocalDate.parse("2026-07-27"),
                now,
                "USD",
                List.of(new ReceiptItemDto(
                        UUID.fromString("00000000-0000-0000-0000-000000000905"),
                        UUID.fromString("00000000-0000-0000-0000-000000000906"),
                        1,
                        "COFFEE",
                        "Coffee",
                        new BigDecimal("2.0000"),
                        new BigDecimal("5.0000"),
                        new BigDecimal("2.0000"),
                        new BigDecimal("5.0000"),
                        "TRACK_INVENTORY",
                        BigDecimal.ZERO.setScale(2),
                        new BigDecimal("10.00"),
                        new BigDecimal("1.50"),
                        new BigDecimal("11.50"))),
                new BigDecimal("10.00"),
                BigDecimal.ZERO.setScale(2),
                List.of(new ReceiptTaxSummaryDto("TAX", "Sales tax", new BigDecimal("10.00"), new BigDecimal("1.50"))),
                new BigDecimal("1.50"),
                new BigDecimal("11.50"),
                List.of(new ReceiptPaymentDto(
                        UUID.fromString("00000000-0000-0000-0000-000000000907"),
                        com.merchtyl.sales.PaymentMethod.CASH,
                        new BigDecimal("11.50"),
                        new BigDecimal("20.00"),
                        new BigDecimal("8.50"),
                        null,
                        now)),
                new BigDecimal("20.00"),
                new BigDecimal("8.50"));
        return new ReceiptResponse(RECEIPT_ID, SALE_ID, document.receiptNumber(), now, reprintCount, reprintCount > 0 ? now : null, document, now, now, 0);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({ReceiptController.class, AuthorizationService.class, TestSecurityConfig.class})
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
