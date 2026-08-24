package com.merchtyl.sales;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditRecordRepository;
import com.merchtyl.cash.CashLedgerDirection;
import com.merchtyl.cash.CashLedgerRepository;
import com.merchtyl.cash.CashLedgerSourceType;
import com.merchtyl.common.ConflictException;
import com.merchtyl.device.Device;
import com.merchtyl.device.DeviceRepository;
import com.merchtyl.idempotency.IdempotencyRecordRepository;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.inventory.InventoryBalanceRepository;
import com.merchtyl.inventory.InventoryService;
import com.merchtyl.inventory.InventoryStockChangeRequest;
import com.merchtyl.inventory.InventoryTransactionRepository;
import com.merchtyl.inventory.InventoryTransactionType;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SaleCompletionIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    SaleService saleService;

    @Autowired
    InventoryService inventoryService;

    @Autowired
    SaleRepository saleRepository;

    @Autowired
    CashLedgerRepository cashLedgerRepository;

    @Autowired
    InventoryBalanceRepository inventoryBalanceRepository;

    @Autowired
    InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    AuditRecordRepository auditRecordRepository;

    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    RegisterSessionRepository registerSessionRepository;

    @Autowired
    DeviceRepository deviceRepository;

    @Autowired
    RegisterRepository registerRepository;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void resetData() {
        auditRecordRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        cashLedgerRepository.deleteAll();
        saleRepository.deleteAll();
        inventoryTransactionRepository.deleteAll();
        inventoryBalanceRepository.deleteAll();
        registerSessionRepository.deleteAll();
        deviceRepository.deleteAll();
        registerRepository.deleteAll();
        productRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void completeSaleAtomicallyWritesInventoryLedgerSnapshotsAuditAndReplaysIdempotently() throws Exception {
        Fixture fixture = fixture("COMPLETE", false, new BigDecimal("10.0000"));
        SaleResponse sale = payableSale(fixture, new BigDecimal("2.0000"), new BigDecimal("50.00"));

        IdempotencyResult first = saleService.completeIdempotently(sale.id(), "complete-key", auth(fixture.cashier()));
        IdempotencyResult replay = saleService.completeIdempotently(sale.id(), "complete-key", auth(fixture.cashier()));

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(first.body()).contains("\"status\":\"COMPLETED\"");
        assertThat(replay.body()).isEqualTo(first.body());

        Sale completed = saleRepository.findByIdForUpdate(sale.id()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(completed.getItems()).hasSize(1);
        assertThat(completed.getItems().getFirst().getCompletedProductCost()).isEqualByComparingTo("2.0000");
        assertThat(completed.getItems().getFirst().getCompletedProductPrice()).isEqualByComparingTo("10.0000");
        assertThat(completed.getItems().getFirst().getCompletedProductCapabilities()).contains("TRACK_INVENTORY");

        assertThat(inventoryBalanceRepository.findByStoreIdAndProductId(fixture.store().getId(), fixture.product().getId()).orElseThrow()
                .getQuantityOnHand()).isEqualByComparingTo("8.0000");
        assertThat(inventoryTransactionRepository.findAll().stream()
                .filter(transaction -> transaction.getTransactionType() == InventoryTransactionType.SALE)
                .toList()).hasSize(1);

        var ledgerEntries = cashLedgerRepository.findByRegisterSession_IdOrderByOccurredAtAscCreatedAtAsc(fixture.session().getId());
        assertThat(ledgerEntries).hasSize(2);
        assertThat(ledgerEntries.get(0).getSourceType()).isEqualTo(CashLedgerSourceType.SALE_CASH_RECEIPT);
        assertThat(ledgerEntries.get(0).getDirection()).isEqualTo(CashLedgerDirection.IN);
        assertThat(ledgerEntries.get(0).getAmount()).isEqualByComparingTo("50.00");
        assertThat(ledgerEntries.get(1).getSourceType()).isEqualTo(CashLedgerSourceType.SALE_CHANGE_GIVEN);
        assertThat(ledgerEntries.get(1).getDirection()).isEqualTo(CashLedgerDirection.OUT);
        assertThat(ledgerEntries.get(1).getAmount()).isEqualByComparingTo("30.00");

        assertThat(auditRecordRepository.findAll().stream()
                .filter(record -> record.getAction().equals(AuditAction.SALE_COMPLETED.name()))
                .toList()).hasSize(1);
    }

    @Test
    void completeSaleAllowsInventoryToBecomeNegative() throws Exception {
        Fixture fixture = fixture("ROLLBACK", false, BigDecimal.ZERO);
        SaleResponse sale = payableSale(fixture, new BigDecimal("2.0000"), new BigDecimal("20.00"));

        saleService.completeIdempotently(sale.id(), "negative-stock-key", auth(fixture.cashier()));

        Sale completed = saleRepository.findByIdForUpdate(sale.id()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(inventoryBalanceRepository.findByStoreIdAndProductId(fixture.store().getId(), fixture.product().getId()).orElseThrow()
                .getQuantityOnHand()).isEqualByComparingTo("-2.0000");
        assertThat(inventoryTransactionRepository.findAll().stream()
                .filter(transaction -> transaction.getTransactionType() == InventoryTransactionType.SALE)
                .findFirst().orElseThrow().getResultingQuantity()).isEqualByComparingTo("-2.0000");
        assertThat(cashLedgerRepository.findByRegisterSession_IdOrderByOccurredAtAscCreatedAtAsc(fixture.session().getId())).isNotEmpty();
        assertThat(auditRecordRepository.findAll().stream()
                .filter(record -> record.getAction().equals(AuditAction.SALE_COMPLETED.name()))
                .toList()).hasSize(1);
    }

    private SaleResponse payableSale(Fixture fixture, BigDecimal quantity, BigDecimal cashTendered) {
        SaleResponse draft = saleService.createDraft(new SaleCreateDraftRequest(fixture.session().getId(), null, "POS"), auth(fixture.cashier()));
        SaleResponse withItem = saleService.addItem(draft.id(), new SaleAddItemRequest(
                fixture.product().getId(),
                quantity,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                null), auth(fixture.cashier()));
        return saleService.recordPayment(withItem.id(), new SalePaymentRequest(
                PaymentMethod.CASH,
                withItem.totalAmount(),
                cashTendered,
                "cash drawer",
                null), auth(fixture.cashier()));
    }

    private Fixture fixture(String suffix, boolean negativeStockAllowed, BigDecimal openingStock) throws Exception {
        Store store = storeRepository.saveAndFlush(store("STORE-" + suffix, negativeStockAllowed));
        Register register = registerRepository.saveAndFlush(register(store, "REG-" + suffix));
        Device device = deviceRepository.saveAndFlush(device(store, register, "browser:" + suffix.toLowerCase()));
        User cashier = userRepository.saveAndFlush(new User("cashier-" + suffix.toLowerCase() + "@sale.test", "Cashier " + suffix, "hash"));
        RegisterSession session = registerSessionRepository.saveAndFlush(session(store, register, device, cashier));
        Product product = productRepository.saveAndFlush(product("SKU-" + suffix));
        if (openingStock.signum() != 0) {
            inventoryService.recordStockChange(new InventoryStockChangeRequest(
                    store.getId(),
                    product.getId(),
                    InventoryTransactionType.OPENING_STOCK,
                    openingStock,
                    "COUNT",
                    null,
                    "Opening stock",
                    Instant.parse("2026-07-27T11:00:00Z"),
                    null), auth(cashier));
        }
        return new Fixture(store, register, device, cashier, session, product);
    }

    private static UsernamePasswordAuthenticationToken auth(User cashier) {
        return new UsernamePasswordAuthenticationToken(
                cashier.getEmail(),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
    }

    private static Product product(String sku) {
        return new Product(new ProductValues(
                sku,
                "House Coffee",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                new BigDecimal("2.0000"),
                new BigDecimal("10.0000"),
                null,
                null,
                true,
                true,
                false,
                null,
                null,
                List.of(),
                List.of(),
                Set.of(ProductCapability.TRACK_INVENTORY)));
    }

    private static Store store(String code, boolean negativeStockAllowed) throws Exception {
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
                negativeStockAllowed,
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

    private static Device device(Store store, Register register, String fingerprint) throws Exception {
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
                fingerprint,
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

    private record Fixture(
            Store store,
            Register register,
            Device device,
            User cashier,
            RegisterSession session,
            Product product) {
    }
}
