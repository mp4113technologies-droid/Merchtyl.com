package com.merchtyl.lottery;

import com.merchtyl.common.PageResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.idempotency.IdempotencyState;
import com.merchtyl.security.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

@SpringBootTest(classes = LotterySaleControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class LotterySaleControllerAuthorizationTest {
    private static final String SALE_JSON = """
            {
              "operatorId": "00000000-0000-0000-0000-000000000801",
              "operatorReference": "TERM-14",
              "ticketReference": "TICKET-99",
              "gameType": "DRAW_TICKET",
              "amount": 25.00,
              "paymentMethod": "CASH",
              "storeId": "00000000-0000-0000-0000-000000000802",
              "registerId": "00000000-0000-0000-0000-000000000803",
              "deviceId": "00000000-0000-0000-0000-000000000804",
              "registerSessionId": "00000000-0000-0000-0000-000000000805"
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockBean
    LotterySaleService lotterySaleService;

    @Test
    void recordRequiresLotterySaleRecordPermission() throws Exception {
        mockMvc.perform(post("/api/v1/lottery/sales")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW")))
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "lottery-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SALE_JSON))
                .andExpect(status().isForbidden());

        verify(lotterySaleService, never()).recordIdempotently(any(), any(), any());
    }

    @Test
    void cashierCanRecordLotterySaleWithIdempotencyKey() throws Exception {
        when(lotterySaleService.recordIdempotently(any(), any(), any())).thenReturn(new IdempotencyResult(
                IdempotencyState.COMPLETED,
                201,
                MediaType.APPLICATION_JSON_VALUE,
                "{\"id\":\"00000000-0000-0000-0000-000000000900\",\"status\":\"RECORDED\"}",
                false));

        mockMvc.perform(post("/api/v1/lottery/sales")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("LOTTERY_SALE_RECORD")))
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "lottery-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SALE_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("RECORDED"));
    }

    @Test
    void cancelRequiresLotterySaleCancelPermission() throws Exception {
        mockMvc.perform(post("/api/v1/lottery/sales/{id}/cancel", "00000000-0000-0000-0000-000000000900")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("LOTTERY_SALE_RECORD")))
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "cancel-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer request\"}"))
                .andExpect(status().isForbidden());

        verify(lotterySaleService, never()).cancelIdempotently(any(), any(), any(), any());
    }

    @Test
    void cashierCanCancelLotterySaleWithIdempotencyKey() throws Exception {
        when(lotterySaleService.cancelIdempotently(any(), any(), any(), any())).thenReturn(new IdempotencyResult(
                IdempotencyState.COMPLETED,
                200,
                MediaType.APPLICATION_JSON_VALUE,
                "{\"id\":\"00000000-0000-0000-0000-000000000901\",\"reason\":\"Customer request\"}",
                false));

        mockMvc.perform(post("/api/v1/lottery/sales/{id}/cancel", "00000000-0000-0000-0000-000000000900")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("LOTTERY_SALE_CANCEL")))
                        .header(IdempotencyService.IDEMPOTENCY_KEY_HEADER, "cancel-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer request\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.reason").value("Customer request"));
    }

    @Test
    void viewerCanSearchLotterySales() throws Exception {
        when(lotterySaleService.search(any())).thenReturn(new PageResponse<>(
                java.util.List.of(),
                0,
                20,
                0,
                0,
                true,
                true));

        mockMvc.perform(get("/api/v1/lottery/sales")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW"))))
                .andExpect(status().isOk());
    }

    @Test
    void searchBindsHistoryFilters() throws Exception {
        when(lotterySaleService.search(any())).thenReturn(new PageResponse<>(
                java.util.List.of(),
                1,
                10,
                0,
                0,
                false,
                true));

        mockMvc.perform(get("/api/v1/lottery/sales")
                        .param("search", "ticket-99")
                        .param("operatorId", "00000000-0000-0000-0000-000000000801")
                        .param("storeId", "00000000-0000-0000-0000-000000000802")
                        .param("registerId", "00000000-0000-0000-0000-000000000803")
                        .param("cashierId", "00000000-0000-0000-0000-000000000806")
                        .param("registerSessionId", "00000000-0000-0000-0000-000000000805")
                        .param("gameType", "DRAW_TICKET")
                        .param("status", "RECORDED")
                        .param("paymentMethod", "CASH")
                        .param("occurredFrom", "2026-07-28T00:00:00Z")
                        .param("occurredTo", "2026-07-28T23:59:59Z")
                        .param("page", "1")
                        .param("size", "10")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("LOTTERY_VIEW"))))
                .andExpect(status().isOk());

        ArgumentCaptor<LotterySaleSearchRequest> request = ArgumentCaptor.forClass(LotterySaleSearchRequest.class);
        verify(lotterySaleService).search(request.capture());
        assertThat(request.getValue().search()).isEqualTo("ticket-99");
        assertThat(request.getValue().operatorId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000801"));
        assertThat(request.getValue().storeId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000802"));
        assertThat(request.getValue().registerId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000803"));
        assertThat(request.getValue().cashierId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000806"));
        assertThat(request.getValue().registerSessionId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000805"));
        assertThat(request.getValue().gameType()).isEqualTo(LotteryGameType.DRAW_TICKET);
        assertThat(request.getValue().status()).isEqualTo(LotterySaleStatus.RECORDED);
        assertThat(request.getValue().paymentMethod().name()).isEqualTo("CASH");
        assertThat(request.getValue().occurredFrom()).isEqualTo(Instant.parse("2026-07-28T00:00:00Z"));
        assertThat(request.getValue().occurredTo()).isEqualTo(Instant.parse("2026-07-28T23:59:59Z"));
        assertThat(request.getValue().page()).isEqualTo(1);
        assertThat(request.getValue().size()).isEqualTo(10);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({LotterySaleController.class, AuthorizationService.class, TestSecurityConfig.class})
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
