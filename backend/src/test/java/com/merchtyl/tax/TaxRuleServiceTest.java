package com.merchtyl.tax;

import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import com.merchtyl.security.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaxRuleServiceTest {
    private final TaxRuleRepository taxRuleRepository = mock(TaxRuleRepository.class);
    private final TaxGroupRepository taxGroupRepository = mock(TaxGroupRepository.class);
    private final TaxComponentRepository taxComponentRepository = mock(TaxComponentRepository.class);
    private final TaxCategoryRepository taxCategoryRepository = mock(TaxCategoryRepository.class);
    private final TaxJurisdictionRepository taxJurisdictionRepository = mock(TaxJurisdictionRepository.class);
    private final TaxTypeRepository taxTypeRepository = mock(TaxTypeRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    private final TaxGroupService taxGroupService = new TaxGroupService(taxGroupRepository, userRepository, auditService);
    private final TaxTypeService taxTypeService = new TaxTypeService(taxTypeRepository, userRepository, auditService);
    private final TaxComponentService taxComponentService = new TaxComponentService(taxComponentRepository, taxTypeService, taxJurisdictionRepository, userRepository, auditService);
    private final TaxCategoryService taxCategoryService = new TaxCategoryService(taxCategoryRepository, taxGroupService, userRepository, auditService);
    private final TaxRuleService taxRuleService = new TaxRuleService(
            taxRuleRepository,
            taxGroupService,
            taxComponentService,
            taxCategoryService,
            taxJurisdictionRepository,
            productRepository,
            new TaxRuleEvaluator(taxRuleRepository),
            userRepository,
            auditService);

    @Test
    void createRuleValidatesReferencesNormalizesCodeAndAudits() {
        TaxGroup group = new TaxGroup("CA-HST", "HST", null, true);
        Product product = product();
        TaxCategory category = new TaxCategory(group, "STANDARD", "Standard", TaxTreatment.STANDARD, null, true);
        when(taxRuleRepository.existsByCodeIgnoreCase("STANDARD-CA")).thenReturn(false);
        when(taxGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(taxCategoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(productRepository.existsById(product.getId())).thenReturn(true);
        when(taxRuleRepository.saveAndFlush(any(TaxRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxRuleResponse response = taxRuleService.create(new TaxRuleRequest(
                " standard-ca ",
                " Standard CA ",
                " Applies standard tax ",
                10,
                LocalDate.of(2026, 1, 1),
                null,
                true,
                List.of(
                        new TaxRuleConditionRequest(TaxRuleConditionType.PRODUCT_TAX_CATEGORY, TaxRuleConditionOperator.EQUALS, category.getId().toString(), null),
                        new TaxRuleConditionRequest(TaxRuleConditionType.PRODUCT, TaxRuleConditionOperator.EQUALS, product.getId().toString(), null)),
                List.of(new TaxRuleActionRequest(TaxRuleActionType.APPLY_TAX_GROUP, group.getId(), null, null))),
                null);

        assertThat(response.code()).isEqualTo("STANDARD-CA");
        assertThat(response.conditions()).hasSize(2);
        assertThat(response.actions()).singleElement().extracting(TaxRuleActionResponse::taxGroupId).isEqualTo(group.getId());
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void createRuleRejectsActionlessRules() {
        when(taxRuleRepository.existsByCodeIgnoreCase("NO_ACTION")).thenReturn(false);

        assertThatThrownBy(() -> taxRuleService.create(new TaxRuleRequest(
                "NO_ACTION",
                "No action",
                null,
                0,
                LocalDate.of(2026, 1, 1),
                null,
                true,
                List.of(),
                List.of()), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("At least one");

        verify(taxRuleRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRuleRequiresCurrentVersion() {
        TaxGroup group = new TaxGroup("CA-HST", "HST", null, true);
        TaxRule rule = rule("STANDARD", 5, group);
        when(taxRuleRepository.findById(rule.getId())).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> taxRuleService.update(rule.getId(), new TaxRuleUpdateRequest(
                rule.getCode(),
                rule.getName(),
                null,
                rule.getPriority(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo(),
                true,
                List.of(),
                List.of(new TaxRuleActionRequest(TaxRuleActionType.EXEMPT, null, null, null)),
                rule.getVersion() + 1), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Tax rule was modified");

        verify(taxRuleRepository, never()).saveAndFlush(any());
    }

    @Test
    void evaluateRulesIsPrioritizedDeterministicAndExplainable() {
        TaxGroup group = new TaxGroup("CA-HST", "HST", null, true);
        TaxComponent gst = component("GST");
        TaxComponent pst = component("PST");
        TaxCategory category = new TaxCategory(group, "STANDARD", "Standard", TaxTreatment.STANDARD, null, true);
        TaxRule applyGroup = rule("APPLY-STANDARD", 10, List.of(
                new TaxRuleConditionValues(TaxRuleConditionType.PRODUCT_TAX_CATEGORY, TaxRuleConditionOperator.EQUALS, category.getId().toString(), null),
                new TaxRuleConditionValues(TaxRuleConditionType.SALE_CHANNEL, TaxRuleConditionOperator.IN, "POS,KIOSK", null)),
                List.of(
                        new TaxRuleActionValues(TaxRuleActionType.APPLY_TAX_GROUP, group, null, null),
                        new TaxRuleActionValues(TaxRuleActionType.APPLY_TAX_COMPONENT, null, pst, null),
                        new TaxRuleActionValues(TaxRuleActionType.INCLUDED_PRICE_BEHAVIOR, null, null, IncludedPriceBehavior.FORCE_INCLUDED.name())));
        TaxRule exemptCustomer = rule("CUSTOMER-EXEMPT", 1, List.of(
                new TaxRuleConditionValues(TaxRuleConditionType.CUSTOMER_EXEMPTION, TaxRuleConditionOperator.IS_TRUE, null, null)),
                List.of(new TaxRuleActionValues(TaxRuleActionType.EXEMPT, null, null, null)));
        TaxRule excludeGst = rule("EXCLUDE-GST", 20, List.of(
                new TaxRuleConditionValues(TaxRuleConditionType.TRANSACTION_DATE, TaxRuleConditionOperator.BETWEEN, "2026-07-01", "2026-07-31")),
                List.of(
                        new TaxRuleActionValues(TaxRuleActionType.APPLY_TAX_COMPONENT, null, gst, null),
                        new TaxRuleActionValues(TaxRuleActionType.EXCLUDE_COMPONENT, null, gst, null),
                        new TaxRuleActionValues(TaxRuleActionType.ROUNDING_STRATEGY, null, null, TaxRoundingStrategy.HALF_EVEN.name())));
        TaxRule skipped = rule("ONLINE-ONLY", 30, List.of(
                new TaxRuleConditionValues(TaxRuleConditionType.SALE_CHANNEL, TaxRuleConditionOperator.EQUALS, "ONLINE", null)),
                List.of(new TaxRuleActionValues(TaxRuleActionType.ZERO_RATE, null, null, null)));
        when(taxRuleRepository.findActiveEffectiveRules(LocalDate.of(2026, 7, 15)))
                .thenReturn(List.of(exemptCustomer, applyGroup, excludeGst, skipped));

        TaxRuleEvaluationResponse response = taxRuleService.evaluate(new TaxRuleEvaluationRequest(
                null,
                null,
                category.getId(),
                null,
                false,
                LocalDate.of(2026, 7, 15),
                "pos"), null);

        assertThat(response.appliedTaxGroupIds()).containsExactly(group.getId());
        assertThat(response.appliedTaxComponentIds()).containsExactly(pst.getId());
        assertThat(response.excludedTaxComponentIds()).containsExactly(gst.getId());
        assertThat(response.includedPriceBehavior()).isEqualTo(IncludedPriceBehavior.FORCE_INCLUDED);
        assertThat(response.roundingStrategy()).isEqualTo(TaxRoundingStrategy.HALF_EVEN);
        assertThat(response.exempt()).isFalse();
        assertThat(response.ruleMatches()).extracting(TaxRuleMatchResponse::code)
                .containsExactly("CUSTOMER-EXEMPT", "APPLY-STANDARD", "EXCLUDE-GST", "ONLINE-ONLY");
        assertThat(response.ruleMatches().get(0).matched()).isFalse();
        assertThat(response.ruleMatches().get(0).conditions()).singleElement()
                .extracting(TaxRuleConditionEvaluationResponse::explanation)
                .asString()
                .contains("CUSTOMER_EXEMPTION");
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    private static TaxRule rule(String code, int priority, TaxGroup group) {
        return rule(code, priority, List.of(), List.of(new TaxRuleActionValues(TaxRuleActionType.APPLY_TAX_GROUP, group, null, null)));
    }

    private static TaxRule rule(String code, int priority, List<TaxRuleConditionValues> conditions, List<TaxRuleActionValues> actions) {
        return new TaxRule(new TaxRuleValues(
                code,
                code,
                null,
                priority,
                LocalDate.of(2026, 1, 1),
                null,
                true,
                conditions,
                actions));
    }

    private static TaxComponent component(String code) {
        TaxType type = new TaxType(code, code, null, true);
        Country country = new Country("CA", "Canada", true);
        TaxJurisdiction jurisdiction = new TaxJurisdiction(country, null, code, code, TaxJurisdictionType.NATIONAL, true);
        return new TaxComponent(type, jurisdiction, code, code, null, true);
    }

    private static Product product() {
        return new Product(new ProductValues(
                "SKU-1",
                "Coffee",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                BigDecimal.ONE,
                BigDecimal.TEN,
                null,
                null,
                true,
                true,
                false,
                null,
                null,
                List.of(),
                List.of(),
                Set.of()));
    }
}
