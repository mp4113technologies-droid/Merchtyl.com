package com.merchtyl.portal;

import com.merchtyl.common.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantPortalServiceTest {
    private final MerchantPortalService service = new MerchantPortalService(mock(JdbcTemplate.class), new MerchantPortalProperties("merchtyl.com", "https://platform.merchtyl.com"));

    @Test void normalizesUrlSafeStableSlug() {
        assertThat(service.normalize("  Adviám Creatives, Inc. ")).isEqualTo("adviam-creatives-inc");
    }

    @Test void rejectsReservedSlug() {
        assertThatThrownBy(() -> service.validate("platform")).isInstanceOf(BadRequestException.class).hasMessage("MERCHANT_SLUG_RESERVED");
    }

    @Test void derivesPortalUrlFromConfiguredDomain() {
        assertThat(service.portalUrl("patel-group")).isEqualTo("https://patel-group.merchtyl.com");
    }

    @Test void derivesMerchantPasswordResetUrlFromAuthoritativeTenantSlug() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID tenantId = UUID.randomUUID();
        when(jdbc.queryForObject("select merchant_slug from tenants where id = ?", String.class, tenantId))
                .thenReturn("sweet-shop");
        MerchantPortalService portals = new MerchantPortalService(jdbc,
                new MerchantPortalProperties("merchtyl.com", "https://platform.merchtyl.com"));

        assertThat(portals.resetPasswordUrl(tenantId, "token/value"))
                .isEqualTo("https://sweet-shop.merchtyl.com/reset-password?token=token%2Fvalue");
    }

    @Test void derivesPlatformAndLocalDevelopmentPasswordResetUrls() {
        assertThat(service.platformResetPasswordUrl("token"))
                .isEqualTo("https://platform.merchtyl.com/reset-password?token=token");
        MerchantPortalService local = new MerchantPortalService(mock(JdbcTemplate.class),
                new MerchantPortalProperties("localhost", "http://localhost:5173"));
        assertThat(local.portalUrl("sweet-shop"))
                .isEqualTo("http://sweet-shop.localhost:5173");
    }
}
