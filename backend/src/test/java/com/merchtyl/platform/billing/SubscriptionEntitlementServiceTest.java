package com.merchtyl.platform.billing;

import com.merchtyl.common.ForbiddenOperationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionEntitlementServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SubscriptionEntitlementService service = new SubscriptionEntitlementService(jdbc);

    @Test
    void includedPlanCapabilityIsEffectiveWithoutSubscriptionCapabilityRow() {
        UUID tenantId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(tenantId),
                eq("FOOD_SERVICE"), eq("FOOD_SERVICE"))).thenReturn(1);

        service.requireActive(tenantId, CommercialCapability.FOOD_SERVICE);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class), eq(tenantId),
                eq("FOOD_SERVICE"), eq("FOOD_SERVICE"));
        assertThat(sql.getValue()).contains("pricing_plan_version_id", "inclusion_type='INCLUDED'",
                "tenant_subscription_capabilities", "status='ACTIVE'");
    }

    @Test
    void unavailableCapabilityIsDenied() {
        UUID tenantId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(tenantId),
                eq("FOOD_SERVICE"), eq("FOOD_SERVICE"))).thenReturn(0);

        assertThatThrownBy(() -> service.requireActive(tenantId, CommercialCapability.FOOD_SERVICE))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("MERCHANT_CAPABILITY_NOT_ENABLED");
    }
}
