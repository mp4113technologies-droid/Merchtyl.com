package com.merchtyl.registersession;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.cash.CashLedgerBreakdownResponse;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.device.Device;
import com.merchtyl.device.DeviceRepository;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRegisterAssignmentRepository;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.RefreshTokenService;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterSessionServiceTest {
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000903");
    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");

    private final RegisterSessionRepository registerSessionRepository = mock(RegisterSessionRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final RegisterRepository registerRepository = mock(RegisterRepository.class);
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserRegisterAssignmentRepository userRegisterAssignmentRepository = mock(UserRegisterAssignmentRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final CashLedgerService cashLedgerService = mock(CashLedgerService.class);
    private final RegisterSessionProperties properties = new RegisterSessionProperties();
    private final RegisterSessionService service = new RegisterSessionService(
            registerSessionRepository,
            storeRepository,
            registerRepository,
            deviceRepository,
            userRepository,
            userRegisterAssignmentRepository,
            auditService,
            cashLedgerService,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private Store store;
    private Register register;
    private Device device;
    private User cashier;

    @BeforeEach
    void setUp() {
        store = mock(Store.class);
        register = mock(Register.class);
        device = mock(Device.class);
        cashier = new User("cashier@example.local", "Cashier One", "hash");

        when(store.getId()).thenReturn(STORE_ID);
        when(store.isActive()).thenReturn(true);
        when(register.getId()).thenReturn(REGISTER_ID);
        when(register.getStore()).thenReturn(store);
        when(register.isActive()).thenReturn(true);
        when(device.getId()).thenReturn(DEVICE_ID);
        when(device.getStore()).thenReturn(store);
        when(device.getRegister()).thenReturn(register);
        when(device.isActive()).thenReturn(true);

        when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));
        when(registerRepository.findById(REGISTER_ID)).thenReturn(Optional.of(register));
        when(registerRepository.findByIdForUpdate(REGISTER_ID)).thenReturn(Optional.of(register));
        when(deviceRepository.findById(DEVICE_ID)).thenReturn(Optional.of(device));
        when(deviceRepository.findByIdForUpdate(DEVICE_ID)).thenReturn(Optional.of(device));
        when(userRepository.findByEmailIgnoreCase("cashier@example.local")).thenReturn(Optional.of(cashier));
        when(registerSessionRepository.saveAndFlush(any(RegisterSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashLedgerService.expectedCash(any(RegisterSession.class))).thenReturn(new BigDecimal("125.50"));
        when(cashLedgerService.breakdown(any(RegisterSession.class))).thenReturn(new CashLedgerBreakdownResponse(
                new BigDecimal("125.50"),
                new BigDecimal("10.00"),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
                new BigDecimal("130.50"),
                List.of()));
    }

    @Test
    void opensSessionForAssignedCashierAndAudits() {
        when(userRegisterAssignmentRepository.existsByUserAndRegister_Id(cashier, REGISTER_ID)).thenReturn(true);

        RegisterSessionResponse response = service.open(openRequest(), authentication("ROLE_CASHIER"));

        assertThat(response.storeId()).isEqualTo(STORE_ID);
        assertThat(response.registerId()).isEqualTo(REGISTER_ID);
        assertThat(response.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(response.assignedCashierId()).isEqualTo(cashier.getId());
        assertThat(response.openedByUserId()).isEqualTo(cashier.getId());
        assertThat(response.status()).isEqualTo(RegisterSessionStatus.OPEN);
        assertThat(response.openingCash()).isEqualByComparingTo("125.50");
        assertThat(response.expectedCash()).isEqualByComparingTo("125.50");
        assertThat(response.openedAt()).isEqualTo(NOW);
        verify(cashLedgerService).appendOpeningFloat(any(RegisterSession.class), org.mockito.ArgumentMatchers.eq(cashier));

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.REGISTER_SESSION_OPENED);
        assertThat(audit.getValue().entityType()).isEqualTo("REGISTER_SESSION");
        assertThat(audit.getValue().storeId()).isEqualTo(STORE_ID);
        assertThat(audit.getValue().registerId()).isEqualTo(REGISTER_ID);
    }

    @Test
    void opensSessionWithoutDeviceWhenEnforcementIsDisabled() {
        when(userRegisterAssignmentRepository.existsByUserAndRegister_Id(cashier, REGISTER_ID)).thenReturn(true);

        RegisterSessionResponse response = service.open(
                new RegisterSessionOpenRequest(STORE_ID, REGISTER_ID, null, new BigDecimal("125.50")),
                authentication("ROLE_CASHIER"));

        assertThat(response.deviceId()).isNull();
        assertThat(response.deviceName()).isNull();
        verify(deviceRepository, never()).findById(any());
        verify(deviceRepository, never()).findByIdForUpdate(any());
        verify(cashLedgerService).appendOpeningFloat(any(RegisterSession.class), org.mockito.ArgumentMatchers.eq(cashier));
    }

    @Test
    void requiresDeviceWhenEnforcementIsEnabled() {
        RegisterDeviceEnforcementProperties enforcement = new RegisterDeviceEnforcementProperties();
        enforcement.setEnabled(true);
        RegisterSessionService enforcedService = new RegisterSessionService(
                registerSessionRepository, storeRepository, registerRepository, deviceRepository, userRepository,
                userRegisterAssignmentRepository, auditService, cashLedgerService, properties,
                Clock.fixed(NOW, ZoneOffset.UTC), null, enforcement);

        assertThatThrownBy(() -> enforcedService.open(
                new RegisterSessionOpenRequest(STORE_ID, REGISTER_ID, null, new BigDecimal("125.50")),
                authentication("ROLE_CASHIER")))
                .isInstanceOf(RegisterDeviceRequiredException.class);

        verify(registerSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSecondOpenSessionForRegister() {
        when(registerSessionRepository.existsByRegister_IdAndStatus(REGISTER_ID, RegisterSessionStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> service.open(openRequest(), authentication("ROLE_MANAGER")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Register already has an open session");

        verify(registerSessionRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void rejectsOpeningCashSubmissionWhenRegisterAlreadyHasOpenSession() {
        RegisterSession existing = new RegisterSession(
                store, register, device, cashier, new BigDecimal("200.00"), NOW.minusSeconds(3600));
        when(registerSessionRepository.findFirstByRegister_IdAndStatusOrderByOpenedAtDesc(
                REGISTER_ID, RegisterSessionStatus.OPEN)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.open(
                new RegisterSessionOpenRequest(STORE_ID, REGISTER_ID, DEVICE_ID, new BigDecimal("999.00")),
                authentication("ROLE_TENANT_OWNER")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("REGISTER_OPENING_CASH_IMMUTABLE");

        assertThat(existing.getOpeningCash()).isEqualByComparingTo("200.00");
        verify(registerSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void cashierCannotOpenSecondRegisterSession() {
        when(userRegisterAssignmentRepository.existsByUserAndRegister_Id(cashier, REGISTER_ID)).thenReturn(true);
        when(registerSessionRepository.existsByAssignedCashier_IdAndStatus(cashier.getId(), RegisterSessionStatus.OPEN))
                .thenReturn(true);

        assertThatThrownBy(() -> service.open(openRequest(), authentication("ROLE_CASHIER")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CASHIER_ALREADY_HAS_OPEN_SESSION");

        verify(registerSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void cashierMustBeAssignedToRegister() {
        when(userRegisterAssignmentRepository.existsByUserAndRegister_Id(cashier, REGISTER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.open(openRequest(), authentication("ROLE_CASHIER")))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Cashier is not assigned");

        verify(registerSessionRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void currentCanBeFoundByDeviceIdentifier() {
        RegisterSession session = new RegisterSession(
                store,
                register,
                device,
                cashier,
                new BigDecimal("25.00"),
                NOW);
        when(registerSessionRepository.findFirstByDevice_DeviceIdentifierIgnoreCaseAndStatusOrderByOpenedAtDesc(
                "browser:test",
                RegisterSessionStatus.OPEN)).thenReturn(Optional.of(session));

        RegisterSessionResponse response = service.current(null, "browser:test", authentication("ROLE_CASHIER"));

        assertThat(response.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(response.status()).isEqualTo(RegisterSessionStatus.OPEN);
        assertThat(response.expectedCash()).isEqualByComparingTo("130.50");
        assertThat(response.reconciliation().totalIn()).isEqualByComparingTo("10.00");
    }

    @Test
    void cashierCannotResumeAnotherOperatorsDeviceSession() {
        User otherCashier = new User("other@example.local", "Other Cashier", "hash");
        RegisterSession session = new RegisterSession(
                store,
                register,
                device,
                otherCashier,
                new BigDecimal("25.00"),
                NOW);
        when(registerSessionRepository.findFirstByDevice_DeviceIdentifierIgnoreCaseAndStatusOrderByOpenedAtDesc(
                "browser:test",
                RegisterSessionStatus.OPEN)).thenReturn(Optional.of(session));

        RegisterSessionResponse response = service.current(null, "browser:test", authentication("ROLE_CASHIER"));

        assertThat(response).isNull();
        verify(cashLedgerService, never()).breakdown(session);
    }

    @Test
    void ownerOverridePreservesOpenerChangesOperatorAndRevokesDisplacedUser() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000910");
        User owner = new User("owner@example.local", "Owner One", "hash");
        owner.assignTenant(tenantId);
        cashier.assignTenant(tenantId);
        RegisterSession session = new RegisterSession(store, register, device, cashier, new BigDecimal("25.00"), NOW);
        RegisterSessionOperatorHistoryRepository historyRepository = mock(RegisterSessionOperatorHistoryRepository.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        ReflectionTestUtils.setField(service, "operatorHistoryRepository", historyRepository);
        ReflectionTestUtils.setField(service, "refreshTokenService", refreshTokenService);
        when(registerSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(userRepository.findByEmailIgnoreCase("owner@example.local")).thenReturn(Optional.of(owner));

        RegisterSessionResponse response = service.override(
                session.getId(),
                new RegisterSessionOverrideRequest("Operational takeover", 0L),
                new UsernamePasswordAuthenticationToken("owner@example.local", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_TENANT_OWNER"))));

        assertThat(response.id()).isEqualTo(session.getId());
        assertThat(response.openedByUserId()).isEqualTo(cashier.getId());
        assertThat(response.assignedCashierId()).isEqualTo(owner.getId());
        verify(historyRepository).save(any(RegisterSessionOperatorHistory.class));
        verify(refreshTokenService).revokeActiveTokensForUser(cashier, NOW);
        verify(auditService).record(org.mockito.ArgumentMatchers.argThat(command ->
                command.action() == AuditAction.REGISTER_SESSION_OVERRIDDEN));
    }

    @Test
    void closesOpenSessionWithLedgerExpectedCashAndAudits() {
        RegisterSession session = new RegisterSession(
                store,
                register,
                device,
                cashier,
                new BigDecimal("125.50"),
                NOW);
        when(registerSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        RegisterSessionResponse response = service.close(
                session.getId(),
                new RegisterSessionCloseRequest(new BigDecimal("130.00"), 0L),
                authentication("ROLE_CASHIER"));

        assertThat(response.status()).isEqualTo(RegisterSessionStatus.CLOSED);
        assertThat(response.countedCash()).isEqualByComparingTo("130.00");
        assertThat(response.expectedCashAtClose()).isEqualByComparingTo("130.50");
        assertThat(response.differenceCash()).isEqualByComparingTo("-0.50");
        assertThat(response.closedByUserId()).isEqualTo(cashier.getId());
        assertThat(response.closedAt()).isEqualTo(NOW);

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService, org.mockito.Mockito.atLeastOnce()).record(audit.capture());
        assertThat(audit.getAllValues().getLast().action()).isEqualTo(AuditAction.REGISTER_SESSION_CLOSED);
    }

    @Test
    void closeRejectsStaleVersion() {
        RegisterSession session = new RegisterSession(
                store,
                register,
                device,
                cashier,
                new BigDecimal("125.50"),
                NOW);
        when(registerSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.close(
                session.getId(),
                new RegisterSessionCloseRequest(new BigDecimal("130.00"), 9L),
                authentication("ROLE_CASHIER")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Register session was modified by another transaction");

        verify(registerSessionRepository, never()).saveAndFlush(session);
    }

    @Test
    void forceCloseRecordsReasonAndAudits() {
        RegisterSession session = new RegisterSession(
                store,
                register,
                device,
                cashier,
                new BigDecimal("125.50"),
                NOW);
        when(registerSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        RegisterSessionResponse response = service.forceClose(
                session.getId(),
                new RegisterSessionForceCloseRequest(new BigDecimal("125.00"), " Device failed ", 0L),
                authentication("ROLE_MANAGER"));

        assertThat(response.status()).isEqualTo(RegisterSessionStatus.FORCE_CLOSED);
        assertThat(response.forceCloseReason()).isEqualTo("Device failed");
        assertThat(response.differenceCash()).isEqualByComparingTo("-5.50");

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService, org.mockito.Mockito.atLeastOnce()).record(audit.capture());
        assertThat(audit.getAllValues().getLast().action()).isEqualTo(AuditAction.REGISTER_SESSION_FORCE_CLOSED);
    }

    @Test
    void searchBuildsRegisterReconciliationWithBulkLedgerBreakdowns() {
        RegisterSession session = new RegisterSession(
                store,
                register,
                device,
                cashier,
                new BigDecimal("125.50"),
                NOW);
        CashLedgerBreakdownResponse breakdown = new CashLedgerBreakdownResponse(
                new BigDecimal("125.50"),
                new BigDecimal("10.00"),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
                new BigDecimal("130.50"),
                List.of());
        when(registerSessionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(session)));
        when(cashLedgerService.breakdowns(List.of(session))).thenReturn(Map.of(session.getId(), breakdown));

        var response = service.search(new RegisterSessionSearchRequest(
                STORE_ID,
                REGISTER_ID,
                DEVICE_ID,
                cashier.getId(),
                RegisterSessionStatus.OPEN,
                NOW.minusSeconds(60),
                NOW.plusSeconds(60),
                0,
                20));

        assertThat(response.content()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(session.getId());
            assertThat(row.reconciliation().expectedCash()).isEqualByComparingTo("130.50");
        });
        verify(cashLedgerService).breakdowns(List.of(session));
        verify(cashLedgerService, never()).breakdown(session);
    }

    private static RegisterSessionOpenRequest openRequest() {
        return new RegisterSessionOpenRequest(STORE_ID, REGISTER_ID, DEVICE_ID, new BigDecimal("125.50"));
    }

    private static UsernamePasswordAuthenticationToken authentication(String role) {
        return new UsernamePasswordAuthenticationToken(
                "cashier@example.local",
                "n/a",
                List.of(new SimpleGrantedAuthority(role)));
    }
}
