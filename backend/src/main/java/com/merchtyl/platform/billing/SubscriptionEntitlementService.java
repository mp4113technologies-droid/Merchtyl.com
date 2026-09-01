package com.merchtyl.platform.billing;
import com.merchtyl.common.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
@Service
public class SubscriptionEntitlementService {
    private final JdbcTemplate jdbc;
    public SubscriptionEntitlementService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional public void requireOrActivate(UUID tenantId,CommercialCapability capability){
        Map<String,Object> subscription=jdbc.queryForList("select id,pricing_plan_version_id from tenant_subscriptions where tenant_id=? and status in ('TRIAL','ACTIVE','PAST_DUE')",tenantId).stream().findFirst().orElseThrow(()->new ConflictException("SUBSCRIPTION_CAPABILITY_NOT_AVAILABLE"));UUID subscriptionId=(UUID)subscription.get("id");
        Integer active=jdbc.queryForObject("select count(*) from tenant_subscription_capabilities where subscription_id=? and capability=? and status='ACTIVE'",Integer.class,subscriptionId,capability.name());if(active!=null&&active>0)return;
        List<Map<String,Object>> configured=jdbc.queryForList("select inclusion_type,billing_unit,unit_price from platform_pricing_plan_version_capabilities where pricing_plan_version_id=? and capability=?",subscription.get("pricing_plan_version_id"),capability.name());
        if(configured.isEmpty()||"NOT_AVAILABLE".equals(configured.getFirst().get("inclusion_type")))throw new ConflictException("SUBSCRIPTION_CAPABILITY_NOT_AVAILABLE");Map<String,Object> value=configured.getFirst();
        jdbc.update("insert into tenant_subscription_capabilities(id,subscription_id,capability,status,inclusion_type_snapshot,billing_unit_snapshot,unit_price_snapshot,effective_from) values (?,?,?,?,?,?,?,?) on conflict(subscription_id,capability) do update set status='ACTIVE',inclusion_type_snapshot=excluded.inclusion_type_snapshot,billing_unit_snapshot=excluded.billing_unit_snapshot,unit_price_snapshot=coalesce(tenant_subscription_capabilities.custom_unit_price,excluded.unit_price_snapshot),effective_from=excluded.effective_from,updated_at=now(),version=tenant_subscription_capabilities.version+1",UUID.randomUUID(),subscriptionId,capability.name(),"ACTIVE",value.get("inclusion_type"),value.get("billing_unit"),value.get("unit_price"),Date.valueOf(LocalDate.now()));
    }
    @Transactional public void deactivateIfUnused(UUID tenantId,CommercialCapability capability){
        int usage=(capability==CommercialCapability.FOOD_SERVICE||capability==CommercialCapability.LOTTERY)
                ?Objects.requireNonNull(jdbc.queryForObject("select count(*) from stores store join store_capabilities capability on capability.store_id=store.id where store.tenant_id=? and store.active=true and capability.capability=?",Integer.class,tenantId,capability.name())):0;
        if(usage==0)jdbc.update("update tenant_subscription_capabilities set status='INACTIVE',effective_to=current_date,updated_at=now(),version=version+1 where subscription_id in(select id from tenant_subscriptions where tenant_id=?) and capability=? and inclusion_type_snapshot='PAID_ADD_ON'",tenantId,capability.name());
    }

    @Transactional(readOnly = true) public void requireActive(UUID tenantId, CommercialCapability capability) {
        Integer active = jdbc.queryForObject("select count(*) from tenant_subscription_capabilities capability join tenant_subscriptions subscription on subscription.id=capability.subscription_id where subscription.tenant_id=? and subscription.status in ('ACTIVE','TRIAL') and capability.capability=? and capability.status='ACTIVE'", Integer.class, tenantId, capability.name());
        if (active == null || active == 0) throw new ConflictException(capability.name()+"_NOT_ENTITLED: The merchant subscription does not entitle this capability");
    }
}
