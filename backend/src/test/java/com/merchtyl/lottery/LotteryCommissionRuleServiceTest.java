package com.merchtyl.lottery;

import com.merchtyl.audit.AuditRecord;
import com.merchtyl.audit.AuditService;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.features.FeatureCode;
import com.merchtyl.features.FeatureService;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import com.merchtyl.tax.Country;
import com.merchtyl.tax.TaxJurisdiction;
import com.merchtyl.tax.TaxJurisdictionRepository;
import com.merchtyl.tax.TaxJurisdictionType;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LotteryCommissionRuleServiceTest {
    private final LotteryCommissionRuleRepository ruleRepository = mock(LotteryCommissionRuleRepository.class);
    private final LotteryOperatorRepository operatorRepository = mock(LotteryOperatorRepository.class);
    private final TaxJurisdictionRepository jurisdictionRepository = mock(TaxJurisdictionRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final FeatureService featureService = mock(FeatureService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final LotteryCommissionRuleService service = new LotteryCommissionRuleService(
            ruleRepository,
            operatorRepository,
            jurisdictionRepository,
            storeRepository,
            featureService,
            userRepository,
            auditService);

    @Test
    void createPercentRuleRequiresFeaturePreventsOverlapAndAudits() {
        TaxJurisdiction jurisdiction = jurisdiction();
        LotteryOperator operator = operator(jurisdiction);
        Store store = store();
        UUID operatorId = operator.getId();
        UUID jurisdictionId = jurisdiction.getId();
        UUID storeId = store.getId();
        stubReferences(operator, jurisdiction, store);
        when(ruleRepository.existsOverlappingRule(
                eq(operatorId),
                eq(jurisdictionId),
                eq(storeId),
                eq(LotteryCommissionRuleType.PERCENT_OF_SALES),
                eq(LocalDate.of(2026, 8, 1)),
                eq(LocalDate.of(9999, 12, 31)),
                eq(Set.of(LotteryCommissionRuleStatus.ACTIVE)),
                isNull())).thenReturn(false);
        when(ruleRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditService.record(any())).thenReturn(mock(AuditRecord.class));

        LotteryCommissionRuleResponse response = service.create(percentRequest(LotteryCommissionRuleStatus.ACTIVE),
                new TestingAuthenticationToken("owner@example.local", null));

        assertThat(response.name()).isEqualTo("Sales commission");
        assertThat(response.operatorId()).isEqualTo(operatorId);
        assertThat(response.ruleType()).isEqualTo(LotteryCommissionRuleType.PERCENT_OF_SALES);
        assertThat(response.commissionRatePercent()).isEqualByComparingTo("5.2500");
        assertThat(response.fixedAmount()).isNull();
        verify(featureService).requireEnabled(FeatureCode.LOTTERY_SALES, null, null);
        verify(auditService).record(any());
    }

    @Test
    void fixedPerPeriodRequiresAmountCurrencyAndPeriod() {
        TaxJurisdiction jurisdiction = jurisdiction();
        stubReferences(operator(jurisdiction), jurisdiction, store());

        assertThatThrownBy(() -> service.create(new LotteryCommissionRuleRequest(
                "Monthly fee",
                UUID.fromString("00000000-0000-0000-0000-000000000901"),
                UUID.fromString("00000000-0000-0000-0000-000000000902"),
                UUID.fromString("00000000-0000-0000-0000-000000000903"),
                LotteryCommissionRuleType.FIXED_PER_PERIOD,
                null,
                new BigDecimal("15.00"),
                "USD",
                null,
                LocalDate.of(2026, 8, 1),
                null,
                LotteryCommissionRuleStatus.DRAFT,
                null), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("fixedPeriod is required");

        verify(ruleRepository, never()).saveAndFlush(any());
    }

    @Test
    void overlappingActiveRulesOfSameTypeAreRejected() {
        TaxJurisdiction jurisdiction = jurisdiction();
        stubReferences(operator(jurisdiction), jurisdiction, store());
        when(ruleRepository.existsOverlappingRule(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(percentRequest(LotteryCommissionRuleStatus.ACTIVE), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("overlaps");

        verify(ruleRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsStaleVersion() {
        LotteryCommissionRule rule = rule(LotteryCommissionRuleStatus.DRAFT);
        when(ruleRepository.findById(rule.getId())).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> service.update(rule.getId(), updateRequest(99L), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified");
    }

    @Test
    void deleteRequiresCurrentVersion() {
        LotteryCommissionRule rule = rule(LotteryCommissionRuleStatus.DRAFT);
        when(ruleRepository.findById(rule.getId())).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> service.delete(rule.getId(), 99L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified");

        verify(ruleRepository, never()).delete(any(LotteryCommissionRule.class));
    }

    @Test
    void disabledLotterySalesFeatureBlocksReadsAndWrites() {
        org.mockito.Mockito.doThrow(new ForbiddenOperationException("Feature is disabled: LOTTERY_SALES"))
                .when(featureService)
                .requireEnabled(FeatureCode.LOTTERY_SALES, null, null);

        assertThatThrownBy(() -> service.search(new LotteryCommissionRuleSearchRequest(null, null, null, null, null, 0, 20)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("LOTTERY_SALES");

        assertThatThrownBy(() -> service.create(percentRequest(LotteryCommissionRuleStatus.DRAFT), null))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private LotteryCommissionRuleRequest percentRequest(LotteryCommissionRuleStatus status) {
        return new LotteryCommissionRuleRequest(
                "Sales commission",
                UUID.fromString("00000000-0000-0000-0000-000000000901"),
                UUID.fromString("00000000-0000-0000-0000-000000000902"),
                UUID.fromString("00000000-0000-0000-0000-000000000903"),
                LotteryCommissionRuleType.PERCENT_OF_SALES,
                new BigDecimal("5.25"),
                null,
                null,
                null,
                LocalDate.of(2026, 8, 1),
                null,
                status,
                "Weekly sales commission");
    }

    private LotteryCommissionRuleUpdateRequest updateRequest(long version) {
        LotteryCommissionRuleRequest request = percentRequest(LotteryCommissionRuleStatus.ACTIVE);
        return new LotteryCommissionRuleUpdateRequest(
                request.name(),
                request.operatorId(),
                request.jurisdictionId(),
                request.storeId(),
                request.ruleType(),
                request.commissionRatePercent(),
                request.fixedAmount(),
                request.currencyCode(),
                request.fixedPeriod(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.status(),
                request.notes(),
                version);
    }

    private LotteryCommissionRule rule(LotteryCommissionRuleStatus status) {
        return new LotteryCommissionRule(new LotteryCommissionRuleValues(
                "Sales commission",
                operator(jurisdiction()),
                jurisdiction(),
                store(),
                LotteryCommissionRuleType.PERCENT_OF_SALES,
                new BigDecimal("5.2500"),
                null,
                null,
                null,
                LocalDate.of(2026, 8, 1),
                null,
                status,
                null));
    }

    private void stubReferences(LotteryOperator operator, TaxJurisdiction jurisdiction, Store store) {
        when(operatorRepository.findById(any())).thenReturn(Optional.of(operator));
        when(jurisdictionRepository.findById(any())).thenReturn(Optional.of(jurisdiction));
        when(storeRepository.findById(any())).thenReturn(Optional.of(store));
    }

    private static LotteryOperator operator(TaxJurisdiction jurisdiction) {
        return new LotteryOperator(new LotteryOperatorValues(
                "STATE",
                "State Lottery",
                jurisdiction,
                "support@example.test",
                SettlementFrequency.WEEKLY,
                true));
    }

    private static TaxJurisdiction jurisdiction() {
        return new TaxJurisdiction(
                new Country("US", "United States", true),
                null,
                "CA",
                "California",
                TaxJurisdictionType.STATE,
                true);
    }

    private static Store store() {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000903"));
        when(store.getCode()).thenReturn("MAIN");
        when(store.getName()).thenReturn("Main Store");
        return store;
    }
}
