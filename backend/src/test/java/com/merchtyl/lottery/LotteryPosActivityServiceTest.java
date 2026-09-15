package com.merchtyl.lottery;

import com.merchtyl.audit.AuditService;
import com.merchtyl.cash.*;
import com.merchtyl.eod.BusinessDay;
import com.merchtyl.features.FeatureCode;
import com.merchtyl.features.FeatureService;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterType;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LotteryPosActivityServiceTest {
    private final LotteryPosActivityRepository repository = mock(LotteryPosActivityRepository.class);
    private final RegisterSessionRepository sessions = mock(RegisterSessionRepository.class);
    private final StoreAccessService storeAccess = mock(StoreAccessService.class);
    private final FeatureService features = mock(FeatureService.class);
    private final CashLedgerService cashLedger = mock(CashLedgerService.class);
    private final AuditService audit = mock(AuditService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final RegisterSession session = mock(RegisterSession.class);
    private final Store store = mock(Store.class);
    private final Register register = mock(Register.class);
    private final User cashier = mock(User.class);
    private final BusinessDay day = mock(BusinessDay.class);
    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final UUID registerId = UUID.randomUUID();
    private final UUID cashierId = UUID.randomUUID();
    private LotteryPosActivityService service;

    @BeforeEach
    void setUp() {
        service = new LotteryPosActivityService(repository, sessions, storeAccess, features, cashLedger, audit,
                Clock.fixed(Instant.parse("2026-09-15T14:00:00Z"), ZoneOffset.UTC));
        when(repository.findByOperationId(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(sessions.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(storeAccess.currentTenantUser(authentication)).thenReturn(cashier);
        when(storeAccess.canAccessStore(cashierId, storeId)).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(ignored -> List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
        when(session.getStore()).thenReturn(store);
        when(session.getRegister()).thenReturn(register);
        when(session.getAssignedCashier()).thenReturn(cashier);
        when(session.getBusinessDay()).thenReturn(day);
        when(session.getStatus()).thenReturn(RegisterSessionStatus.OPEN);
        when(session.isBusinessDayOperational()).thenReturn(true);
        when(store.getId()).thenReturn(storeId);
        when(store.getTenantId()).thenReturn(tenantId);
        when(store.getCurrencyCode()).thenReturn("CAD");
        when(store.isActive()).thenReturn(true);
        when(register.getId()).thenReturn(registerId);
        when(register.getType()).thenReturn(RegisterType.RETAIL);
        when(register.isActive()).thenReturn(true);
        when(cashier.getId()).thenReturn(cashierId);
        when(cashier.getTenantId()).thenReturn(tenantId);
        when(day.getBusinessDate()).thenReturn(LocalDate.of(2026, 9, 15));
    }

    @Test
    void soldUsesOpenRetailSessionWithoutDeviceAndAddsCash() {
        LotteryPosActivityResponse response = service.record(
                new LotteryPosActivityRequest(sessionId, LotteryPosActivityType.SOLD, new BigDecimal("100.00"), UUID.randomUUID()),
                authentication);

        assertThat(response.type()).isEqualTo(LotteryPosActivityType.SOLD);
        assertThat(response.source()).isEqualTo("MANUAL_POS");
        ArgumentCaptor<CashLedgerEntryCommand> ledger = ArgumentCaptor.forClass(CashLedgerEntryCommand.class);
        verify(cashLedger).append(ledger.capture());
        assertThat(ledger.getValue().sourceType()).isEqualTo(CashLedgerSourceType.LOTTERY_SALE_CASH);
        assertThat(ledger.getValue().direction()).isEqualTo(CashLedgerDirection.IN);
        assertThat(ledger.getValue().amount()).isEqualByComparingTo("100.00");
        verify(features).requireEnabled(FeatureCode.LOTTERY_SALES, storeId, registerId);
    }

    @Test
    void winUsesSameSessionAndRecordsCashOut() {
        service.record(new LotteryPosActivityRequest(sessionId, LotteryPosActivityType.WIN,
                new BigDecimal("25.00"), UUID.randomUUID()), authentication);

        ArgumentCaptor<CashLedgerEntryCommand> ledger = ArgumentCaptor.forClass(CashLedgerEntryCommand.class);
        verify(cashLedger).append(ledger.capture());
        assertThat(ledger.getValue().sourceType()).isEqualTo(CashLedgerSourceType.LOTTERY_PAYOUT_CASH);
        assertThat(ledger.getValue().direction()).isEqualTo(CashLedgerDirection.OUT);
        assertThat(ledger.getValue().amount()).isEqualByComparingTo("25.00");
    }
}
