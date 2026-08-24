package com.merchtyl.lottery;

import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ConflictException;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import com.merchtyl.tax.Country;
import com.merchtyl.tax.TaxJurisdiction;
import com.merchtyl.tax.TaxJurisdictionType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LotterySettlementServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

    private final LotterySettlementRepository settlementRepository = mock(LotterySettlementRepository.class);
    private final LotteryOperatorRepository operatorRepository = mock(LotteryOperatorRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final LotterySaleRepository saleRepository = mock(LotterySaleRepository.class);
    private final LotteryPayoutRepository payoutRepository = mock(LotteryPayoutRepository.class);
    private final LotterySaleCancellationRepository cancellationRepository = mock(LotterySaleCancellationRepository.class);
    private final LotteryPayoutReversalRepository reversalRepository = mock(LotteryPayoutReversalRepository.class);
    private final LotteryCommissionRuleRepository commissionRuleRepository = mock(LotteryCommissionRuleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final LotterySettlementService service = new LotterySettlementService(
            settlementRepository,
            operatorRepository,
            storeRepository,
            saleRepository,
            payoutRepository,
            cancellationRepository,
            reversalRepository,
            commissionRuleRepository,
            userRepository,
            auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void approveCalculatedSettlementAuditsAndStoresApprover() {
        User manager = new User("manager@example.local", "Manager", "hash");
        LotterySettlement settlement = settlement();
        when(settlementRepository.findById(settlement.getId())).thenReturn(Optional.of(settlement));
        when(userRepository.findByEmailIgnoreCase("manager@example.local")).thenReturn(Optional.of(manager));
        when(settlementRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LotterySettlementResponse response = service.approve(
                settlement.getId(),
                new LotterySettlementLifecycleRequest(0L, null, "Reviewed"),
                auth("manager@example.local"));

        assertThat(response.status()).isEqualTo(LotterySettlementStatus.APPROVED);
        assertThat(response.approvedBy()).isEqualTo(manager.getId());
        assertThat(response.approvedAt()).isEqualTo(NOW);
        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action().name()).isEqualTo("LOTTERY_SETTLEMENT_APPROVED");
    }

    @Test
    void postRequiresApprovedSettlement() {
        LotterySettlement settlement = settlement();
        when(settlementRepository.findById(settlement.getId())).thenReturn(Optional.of(settlement));

        assertThatThrownBy(() -> service.post(
                settlement.getId(),
                new LotterySettlementLifecycleRequest(0L, null, null),
                auth("owner@example.local")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("approved");

        verify(settlementRepository, never()).saveAndFlush(any());
    }

    @Test
    void postApprovedSettlementAuditsAndStoresPoster() {
        User manager = new User("manager@example.local", "Manager", "hash");
        User owner = new User("owner@example.local", "Owner", "hash");
        LotterySettlement settlement = settlement();
        settlement.approve(manager, NOW.minusSeconds(60), null);
        when(settlementRepository.findById(settlement.getId())).thenReturn(Optional.of(settlement));
        when(userRepository.findByEmailIgnoreCase("owner@example.local")).thenReturn(Optional.of(owner));
        when(settlementRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LotterySettlementResponse response = service.post(
                settlement.getId(),
                new LotterySettlementLifecycleRequest(0L, null, "Posted"),
                auth("owner@example.local"));

        assertThat(response.status()).isEqualTo(LotterySettlementStatus.POSTED);
        assertThat(response.postedBy()).isEqualTo(owner.getId());
        assertThat(response.postedAt()).isEqualTo(NOW);
        verify(auditService).record(any());
    }

    @Test
    void reopenPostedSettlementRequiresReasonAndAudits() {
        User manager = new User("manager@example.local", "Manager", "hash");
        User owner = new User("owner@example.local", "Owner", "hash");
        LotterySettlement settlement = settlement();
        settlement.approve(manager, NOW.minusSeconds(120), null);
        settlement.post(owner, NOW.minusSeconds(60), null);
        when(settlementRepository.findById(settlement.getId())).thenReturn(Optional.of(settlement));
        when(userRepository.findByEmailIgnoreCase("manager@example.local")).thenReturn(Optional.of(manager));
        when(settlementRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LotterySettlementResponse response = service.reopen(
                settlement.getId(),
                new LotterySettlementLifecycleRequest(0L, "Operator correction", null),
                auth("manager@example.local"));

        assertThat(response.status()).isEqualTo(LotterySettlementStatus.REOPENED);
        assertThat(response.reopenedBy()).isEqualTo(manager.getId());
        assertThat(response.reopenReason()).isEqualTo("Operator correction");
        verify(auditService).record(any());
    }

    @Test
    void staleVersionIsRejected() {
        LotterySettlement settlement = settlement();
        when(settlementRepository.findById(settlement.getId())).thenReturn(Optional.of(settlement));

        assertThatThrownBy(() -> service.approve(
                settlement.getId(),
                new LotterySettlementLifecycleRequest(99L, null, null),
                auth("manager@example.local")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified");
    }

    private static TestingAuthenticationToken auth(String email) {
        return new TestingAuthenticationToken(email, null);
    }

    private static LotterySettlement settlement() {
        TaxJurisdiction jurisdiction = new TaxJurisdiction(
                new Country("US", "United States", true),
                null,
                "CA",
                "California",
                TaxJurisdictionType.STATE,
                true);
        LotteryOperator operator = new LotteryOperator(new LotteryOperatorValues(
                "STATE",
                "State Lottery",
                jurisdiction,
                "support@example.test",
                SettlementFrequency.WEEKLY,
                true));
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000903"));
        when(store.getCode()).thenReturn("MAIN");
        when(store.getName()).thenReturn("Main Store");
        return new LotterySettlement(new LotterySettlementValues(
                operator,
                jurisdiction,
                store,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-07"),
                new BigDecimal("150.00"),
                new BigDecimal("70.00"),
                new BigDecimal("50.00"),
                new BigDecimal("30.00"),
                new BigDecimal("26.00"),
                new BigDecimal("34.00"),
                "USD",
                NOW));
    }
}
