package com.merchtyl.portal;

import com.merchtyl.common.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
}
