package com.merchtyl.reports;

import com.merchtyl.device.Device;
import com.merchtyl.lottery.LotteryOperator;
import com.merchtyl.lottery.LotteryPayout;
import com.merchtyl.lottery.LotteryPayoutApproval;
import com.merchtyl.lottery.LotteryPayoutApprovalRepository;
import com.merchtyl.lottery.LotteryPayoutApprovalType;
import com.merchtyl.lottery.LotteryPayoutMethod;
import com.merchtyl.lottery.LotteryPayoutPolicy;
import com.merchtyl.lottery.LotteryPayoutRepository;
import com.merchtyl.lottery.LotteryPayoutReversal;
import com.merchtyl.lottery.LotteryPayoutReversalRepository;
import com.merchtyl.lottery.LotteryPayoutStatus;
import com.merchtyl.lottery.LotterySale;
import com.merchtyl.lottery.LotterySaleCancellation;
import com.merchtyl.lottery.LotterySaleCancellationRepository;
import com.merchtyl.lottery.LotterySaleRepository;
import com.merchtyl.lottery.LotterySaleStatus;
import com.merchtyl.lottery.LotterySettlement;
import com.merchtyl.lottery.LotterySettlementRepository;
import com.merchtyl.lottery.LotterySettlementStatus;
import com.merchtyl.lottery.LotteryVerificationState;
import com.merchtyl.register.Register;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import com.merchtyl.tax.TaxJurisdiction;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LotteryReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000503");
    private static final UUID CASHIER_ID = UUID.fromString("00000000-0000-0000-0000-000000000504");

    private final LotterySaleRepository saleRepository = mock(LotterySaleRepository.class);
    private final LotteryPayoutRepository payoutRepository = mock(LotteryPayoutRepository.class);
    private final LotteryPayoutApprovalRepository approvalRepository = mock(LotteryPayoutApprovalRepository.class);
    private final LotteryPayoutReversalRepository reversalRepository = mock(LotteryPayoutReversalRepository.class);
    private final LotterySaleCancellationRepository cancellationRepository = mock(LotterySaleCancellationRepository.class);
    private final LotterySettlementRepository settlementRepository = mock(LotterySettlementRepository.class);
    private final LotteryReportService service = new LotteryReportService(
            saleRepository,
            payoutRepository,
            approvalRepository,
            reversalRepository,
            cancellationRepository,
            settlementRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void summarizesLotteryActivitySettlementVarianceAndCharts() {
        LotterySale sale = sale("00000000-0000-0000-0000-000000000601", "120.00", LotterySaleStatus.RECORDED);
        LotteryPayout payout = payout("00000000-0000-0000-0000-000000000602", "40.00", LotteryPayoutStatus.PAID);
        LotteryPayout referral = payout("00000000-0000-0000-0000-000000000603", "75.00", LotteryPayoutStatus.REFERRED_TO_OPERATOR);
        LotteryPayoutApproval approval = approval(payout, LotteryPayoutApprovalType.MANAGER_APPROVAL, "40.00");
        LotteryPayoutReversal reversal = reversal(payout, "10.00");
        LotterySaleCancellation cancellation = cancellation(sale, "20.00");
        LotterySettlement settlement = settlement("00000000-0000-0000-0000-000000000604", "120.00", "40.00", "12.00", "58.00");

        when(saleRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(sale));
        when(payoutRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(payout, referral));
        when(approvalRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(approval));
        when(reversalRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(reversal));
        when(cancellationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(cancellation));
        when(settlementRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(settlement));

        LotteryReportResponse response = service.summarize(new LotteryReportRequest(
                OPERATOR_ID,
                STORE_ID,
                REGISTER_ID,
                CASHIER_ID,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31")));

        assertThat(response.sales()).isEqualByComparingTo("120.00");
        assertThat(response.payouts()).isEqualByComparingTo("40.00");
        assertThat(response.approvals()).isEqualByComparingTo("40.00");
        assertThat(response.reversals()).isEqualByComparingTo("10.00");
        assertThat(response.referrals()).isEqualByComparingTo("75.00");
        assertThat(response.cancellations()).isEqualByComparingTo("20.00");
        assertThat(response.commission()).isEqualByComparingTo("12.00");
        assertThat(response.calculatedSettlement()).isEqualByComparingTo("58.00");
        assertThat(response.settlement()).isEqualByComparingTo("58.00");
        assertThat(response.variance()).isEqualByComparingTo("0.00");
        assertThat(response.saleRows()).hasSize(1);
        assertThat(response.payoutRows()).hasSize(2);
        assertThat(response.approvalRows()).singleElement().satisfies(row -> {
            assertThat(row.approvalType()).isEqualTo(LotteryPayoutApprovalType.MANAGER_APPROVAL);
            assertThat(row.ticketNumber()).isEqualTo("TICKET-1");
        });
        assertThat(response.reversalRows()).hasSize(1);
        assertThat(response.referralRows()).singleElement().satisfies(row ->
                assertThat(row.status()).isEqualTo(LotteryPayoutStatus.REFERRED_TO_OPERATOR));
        assertThat(response.commissionRows()).singleElement().satisfies(row ->
                assertThat(row.commission()).isEqualByComparingTo("12.00"));
        assertThat(response.chartRows()).extracting(LotteryReportChartPoint::date)
                .contains(LocalDate.parse("2026-07-27"), LocalDate.parse("2026-07-31"));
        assertThat(response.generatedAt()).isEqualTo(NOW);
    }

    private static LotterySale sale(String id, String amount, LotterySaleStatus status) {
        LotteryOperator operator = operator();
        Store store = store();
        Register register = register();
        Device device = device();
        User cashier = user(CASHIER_ID, "cashier@example.local", "Cashier One");
        LotterySale sale = mock(LotterySale.class);
        when(sale.getId()).thenReturn(UUID.fromString(id));
        when(sale.getOperator()).thenReturn(operator);
        when(sale.getOperatorReference()).thenReturn("OP-REF");
        when(sale.getTicketReference()).thenReturn("SALE-TICKET");
        when(sale.getGameType()).thenReturn(com.merchtyl.lottery.LotteryGameType.DRAW_TICKET);
        when(sale.getAmount()).thenReturn(new BigDecimal(amount));
        when(sale.getCurrencyCode()).thenReturn("USD");
        when(sale.getPaymentMethod()).thenReturn(PaymentMethod.CASH);
        when(sale.getStore()).thenReturn(store);
        when(sale.getRegister()).thenReturn(register);
        when(sale.getDevice()).thenReturn(device);
        when(sale.getCashier()).thenReturn(cashier);
        when(sale.getRegisterSession()).thenReturn(null);
        when(sale.getStatus()).thenReturn(status);
        when(sale.getOperationId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000701"));
        when(sale.getOccurredAt()).thenReturn(Instant.parse("2026-07-27T10:00:00Z"));
        when(sale.getCreatedAt()).thenReturn(NOW);
        when(sale.getUpdatedAt()).thenReturn(NOW);
        when(sale.getVersion()).thenReturn(0L);
        return sale;
    }

    private static LotteryPayout payout(String id, String amount, LotteryPayoutStatus status) {
        LotteryOperator operator = operator();
        LotteryPayoutPolicy policy = policy();
        Store store = store();
        Register register = register();
        Device device = device();
        User cashier = user(CASHIER_ID, "cashier@example.local", "Cashier One");
        LotteryPayout payout = mock(LotteryPayout.class);
        when(payout.getId()).thenReturn(UUID.fromString(id));
        when(payout.getOperator()).thenReturn(operator);
        when(payout.getPolicy()).thenReturn(policy);
        when(payout.getStore()).thenReturn(store);
        when(payout.getRegister()).thenReturn(register);
        when(payout.getDevice()).thenReturn(device);
        when(payout.getCashier()).thenReturn(cashier);
        when(payout.getRegisterSession()).thenReturn(null);
        when(payout.getTicketNumber()).thenReturn("TICKET-1");
        when(payout.getValidationReference()).thenReturn("VAL-1");
        when(payout.getAmount()).thenReturn(new BigDecimal(amount));
        when(payout.getCurrencyCode()).thenReturn("USD");
        when(payout.getPayoutMethod()).thenReturn(LotteryPayoutMethod.CASH);
        when(payout.getStatus()).thenReturn(status);
        when(payout.getTicketValidationState()).thenReturn(LotteryVerificationState.VERIFIED);
        when(payout.getAgeVerificationState()).thenReturn(LotteryVerificationState.NOT_REQUIRED);
        when(payout.getIdentificationVerificationState()).thenReturn(LotteryVerificationState.NOT_REQUIRED);
        when(payout.getCashierApprovalLimit()).thenReturn(new BigDecimal("50.00"));
        when(payout.getManagerApprovalThreshold()).thenReturn(new BigDecimal("500.00"));
        when(payout.getOperatorReferralThreshold()).thenReturn(new BigDecimal("1000.00"));
        when(payout.getMaximumCashPayout()).thenReturn(new BigDecimal("999.00"));
        when(payout.isTicketValidationRequired()).thenReturn(true);
        when(payout.isAgeVerificationRequired()).thenReturn(false);
        when(payout.isIdentificationRequired()).thenReturn(false);
        when(payout.isAlternateRegisterAllowed()).thenReturn(true);
        when(payout.getBusinessDate()).thenReturn(LocalDate.parse("2026-07-27"));
        when(payout.getOccurredAt()).thenReturn(Instant.parse("2026-07-27T11:00:00Z"));
        when(payout.getValidatedBy()).thenReturn(null);
        when(payout.getValidatedAt()).thenReturn(null);
        when(payout.getAuthorizedBy()).thenReturn(null);
        when(payout.getAuthorizedAt()).thenReturn(null);
        when(payout.getPaidBy()).thenReturn(null);
        when(payout.getPaidAt()).thenReturn(null);
        when(payout.getRejectedBy()).thenReturn(null);
        when(payout.getRejectedAt()).thenReturn(null);
        when(payout.getRejectionReason()).thenReturn(null);
        when(payout.getNotes()).thenReturn(null);
        when(payout.getApprovals()).thenReturn(List.of());
        when(payout.getCreatedAt()).thenReturn(NOW);
        when(payout.getUpdatedAt()).thenReturn(NOW);
        when(payout.getVersion()).thenReturn(0L);
        return payout;
    }

    private static LotteryPayoutApproval approval(LotteryPayout payout, LotteryPayoutApprovalType type, String amount) {
        User manager = user(UUID.fromString("00000000-0000-0000-0000-000000000505"), "manager@example.local", "Manager One");
        LotteryPayoutApproval approval = mock(LotteryPayoutApproval.class);
        when(approval.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000605"));
        when(approval.getPayout()).thenReturn(payout);
        when(approval.getApprovalType()).thenReturn(type);
        when(approval.getApprovedBy()).thenReturn(manager);
        when(approval.getApprovedAt()).thenReturn(Instant.parse("2026-07-27T11:30:00Z"));
        when(approval.getPayoutAmount()).thenReturn(new BigDecimal(amount));
        when(approval.getThresholdAmount()).thenReturn(new BigDecimal("50.00"));
        when(approval.getNotes()).thenReturn("Manager approved");
        when(approval.getCreatedAt()).thenReturn(NOW);
        when(approval.getUpdatedAt()).thenReturn(NOW);
        when(approval.getVersion()).thenReturn(0L);
        return approval;
    }

    private static LotteryPayoutReversal reversal(LotteryPayout payout, String amount) {
        User cashier = user(CASHIER_ID, "cashier@example.local", "Cashier One");
        LotteryPayoutReversal reversal = mock(LotteryPayoutReversal.class);
        when(reversal.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000606"));
        when(reversal.getOriginalPayout()).thenReturn(payout);
        when(reversal.getReversedBy()).thenReturn(cashier);
        when(reversal.getAmount()).thenReturn(new BigDecimal(amount));
        when(reversal.getCurrencyCode()).thenReturn("USD");
        when(reversal.getOperationId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000702"));
        when(reversal.getReversedAt()).thenReturn(Instant.parse("2026-07-27T12:00:00Z"));
        when(reversal.getReason()).thenReturn("Ticket voided");
        when(reversal.getCreatedAt()).thenReturn(NOW);
        when(reversal.getUpdatedAt()).thenReturn(NOW);
        when(reversal.getVersion()).thenReturn(0L);
        return reversal;
    }

    private static LotterySaleCancellation cancellation(LotterySale sale, String amount) {
        User cashier = user(CASHIER_ID, "cashier@example.local", "Cashier One");
        LotterySaleCancellation cancellation = mock(LotterySaleCancellation.class);
        when(cancellation.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000607"));
        when(cancellation.getOriginalSale()).thenReturn(sale);
        when(cancellation.getCancelledBy()).thenReturn(cashier);
        when(cancellation.getAmount()).thenReturn(new BigDecimal(amount));
        when(cancellation.getCurrencyCode()).thenReturn("USD");
        when(cancellation.isCashReturned()).thenReturn(true);
        when(cancellation.getOperationId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000703"));
        when(cancellation.getCancelledAt()).thenReturn(Instant.parse("2026-07-27T12:30:00Z"));
        when(cancellation.getReason()).thenReturn("Customer cancelled");
        when(cancellation.getCreatedAt()).thenReturn(NOW);
        when(cancellation.getUpdatedAt()).thenReturn(NOW);
        when(cancellation.getVersion()).thenReturn(0L);
        return cancellation;
    }

    private static LotterySettlement settlement(String id, String grossSales, String payouts, String commission, String expected) {
        LotteryOperator operator = operator();
        TaxJurisdiction jurisdiction = jurisdiction();
        Store store = store();
        LotterySettlement settlement = mock(LotterySettlement.class);
        when(settlement.getId()).thenReturn(UUID.fromString(id));
        when(settlement.getOperator()).thenReturn(operator);
        when(settlement.getJurisdiction()).thenReturn(jurisdiction);
        when(settlement.getStore()).thenReturn(store);
        when(settlement.getPeriodStart()).thenReturn(LocalDate.parse("2026-07-01"));
        when(settlement.getPeriodEnd()).thenReturn(LocalDate.parse("2026-07-31"));
        when(settlement.getGrossSales()).thenReturn(new BigDecimal(grossSales));
        when(settlement.getTotalPayouts()).thenReturn(new BigDecimal(payouts));
        when(settlement.getCancellations()).thenReturn(new BigDecimal("20.00"));
        when(settlement.getAdjustments()).thenReturn(new BigDecimal("10.00"));
        when(settlement.getCommission()).thenReturn(new BigDecimal(commission));
        when(settlement.getExpectedSettlement()).thenReturn(new BigDecimal(expected));
        when(settlement.getCurrencyCode()).thenReturn("USD");
        when(settlement.getCalculatedAt()).thenReturn(NOW);
        when(settlement.getStatus()).thenReturn(LotterySettlementStatus.CALCULATED);
        when(settlement.getApprovedBy()).thenReturn(null);
        when(settlement.getApprovedAt()).thenReturn(null);
        when(settlement.getPostedBy()).thenReturn(null);
        when(settlement.getPostedAt()).thenReturn(null);
        when(settlement.getReopenedBy()).thenReturn(null);
        when(settlement.getReopenedAt()).thenReturn(null);
        when(settlement.getReopenReason()).thenReturn(null);
        when(settlement.getLifecycleNotes()).thenReturn(null);
        when(settlement.getCreatedAt()).thenReturn(NOW);
        when(settlement.getUpdatedAt()).thenReturn(NOW);
        when(settlement.getVersion()).thenReturn(0L);
        return settlement;
    }

    private static LotteryOperator operator() {
        LotteryOperator operator = mock(LotteryOperator.class);
        when(operator.getId()).thenReturn(OPERATOR_ID);
        when(operator.getCode()).thenReturn("ATL");
        when(operator.getName()).thenReturn("Atlantic Lottery");
        return operator;
    }

    private static Store store() {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(STORE_ID);
        when(store.getCode()).thenReturn("MAIN");
        when(store.getName()).thenReturn("Main Store");
        return store;
    }

    private static Register register() {
        Register register = mock(Register.class);
        when(register.getId()).thenReturn(REGISTER_ID);
        when(register.getCode()).thenReturn("R1");
        when(register.getName()).thenReturn("Register 1");
        return register;
    }

    private static Device device() {
        Device device = mock(Device.class);
        when(device.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000506"));
        when(device.getDeviceIdentifier()).thenReturn("POS-1");
        when(device.getDisplayName()).thenReturn("POS 1");
        return device;
    }

    private static User user(UUID id, String email, String displayName) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        when(user.getDisplayName()).thenReturn(displayName);
        return user;
    }

    private static LotteryPayoutPolicy policy() {
        LotteryPayoutPolicy policy = mock(LotteryPayoutPolicy.class);
        when(policy.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000507"));
        return policy;
    }

    private static TaxJurisdiction jurisdiction() {
        TaxJurisdiction jurisdiction = mock(TaxJurisdiction.class);
        when(jurisdiction.getId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000508"));
        when(jurisdiction.getCode()).thenReturn("NB");
        when(jurisdiction.getName()).thenReturn("New Brunswick");
        return jurisdiction;
    }
}
