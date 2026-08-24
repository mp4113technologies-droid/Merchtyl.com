package com.merchtyl.cash;

import com.merchtyl.common.ConflictException;
import com.merchtyl.device.Device;
import com.merchtyl.device.DeviceRepository;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterRepository;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CashLedgerConcurrencyIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CashLedgerService cashLedgerService;

    @Autowired
    CashLedgerRepository cashLedgerRepository;

    @Autowired
    RegisterSessionRepository registerSessionRepository;

    @Autowired
    DeviceRepository deviceRepository;

    @Autowired
    RegisterRepository registerRepository;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
        cashLedgerRepository.deleteAll();
        registerSessionRepository.deleteAll();
        deviceRepository.deleteAll();
        registerRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentEntriesWithSameOperationIdAreRecordedOnceAndExpectedCashIsLedgerSum() throws Exception {
        Store store = storeRepository.saveAndFlush(store("MAIN"));
        Register register = registerRepository.saveAndFlush(register(store, "FRONT-1"));
        Device device = deviceRepository.saveAndFlush(device(store, register));
        User cashier = userRepository.saveAndFlush(new User("cashier@cash.test", "Cashier", "hash"));
        RegisterSession session = registerSessionRepository.saveAndFlush(session(store, register, device, cashier));

        cashLedgerService.appendOpeningFloat(session, cashier);
        UUID operationId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> receipt = () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                cashLedgerService.append(new CashLedgerEntryCommand(
                        store,
                        register,
                        session,
                        CashLedgerSourceType.SALE_CASH_RECEIPT,
                        UUID.randomUUID(),
                        CashLedgerDirection.IN,
                        new BigDecimal("20.00"),
                        "USD",
                        LocalDate.parse("2026-07-27"),
                        Instant.parse("2026-07-27T12:05:00Z"),
                        cashier,
                        operationId,
                        "Cash receipt"));
                return true;
            } catch (ConflictException exception) {
                return false;
            }
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(receipt);
            var second = executor.submit(receipt);
            start.countDown();

            List<Boolean> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(cashLedgerRepository.findAll()).hasSize(2);
            assertThat(cashLedgerService.expectedCash(session.getId())).isEqualByComparingTo("120.00");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ledgerRowsAreImmutable() throws Exception {
        Store store = storeRepository.saveAndFlush(store("MAIN"));
        Register register = registerRepository.saveAndFlush(register(store, "FRONT-1"));
        Device device = deviceRepository.saveAndFlush(device(store, register));
        User cashier = userRepository.saveAndFlush(new User("cashier-immutable@cash.test", "Cashier", "hash"));
        RegisterSession session = registerSessionRepository.saveAndFlush(session(store, register, device, cashier));
        CashLedgerEntryResponse entry = cashLedgerService.appendOpeningFloat(session, cashier);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE cash_ledger_entries SET notes = ? WHERE id = ?",
                "mutated",
                entry.id()))
                .hasMessageContaining("cash_ledger_entries are immutable");
    }

    private static Store store(String code) throws Exception {
        Constructor<Store> constructor = Store.class.getDeclaredConstructor(
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                boolean.class,
                boolean.class,
                boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                code,
                "Main Store",
                null,
                "US",
                "CA",
                "100 Market Street",
                null,
                null,
                "USD",
                "en-US",
                "America/Los_Angeles",
                false,
                false,
                true);
    }

    private static Register register(Store store, String code) throws Exception {
        Constructor<Register> constructor = Register.class.getDeclaredConstructor(
                Store.class,
                String.class,
                String.class,
                String.class,
                boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(store, code, "Front Register", "Front counter", true);
    }

    private static Device device(Store store, Register register) throws Exception {
        Constructor<Device> constructor = Device.class.getDeclaredConstructor(
                Store.class,
                Register.class,
                String.class,
                String.class,
                String.class,
                boolean.class,
                Instant.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                store,
                register,
                "browser:cash-ledger-test",
                "Front Browser",
                "BROWSER_POS",
                true,
                Instant.parse("2026-07-27T12:00:00Z"));
    }

    private static RegisterSession session(Store store, Register register, Device device, User cashier) throws Exception {
        Constructor<RegisterSession> constructor = RegisterSession.class.getDeclaredConstructor(
                Store.class,
                Register.class,
                Device.class,
                User.class,
                BigDecimal.class,
                Instant.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                store,
                register,
                device,
                cashier,
                new BigDecimal("100.00"),
                Instant.parse("2026-07-27T12:00:00Z"));
    }
}
