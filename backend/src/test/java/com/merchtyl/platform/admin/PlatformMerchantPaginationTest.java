package com.merchtyl.platform.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditService;
import com.merchtyl.auth.JwtService;
import com.merchtyl.config.JwtProperties;
import com.merchtyl.config.PlatformAdministrationProperties;
import com.merchtyl.config.SecurityProperties;
import com.merchtyl.email.EmailProperties;
import com.merchtyl.reference.ReferenceDataService;
import com.merchtyl.security.RefreshTokenService;
import com.merchtyl.security.TemporaryPasswordGenerator;
import com.merchtyl.security.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformMerchantPaginationTest {
    @Test
    void performsSearchFiltersStableSortAndPaginationInSql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(126L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        PlatformAdministrationService service = service(jdbc);

        var response = service.listTenants(new PlatformDtos.TenantListRequest(
                1, 10, "  market  ", TenantStatus.ACTIVE, "ca", "nb",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "trial", "growth", "createdAt,desc"));

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(126);
        assertThat(response.totalPages()).isEqualTo(13);
        verify(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void capsPageSizeAtOneHundred() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        var response = service(jdbc).listTenants(new PlatformDtos.TenantListRequest(
                -1, 100_000, null, null, null, null, null, null, null, null, null));

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(100);
        assertThat(response.first()).isTrue();
    }

    private static PlatformAdministrationService service(JdbcTemplate jdbc) {
        return new PlatformAdministrationService(jdbc, new ObjectMapper(), mock(PlatformUserRepository.class),
                mock(UserRepository.class), mock(PasswordEncoder.class), mock(JwtService.class), mock(RefreshTokenService.class),
                new JwtProperties("test", "12345678901234567890123456789012", 15, 7),
                new PlatformAdministrationProperties(null, null, null), mock(AuditService.class),
                mock(ReferenceDataService.class), mock(ApplicationEventPublisher.class), new EmailProperties(null, null, null, null, null, null, null),
                mock(Environment.class), mock(TemporaryPasswordGenerator.class), new SecurityProperties(null, null, null));
    }
}
