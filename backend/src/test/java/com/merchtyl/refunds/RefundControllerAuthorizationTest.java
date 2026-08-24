package com.merchtyl.refunds;

import com.merchtyl.common.PageResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.idempotency.IdempotencyState;
import com.merchtyl.sales.PaymentMethod;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RefundControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class RefundControllerAuthorizationTest {
    private static final UUID REFUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000970");
    private static final UUID RETURN_ID = UUID.fromString("00000000-0000-0000-0000-000000000971");
    private static final UUID SALE_ID = UUID.fromString("00000000-0000-0000-0000-000000000972");
    private static final String CREATE_JSON = """
            {
              "returnId": "00000000-0000-0000-0000-000000000971",
              "reason": "Customer refund",
              "payments": [
                {
                  "method": "CASH",
                  "amount": 5.75
                }
              ]
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RefundService refundService;

    @Test
    void createRequiresRefundCreatePermission() throws Exception {
        mockMvc.perform(post("/api/v1/refunds")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("REFUND_VIEW")))
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "refund-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isForbidden());

        verify(refundService, never()).createIdempotently(any(), any(), any());
    }

    @Test
    void creatorCanCreateRefundIdempotently() throws Exception {
        when(refundService.createIdempotently(any(), any(), any())).thenReturn(new IdempotencyResult(
                IdempotencyState.COMPLETED,
                201,
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {"id":"00000000-0000-0000-0000-000000000970","returnId":"00000000-0000-0000-0000-000000000971","originalSaleId":"00000000-0000-0000-0000-000000000972","totalAmount":5.75}
                        """,
                false));

        mockMvc.perform(post("/api/v1/refunds")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("REFUND_CREATE")))
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "refund-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.returnId").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.originalSaleId").value(SALE_ID.toString()));
    }

    @Test
    void readRequiresRefundViewPermission() throws Exception {
        mockMvc.perform(get("/api/v1/refunds/{id}", REFUND_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("REFUND_CREATE"))))
                .andExpect(status().isForbidden());

        verify(refundService, never()).get(any());
    }

    @Test
    void viewerCanReadAndSearchRefunds() throws Exception {
        when(refundService.get(REFUND_ID)).thenReturn(response());
        when(refundService.search(any())).thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/refunds/{id}", REFUND_ID)
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("REFUND_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REFUND_ID.toString()));

        mockMvc.perform(get("/api/v1/refunds")
                        .param("returnId", RETURN_ID.toString())
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("REFUND_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].returnId").value(RETURN_ID.toString()));
    }

    private static RefundResponse response() {
        Instant now = Instant.parse("2026-07-28T15:00:00Z");
        return new RefundResponse(
                REFUND_ID,
                RETURN_ID,
                SALE_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000973"),
                UUID.fromString("00000000-0000-0000-0000-000000000974"),
                UUID.fromString("00000000-0000-0000-0000-000000000975"),
                UUID.fromString("00000000-0000-0000-0000-000000000976"),
                LocalDate.parse("2026-07-28"),
                now,
                "USD",
                "Customer refund",
                new BigDecimal("5.00"),
                new BigDecimal("0.75"),
                new BigDecimal("5.75"),
                null,
                null,
                null,
                List.of(new RefundPaymentResponse(
                        UUID.fromString("00000000-0000-0000-0000-000000000977"),
                        null,
                        1,
                        PaymentMethod.CASH,
                        new BigDecimal("5.75"),
                        "USD",
                        null,
                        null,
                        0)),
                List.of(new RefundItemTaxResponse(
                        UUID.fromString("00000000-0000-0000-0000-000000000978"),
                        UUID.fromString("00000000-0000-0000-0000-000000000979"),
                        UUID.fromString("00000000-0000-0000-0000-000000000980"),
                        1,
                        null,
                        "TAX",
                        "Original sales tax",
                        new BigDecimal("5.00"),
                        new BigDecimal("0.75"),
                        "USD",
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
    @Import({RefundController.class, SecurityTestConfiguration.class})
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
