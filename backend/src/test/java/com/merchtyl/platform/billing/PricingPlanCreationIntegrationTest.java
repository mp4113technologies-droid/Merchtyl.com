package com.merchtyl.platform.billing;

import com.merchtyl.common.ConflictException;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.platform.admin.PlatformAdministrationService;
import com.merchtyl.platform.admin.PlatformDtos.MerchantOnboardingRequest;
import com.merchtyl.store.StoreCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Set;

import static com.merchtyl.platform.billing.BillingDtos.CapabilityPrice;
import static com.merchtyl.platform.billing.BillingDtos.PlanRequest;
import static com.merchtyl.platform.billing.BillingDtos.PlanResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PricingPlanCreationIntegrationTest {
    private static final String ACTOR_EMAIL = "pricing.integration@merchtyl.test";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private PlatformBillingService billing;
    @Autowired private PlatformAdministrationService administration;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void createPlatformActor() {
        jdbc.update("""
                insert into platform_users(id,email,display_name,password_hash,role,enabled,locked,password_change_required)
                values (?,?,?,?, 'PLATFORM_SUPER_ADMIN',true,false,false)
                on conflict(email) do nothing
                """, UUID.randomUUID(), ACTOR_EMAIL, "Pricing Integration", "not-used");
    }

    @Test
    void createsAndReloadsExactProductionPayload() {
        PlanResponse created = billing.createPlan(productionRequest("ESSRETKIC"), authentication());
        PlanResponse reloaded = reload(created.id());

        assertThat(reloaded.code()).isEqualTo("ESSRETKIC");
        assertThat(reloaded.includedStores()).isEqualTo(1);
        assertThat(reloaded.includedRegisters()).isEqualTo(5);
        assertThat(reloaded.includedUsers()).isEqualTo(5);
        assertThat(reloaded.additionalRegisterPrice()).isEqualByComparingTo("15");
        assertThat(reloaded.taxBehavior()).isEqualTo("EXCLUSIVE");
        assertThat(reloaded.effectiveFrom()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(reloaded.effectiveTo()).isNull();
        assertThat(reloaded.capabilityPrices()).contains(
                new CapabilityPrice(CommercialCapability.REPORTING, CapabilityInclusionType.PAID_ADD_ON, BillingUnit.PER_STORE, new BigDecimal("15")),
                new CapabilityPrice(CommercialCapability.FOOD_SERVICE, CapabilityInclusionType.INCLUDED, null, null));

        Integer versions = jdbc.queryForObject(
                "select count(*) from platform_pricing_plan_versions where pricing_plan_id=?",
                Integer.class,
                created.id());
        Integer capabilities = jdbc.queryForObject("""
                select count(*) from platform_pricing_plan_version_capabilities capability
                join platform_pricing_plan_versions version on version.id=capability.pricing_plan_version_id
                where version.pricing_plan_id=?
                """, Integer.class, created.id());
        Integer reportingMirror = jdbc.queryForObject("""
                select count(*) from platform_pricing_plan_capability_prices
                where pricing_plan_id=? and capability='REPORTING' and monthly_price_per_store=15
                """, Integer.class, created.id());

        assertThat(versions).isEqualTo(1);
        assertThat(capabilities).isEqualTo(9);
        assertThat(reportingMirror).isEqualTo(1);
    }

    @Test
    void persistsEverySupportedPaidAddOnBillingUnit() {
        List<BillingUnit> units = List.of(BillingUnit.PER_MERCHANT, BillingUnit.PER_STORE, BillingUnit.PER_USER, BillingUnit.PER_REGISTER);
        for (int index = 0; index < units.size(); index++) {
            BillingUnit unit = units.get(index);
            PlanRequest request = request(
                    "UNIT_%d".formatted(index),
                    List.of(new CapabilityPrice(CommercialCapability.REPORTING, CapabilityInclusionType.PAID_ADD_ON, unit, BigDecimal.TEN)));
            PlanResponse created = billing.createPlan(request, authentication());
            assertThat(reload(created.id()).capabilityPrices().getFirst().billingUnit()).isEqualTo(unit);
        }
    }

    @Test
    void duplicatePlanCodeReturnsDomainConflict() {
        billing.createPlan(productionRequest("TESTPLAN"), authentication());

        assertThatThrownBy(() -> billing.createPlan(productionRequest("TESTPLAN"), authentication()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PRICING_PLAN_CODE_ALREADY_EXISTS")
                .hasMessageContaining("TESTPLAN");
    }

    @Test
    void activatesDraftPlanWithoutCreatingDuplicatePricingVersion() {
        PlanRequest draft = withStatus(productionRequest("DRAFT_ACTIVE"), "DRAFT");
        PlanResponse created = billing.createPlan(draft, authentication());

        PlanResponse activated = billing.updatePlan(created.id(), withStatus(draft, "ACTIVE"), authentication());

        assertThat(activated.status()).isEqualTo("ACTIVE");
        assertThat(billing.activePlanOptions()).extracting(PlanResponse::id).contains(created.id());
        assertThat(jdbc.queryForObject(
                "select count(*) from platform_pricing_plan_versions where pricing_plan_id=?",
                Integer.class, created.id())).isEqualTo(1);
    }

    @Test
    void rejectsActivationWithoutCurrentlyEffectivePricingVersion() {
        PlanRequest future = new PlanRequest("FUTURE_DRAFT", "Future Draft", null, "DRAFT", "MONTHLY",
                BigDecimal.TEN, BigDecimal.ZERO, "CAD", 0, 1, 1, 1, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(included(CommercialCapability.RETAIL_POS)),
                "EXCLUSIVE", LocalDate.now().plusDays(10), null);
        PlanResponse created = billing.createPlan(future, authentication());

        assertThatThrownBy(() -> billing.updatePlan(created.id(), withStatus(future, "ACTIVE"), authentication()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PRICING_PLAN_NO_EFFECTIVE_VERSION");
    }

    @Test
    void createsMerchantAndSingleVersionedSubscriptionFromReportedPayload() {
        PlanResponse plan = billing.createPlan(productionRequest("ONBOARDING_EXACT"), authentication());

        var created = administration.createMerchant(onboardingRequest("TEST123", "Test3@adviam.com", plan.id()), authentication());
        UUID tenantId = created.tenant().id();

        assertThat(jdbc.queryForObject("select count(*) from tenants where id=?", Integer.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from security_users where tenant_id=?", Integer.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from tenant_subscriptions where tenant_id=? and pricing_plan_id=? and pricing_plan_version_id is not null", Integer.class, tenantId, plan.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from tenant_subscription_capability_price_snapshots where subscription_id in(select id from tenant_subscriptions where tenant_id=?)", Integer.class, tenantId)).isZero();
        assertThat(jdbc.queryForList("select capability from tenant_store_operation_defaults where tenant_id=? order by capability", String.class, tenantId))
                .containsExactly("FOOD_SERVICE", "RETAIL");
    }

    @Test
    void duplicateTenantCodeAndOwnerEmailReturnSpecificDomainConflicts() {
        PlanResponse plan = billing.createPlan(productionRequest("ONBOARDING_DUPLICATES"), authentication());
        administration.createMerchant(onboardingRequest("DUPLICATE_CODE", "unique.owner@adviam.com", plan.id()), authentication());

        assertThatThrownBy(() -> administration.createMerchant(onboardingRequest("DUPLICATE_CODE", "other.owner@adviam.com", plan.id()), authentication()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("TENANT_CODE_ALREADY_EXISTS");
        assertThatThrownBy(() -> administration.createMerchant(onboardingRequest("OTHER_CODE", "UNIQUE.OWNER@ADVIAM.COM", plan.id()), authentication()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("OWNER_EMAIL_ALREADY_EXISTS");
    }

    @Test
    void rejectsUnavailableFoodServiceBeforeAnyTenantIsInserted() {
        PlanRequest withoutFood = request("NO_FOOD", List.of(
                included(CommercialCapability.RETAIL_POS), unavailable(CommercialCapability.FOOD_SERVICE)));
        PlanResponse plan = billing.createPlan(withoutFood, authentication());

        assertThatThrownBy(() -> administration.createMerchant(onboardingRequest("NO_FOOD_TENANT", "no.food@adviam.com", plan.id()), authentication()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PLAN_CAPABILITY_NOT_AVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from tenants where tenant_code='NO_FOOD_TENANT'", Integer.class)).isZero();
    }

    private static PlanRequest productionRequest(String code) {
        return request(code, List.of(
                included(CommercialCapability.RETAIL_POS),
                included(CommercialCapability.INVENTORY),
                included(CommercialCapability.REGISTER_MANAGEMENT),
                included(CommercialCapability.RETURNS),
                new CapabilityPrice(CommercialCapability.REPORTING, CapabilityInclusionType.PAID_ADD_ON, BillingUnit.PER_STORE, new BigDecimal("15")),
                unavailable(CommercialCapability.ADVANCED_REPORTING),
                unavailable(CommercialCapability.EMPLOYEE_MANAGEMENT),
                included(CommercialCapability.FOOD_SERVICE),
                unavailable(CommercialCapability.LOTTERY)));
    }

    private PlanResponse reload(UUID id) {
        return billing.plans(0, 100).content().stream()
                .filter(plan -> plan.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static PlanRequest request(String code, List<CapabilityPrice> capabilities) {
        return new PlanRequest(
                code,
                "Essential Retail plus Kitchen",
                "retail store plus kitchen",
                "ACTIVE",
                "MONTHLY",
                new BigDecimal("100"),
                new BigDecimal("200"),
                "CAD",
                2,
                1,
                5,
                5,
                BigDecimal.ZERO,
                new BigDecimal("15"),
                BigDecimal.ZERO,
                capabilities,
                "EXCLUSIVE",
                LocalDate.of(2026, 8, 31),
                null);
    }

    private static CapabilityPrice included(CommercialCapability capability) {
        return new CapabilityPrice(capability, CapabilityInclusionType.INCLUDED, null, null);
    }

    private static CapabilityPrice unavailable(CommercialCapability capability) {
        return new CapabilityPrice(capability, CapabilityInclusionType.NOT_AVAILABLE, null, null);
    }

    private static MerchantOnboardingRequest onboardingRequest(String tenantCode, String ownerEmail, UUID planId) {
        return new MerchantOnboardingRequest(tenantCode, "Test3", "test3", "CA", "NL", "America/St_Johns", "CAD", "CA-NL",
                null, "Test1234", "123", 2, "", planId, "Test123", "test123", ownerEmail,
                "123123123123", null, Set.of(StoreCapability.RETAIL, StoreCapability.FOOD_SERVICE), "Sweetshop");
    }

    private static PlanRequest withStatus(PlanRequest request, String status) {
        return new PlanRequest(request.code(), request.name(), request.description(), status, request.billingInterval(),
                request.basePrice(), request.oneTimeOnboardingFee(), request.currency(), request.trialDays(),
                request.includedStores(), request.includedRegisters(), request.includedUsers(), request.additionalStorePrice(),
                request.additionalRegisterPrice(), request.additionalUserPrice(), request.capabilityPrices(), request.taxBehavior(),
                request.effectiveFrom(), request.effectiveTo());
    }

    private static UsernamePasswordAuthenticationToken authentication() {
        return UsernamePasswordAuthenticationToken.authenticated(ACTOR_EMAIL, "n/a", List.of());
    }
}
