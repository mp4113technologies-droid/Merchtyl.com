package com.merchtyl.lottery;

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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LotteryPayoutControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class LotteryPayoutControllerAuthorizationTest {
    private static final UUID PAYOUT_ID = UUID.fromString("00000000-0000-0000-0000-000000000700");
    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000704");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000705");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000706");
    private static final UUID CASHIER_ID = UUID.fromString("00000000-0000-0000-0000-000000000707");
    private static final String CREATE_JSON = """
            {
              "operatorId": "00000000-0000-0000-0000-000000000701",
              "storeId": "00000000-0000-0000-0000-000000000703",
              "registerId": "00000000-0000-0000-0000-000000000704",
              "deviceId": "00000000-0000-0000-0000-000000000705",
              "registerSessionId": "00000000-0000-0000-0000-000000000706",
              "ticketNumber": "TICKET-1",
              "amount": 75.00,
              "payoutMethod": "CASH"
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LotteryPayoutService lotteryPayoutService;

    @Test
    void createRequiresPayoutRecordPermission() throws Exception {
        mockMvc.perform(post("/api/v1/lottery/payouts")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isForbidden());

        verify(lotteryPayoutService, never()).create(any(), any());
    }

    @Test
    void recorderCanCreateAndValidatePayout() throws Exception {
        when(lotteryPayoutService.create(any(), any())).thenReturn(response(LotteryPayoutStatus.DRAFT));
        when(lotteryPayoutService.validate(any(), any(), any())).thenReturn(response(LotteryPayoutStatus.VALIDATED));

        mockMvc.perform(post("/api/v1/lottery/payouts")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("LOTTERY_PAYOUT_RECORD")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/lottery/payouts/{id}/validate", PAYOUT_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("LOTTERY_PAYOUT_RECORD")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0,
                                  "ticketValidationState": "VERIFIED",
                                  "ageVerificationState": "VERIFIED",
                                  "identificationVerificationState": "VERIFIED",
                                  "validationReference": "VALID-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    void rejectRequiresPayoutApprovePermission() throws Exception {
        mockMvc.perform(post("/api/v1/lottery/payouts/{id}/reject", PAYOUT_ID)
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("LOTTERY_PAYOUT_RECORD")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"reason\":\"Ticket failed validation\"}"))
                .andExpect(status().isForbidden());

        verify(lotteryPayoutService, never()).reject(any(), any(), any());
    }

    @Test
    void viewerCanSearchPayouts() throws Exception {
        when(lotteryPayoutService.search(any())).thenReturn(new PageResponse<>(
                List.of(response(LotteryPayoutStatus.VALIDATED)),
                0,
                20,
                1,
                1,
                true,
                true));

        mockMvc.perform(get("/api/v1/lottery/payouts")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(PAYOUT_ID.toString()));
    }

    @Test
    void viewerCanReadAvailableCash() throws Exception {
        when(lotteryPayoutService.availableCash(SESSION_ID, OPERATOR_ID)).thenReturn(new LotteryPayoutCashAvailabilityResponse(
                SESSION_ID,
                POLICY_ID,
                new BigDecimal("300.00"),
                new BigDecimal("50.00"),
                new BigDecimal("75.00"),
                new BigDecimal("175.00"),
                "USD"));

        mockMvc.perform(get("/api/v1/lottery/payouts/available-cash")
                        .param("registerSessionId", SESSION_ID.toString())
                        .param("operatorId", OPERATOR_ID.toString())
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availablePayoutCash").value(175.00));
    }

    @Test
    void completeCashRequiresPayoutRecordPermission() throws Exception {
        mockMvc.perform(post("/api/v1/lottery/payouts/{id}/complete-cash", PAYOUT_ID)
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "pay-key")
                        .with(user("viewer").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW"))))
                .andExpect(status().isForbidden());

        verify(lotteryPayoutService, never()).completeCashIdempotently(any(), any(), any());
    }

    @Test
    void reverseRequiresPayoutApprovePermission() throws Exception {
        mockMvc.perform(post("/api/v1/lottery/payouts/{id}/reverse", PAYOUT_ID)
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "reverse-key")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("LOTTERY_PAYOUT_RECORD")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Manager correction\"}"))
                .andExpect(status().isForbidden());

        verify(lotteryPayoutService, never()).reverseIdempotently(any(), any(), any(), any());
    }

    @Test
    void managerCanReversePayoutWithIdempotencyKey() throws Exception {
        when(lotteryPayoutService.reverseIdempotently(any(), any(), any(), any())).thenReturn(new IdempotencyResult(
                IdempotencyState.COMPLETED,
                200,
                MediaType.APPLICATION_JSON_VALUE,
                "{\"id\":\"00000000-0000-0000-0000-000000000910\",\"reason\":\"Manager correction\"}",
                false));

        mockMvc.perform(post("/api/v1/lottery/payouts/{id}/reverse", PAYOUT_ID)
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "reverse-key")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_PAYOUT_APPROVE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Manager correction\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Manager correction"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("false"));
    }

    @Test
    void recorderCanCompleteCashWithIdempotencyKey() throws Exception {
        when(lotteryPayoutService.completeCashIdempotently(any(), any(), any())).thenReturn(new IdempotencyResult(
                IdempotencyState.COMPLETED,
                200,
                MediaType.APPLICATION_JSON_VALUE,
                "{\"status\":\"PAID\"}",
                true));

        mockMvc.perform(post("/api/v1/lottery/payouts/{id}/complete-cash", PAYOUT_ID)
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "pay-key")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("LOTTERY_PAYOUT_RECORD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true"));
    }

    private static LotteryPayoutResponse response(LotteryPayoutStatus status) {
        return new LotteryPayoutResponse(
                PAYOUT_ID,
                OPERATOR_ID,
                "STATE",
                "State Lottery",
                POLICY_ID,
                STORE_ID,
                "MAIN",
                "Main Store",
                REGISTER_ID,
                "FRONT",
                "Front Register",
                DEVICE_ID,
                "browser:test",
                "Front Browser",
                CASHIER_ID,
                "cashier@example.test",
                "Cashier One",
                SESSION_ID,
                "TICKET-1",
                status == LotteryPayoutStatus.DRAFT ? null : "VALID-1",
                new BigDecimal("75.00"),
                "USD",
                LotteryPayoutMethod.CASH,
                status,
                status == LotteryPayoutStatus.DRAFT ? LotteryVerificationState.PENDING : LotteryVerificationState.VERIFIED,
                status == LotteryPayoutStatus.DRAFT ? LotteryVerificationState.PENDING : LotteryVerificationState.VERIFIED,
                status == LotteryPayoutStatus.DRAFT ? LotteryVerificationState.PENDING : LotteryVerificationState.VERIFIED,
                new BigDecimal("100.00"),
                new BigDecimal("250.00"),
                new BigDecimal("500.00"),
                new BigDecimal("400.00"),
                true,
                true,
                true,
                false,
                LocalDate.parse("2026-07-28"),
                Instant.parse("2026-07-28T12:00:00Z"),
                status == LotteryPayoutStatus.DRAFT ? null : CASHIER_ID,
                status == LotteryPayoutStatus.DRAFT ? null : Instant.parse("2026-07-28T12:01:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Instant.parse("2026-07-28T12:00:00Z"),
                Instant.parse("2026-07-28T12:00:00Z"),
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
    @Import({LotteryPayoutController.class, AuthorizationService.class, TestSecurityConfig.class})
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
