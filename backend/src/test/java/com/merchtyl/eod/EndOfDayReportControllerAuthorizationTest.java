package com.merchtyl.eod;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EndOfDayReportControllerAuthorizationTest.TestApplication.class)
@AutoConfigureMockMvc
class EndOfDayReportControllerAuthorizationTest {
    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-0000-0000-000000001101");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    BusinessDayService businessDayService;

    @Test
    void cashierCannotViewReports() throws Exception {
        mockMvc.perform(get("/api/v1/end-of-day-reports")
                        .with(user("cashier").authorities(new SimpleGrantedAuthority("BUSINESS_DAY_VIEW"))))
                .andExpect(status().isForbidden());

        verify(businessDayService, never()).searchReports(any());
    }

    @Test
    void managerCanFilterReports() throws Exception {
        when(businessDayService.searchReports(any())).thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/end-of-day-reports")
                        .param("reportNumber", "MAIN-2026")
                        .param("status", "CLOSED")
                        .with(user("manager").authorities(new SimpleGrantedAuthority("END_OF_DAY_REPORT_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reportNumber").value("MAIN-2026-07-29-R1"));
    }

    @Test
    void exportRequiresExportPermission() throws Exception {
        mockMvc.perform(get("/api/v1/end-of-day-reports/{id}/export/csv", REPORT_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("END_OF_DAY_REPORT_VIEW"))))
                .andExpect(status().isForbidden());

        verify(businessDayService, never()).exportCsv(eq(REPORT_ID), any());
    }

    @Test
    void csvExportReturnsCsvContentType() throws Exception {
        when(businessDayService.getReport(REPORT_ID)).thenReturn(response());
        when(businessDayService.exportCsv(eq(REPORT_ID), any())).thenReturn("# summary\nmetric,value\n");

        mockMvc.perform(get("/api/v1/end-of-day-reports/{id}/export/csv", REPORT_ID)
                        .with(user("manager").authorities(new SimpleGrantedAuthority("END_OF_DAY_REPORT_EXPORT"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string("# summary\nmetric,value\n"));
    }

    private static EndOfDayReportResponse response() {
        return new EndOfDayReportResponse(
                REPORT_ID,
                UUID.fromString("00000000-0000-0000-0000-000000001102"),
                UUID.fromString("00000000-0000-0000-0000-000000001103"),
                "MAIN",
                "Main Store",
                LocalDate.parse("2026-07-29"),
                BusinessDayStatus.CLOSED,
                0,
                "MAIN-2026-07-29-R1",
                1,
                Instant.parse("2026-07-29T23:05:00Z"),
                UUID.fromString("00000000-0000-0000-0000-000000001104"),
                "Manager One",
                bd("100.00"),
                bd("80.00"),
                bd("5.00"),
                bd("10.00"),
                bd("0.00"),
                bd("6.00"),
                2,
                bd("40.00"),
                bd("50.00"),
                bd("30.00"),
                bd("4.0000"),
                bd("2.0000"),
                bd("125.00"),
                bd("125.00"),
                bd("0.00"),
                "USD",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                null,
                "{}",
                0);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class
    })
    @EnableMethodSecurity
    @Import({EndOfDayReportController.class, AuthorizationService.class, TestSecurityConfig.class})
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
