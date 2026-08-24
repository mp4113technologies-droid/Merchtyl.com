package com.merchtyl.lottery;

import com.merchtyl.audit.AuditRecord;
import com.merchtyl.audit.AuditService;
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

class LotteryPayoutPolicyServiceTest {
    private final LotteryPayoutPolicyRepository policyRepository = mock(LotteryPayoutPolicyRepository.class);
    private final LotteryOperatorRepository operatorRepository = mock(LotteryOperatorRepository.class);
    private final TaxJurisdictionRepository jurisdictionRepository = mock(TaxJurisdictionRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final FeatureService featureService = mock(FeatureService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final LotteryPayoutPolicyService service = new LotteryPayoutPolicyService(
            policyRepository,
            operatorRepository,
            jurisdictionRepository,
            storeRepository,
            featureService,
            userRepository,
            auditService);

    @Test
    void createRequiresFeaturePreventsOverlapAndAuditsPolicy() {
        TaxJurisdiction jurisdiction = jurisdiction();
        LotteryOperator operator = operator(jurisdiction);
        Store store = store();
        UUID operatorId = operator.getId();
        UUID jurisdictionId = jurisdiction.getId();
        UUID storeId = store.getId();
        when(operatorRepository.findById(any())).thenReturn(Optional.of(operator));
        when(jurisdictionRepository.findById(any())).thenReturn(Optional.of(jurisdiction));
        when(storeRepository.findById(any())).thenReturn(Optional.of(store));
        when(policyRepository.existsOverlappingPolicy(
                eq(operatorId),
                eq(jurisdictionId),
                eq(storeId),
                eq(LocalDate.of(2026, 8, 1)),
                eq(LocalDate.of(9999, 12, 31)),
                eq(Set.of(LotteryPayoutPolicyStatus.ACTIVE, LotteryPayoutPolicyStatus.SCHEDULED)),
                isNull())).thenReturn(false);
        when(policyRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditService.record(any())).thenReturn(mock(AuditRecord.class));

        LotteryPayoutPolicyResponse response = service.create(request(LotteryPayoutPolicyStatus.ACTIVE),
                new TestingAuthenticationToken("owner@example.local", null));

        assertThat(response.operatorId()).isEqualTo(operatorId);
        assertThat(response.jurisdictionId()).isEqualTo(jurisdictionId);
        assertThat(response.storeId()).isEqualTo(storeId);
        assertThat(response.maximumCashPayout()).isEqualByComparingTo("2500.00");
        verify(featureService).requireEnabled(FeatureCode.LOTTERY_SALES, null, null);
        verify(auditService).record(any());
    }

    @Test
    void overlappingScheduledOrActivePoliciesAreRejected() {
        TaxJurisdiction jurisdiction = jurisdiction();
        LotteryOperator operator = operator(jurisdiction);
        Store store = store();
        when(operatorRepository.findById(any())).thenReturn(Optional.of(operator));
        when(jurisdictionRepository.findById(any())).thenReturn(Optional.of(jurisdiction));
        when(storeRepository.findById(any())).thenReturn(Optional.of(store));
        when(policyRepository.existsOverlappingPolicy(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(LotteryPayoutPolicyStatus.SCHEDULED), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("overlaps");

        verify(policyRepository, never()).saveAndFlush(any());
    }

    @Test
    void draftPoliciesDoNotBlockOnOverlap() {
        TaxJurisdiction jurisdiction = jurisdiction();
        LotteryOperator operator = operator(jurisdiction);
        Store store = store();
        when(operatorRepository.findById(any())).thenReturn(Optional.of(operator));
        when(jurisdictionRepository.findById(any())).thenReturn(Optional.of(jurisdiction));
        when(storeRepository.findById(any())).thenReturn(Optional.of(store));
        when(policyRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditService.record(any())).thenReturn(mock(AuditRecord.class));

        LotteryPayoutPolicyResponse response = service.create(request(LotteryPayoutPolicyStatus.DRAFT), null);

        assertThat(response.status()).isEqualTo(LotteryPayoutPolicyStatus.DRAFT);
        verify(policyRepository, never()).existsOverlappingPolicy(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void staleVersionIsRejected() {
        LotteryPayoutPolicy policy = policy(LotteryPayoutPolicyStatus.DRAFT);
        when(policyRepository.findById(policy.getId())).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.update(policy.getId(), updateRequest(99L), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified");
    }

    @Test
    void disabledLotterySalesFeatureBlocksReadAndWriteOperations() {
        org.mockito.Mockito.doThrow(new ForbiddenOperationException("Feature is disabled: LOTTERY_SALES"))
                .when(featureService)
                .requireEnabled(FeatureCode.LOTTERY_SALES, null, null);

        assertThatThrownBy(() -> service.search(new LotteryPayoutPolicySearchRequest(null, null, null, null, 0, 20)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("LOTTERY_SALES");

        assertThatThrownBy(() -> service.create(request(LotteryPayoutPolicyStatus.ACTIVE), null))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private LotteryPayoutPolicyRequest request(LotteryPayoutPolicyStatus status) {
        return new LotteryPayoutPolicyRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000901"),
                UUID.fromString("00000000-0000-0000-0000-000000000902"),
                UUID.fromString("00000000-0000-0000-0000-000000000903"),
                new BigDecimal("2500.00"),
                new BigDecimal("200.00"),
                new BigDecimal("500.00"),
                new BigDecimal("2500.00"),
                new BigDecimal("150.00"),
                true,
                true,
                true,
                true,
                true,
                false,
                LocalDate.of(2026, 8, 1),
                null,
                status);
    }

    private LotteryPayoutPolicyUpdateRequest updateRequest(long version) {
        LotteryPayoutPolicyRequest request = request(LotteryPayoutPolicyStatus.ACTIVE);
        return new LotteryPayoutPolicyUpdateRequest(
                request.operatorId(),
                request.jurisdictionId(),
                request.storeId(),
                request.maximumCashPayout(),
                request.cashierApprovalLimit(),
                request.managerApprovalThreshold(),
                request.operatorReferralThreshold(),
                request.protectedRegisterFloat(),
                request.allowCashPayout(),
                request.allowStoreCredit(),
                request.requireTicketValidation(),
                request.requireAgeVerification(),
                request.requireCustomerIdentification(),
                request.allowAlternateRegister(),
                request.effectiveFrom(),
                request.effectiveTo(),
                request.status(),
                version);
    }

    private LotteryPayoutPolicy policy(LotteryPayoutPolicyStatus status) {
        return new LotteryPayoutPolicy(new LotteryPayoutPolicyValues(
                operator(jurisdiction()),
                jurisdiction(),
                store(),
                new BigDecimal("2500.00"),
                new BigDecimal("200.00"),
                new BigDecimal("500.00"),
                new BigDecimal("2500.00"),
                new BigDecimal("150.00"),
                true,
                true,
                true,
                true,
                true,
                false,
                LocalDate.of(2026, 8, 1),
                null,
                status));
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
