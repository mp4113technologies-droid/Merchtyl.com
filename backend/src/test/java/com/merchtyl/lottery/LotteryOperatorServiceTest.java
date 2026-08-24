package com.merchtyl.lottery;

import com.merchtyl.audit.AuditRecord;
import com.merchtyl.audit.AuditService;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.features.FeatureCode;
import com.merchtyl.features.FeatureService;
import com.merchtyl.security.UserRepository;
import com.merchtyl.tax.Country;
import com.merchtyl.tax.TaxJurisdiction;
import com.merchtyl.tax.TaxJurisdictionRepository;
import com.merchtyl.tax.TaxJurisdictionType;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LotteryOperatorServiceTest {
    private final LotteryOperatorRepository lotteryOperatorRepository = mock(LotteryOperatorRepository.class);
    private final TaxJurisdictionRepository taxJurisdictionRepository = mock(TaxJurisdictionRepository.class);
    private final FeatureService featureService = mock(FeatureService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final LotteryOperatorService service = new LotteryOperatorService(
            lotteryOperatorRepository,
            taxJurisdictionRepository,
            featureService,
            userRepository,
            auditService);

    @Test
    void createRequiresLotterySalesFeatureAndAuditsOperator() {
        TaxJurisdiction jurisdiction = jurisdiction();
        when(taxJurisdictionRepository.findById(jurisdiction.getId())).thenReturn(Optional.of(jurisdiction));
        when(lotteryOperatorRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditService.record(any())).thenReturn(mock(AuditRecord.class));

        LotteryOperatorResponse response = service.create(new LotteryOperatorRequest(
                        "state",
                        "State Lottery",
                        jurisdiction.getId(),
                        "support@example.test",
                        SettlementFrequency.WEEKLY,
                        true),
                new TestingAuthenticationToken("owner@example.local", null));

        assertThat(response.code()).isEqualTo("STATE");
        assertThat(response.jurisdictionId()).isEqualTo(jurisdiction.getId());
        verify(featureService).requireEnabled(FeatureCode.LOTTERY_SALES, null, null);
        verify(auditService).record(any());
    }

    @Test
    void disabledLotterySalesFeatureBlocksReadAndWriteOperations() {
        org.mockito.Mockito.doThrow(new ForbiddenOperationException("Feature is disabled: LOTTERY_SALES"))
                .when(featureService)
                .requireEnabled(FeatureCode.LOTTERY_SALES, null, null);

        assertThatThrownBy(() -> service.search(new LotteryOperatorSearchRequest(null, null, null, null, null, 0, 20)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("LOTTERY_SALES");

        assertThatThrownBy(() -> service.create(new LotteryOperatorRequest(
                "state",
                "State Lottery",
                UUID.randomUUID(),
                null,
                SettlementFrequency.WEEKLY,
                true), null))
                .isInstanceOf(ForbiddenOperationException.class);
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
}
