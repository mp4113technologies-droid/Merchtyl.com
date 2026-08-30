package com.merchtyl.platform.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.platform.admin.PlatformUserRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.Set;
import java.util.EnumSet;

import static com.merchtyl.platform.billing.BillingDtos.*;

@Service
public class PlatformBillingService {
    private static final Logger log = LoggerFactory.getLogger(PlatformBillingService.class);
    private static final UUID SETTINGS_ID = UUID.fromString("00000000-0000-0000-0000-000000000b75");

    private final JdbcTemplate jdbc;
    private final SubscriptionBillingService calculator;
    private final AuditService auditService;
    private final PlatformUserRepository platformUsers;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public PlatformBillingService(JdbcTemplate jdbc, SubscriptionBillingService calculator, AuditService auditService,
                                  PlatformUserRepository platformUsers, UserRepository users, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.calculator = calculator;
        this.auditService = auditService;
        this.platformUsers = platformUsers;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        BillingSettingsResponse settings = settings();
        return new Overview(
                count("select count(*) from tenant_subscriptions where status = 'ACTIVE'"),
                count("select count(*) from tenant_subscriptions where status = 'TRIAL'"),
                money(decimal("""
                    select coalesce(sum(case when billing_interval = 'YEARLY' then coalesce(custom_base_price, base_price_snapshot, 0) / 12 else coalesce(custom_base_price, base_price_snapshot, 0) end), 0)
                    from tenant_subscriptions where status in ('ACTIVE','TRIAL','PAST_DUE') and currency_code = ?
                    """, settings.defaultCurrency())),
                count("select count(*) from platform_invoices where issue_date >= date_trunc('month', current_date)"),
                money(decimal("select coalesce(sum(amount_outstanding),0) from platform_invoices where status in ('ISSUED','SENT','PARTIALLY_PAID','PAST_DUE') and currency_code = ?", settings.defaultCurrency())),
                count("select count(*) from platform_invoices where status = 'PAST_DUE'"),
                money(decimal("select coalesce(sum(amount),0) from platform_invoice_payments p join platform_invoices i on i.id=p.invoice_id where p.payment_date >= date_trunc('month', current_date) and i.currency_code = ?", settings.defaultCurrency())),
                count("select count(*) from tenant_subscriptions where cancel_at_period_end = true and status <> 'CANCELLED'"),
                settings.defaultCurrency());
    }

    @Transactional(readOnly = true)
    public Page<PlanResponse> plans(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        List<PlanResponse> content = jdbc.query("""
                select p.*, (select count(*) from tenant_subscriptions s where s.pricing_plan_id=p.id and s.status in ('TRIAL','ACTIVE','PAST_DUE')) active_merchants
                from platform_pricing_plans p order by p.name, p.id limit ? offset ?
                """, PlatformBillingService::plan, safeSize, safePage * safeSize).stream().map(this::withCapabilityPrices).toList();
        long total = count("select count(*) from platform_pricing_plans");
        return new Page<>(content, safePage, safeSize, total, pages(total, safeSize));
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> activePlanOptions() {
        return jdbc.query("""
                select p.*, (select count(*) from tenant_subscriptions s where s.pricing_plan_id=p.id and s.status in ('TRIAL','ACTIVE','PAST_DUE')) active_merchants
                from platform_pricing_plans p
                where p.status='ACTIVE' and p.effective_from<=current_date and (p.effective_to is null or p.effective_to>=current_date)
                order by p.name,p.id
                """, PlatformBillingService::plan).stream().map(this::withCapabilityPrices).toList();
    }

    @Transactional
    public PlanResponse createPlan(PlanRequest request, Authentication authentication) {
        validatePlan(request);
        UUID id = UUID.randomUUID();
        UUID actor = platformActor(authentication);
        jdbc.update("""
                insert into platform_pricing_plans(id,code,name,description,status,billing_interval,base_price,one_time_onboarding_fee,currency_code,trial_days,
                  included_stores,included_registers,included_users,additional_store_price,additional_register_price,additional_user_price,
                  tax_behavior,effective_from,effective_to,created_by)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, code(request.code()), request.name().trim(), clean(request.description()), status(request.status()), interval(request.billingInterval()),
                request.basePrice(), request.oneTimeOnboardingFee(), request.currency().toUpperCase(Locale.ROOT), request.trialDays(), request.includedStores(), request.includedRegisters(),
                request.includedUsers(), request.additionalStorePrice(), request.additionalRegisterPrice(), request.additionalUserPrice(),
                taxBehavior(request.taxBehavior()), Date.valueOf(request.effectiveFrom()), date(request.effectiveTo()), actor);
        replacePlanCapabilityPrices(id, request.capabilityPrices());
        snapshotPlan(id, 1, request, actor);
        PlanResponse response = plan(id);
        audit(actor, AuditAction.PRICING_PLAN_CREATED, "PLATFORM_PRICING_PLAN", id, null, response, request.name());
        return response;
    }

    @Transactional
    public PlanResponse updatePlan(UUID id, PlanRequest request, Authentication authentication) {
        validatePlan(request);
        PlanResponse before = plan(id);
        int updated = jdbc.update("""
                update platform_pricing_plans set code=?,name=?,description=?,status=?,billing_interval=?,base_price=?,one_time_onboarding_fee=?,currency_code=?,trial_days=?,
                  included_stores=?,included_registers=?,included_users=?,additional_store_price=?,additional_register_price=?,additional_user_price=?,
                  tax_behavior=?,effective_from=?,effective_to=?,updated_at=now(),version=version+1 where id=?
                """, code(request.code()), request.name().trim(), clean(request.description()), status(request.status()), interval(request.billingInterval()),
                request.basePrice(), request.oneTimeOnboardingFee(), request.currency().toUpperCase(Locale.ROOT), request.trialDays(), request.includedStores(), request.includedRegisters(),
                request.includedUsers(), request.additionalStorePrice(), request.additionalRegisterPrice(), request.additionalUserPrice(),
                taxBehavior(request.taxBehavior()), Date.valueOf(request.effectiveFrom()), date(request.effectiveTo()), id);
        if (updated == 0) throw new NotFoundException("Pricing plan not found");
        UUID actor = platformActor(authentication);
        replacePlanCapabilityPrices(id, request.capabilityPrices());
        snapshotPlan(id, Math.toIntExact(before.version() + 2), request, actor);
        PlanResponse after = plan(id);
        AuditAction action = !before.status().equals(after.status()) && after.status().equals("ACTIVE")
                ? AuditAction.PRICING_PLAN_ACTIVATED
                : !before.status().equals(after.status()) ? AuditAction.PRICING_PLAN_DEACTIVATED : AuditAction.PRICING_PLAN_UPDATED;
        audit(actor, action, "PLATFORM_PRICING_PLAN", id, before, after, request.name());
        return after;
    }

    @Transactional(readOnly=true)
    public List<CapabilityDefinition> capabilityDefinitions(){
        return List.of(
                definition(CommercialCapability.RETAIL_POS,BillingUnit.PER_MERCHANT),definition(CommercialCapability.INVENTORY,BillingUnit.PER_MERCHANT),
                definition(CommercialCapability.REGISTER_MANAGEMENT,BillingUnit.PER_REGISTER),definition(CommercialCapability.RETURNS,BillingUnit.PER_MERCHANT),
                definition(CommercialCapability.REPORTING,BillingUnit.PER_MERCHANT),definition(CommercialCapability.ADVANCED_REPORTING,BillingUnit.PER_MERCHANT),
                definition(CommercialCapability.EMPLOYEE_MANAGEMENT,BillingUnit.PER_MERCHANT),definition(CommercialCapability.FOOD_SERVICE,BillingUnit.PER_STORE),
                definition(CommercialCapability.LOTTERY,BillingUnit.PER_STORE));
    }

    @Transactional(readOnly=true)
    public List<PricingVersionResponse> pricingHistory(UUID planId){plan(planId);return jdbc.query("select v.*,p.code,p.name,p.description,p.status plan_status,p.tax_behavior from platform_pricing_plan_versions v join platform_pricing_plans p on p.id=v.pricing_plan_id where v.pricing_plan_id=? order by v.version_number desc",(rs,row)->pricingVersion(rs,row),planId);}

    @Transactional
    public PricingVersionResponse schedulePricingVersion(UUID planId,PricingVersionRequest request,Authentication authentication){
        PlanResponse current=plan(planId);validatePlan(request.pricing());
        if(current.version()!=request.expectedPlanVersion())throw new ConflictException("PRICING_PLAN_MODIFIED");
        requireCapabilityRemovalConfirmation(planId,current.capabilityPrices(),request.pricing().capabilityPrices(),request.confirmCapabilityRemoval());
        LocalDate effective=effectiveDate(planId,request);
        Integer conflict=jdbc.queryForObject("select count(*) from platform_pricing_plan_versions where pricing_plan_id=? and status in ('SCHEDULED','ACTIVE') and effective_from::date=?",Integer.class,planId,Date.valueOf(effective));
        if(conflict!=null&&conflict>0)throw new ConflictException("PRICING_PLAN_VERSION_CONFLICT");
        validateCapabilities(request.pricing().capabilityPrices());
        UUID actor=platformActor(authentication);int number=Objects.requireNonNull(jdbc.queryForObject("select coalesce(max(version_number),0)+1 from platform_pricing_plan_versions where pricing_plan_id=?",Integer.class,planId));UUID versionId=UUID.randomUUID();
        insertPricingVersion(versionId,planId,number,request.pricing(),effective,"SCHEDULED",subscriberPolicy(request.existingSubscriberPolicy()),actor);
        jdbc.update("update platform_pricing_plans set version=version+1,updated_at=now() where id=? and version=?",planId,request.expectedPlanVersion());
        PricingVersionResponse response=pricingVersion(versionId);audit(actor,AuditAction.PRICING_PLAN_PRICE_CHANGE_SCHEDULED,"PLATFORM_PRICING_PLAN_VERSION",versionId,current,response,"effective="+effective+",policy="+request.existingSubscriberPolicy());return response;
    }

    @Transactional
    public void cancelPricingVersion(UUID planId,UUID versionId,Authentication authentication){
        PricingVersionResponse before=pricingVersion(versionId);if(!before.pricingPlanId().equals(planId))throw new NotFoundException("PRICING_PLAN_NOT_FOUND");if(!"SCHEDULED".equals(before.status()))throw new ConflictException("Only scheduled pricing can be cancelled");
        jdbc.update("update platform_pricing_plan_versions set status='CANCELLED',cancelled_at=now(),version=version+1 where id=? and status='SCHEDULED'",versionId);audit(platformActor(authentication),AuditAction.PRICING_PLAN_PRICE_CHANGE_CANCELLED,"PLATFORM_PRICING_PLAN_VERSION",versionId,before,pricingVersion(versionId),"Scheduled pricing cancelled");
    }

    @Transactional
    public void activateDuePricingVersions(){
        List<UUID> due=jdbc.query("select id from platform_pricing_plan_versions where status='SCHEDULED' and effective_from<=now() order by effective_from for update skip locked",(rs,row)->rs.getObject(1,UUID.class));
        due.forEach(this::activatePricingVersion);
    }

    @Transactional
    public void adoptDuePricingVersion(UUID tenantId){
        SubscriptionResponse subscription=subscription(tenantId);
        List<UUID> applicable=jdbc.query("select id from platform_pricing_plan_versions where pricing_plan_id=? and status='ACTIVE' and subscriber_policy='APPLY_NEXT_BILLING_CYCLE' and effective_from::date<=? order by version_number desc limit 1",(rs,row)->rs.getObject(1,UUID.class),subscription.pricingPlanId(),Date.valueOf(subscription.nextBillingDate()));
        if(applicable.isEmpty()||applicable.getFirst().equals(jdbc.queryForObject("select pricing_plan_version_id from tenant_subscriptions where id=?",UUID.class,subscription.id())))return;
        if(jdbc.queryForObject("select count(*) from tenant_subscriptions where id=? and (custom_base_price is not null or custom_additional_store_price is not null)",Integer.class,subscription.id())>0)return;
        PricingVersionResponse version=pricingVersion(applicable.getFirst());PlanRequest price=version.pricing();
        jdbc.update("update tenant_subscriptions set pricing_plan_version_id=?,base_price_snapshot=?,included_stores_snapshot=?,additional_store_price_snapshot=?,onboarding_fee_snapshot=?,updated_at=now(),version=version+1 where id=?",version.id(),price.basePrice(),price.includedStores(),price.additionalStorePrice(),price.oneTimeOnboardingFee(),subscription.id());
        replaceSubscriptionCapabilitySnapshots(subscription.id(),price.capabilityPrices());syncSubscriptionEntitlements(subscription.id(),price.capabilityPrices(),subscription.nextBillingDate());
    }

    @Transactional
    public SubscriptionResponse assignSubscription(UUID tenantId, SubscriptionRequest request, Authentication authentication) {
        tenantName(tenantId);
        PlanResponse plan = plan(request.pricingPlanId());
        if (!plan.status().equals("ACTIVE")) throw new ConflictException("Only an active pricing plan can be assigned");
        validateSubscription(request, plan);
        LocalDate periodEnd = nextPeriod(request.startDate(), request.billingInterval()).minusDays(1);
        UUID actor = platformActor(authentication);
        UUID id = jdbc.query("select id from tenant_subscriptions where tenant_id=?", (rs, row) -> rs.getObject(1, UUID.class), tenantId)
                .stream().findFirst().orElse(UUID.randomUUID());
        jdbc.update("""
                insert into tenant_subscriptions(id,tenant_id,plan_code,status,starts_at,trial_ends_at,renews_at,maximum_stores,maximum_users,features,
                  pricing_plan_id,billing_interval,current_period_start,current_period_end,next_billing_date,base_price_snapshot,currency_code,
                  plan_name_snapshot,plan_code_snapshot,included_stores_snapshot,additional_store_price_snapshot,onboarding_fee_snapshot,
                  custom_base_price,custom_onboarding_fee,custom_additional_store_price,custom_additional_register_price,custom_additional_user_price,
                  discount_name,discount_type,discount_value,pricing_notes,pricing_effective_from,payment_terms_days)
                values (?,?,?,?,?,?,?,?,?,'{}',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                on conflict (tenant_id) do update set plan_code=excluded.plan_code,status=excluded.status,starts_at=excluded.starts_at,
                  trial_ends_at=excluded.trial_ends_at,renews_at=excluded.renews_at,pricing_plan_id=excluded.pricing_plan_id,
                  billing_interval=excluded.billing_interval,current_period_start=excluded.current_period_start,current_period_end=excluded.current_period_end,
                  next_billing_date=excluded.next_billing_date,base_price_snapshot=excluded.base_price_snapshot,currency_code=excluded.currency_code,
                  plan_name_snapshot=excluded.plan_name_snapshot,plan_code_snapshot=excluded.plan_code_snapshot,
                  included_stores_snapshot=excluded.included_stores_snapshot,additional_store_price_snapshot=excluded.additional_store_price_snapshot,
                  onboarding_fee_snapshot=excluded.onboarding_fee_snapshot,custom_base_price=excluded.custom_base_price,
                  custom_onboarding_fee=excluded.custom_onboarding_fee,custom_additional_store_price=excluded.custom_additional_store_price,
                  custom_additional_register_price=excluded.custom_additional_register_price,custom_additional_user_price=excluded.custom_additional_user_price,
                  discount_name=excluded.discount_name,discount_type=excluded.discount_type,discount_value=excluded.discount_value,
                  pricing_notes=excluded.pricing_notes,pricing_effective_from=excluded.pricing_effective_from,payment_terms_days=excluded.payment_terms_days,
                  cancel_at_period_end=false,cancelled_at=null,cancellation_reason=null,updated_at=now(),version=tenant_subscriptions.version+1
                """, id, tenantId, plan.code(), subscriptionStatus(request.status()), Timestamp.from(request.startDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant()),
                timestamp(request.trialEndDate()), timestamp(nextPeriod(request.startDate(), request.billingInterval())), plan.includedStores(), plan.includedUsers(),
                plan.id(), interval(request.billingInterval()), Date.valueOf(request.startDate()), Date.valueOf(periodEnd), Date.valueOf(nextPeriod(request.startDate(), request.billingInterval())),
                plan.basePrice(), plan.currency(), plan.name(), plan.code(), plan.includedStores(), plan.additionalStorePrice(), plan.oneTimeOnboardingFee(),
                request.customBasePrice(), request.customOnboardingFee(), request.customAdditionalStorePrice(), request.customAdditionalRegisterPrice(),
                request.customAdditionalUserPrice(), clean(request.discountName()), discountType(request.discountType()), request.discountValue(),
                clean(request.pricingNotes()), Date.valueOf(request.startDate()), request.paymentTermsDays());
        replaceSubscriptionCapabilitySnapshots(id, plan.capabilityPrices());
        UUID planVersion=jdbc.query("select id from platform_pricing_plan_versions where pricing_plan_id=? and status='ACTIVE' and effective_from<=now() order by version_number desc limit 1",(rs,row)->rs.getObject(1,UUID.class),plan.id()).stream().findFirst().orElse(null);
        jdbc.update("update tenant_subscriptions set pricing_plan_version_id=? where id=?",planVersion,id);
        syncSubscriptionEntitlements(id,plan.capabilityPrices(),request.startDate());
        SubscriptionResponse response = subscription(tenantId);
        audit(actor, AuditAction.MERCHANT_SUBSCRIPTION_CREATED, "MERCHANT_SUBSCRIPTION", response.id(), null, response, response.planCode());
        log.info("billing_event event=SUBSCRIPTION_STATUS_CHANGED tenant_id={} subscription_public_id={} status={}", tenantId, response.id(), response.status());
        return response;
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse subscription(UUID tenantId) {
        SubscriptionResponse response = jdbc.query("""
                select s.*,t.display_name merchant_name,coalesce(s.plan_name_snapshot,p.name,s.plan_code) plan_name,
                  coalesce(s.plan_code_snapshot,p.code,s.plan_code) current_plan_code,p.base_price standard_base_price,
                  (select count(*) from stores store_count where store_count.tenant_id=s.tenant_id and store_count.active=true) billable_store_count
                from tenant_subscriptions s join tenants t on t.id=s.tenant_id left join platform_pricing_plans p on p.id=s.pricing_plan_id
                where s.tenant_id=?
                """, PlatformBillingService::subscription, tenantId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Merchant subscription not found"));
        return withCapabilityCharges(response, tenantCapabilityCounts(tenantId));
    }

    @Transactional(readOnly = true)
    public PricingPreview planPreview(UUID planId, int storeCount, int foodServiceStoreCount) {
        PlanResponse plan=plan(planId);
        return preview(plan.currency(),plan.basePrice(),plan.includedStores(),plan.additionalStorePrice(),Math.max(0,storeCount),Map.of("FOOD_SERVICE",Math.max(0,foodServiceStoreCount)),plan.capabilityPrices());
    }

    @Transactional(readOnly = true)
    public PricingPreview subscriptionPreview(UUID tenantId, int additionalStores, boolean foodService) {
        SubscriptionResponse subscription=subscription(tenantId);
        Map<String,Integer> counts=new HashMap<>(tenantCapabilityCounts(tenantId));
        if(foodService)counts.merge("FOOD_SERVICE",1,Integer::sum);
        List<CapabilityPrice> prices=jdbc.query("select capability,monthly_price_per_store from tenant_subscription_capability_price_snapshots where subscription_id=? order by capability",(rs,row)->new CapabilityPrice(CommercialCapability.valueOf(rs.getString(1)),CapabilityInclusionType.PAID_ADD_ON,BillingUnit.PER_STORE,rs.getBigDecimal(2)),subscription.id());
        return preview(subscription.currency(),subscription.merchantBasePrice(),subscription.includedStoresSnapshot(),subscription.additionalStorePriceSnapshot(),subscription.currentBillableStores()+Math.max(0,additionalStores),counts,prices);
    }

    private PricingPreview preview(String currency,BigDecimal base,Integer included,BigDecimal additionalRate,int stores,Map<String,Integer> counts,List<CapabilityPrice> prices){
        int includedCount=included==null?stores:included;int additional=Math.max(0,stores-includedCount);BigDecimal storeTotal=value(additionalRate,BigDecimal.ZERO).multiply(BigDecimal.valueOf(additional));
        List<CapabilityCharge> charges=prices.stream().map(price->{int count=counts.getOrDefault(price.capability().name(),0);return new CapabilityCharge(price.capability(),capabilityDescription(price.capability().name()),count,price.monthlyPricePerStore(),money(price.monthlyPricePerStore().multiply(BigDecimal.valueOf(count))));}).toList();
        BigDecimal total=value(base,BigDecimal.ZERO).add(storeTotal).add(charges.stream().map(CapabilityCharge::monthlyTotal).reduce(BigDecimal.ZERO,BigDecimal::add));
        return new PricingPreview(currency,money(value(base,BigDecimal.ZERO)),stores,includedCount,additional,money(storeTotal),charges,money(total));
    }

    @Transactional
    public SubscriptionResponse subscriptionAction(UUID tenantId, SubscriptionActionRequest request, Authentication authentication) {
        SubscriptionResponse before = subscription(tenantId);
        UUID actor = platformActor(authentication);
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        AuditAction auditAction;
        switch (action) {
            case "PAUSE" -> { jdbc.update("update tenant_subscriptions set status='PAUSED',updated_at=now(),version=version+1 where tenant_id=?", tenantId); auditAction = AuditAction.MERCHANT_SUBSCRIPTION_PAUSED; }
            case "RESUME" -> { jdbc.update("update tenant_subscriptions set status='ACTIVE',next_billing_date=greatest(next_billing_date,current_date),updated_at=now(),version=version+1 where tenant_id=?", tenantId); auditAction = AuditAction.MERCHANT_SUBSCRIPTION_RESUMED; }
            case "CANCEL" -> {
                jdbc.update(request.atPeriodEnd()
                                ? "update tenant_subscriptions set cancel_at_period_end=true,cancel_requested_at=now(),cancellation_reason=?,cancelled_by=?,updated_at=now(),version=version+1 where tenant_id=?"
                                : "update tenant_subscriptions set status='CANCELLED',cancelled_at=now(),cancel_requested_at=now(),cancellation_reason=?,cancelled_by=?,updated_at=now(),version=version+1 where tenant_id=?",
                        clean(request.reason()), actor, tenantId);
                auditAction = AuditAction.MERCHANT_SUBSCRIPTION_CANCELLED;
            }
            default -> throw new BadRequestException("Unsupported subscription action");
        }
        SubscriptionResponse after = subscription(tenantId);
        audit(actor, auditAction, "MERCHANT_SUBSCRIPTION", after.id(), before, after, request.reason());
        log.info("billing_event event=SUBSCRIPTION_STATUS_CHANGED tenant_id={} subscription_public_id={} status={}", tenantId, after.id(), after.status());
        return after;
    }

    @Transactional
    public InvoiceResponse generateInvoice(UUID tenantId, InvoiceGenerateRequest request, Authentication authentication) {
        return generateInvoice(tenantId, request, authentication == null ? null : platformActor(authentication));
    }

    @Transactional
    public InvoiceResponse generateInvoice(UUID tenantId, InvoiceGenerateRequest request, UUID actor) {
        SubscriptionResponse subscription = subscription(tenantId);
        if (!List.of("ACTIVE", "PAST_DUE").contains(subscription.status())) throw new ConflictException("Subscription is not billable");
        LocalDate start = request.periodStart() == null ? subscription.currentPeriodStart() : request.periodStart();
        LocalDate end = request.periodEnd() == null ? subscription.currentPeriodEnd() : request.periodEnd();
        if (start == null || end == null || end.isBefore(start)) throw new BadRequestException("A valid billing period is required");
        Map<String, Object> usage = usage(tenantId);
        Map<String, Object> tax = tax(tenantId, start);
        BigDecimal base = subscription.merchantBasePrice();
        boolean includeOnboardingFee = subscription.onboardingFeeInvoicedAt() == null
                && subscription.onboardingFeeSnapshot() != null && subscription.onboardingFeeSnapshot().signum() > 0;
        List<SubscriptionBillingService.CapabilityUsage> capabilityUsage = capabilityUsage(subscription.id(), tenantId);
        SubscriptionBillingService.Calculation calculation = calculator.calculate(new SubscriptionBillingService.Input(
                subscription.planName(), base, subscription.includedStoresSnapshot(), null, null,
                subscription.additionalStorePriceSnapshot(), null, null,
                number(usage.get("stores")), number(usage.get("registers")), number(usage.get("users")),
                subscription.discountType(), subscription.discountValue(), (BigDecimal) tax.get("rate"),
                subscription.onboardingFeeSnapshot(), includeOnboardingFee, capabilityUsage));
        int billableStores = number(usage.get("stores"));
        int additionalStores = Math.max(0, billableStores - (subscription.includedStoresSnapshot() == null ? 0 : subscription.includedStoresSnapshot()));
        log.info("billing_event event=MONTHLY_SUBSCRIPTION_CALCULATED tenant_id={} subscription_public_id={} plan_code={} billable_store_count={} additional_store_count={}",
                tenantId, subscription.id(), subscription.planCode(), billableStores, additionalStores);
        if (additionalStores > 0) {
            log.info("billing_event event=ADDITIONAL_STORE_CHARGE_CALCULATED tenant_id={} subscription_public_id={} additional_store_count={}",
                    tenantId, subscription.id(), additionalStores);
        }
        Map<String, Object> merchant = merchant(tenantId);
        BillingSettingsResponse settings = settings();
        int terms = subscription.paymentTermsDays() == null ? settings.defaultPaymentTermsDays() : subscription.paymentTermsDays();
        UUID invoiceId = UUID.randomUUID();
        String invoiceNumber = nextInvoiceNumber(settings.invoicePrefix());
        log.info("billing_event event=SUBSCRIPTION_INVOICE_GENERATION_STARTED tenant_id={} subscription_public_id={} period_start={} period_end={}", tenantId, subscription.id(), start, end);
        try {
            jdbc.update("""
                    insert into platform_invoices(id,invoice_number,tenant_id,subscription_id,pricing_plan_id,billing_period_start,billing_period_end,
                      issue_date,due_date,currency_code,subtotal,discount_total,tax_total,total,amount_paid,amount_outstanding,status,
                      merchant_business_name_snapshot,merchant_billing_email_snapshot,merchant_billing_address_snapshot,tax_label_snapshot,
                      tax_rate_snapshot,tax_registration_number_snapshot,notes,issued_at)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,'ISSUED',?,?,?,?,?,?,?,now())
                    """, invoiceId, invoiceNumber, tenantId, subscription.id(), subscription.pricingPlanId(), Date.valueOf(start), Date.valueOf(end), Date.valueOf(LocalDate.now()),
                    Date.valueOf(LocalDate.now().plusDays(terms)), subscription.currency(), calculation.subtotal(), calculation.discount(), calculation.tax(), calculation.total(),
                    calculation.total(), merchant.get("name"), merchant.get("email"), merchant.get("address"), tax.get("label"), tax.get("rate"), tax.get("registration"), clean(request.notes()));
        } catch (DuplicateKeyException duplicate) {
            log.info("billing_event event=SUBSCRIPTION_INVOICE_ALREADY_EXISTS tenant_id={} subscription_public_id={} period_start={} period_end={}", tenantId, subscription.id(), start, end);
            return invoiceForPeriod(subscription.id(), start, end);
        }
        for (SubscriptionBillingService.CalculatedLine line : calculation.lines()) {
            jdbc.update("insert into platform_invoice_lines(id,invoice_id,line_type,description,quantity,unit_price,line_subtotal,line_total) values (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), invoiceId, line.lineType(), line.description(), line.quantity(), line.unitPrice(), line.lineSubtotal(), line.lineSubtotal());
        }
        if (includeOnboardingFee) {
            jdbc.update("update tenant_subscriptions set onboarding_fee_invoiced_at=now(),onboarding_fee_invoice_id=? where id=? and onboarding_fee_invoiced_at is null",
                    invoiceId, subscription.id());
            log.info("billing_event event=ONBOARDING_FEE_BILLED tenant_id={} subscription_public_id={} invoice_public_id={}", tenantId, subscription.id(), invoiceId);
            audit(actor, AuditAction.MERCHANT_ONBOARDING_FEE_INVOICED, "MERCHANT_SUBSCRIPTION", subscription.id(), null,
                    Map.of("tenantId", tenantId, "invoiceId", invoiceId), "One-time onboarding fee invoiced");
        }
        if (calculation.discount().signum() > 0) {
            jdbc.update("insert into platform_invoice_lines(id,invoice_id,line_type,description,quantity,unit_price,discount_amount,line_subtotal,line_total) values (?,?,'DISCOUNT',?,1,0,?,0,0)",
                    UUID.randomUUID(), invoiceId, subscription.discountName() == null ? "Subscription discount" : subscription.discountName(), calculation.discount());
        }
        jdbc.update("""
                update tenant_subscriptions set current_period_start=?,current_period_end=?,next_billing_date=?,renews_at=?,updated_at=now(),version=version+1 where id=?
                """, Date.valueOf(nextPeriod(start, subscription.billingInterval())), Date.valueOf(nextPeriod(nextPeriod(start, subscription.billingInterval()), subscription.billingInterval()).minusDays(1)),
                Date.valueOf(nextPeriod(start, subscription.billingInterval())), timestamp(nextPeriod(start, subscription.billingInterval())), subscription.id());
        InvoiceResponse invoice = invoice(invoiceId);
        audit(actor, AuditAction.PLATFORM_INVOICE_GENERATED, "PLATFORM_INVOICE", invoiceId, null, invoice, invoiceNumber);
        log.info("billing_event event=SUBSCRIPTION_INVOICE_GENERATED tenant_id={} subscription_public_id={} invoice_public_id={} invoice_number={}", tenantId, subscription.id(), invoiceId, invoiceNumber);
        return invoice;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> invoices(UUID tenantId, String status, String search, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100), safePage = Math.max(page, 0);
        String where = " where (?::uuid is null or i.tenant_id=?::uuid) and (? is null or i.status=?) and (? is null or lower(i.invoice_number||' '||i.merchant_business_name_snapshot) like '%'||lower(?)||'%') ";
        Object tenant = tenantId == null ? null : tenantId.toString();
        String normalizedStatus = clean(status), normalizedSearch = clean(search);
        List<InvoiceResponse> content = jdbc.query("select i.*,p.code plan_code from platform_invoices i left join platform_pricing_plans p on p.id=i.pricing_plan_id" + where + "order by i.issue_date desc,i.invoice_number desc limit ? offset ?",
                PlatformBillingService::invoice, tenant, tenant, normalizedStatus, normalizedStatus, normalizedSearch, normalizedSearch, safeSize, safePage * safeSize);
        long total = jdbc.queryForObject("select count(*) from platform_invoices i" + where, Long.class, tenant, tenant, normalizedStatus, normalizedStatus, normalizedSearch, normalizedSearch);
        return new Page<>(content, safePage, safeSize, total, pages(total, safeSize));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse invoice(UUID id) {
        InvoiceResponse base = jdbc.query("select i.*,p.code plan_code from platform_invoices i left join platform_pricing_plans p on p.id=i.pricing_plan_id where i.id=?",
                PlatformBillingService::invoice, id).stream().findFirst().orElseThrow(() -> new NotFoundException("Platform invoice not found"));
        return withLines(base);
    }

    @Transactional
    public InvoiceResponse recordPayment(UUID invoiceId, PaymentRequest request, Authentication authentication) {
        InvoiceResponse before = invoice(invoiceId);
        if (List.of("VOID", "CANCELLED", "PAID").contains(before.status())) throw new ConflictException("Invoice does not accept payments");
        if (request.amount().compareTo(before.amountOutstanding()) > 0) throw new BadRequestException("Payment cannot exceed outstanding balance");
        UUID actor = platformActor(authentication);
        jdbc.update("insert into platform_invoice_payments(id,invoice_id,amount,payment_date,payment_method,reference,notes,recorded_by) values (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), invoiceId, money(request.amount()), Date.valueOf(request.paymentDate()), paymentMethod(request.paymentMethod()), clean(request.reference()), clean(request.notes()), actor);
        BigDecimal paid = money(before.amountPaid().add(request.amount()));
        BigDecimal outstanding = money(before.total().subtract(paid));
        String status = outstanding.signum() == 0 ? "PAID" : "PARTIALLY_PAID";
        jdbc.update("update platform_invoices set amount_paid=?,amount_outstanding=?,status=?,paid_at=case when ?='PAID' then now() else null end,updated_at=now(),version=version+1 where id=?",
                paid, outstanding, status, status, invoiceId);
        InvoiceResponse after = invoice(invoiceId);
        audit(actor, AuditAction.PLATFORM_INVOICE_PAYMENT_RECORDED, "PLATFORM_INVOICE", invoiceId, before, after, request.reference());
        log.info("billing_event event=SUBSCRIPTION_PAYMENT_RECORDED tenant_id={} subscription_public_id={} invoice_public_id={} invoice_number={}", after.tenantId(), after.subscriptionId(), after.id(), after.invoiceNumber());
        return after;
    }

    @Transactional
    public InvoiceResponse voidInvoice(UUID invoiceId, String reason, Authentication authentication) {
        InvoiceResponse before = invoice(invoiceId);
        if (before.amountPaid().signum() > 0) throw new ConflictException("Paid invoices cannot be voided");
        UUID actor = platformActor(authentication);
        jdbc.update("update platform_invoices set status='VOID',voided_at=now(),notes=coalesce(?,notes),updated_at=now(),version=version+1 where id=?", clean(reason), invoiceId);
        InvoiceResponse after = invoice(invoiceId);
        audit(actor, AuditAction.PLATFORM_INVOICE_VOIDED, "PLATFORM_INVOICE", invoiceId, before, after, reason);
        return after;
    }

    @Transactional(readOnly = true)
    public BillingSettingsResponse settings() {
        return jdbc.query("select * from platform_billing_settings where id=?", (rs, row) -> settings(rs, row), SETTINGS_ID).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Platform billing settings not found"));
    }

    @Transactional
    public BillingSettingsResponse updateSettings(BillingSettingsRequest request, Authentication authentication) {
        BillingSettingsResponse before = settings();
        jdbc.update("""
                update platform_billing_settings set legal_name=?,billing_address=?,support_email=?,invoice_sender_email=?,default_currency=?,
                  default_payment_terms_days=?,invoice_prefix=?,tax_registration_number=?,default_tax_rule_id=?,invoice_footer=?,payment_instructions=?,
                  billing_enforcement_enabled=?,updated_at=now(),version=version+1 where id=?
                """, clean(request.legalName()), clean(request.billingAddress()), clean(request.supportEmail()), clean(request.invoiceSenderEmail()),
                request.defaultCurrency().toUpperCase(Locale.ROOT), request.defaultPaymentTermsDays(), code(request.invoicePrefix()), clean(request.taxRegistrationNumber()),
                request.defaultTaxRuleId(), clean(request.invoiceFooter()), clean(request.paymentInstructions()), request.billingEnforcementEnabled(), SETTINGS_ID);
        BillingSettingsResponse after = settings();
        audit(platformActor(authentication), AuditAction.PRICING_PLAN_UPDATED, "PLATFORM_BILLING_SETTINGS", SETTINGS_ID, before, after, "Billing settings updated");
        return after;
    }

    @Transactional
    public void markPastDue() {
        List<UUID> ids = jdbc.query("select id from platform_invoices where status in ('ISSUED','SENT','PARTIALLY_PAID') and due_date < current_date and amount_outstanding > 0",
                (rs, row) -> rs.getObject(1, UUID.class));
        for (UUID id : ids) {
            InvoiceResponse before = invoice(id);
            jdbc.update("update platform_invoices set status='PAST_DUE',updated_at=now(),version=version+1 where id=?", id);
            audit(null, AuditAction.PLATFORM_INVOICE_MARKED_PAST_DUE, "PLATFORM_INVOICE", id, before, invoice(id), "Payment due date passed");
        }
    }

    @Transactional(readOnly = true)
    public UUID tenantFor(Authentication authentication) {
        User user = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow(() -> new NotFoundException("Tenant user not found"));
        if (user.getTenantId() == null) throw new NotFoundException("Tenant context not found");
        return user.getTenantId();
    }

    private InvoiceResponse invoiceForPeriod(UUID subscriptionId, LocalDate start, LocalDate end) {
        UUID id = jdbc.queryForObject("select id from platform_invoices where subscription_id=? and billing_period_start=? and billing_period_end=?", UUID.class, subscriptionId, Date.valueOf(start), Date.valueOf(end));
        return invoice(id);
    }

    private InvoiceResponse withLines(InvoiceResponse invoice) {
        List<InvoiceLine> lines = jdbc.query("select * from platform_invoice_lines where invoice_id=? order by created_at,id", (rs, row) -> new InvoiceLine(
                rs.getObject("id", UUID.class), rs.getString("line_type"), rs.getString("description"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_price"), rs.getBigDecimal("discount_amount"), rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("line_subtotal"), rs.getBigDecimal("line_total")), invoice.id());
        return new InvoiceResponse(invoice.id(), invoice.invoiceNumber(), invoice.tenantId(), invoice.merchantName(), invoice.subscriptionId(), invoice.pricingPlanId(),
                invoice.planCode(), invoice.billingPeriodStart(), invoice.billingPeriodEnd(), invoice.issueDate(), invoice.dueDate(), invoice.currency(),
                invoice.subtotal(), invoice.discountTotal(), invoice.taxTotal(), invoice.total(), invoice.amountPaid(), invoice.amountOutstanding(), invoice.status(),
                invoice.billingEmail(), invoice.billingAddress(), invoice.taxLabel(), invoice.taxRate(), invoice.notes(), invoice.issuedAt(), invoice.sentAt(),
                invoice.paidAt(), invoice.voidedAt(), lines);
    }

    private PlanResponse plan(UUID id) {
        PlanResponse response = jdbc.query("select p.*,(select count(*) from tenant_subscriptions s where s.pricing_plan_id=p.id and s.status in ('TRIAL','ACTIVE','PAST_DUE')) active_merchants from platform_pricing_plans p where p.id=?",
                PlatformBillingService::plan, id).stream().findFirst().orElseThrow(() -> new NotFoundException("Pricing plan not found"));
        return withCapabilityPrices(response);
    }

    private PlanResponse withCapabilityPrices(PlanResponse plan) {
        List<CapabilityPrice> prices = jdbc.query("""
                select c.capability,c.inclusion_type,c.billing_unit,c.unit_price
                from platform_pricing_plan_version_capabilities c
                join platform_pricing_plan_versions v on v.id=c.pricing_plan_version_id
                where v.id=(select id from platform_pricing_plan_versions where pricing_plan_id=? and status='ACTIVE' order by version_number desc limit 1)
                order by c.capability
                """,(rs,row)->new CapabilityPrice(CommercialCapability.valueOf(rs.getString(1)),CapabilityInclusionType.valueOf(rs.getString(2)),rs.getString(3)==null?null:BillingUnit.valueOf(rs.getString(3)),rs.getBigDecimal(4)),plan.id());
        if(prices.isEmpty())prices=jdbc.query("select capability,monthly_price_per_store from platform_pricing_plan_capability_prices where pricing_plan_id=? order by capability",
                (rs,row)->new CapabilityPrice(CommercialCapability.valueOf(rs.getString(1)),CapabilityInclusionType.PAID_ADD_ON,BillingUnit.PER_STORE,rs.getBigDecimal(2)),plan.id());
        return new PlanResponse(plan.id(),plan.code(),plan.name(),plan.description(),plan.status(),plan.billingInterval(),plan.basePrice(),plan.oneTimeOnboardingFee(),plan.currency(),plan.trialDays(),plan.includedStores(),plan.includedRegisters(),plan.includedUsers(),plan.additionalStorePrice(),plan.additionalRegisterPrice(),plan.additionalUserPrice(),plan.taxBehavior(),prices,plan.effectiveFrom(),plan.effectiveTo(),plan.activeMerchants(),plan.createdAt(),plan.updatedAt(),plan.version());
    }

    private void replacePlanCapabilityPrices(UUID planId, List<CapabilityPrice> prices) {
        jdbc.update("delete from platform_pricing_plan_capability_prices where pricing_plan_id=?",planId);
        if(prices==null)return;
        prices.stream().filter(price->normalizeInclusion(price)==CapabilityInclusionType.PAID_ADD_ON).forEach(price->jdbc.update("insert into platform_pricing_plan_capability_prices(pricing_plan_id,capability,monthly_price_per_store) values (?,?,?)",planId,price.capability().name(),price.monthlyPricePerStore()));
    }

    private void replaceSubscriptionCapabilitySnapshots(UUID subscriptionId, List<CapabilityPrice> prices) {
        jdbc.update("delete from tenant_subscription_capability_price_snapshots where subscription_id=?",subscriptionId);
        prices.stream().filter(price->normalizeInclusion(price)==CapabilityInclusionType.PAID_ADD_ON).forEach(price->jdbc.update("insert into tenant_subscription_capability_price_snapshots(subscription_id,capability,monthly_price_per_store) values (?,?,?)",subscriptionId,price.capability().name(),price.monthlyPricePerStore()));
    }

    private Map<String,Integer> tenantCapabilityCounts(UUID tenantId) {
        return jdbc.query("select capability,count(*) quantity from store_capabilities sc join stores s on s.id=sc.store_id where s.tenant_id=? and s.active=true group by capability",
                rs->{Map<String,Integer> counts=new HashMap<>();while(rs.next())counts.put(rs.getString(1),rs.getInt(2));return counts;},tenantId);
    }

    private List<SubscriptionBillingService.CapabilityUsage> capabilityUsage(UUID subscriptionId, UUID tenantId) {
        return jdbc.query("select capability,billing_unit_snapshot,coalesce(custom_unit_price,unit_price_snapshot) unit_price from tenant_subscription_capabilities where subscription_id=? and status='ACTIVE' and inclusion_type_snapshot='PAID_ADD_ON' order by capability",
                (rs,row)->new SubscriptionBillingService.CapabilityUsage(rs.getString(1),capabilityDescription(rs.getString(1)),billingQuantity(tenantId,rs.getString(1),rs.getString(2)),rs.getBigDecimal(3)),subscriptionId);
    }

    private SubscriptionResponse withCapabilityCharges(SubscriptionResponse value, Map<String,Integer> counts) {
        List<CapabilityCharge> charges=jdbc.query("select capability,billing_unit_snapshot,coalesce(custom_unit_price,unit_price_snapshot) unit_price from tenant_subscription_capabilities where subscription_id=? and status='ACTIVE' and inclusion_type_snapshot='PAID_ADD_ON' order by capability",
                (rs,row)->{var capability=CommercialCapability.valueOf(rs.getString(1));int count=billingQuantity(value.tenantId(),capability.name(),rs.getString(2));BigDecimal rate=rs.getBigDecimal(3);return new CapabilityCharge(capability,capabilityDescription(capability.name()),count,rate,money(rate.multiply(BigDecimal.valueOf(count))));},value.id());
        BigDecimal total=value.estimatedMonthlyPrice().add(charges.stream().map(CapabilityCharge::monthlyTotal).reduce(BigDecimal.ZERO,BigDecimal::add));
        return new SubscriptionResponse(value.id(),value.tenantId(),value.merchantName(),value.pricingPlanId(),value.planCode(),value.planName(),value.status(),value.billingInterval(),value.subscriptionStartDate(),value.currentPeriodStart(),value.currentPeriodEnd(),value.nextBillingDate(),value.trialEndDate(),value.cancelAtPeriodEnd(),value.cancelledAt(),value.cancellationReason(),value.standardBasePrice(),value.merchantBasePrice(),value.currency(),value.includedStoresSnapshot(),value.additionalStorePriceSnapshot(),value.onboardingFeeSnapshot(),value.onboardingFeeInvoicedAt(),value.currentBillableStores(),value.additionalBillableStores(),money(total),charges,value.customAdditionalStorePrice(),value.customAdditionalRegisterPrice(),value.customAdditionalUserPrice(),value.discountName(),value.discountType(),value.discountValue(),value.pricingNotes(),value.paymentTermsDays(),value.version());
    }

    private static String capabilityDescription(String capability){return switch(capability){case "FOOD_SERVICE"->"Food Service add-on";default->capability.replace('_',' ')+" add-on";};}
    private int billingQuantity(UUID tenantId,String capability,String unit){return switch(BillingUnit.valueOf(unit)){case PER_MERCHANT->1;case PER_STORE->"FOOD_SERVICE".equals(capability)?number(jdbc.queryForMap("select count(*) quantity from stores s join store_capabilities c on c.store_id=s.id where s.tenant_id=? and s.active=true and c.capability='FOOD_SERVICE'",tenantId).get("quantity")):"LOTTERY".equals(capability)?number(jdbc.queryForMap("select count(distinct policy.store_id) quantity from lottery_payout_policies policy join stores s on s.id=policy.store_id where s.tenant_id=? and s.active=true and policy.active=true",tenantId).get("quantity")):number(jdbc.queryForMap("select count(*) quantity from stores where tenant_id=? and active=true",tenantId).get("quantity"));case PER_REGISTER->number(jdbc.queryForMap("select count(*) quantity from registers r join stores s on s.id=r.store_id where s.tenant_id=? and r.active=true",tenantId).get("quantity"));};}
    private static CapabilityInclusionType normalizeInclusion(CapabilityPrice price){return price.inclusionType()==null?CapabilityInclusionType.PAID_ADD_ON:price.inclusionType();}

    private void snapshotPlan(UUID id, int version, PlanRequest request, UUID actor) {
        jdbc.update("update platform_pricing_plan_versions set status='SUPERSEDED',effective_to=? where pricing_plan_id=? and status='ACTIVE'",timestamp(request.effectiveFrom().minusDays(1)),id);
        insertPricingVersion(UUID.randomUUID(),id,version,request,request.effectiveFrom(),"ACTIVE","NEW_SUBSCRIPTIONS_ONLY",actor);
    }

    private void insertPricingVersion(UUID versionId,UUID planId,int number,PlanRequest request,LocalDate effective,String status,String policy,UUID actor) {
        final String snapshot;
        try { snapshot=objectMapper.writeValueAsString(request); } catch(JsonProcessingException exception){throw new BadRequestException("Pricing plan snapshot could not be created");}
        jdbc.update("""
                insert into platform_pricing_plan_versions(id,pricing_plan_id,version_number,snapshot,effective_from,created_by,currency_code,billing_interval,base_price,included_stores,additional_store_price,one_time_onboarding_fee,trial_days,status,subscriber_policy,activated_at)
                values (?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?,?,?)
                """,versionId,planId,number,snapshot,timestamp(effective),actor,request.currency().toUpperCase(Locale.ROOT),interval(request.billingInterval()),request.basePrice(),request.includedStores(),request.additionalStorePrice(),request.oneTimeOnboardingFee(),request.trialDays(),status,policy,"ACTIVE".equals(status)?Timestamp.from(Instant.now()):null);
        List<CapabilityPrice> capabilities=request.capabilityPrices()==null?List.of():request.capabilityPrices();
        capabilities.forEach(capability->jdbc.update("insert into platform_pricing_plan_version_capabilities(id,pricing_plan_version_id,capability,inclusion_type,billing_unit,unit_price) values (?,?,?,?,?,?)",UUID.randomUUID(),versionId,capability.capability().name(),normalizeInclusion(capability).name(),capability.billingUnit()==null?null:capability.billingUnit().name(),capability.monthlyPricePerStore()));
    }

    private PricingVersionResponse pricingVersion(UUID id){return jdbc.query("select v.*,p.code,p.name,p.description,p.status plan_status,p.tax_behavior from platform_pricing_plan_versions v join platform_pricing_plans p on p.id=v.pricing_plan_id where v.id=?",(rs,row)->pricingVersion(rs,row),id).stream().findFirst().orElseThrow(()->new NotFoundException("PRICING_PLAN_NOT_FOUND"));}
    private PricingVersionResponse pricingVersion(ResultSet rs,int row)throws SQLException{
        UUID id=rs.getObject("id",UUID.class);List<CapabilityPrice> capabilities=jdbc.query("select capability,inclusion_type,billing_unit,unit_price from platform_pricing_plan_version_capabilities where pricing_plan_version_id=? order by capability",(values,index)->new CapabilityPrice(CommercialCapability.valueOf(values.getString(1)),CapabilityInclusionType.valueOf(values.getString(2)),values.getString(3)==null?null:BillingUnit.valueOf(values.getString(3)),values.getBigDecimal(4)),id);
        PlanRequest pricing=new PlanRequest(rs.getString("code"),rs.getString("name"),rs.getString("description"),rs.getString("plan_status"),rs.getString("billing_interval"),rs.getBigDecimal("base_price"),rs.getBigDecimal("one_time_onboarding_fee"),rs.getString("currency_code"),rs.getInt("trial_days"),integer(rs,"included_stores"),null,null,rs.getBigDecimal("additional_store_price"),null,null,capabilities,rs.getString("tax_behavior"),localDate(rs,"effective_from"),localDate(rs,"effective_to"));
        Integer used=jdbc.queryForObject("select count(*) from platform_invoices where subscription_id in(select id from tenant_subscriptions where pricing_plan_version_id=?)",Integer.class,id);
        return new PricingVersionResponse(id,rs.getObject("pricing_plan_id",UUID.class),rs.getInt("version_number"),rs.getString("status"),localDate(rs,"effective_from"),localDate(rs,"effective_to"),rs.getString("subscriber_policy"),pricing,used!=null&&used>0,instant(rs,"created_at"),rs.getLong("version"));
    }

    private void activatePricingVersion(UUID versionId){
        PricingVersionResponse version=pricingVersion(versionId);PlanRequest price=version.pricing();
        jdbc.update("update platform_pricing_plan_versions set status='SUPERSEDED',effective_to=? where pricing_plan_id=? and status='ACTIVE' and id<>?",timestamp(version.effectiveFrom().minusDays(1)),version.pricingPlanId(),versionId);
        jdbc.update("update platform_pricing_plan_versions set status='ACTIVE',activated_at=now(),version=version+1 where id=?",versionId);
        jdbc.update("update platform_pricing_plans set billing_interval=?,base_price=?,one_time_onboarding_fee=?,currency_code=?,trial_days=?,included_stores=?,additional_store_price=?,effective_from=?,updated_at=now(),version=version+1 where id=?",price.billingInterval(),price.basePrice(),price.oneTimeOnboardingFee(),price.currency(),price.trialDays(),price.includedStores(),price.additionalStorePrice(),Date.valueOf(version.effectiveFrom()),version.pricingPlanId());
        replacePlanCapabilityPrices(version.pricingPlanId(),price.capabilityPrices());
    }

    private void syncSubscriptionEntitlements(UUID subscriptionId,List<CapabilityPrice> capabilities,LocalDate effective){
        List<CapabilityPrice> configured=capabilities==null?List.of():capabilities;
        Set<CommercialCapability> offered=configured.stream()
                .filter(value->normalizeInclusion(value)!=CapabilityInclusionType.NOT_AVAILABLE).map(CapabilityPrice::capability).collect(java.util.stream.Collectors.toSet());
        for(CommercialCapability capability:CommercialCapability.values())if(!offered.contains(capability))jdbc.update("update tenant_subscription_capabilities set status='INACTIVE',effective_to=?,updated_at=now(),version=version+1 where subscription_id=? and capability=? and status='ACTIVE'",Date.valueOf(effective),subscriptionId,capability.name());
        configured.stream().filter(value->normalizeInclusion(value)!=CapabilityInclusionType.NOT_AVAILABLE).forEach(value->jdbc.update("""
                insert into tenant_subscription_capabilities(id,subscription_id,capability,status,inclusion_type_snapshot,billing_unit_snapshot,unit_price_snapshot,effective_from)
                values (?,?,?,?,?,?,?,?) on conflict(subscription_id,capability) do update set inclusion_type_snapshot=excluded.inclusion_type_snapshot,billing_unit_snapshot=excluded.billing_unit_snapshot,unit_price_snapshot=excluded.unit_price_snapshot,effective_from=excluded.effective_from,updated_at=now(),version=tenant_subscription_capabilities.version+1
                """,UUID.randomUUID(),subscriptionId,value.capability().name(),normalizeInclusion(value)==CapabilityInclusionType.INCLUDED?"ACTIVE":"INACTIVE",normalizeInclusion(value).name(),value.billingUnit()==null?null:value.billingUnit().name(),value.monthlyPricePerStore(),Date.valueOf(effective)));
    }

    private static CapabilityDefinition definition(CommercialCapability capability,BillingUnit... units){return new CapabilityDefinition(capability,capability.name().replace('_',' '),List.of(units));}
    private LocalDate effectiveDate(UUID planId,PricingVersionRequest request){
        LocalDate value=request.effectiveDate();
        if("NEXT_BILLING_CYCLE".equalsIgnoreCase(request.effectivePolicy()))value=jdbc.query("select min(next_billing_date) from tenant_subscriptions where pricing_plan_id=? and status in ('TRIAL','ACTIVE','PAST_DUE')",rs->{rs.next();Date date=rs.getDate(1);return date==null?LocalDate.now().plusDays(1):date.toLocalDate();},planId);
        if(value==null||!value.isAfter(LocalDate.now()))throw new BadRequestException("PRICING_PLAN_EFFECTIVE_DATE_INVALID");return value;
    }
    private void requireCapabilityRemovalConfirmation(UUID planId,List<CapabilityPrice> current,List<CapabilityPrice> proposed,boolean confirmed){
        if(confirmed)return;
        Map<CommercialCapability,CapabilityInclusionType> next=(proposed==null?List.<CapabilityPrice>of():proposed).stream().collect(java.util.stream.Collectors.toMap(CapabilityPrice::capability,PlatformBillingService::normalizeInclusion));
        for(CapabilityPrice capability:current){
            if(normalizeInclusion(capability)==CapabilityInclusionType.NOT_AVAILABLE||next.getOrDefault(capability.capability(),CapabilityInclusionType.NOT_AVAILABLE)!=CapabilityInclusionType.NOT_AVAILABLE)continue;
            Integer active=jdbc.queryForObject("select count(*) from tenant_subscription_capabilities c join tenant_subscriptions s on s.id=c.subscription_id where s.pricing_plan_id=? and c.capability=? and c.status='ACTIVE'",Integer.class,planId,capability.capability().name());
            if(active!=null&&active>0)throw new ConflictException(active+" active merchant subscriptions currently use "+capability.capability().name()+"; confirmation is required");
        }
    }
    private static String subscriberPolicy(String value){return allowed(value,List.of("NEW_SUBSCRIPTIONS_ONLY","APPLY_NEXT_BILLING_CYCLE"),"subscriber policy");}
    private static void validateCapabilities(List<CapabilityPrice> capabilities){Set<CommercialCapability> seen=EnumSet.noneOf(CommercialCapability.class);for(CapabilityPrice value:capabilities==null?List.<CapabilityPrice>of():capabilities){if(!seen.add(value.capability()))throw new BadRequestException("PRICING_PLAN_CAPABILITY_DUPLICATE");CapabilityInclusionType type=normalizeInclusion(value);if(type==CapabilityInclusionType.PAID_ADD_ON&&(value.billingUnit()==null||value.monthlyPricePerStore()==null))throw new BadRequestException("Paid add-on requires price and billing unit");if(type!=CapabilityInclusionType.PAID_ADD_ON&&value.monthlyPricePerStore()!=null&&value.monthlyPricePerStore().signum()!=0)throw new BadRequestException("Included or unavailable capability cannot have a separate price");}}

    private Map<String, Object> merchant(UUID tenantId) {
        return jdbc.queryForMap("""
                select t.display_name name,coalesce(c.billing_email,m.contact_email) email,
                  coalesce(concat_ws(', ',c.address_line1,c.address_line2,c.city,c.province_state,c.postal_code,c.country_code),m.billing_address) address
                from tenants t join merchant_profiles m on m.tenant_id=t.id left join merchant_billing_contacts c on c.tenant_id=t.id where t.id=?
                """, tenantId);
    }

    private Map<String, Object> usage(UUID tenantId) {
        return jdbc.queryForMap("""
                select (select count(*) from stores where tenant_id=? and active=true) stores,
                       (select count(*) from registers r join stores s on s.id=r.store_id where s.tenant_id=? and r.active=true) registers,
                       (select count(*) from security_users where tenant_id=? and enabled=true) users
                """, tenantId, tenantId, tenantId);
    }

    private Map<String, Object> tax(UUID tenantId, LocalDate date) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select r.label,r.rate,r.registration_number registration from merchant_billing_contacts c
                join platform_billing_tax_rules r on r.id=c.tax_rule_id
                where c.tenant_id=? and r.active=true and r.effective_from<=? and (r.effective_to is null or r.effective_to>=?)
                """, tenantId, Date.valueOf(date), Date.valueOf(date));
        if (rows.isEmpty()) return Map.of("label", "Tax", "rate", BigDecimal.ZERO, "registration", "");
        return rows.getFirst();
    }

    private UUID platformActor(Authentication authentication) {
        return platformUsers.findByEmail(authentication.getName()).orElseThrow(() -> new NotFoundException("Platform actor not found")).id();
    }

    private String tenantName(UUID tenantId) {
        return jdbc.query("select display_name from tenants where id=?", (rs, row) -> rs.getString(1), tenantId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Merchant not found"));
    }

    private String nextInvoiceNumber(String prefix) {
        Long sequence = jdbc.queryForObject("select nextval('platform_invoice_number_seq')", Long.class);
        return "%s-%d-%06d".formatted(prefix, Year.now().getValue(), sequence);
    }

    private long count(String sql, Object... args) { Long value = jdbc.queryForObject(sql, Long.class, args); return value == null ? 0 : value; }
    private BigDecimal decimal(String sql, Object... args) { BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args); return value == null ? BigDecimal.ZERO : value; }

    private void audit(UUID actor, AuditAction action, String type, UUID id, Object before, Object after, String reason) {
        auditService.record(new CreateAuditRecordCommand(actor, action, type, id, null, null, before, after, reason));
    }

    private static PlanResponse plan(ResultSet rs, int row) throws SQLException {
        return new PlanResponse(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("description"),
                rs.getString("status"), rs.getString("billing_interval"), rs.getBigDecimal("base_price"), rs.getBigDecimal("one_time_onboarding_fee"), rs.getString("currency_code"),
                rs.getInt("trial_days"), integer(rs, "included_stores"), integer(rs, "included_registers"), integer(rs, "included_users"),
                rs.getBigDecimal("additional_store_price"), rs.getBigDecimal("additional_register_price"), rs.getBigDecimal("additional_user_price"),
                rs.getString("tax_behavior"), List.of(), rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
                rs.getLong("active_merchants"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
    }

    private static SubscriptionResponse subscription(ResultSet rs, int row) throws SQLException {
        int billableStores = rs.getInt("billable_store_count");
        Integer includedStores = integer(rs, "included_stores_snapshot");
        int additionalStores = Math.max(0, billableStores - (includedStores == null ? 0 : includedStores));
        BigDecimal base = rs.getBigDecimal("custom_base_price") == null ? rs.getBigDecimal("base_price_snapshot") : rs.getBigDecimal("custom_base_price");
        BigDecimal storeRate = rs.getBigDecimal("custom_additional_store_price") == null ? rs.getBigDecimal("additional_store_price_snapshot") : rs.getBigDecimal("custom_additional_store_price");
        BigDecimal estimated = money(value(base, BigDecimal.ZERO).add(value(storeRate, BigDecimal.ZERO).multiply(BigDecimal.valueOf(additionalStores))));
        return new SubscriptionResponse(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("merchant_name"),
                rs.getObject("pricing_plan_id", UUID.class), rs.getString("current_plan_code"), rs.getString("plan_name"), rs.getString("status"),
                rs.getString("billing_interval"), localDate(rs, "starts_at"), rs.getObject("current_period_start", LocalDate.class),
                rs.getObject("current_period_end", LocalDate.class), rs.getObject("next_billing_date", LocalDate.class), localDate(rs, "trial_ends_at"),
                rs.getBoolean("cancel_at_period_end"), instant(rs, "cancelled_at"), rs.getString("cancellation_reason"), rs.getBigDecimal("standard_base_price"),
                base, rs.getString("currency_code"), includedStores,
                rs.getBigDecimal("custom_additional_store_price") == null ? rs.getBigDecimal("additional_store_price_snapshot") : rs.getBigDecimal("custom_additional_store_price"),
                rs.getBigDecimal("custom_onboarding_fee") == null ? rs.getBigDecimal("onboarding_fee_snapshot") : rs.getBigDecimal("custom_onboarding_fee"),
                instant(rs, "onboarding_fee_invoiced_at"), billableStores, additionalStores, estimated, List.of(),
                rs.getBigDecimal("custom_additional_store_price"), rs.getBigDecimal("custom_additional_register_price"),
                rs.getBigDecimal("custom_additional_user_price"), rs.getString("discount_name"), rs.getString("discount_type"), rs.getBigDecimal("discount_value"),
                rs.getString("pricing_notes"), integer(rs, "payment_terms_days"), rs.getLong("version"));
    }

    private static InvoiceResponse invoice(ResultSet rs, int row) throws SQLException {
        return new InvoiceResponse(rs.getObject("id", UUID.class), rs.getString("invoice_number"), rs.getObject("tenant_id", UUID.class),
                rs.getString("merchant_business_name_snapshot"), rs.getObject("subscription_id", UUID.class), rs.getObject("pricing_plan_id", UUID.class),
                rs.getString("plan_code"), rs.getObject("billing_period_start", LocalDate.class), rs.getObject("billing_period_end", LocalDate.class),
                rs.getObject("issue_date", LocalDate.class), rs.getObject("due_date", LocalDate.class), rs.getString("currency_code"), rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("discount_total"), rs.getBigDecimal("tax_total"), rs.getBigDecimal("total"), rs.getBigDecimal("amount_paid"),
                rs.getBigDecimal("amount_outstanding"), rs.getString("status"), rs.getString("merchant_billing_email_snapshot"),
                rs.getString("merchant_billing_address_snapshot"), rs.getString("tax_label_snapshot"), rs.getBigDecimal("tax_rate_snapshot"), rs.getString("notes"),
                instant(rs, "issued_at"), instant(rs, "sent_at"), instant(rs, "paid_at"), instant(rs, "voided_at"), List.of());
    }

    private static BillingSettingsResponse settings(ResultSet rs, int row) throws SQLException {
        return new BillingSettingsResponse(rs.getObject("id", UUID.class), rs.getString("legal_name"), rs.getString("billing_address"),
                rs.getString("support_email"), rs.getString("invoice_sender_email"), rs.getString("default_currency"),
                rs.getInt("default_payment_terms_days"), rs.getString("invoice_prefix"), rs.getString("tax_registration_number"),
                rs.getObject("default_tax_rule_id", UUID.class), rs.getString("invoice_footer"), rs.getString("payment_instructions"),
                rs.getBoolean("billing_enforcement_enabled"), rs.getLong("version"));
    }

    private static void validatePlan(PlanRequest request) {
        status(request.status()); interval(request.billingInterval()); taxBehavior(request.taxBehavior());
        validateCapabilities(request.capabilityPrices());
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) throw new BadRequestException("Plan effective end date cannot precede start date");
    }
    private static void validateSubscription(SubscriptionRequest request, PlanResponse plan) {
        subscriptionStatus(request.status()); interval(request.billingInterval()); discountType(request.discountType());
        if (!plan.currency().matches("[A-Z]{3}")) throw new BadRequestException("Plan currency is invalid");
        if ("PERCENTAGE".equals(request.discountType()) && request.discountValue() != null && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) throw new BadRequestException("Percentage discount must be between 0 and 100");
    }
    private static String status(String value) { return allowed(value, List.of("DRAFT","ACTIVE","INACTIVE","ARCHIVED"), "plan status"); }
    private static String interval(String value) { return allowed(value, List.of("MONTHLY","YEARLY"), "billing interval"); }
    private static String taxBehavior(String value) { return allowed(value, List.of("EXCLUSIVE","INCLUSIVE","EXEMPT"), "tax behavior"); }
    private static String subscriptionStatus(String value) { return allowed(value, List.of("TRIAL","ACTIVE","PAST_DUE","PAUSED","CANCELLED","EXPIRED"), "subscription status"); }
    private static String discountType(String value) { return value == null || value.isBlank() ? null : allowed(value, List.of("PERCENTAGE","FIXED_AMOUNT"), "discount type"); }
    private static String paymentMethod(String value) { return allowed(value, List.of("E_TRANSFER","BANK_TRANSFER","CHEQUE","CASH","OTHER"), "payment method"); }
    private static String allowed(String value, List<String> values, String field) { String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT); if (!values.contains(normalized)) throw new BadRequestException("Invalid " + field); return normalized; }
    private static String code(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static Date date(LocalDate value) { return value == null ? null : Date.valueOf(value); }
    private static Timestamp timestamp(LocalDate value) { return value == null ? null : Timestamp.from(value.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()); }
    private static LocalDate nextPeriod(LocalDate start, String interval) { return "YEARLY".equalsIgnoreCase(interval) ? start.plusYears(1) : start.plusMonths(1); }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal value(BigDecimal custom, BigDecimal standard) { return custom == null ? standard : custom; }
    private static int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private static int pages(long total, int size) { return (int) Math.ceil((double) total / size); }
    private static Integer integer(ResultSet rs, String column) throws SQLException { int value = rs.getInt(column); return rs.wasNull() ? null : value; }
    private static Instant instant(ResultSet rs, String column) throws SQLException { Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant(); }
    private static LocalDate localDate(ResultSet rs, String column) throws SQLException { Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate(); }
}
