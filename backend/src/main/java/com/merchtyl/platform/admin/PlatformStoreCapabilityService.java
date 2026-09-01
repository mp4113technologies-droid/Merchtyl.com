package com.merchtyl.platform.admin;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.platform.billing.BillingQuantityService;
import com.merchtyl.platform.billing.BillingUnit;
import com.merchtyl.platform.billing.CapabilityInclusionType;
import com.merchtyl.platform.billing.CommercialCapability;
import com.merchtyl.platform.billing.SubscriptionEntitlementService;
import com.merchtyl.store.StoreCapability;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.merchtyl.platform.admin.PlatformDtos.*;

@Service
public class PlatformStoreCapabilityService {
    private final JdbcTemplate jdbc;
    private final SubscriptionEntitlementService entitlements;
    private final BillingQuantityService quantities;
    private final AuditService audit;
    private final PlatformUserRepository platformUsers;

    public PlatformStoreCapabilityService(JdbcTemplate jdbc, SubscriptionEntitlementService entitlements,
                                          BillingQuantityService quantities, AuditService audit,
                                          PlatformUserRepository platformUsers) {
        this.jdbc = jdbc;
        this.entitlements = entitlements;
        this.quantities = quantities;
        this.audit = audit;
        this.platformUsers = platformUsers;
    }

    @Transactional(readOnly = true)
    public List<MerchantStoreCapabilityResponse> stores(UUID tenantId) {
        requireTenant(tenantId);
        return jdbc.query("select id,code,name,active,kitchen_display_name,version from stores where tenant_id=? order by name,id",
                (rs, row) -> response(tenantId, rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getBoolean("active"), rs.getString("kitchen_display_name"), rs.getLong("version")), tenantId);
    }

    @Transactional(readOnly = true)
    public StoreCapabilityChangePreview preview(UUID tenantId, UUID storeId, StoreCapabilityUpdateRequest request) {
        StoreRow store = store(tenantId, storeId, false);
        Set<StoreCapability> proposed = validate(request.capabilities());
        SubscriptionRow subscription = subscription(tenantId);
        List<StoreCapabilityPriceImpact> impacts = impacts(tenantId, store.capabilities(), proposed, subscription);
        return new StoreCapabilityChangePreview(tenantId, storeId, store.capabilities(), proposed,
                subscription.currency(), subscription.effectiveDate(), impacts,
                impacts.stream().anyMatch(impact -> impact.inclusionType() == CapabilityInclusionType.PAID_ADD_ON));
    }

    @Transactional
    public MerchantStoreCapabilityResponse update(UUID tenantId, UUID storeId, StoreCapabilityUpdateRequest request,
                                                   Authentication authentication) {
        StoreRow store = store(tenantId, storeId, true);
        if (store.version() != request.version()) throw new ConflictException("CONCURRENT_MODIFICATION: Store was changed by another request");
        Set<StoreCapability> proposed = validate(request.capabilities());
        SubscriptionRow subscription = subscription(tenantId);
        List<StoreCapabilityPriceImpact> impacts = impacts(tenantId, store.capabilities(), proposed, subscription);
        if (!request.confirmPaidAddOns() && impacts.stream().anyMatch(impact ->
                impact.inclusionType() == CapabilityInclusionType.PAID_ADD_ON && impact.newQuantity() > impact.currentQuantity())) {
            throw new ConflictException("CAPABILITY_PRICING_CONFIRMATION_REQUIRED: Confirm the paid add-on pricing change");
        }

        Set<StoreCapability> enabled = EnumSet.copyOf(proposed);
        enabled.removeAll(store.capabilities());
        for (StoreCapability capability : enabled) entitlements.requireOrActivate(tenantId, commercial(capability));

        Set<StoreCapability> disabled = EnumSet.copyOf(store.capabilities());
        disabled.removeAll(proposed);
        guardDisable(storeId, disabled);

        jdbc.update("delete from store_capabilities where store_id=?", storeId);
        proposed.forEach(capability -> jdbc.update("insert into store_capabilities(store_id,capability) values (?,?)",
                storeId, capability.name()));
        String kitchenName = proposed.contains(StoreCapability.FOOD_SERVICE)
                ? kitchenName(request.kitchenDisplayName(), store.name()) : null;
        int changed = jdbc.update("update stores set kitchen_display_name=?,updated_at=now(),version=version+1 where id=? and tenant_id=? and version=?",
                kitchenName, storeId, tenantId, request.version());
        if (changed != 1) throw new ConflictException("CONCURRENT_MODIFICATION: Store was changed by another request");
        for (StoreCapability capability : disabled) entitlements.deactivateIfUnused(tenantId, commercial(capability));
        if (disabled.contains(StoreCapability.FOOD_SERVICE))
            jdbc.update("update registers set active=false,updated_at=now(),version=version+1 where store_id=? and register_type='FOOD_SERVICE' and active=true", storeId);

        UUID actorId = platformUsers.findByEmail(authentication.getName()).orElseThrow().id();
        Map<String, Object> commercialImpact = Map.of("pricingPlanVersionId", subscription.pricingVersionId(),
                "effectiveDate", subscription.effectiveDate(), "impacts", impacts);
        for (StoreCapability capability : enabled) audit.record(new CreateAuditRecordCommand(actorId,
                AuditAction.MERCHANT_STORE_CAPABILITY_ENABLED, "STORE_CAPABILITY", storeId, storeId, null,
                store.capabilities(), Map.of("capability", capability, "capabilities", proposed, "subscriptionImpact", commercialImpact), null));
        for (StoreCapability capability : disabled) audit.record(new CreateAuditRecordCommand(actorId,
                AuditAction.MERCHANT_STORE_CAPABILITY_DISABLED, "STORE_CAPABILITY", storeId, storeId, null,
                store.capabilities(), Map.of("capability", capability, "capabilities", proposed, "subscriptionImpact", commercialImpact), null));
        return response(tenantId, storeId, store.code(), store.name(), store.active(), kitchenName, store.version() + 1);
    }

    private List<StoreCapabilityPriceImpact> impacts(UUID tenantId, Set<StoreCapability> current,
                                                      Set<StoreCapability> proposed, SubscriptionRow subscription) {
        List<StoreCapabilityPriceImpact> result = new ArrayList<>();
        for (StoreCapability capability : StoreCapability.values()) {
            if (current.contains(capability) == proposed.contains(capability)) continue;
            CommercialCapability commercial = commercial(capability);
            CapabilityConfig config = capability(subscription.pricingVersionId(), commercial);
            if (proposed.contains(capability) && config.inclusionType() == CapabilityInclusionType.NOT_AVAILABLE) {
                throw new ConflictException("CAPABILITY_NOT_AVAILABLE_ON_PLAN: " + label(capability) + " is not available on the current Pricing Plan");
            }
            if (config.inclusionType() != CapabilityInclusionType.PAID_ADD_ON) continue;
            int currentQuantity = quantities.quantity(tenantId, commercial, config.billingUnit());
            int delta = current.contains(capability) ? -1 : 1;
            int newQuantity = config.billingUnit() == BillingUnit.PER_STORE ? Math.max(0, currentQuantity + delta) : currentQuantity;
            BigDecimal unitPrice = config.unitPrice() == null ? BigDecimal.ZERO : config.unitPrice();
            result.add(new StoreCapabilityPriceImpact(commercial, config.inclusionType(), config.billingUnit(),
                    currentQuantity, newQuantity, unitPrice, money(unitPrice.multiply(BigDecimal.valueOf(currentQuantity))),
                    money(unitPrice.multiply(BigDecimal.valueOf(newQuantity)))));
        }
        return result;
    }

    private CapabilityConfig capability(UUID pricingVersionId, CommercialCapability capability) {
        List<CapabilityConfig> values = jdbc.query("select inclusion_type,billing_unit,unit_price from platform_pricing_plan_version_capabilities where pricing_plan_version_id=? and capability=?",
                (rs, row) -> new CapabilityConfig(CapabilityInclusionType.valueOf(rs.getString(1)),
                        rs.getString(2) == null ? null : BillingUnit.valueOf(rs.getString(2)), rs.getBigDecimal(3)),
                pricingVersionId, capability.name());
        return values.stream().findFirst().orElse(new CapabilityConfig(CapabilityInclusionType.NOT_AVAILABLE, null, null));
    }

    private SubscriptionRow subscription(UUID tenantId) {
        List<SubscriptionRow> rows = jdbc.query("select pricing_plan_version_id,currency_code,next_billing_date,status from tenant_subscriptions where tenant_id=?",
                (rs, row) -> new SubscriptionRow(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, LocalDate.class), rs.getString(4)), tenantId);
        SubscriptionRow value = rows.stream().findFirst().orElseThrow(() -> new ConflictException("SUBSCRIPTION_NOT_ACTIVE: Merchant subscription is unavailable"));
        if (!Set.of("ACTIVE", "TRIAL").contains(value.status()) || value.pricingVersionId() == null)
            throw new ConflictException("SUBSCRIPTION_NOT_ACTIVE: Merchant subscription is not active");
        return new SubscriptionRow(value.pricingVersionId(), value.currency(),
                value.effectiveDate() == null ? LocalDate.now() : value.effectiveDate(), value.status());
    }

    private StoreRow store(UUID tenantId, UUID storeId, boolean lock) {
        requireTenant(tenantId);
        String sql = "select id,code,name,active,kitchen_display_name,version from stores where id=? and tenant_id=?" + (lock ? " for update" : "");
        List<StoreRow> rows = jdbc.query(sql,
                (rs, row) -> new StoreRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getBoolean(4), rs.getString(5), rs.getLong(6), capabilities(storeId)), storeId, tenantId);
        return rows.stream().findFirst().orElseThrow(() -> new NotFoundException("Store not found for merchant"));
    }

    private void requireTenant(UUID tenantId) {
        Boolean exists = jdbc.queryForObject("select exists(select 1 from tenants where id=?)", Boolean.class, tenantId);
        if (!Boolean.TRUE.equals(exists)) throw new NotFoundException("Merchant not found");
    }

    private Set<StoreCapability> capabilities(UUID storeId) {
        List<String> values = jdbc.queryForList("select capability from store_capabilities where store_id=? order by capability", String.class, storeId);
        Set<StoreCapability> result = EnumSet.noneOf(StoreCapability.class);
        values.forEach(value -> result.add(StoreCapability.valueOf(value)));
        return Set.copyOf(result);
    }

    private MerchantStoreCapabilityResponse response(UUID tenantId, UUID id, String code, String name,
                                                       boolean active, String kitchenName, long version) {
        return new MerchantStoreCapabilityResponse(id, code, name, active, capabilities(id), kitchenName, version);
    }

    private void guardDisable(UUID storeId, Set<StoreCapability> disabled) {
        if (disabled.contains(StoreCapability.FOOD_SERVICE)) {
            Integer open = jdbc.queryForObject("select count(*) from register_sessions session join registers register on register.id=session.register_id where register.store_id=? and register.register_type='FOOD_SERVICE' and session.status='OPEN'", Integer.class, storeId);
            if (open != null && open > 0) throw new ConflictException("CAPABILITY_CHANGE_BLOCKED: Close active Food Service register sessions before disabling Food Service");
        }
        if (disabled.contains(StoreCapability.LOTTERY)) {
            Integer open = jdbc.queryForObject("select count(*) from lottery_settlements where store_id=? and status not in ('POSTED')", Integer.class, storeId);
            if (open != null && open > 0) throw new ConflictException("CAPABILITY_CHANGE_BLOCKED: Post open lottery settlements before disabling Lottery");
        }
    }

    private static Set<StoreCapability> validate(Set<StoreCapability> values) {
        if (values == null || values.isEmpty()) throw new BadRequestException("At least one store capability is required");
        return Set.copyOf(values);
    }

    private static CommercialCapability commercial(StoreCapability capability) {
        return capability == StoreCapability.RETAIL ? CommercialCapability.RETAIL_POS : CommercialCapability.valueOf(capability.name());
    }

    private static String label(StoreCapability capability) {
        return switch (capability) { case RETAIL -> "Retail POS"; case FOOD_SERVICE -> "Restaurant / Kitchen POS"; case LOTTERY -> "Lottery"; };
    }

    private static String kitchenName(String value, String storeName) {
        return value == null || value.isBlank() ? storeName + " Kitchen" : value.trim();
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private record StoreRow(UUID id, String code, String name, boolean active, String kitchenDisplayName,
                            long version, Set<StoreCapability> capabilities) {}
    private record SubscriptionRow(UUID pricingVersionId, String currency, LocalDate effectiveDate, String status) {}
    private record CapabilityConfig(CapabilityInclusionType inclusionType, BillingUnit billingUnit, BigDecimal unitPrice) {}
}
