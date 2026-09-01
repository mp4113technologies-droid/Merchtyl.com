package com.merchtyl.platform.billing;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingQuantityServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final BillingQuantityService service=new BillingQuantityService(jdbc);
    private final UUID tenantId=UUID.randomUUID();

    @Test
    void perMerchantIsAlwaysOneWithoutDatabaseCounting(){
        assertThat(service.quantity(tenantId,CommercialCapability.ADVANCED_REPORTING,BillingUnit.PER_MERCHANT)).isOne();
    }

    @Test
    void perStoreCountsOnlyCapabilityEnabledActiveTenantStores(){
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(tenantId))).thenReturn(2);
        assertThat(service.quantity(tenantId,CommercialCapability.FOOD_SERVICE,BillingUnit.PER_STORE)).isEqualTo(2);
        assertSqlContains("store_capabilities","s.tenant_id=?","s.active=true","FOOD_SERVICE");
    }

    @Test
    void perUserCountsOnlyActiveUnlockedTenantUsers(){
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(tenantId))).thenReturn(8);
        assertThat(service.quantity(tenantId,CommercialCapability.EMPLOYEE_MANAGEMENT,BillingUnit.PER_USER)).isEqualTo(8);
        assertSqlContains("security_users","tenant_id=?","enabled=true","locked=false");
    }

    @Test
    void perRegisterExcludesInactiveRegistersAndScopesFoodServiceStores(){
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(tenantId))).thenReturn(4);
        assertThat(service.quantity(tenantId,CommercialCapability.FOOD_SERVICE,BillingUnit.PER_REGISTER)).isEqualTo(4);
        assertSqlContains("registers","r.active=true","s.active=true","store_capabilities","FOOD_SERVICE");
    }

    @Test
    void lotteryRegisterCountUsesLotteryEnabledStores(){
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(tenantId))).thenReturn(3);
        assertThat(service.quantity(tenantId,CommercialCapability.LOTTERY,BillingUnit.PER_REGISTER)).isEqualTo(3);
        assertSqlContains("store_capabilities","c.capability='LOTTERY'","s.tenant_id=?");
    }

    @Test
    void lotteryStoreCountUsesStoreCapabilityRatherThanOperationalHistory(){
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(tenantId))).thenReturn(2);
        assertThat(service.quantity(tenantId,CommercialCapability.LOTTERY,BillingUnit.PER_STORE)).isEqualTo(2);
        assertSqlContains("store_capabilities","c.capability='LOTTERY'","s.active=true");
    }

    private void assertSqlContains(String... fragments){
        ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(),eq(Integer.class),eq(tenantId));
        for(String fragment:fragments)assertThat(sql.getValue()).contains(fragment);
    }
}
