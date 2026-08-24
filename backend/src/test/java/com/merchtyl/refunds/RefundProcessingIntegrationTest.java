package com.merchtyl.refunds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditRecordRepository;
import com.merchtyl.cash.CashLedgerDirection;
import com.merchtyl.cash.CashLedgerRepository;
import com.merchtyl.cash.CashLedgerSourceType;
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
import com.merchtyl.returns.ReturnCreateRequest;
import com.merchtyl.returns.ReturnItemRequest;
import com.merchtyl.returns.ReturnRepository;
import com.merchtyl.returns.ReturnResponse;
import com.merchtyl.returns.ReturnService;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.sales.SaleAddItemRequest;
import com.merchtyl.sales.SaleCreateDraftRequest;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.SalePaymentRequest;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleResponse;
import com.merchtyl.sales.SaleService;
import com.merchtyl.sales.SaleStatus;
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

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RefundProcessingIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    SaleService saleService;

    @Autowired
    ReturnService returnService;

    @Autowired
    RefundService refundService;

    @Autowired
    InventoryService inventoryService;

    @Autowired
    SaleRepository saleRepository;

    @Autowired
    ReturnRepository returnRepository;

    @Autowired
    RefundRepository refundRepository;

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

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void resetData() {
        auditRecordRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        refundRepository.deleteAll();
        returnRepository.deleteAll();
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
    void refundRestoresInventoryWritesCashLedgerAndReplaysIdempotently() throws Exception {
        Fixture fixture = fixture();
        SaleResponse payable = payableSale(fixture);
        saleService.completeIdempotently(payable.id(), "complete-refund-integration", auth(fixture.cashier()));
        SaleItem completedItem = saleRepository.findByIdForUpdate(payable.id()).orElseThrow().getItems().getFirst();
        ReturnResponse returnResponse = returnService.create(new ReturnCreateRequest(
                payable.id(),
                "Customer refund",
                List.of(new ReturnItemRequest(completedItem.getId(), new BigDecimal("1.0000"), null))), auth(fixture.cashier()));

        IdempotencyResult first = refundService.createIdempotently(new RefundCreateRequest(
                returnResponse.id(),
                "Refund to original tender",
                List.of(new RefundPaymentRequest(PaymentMethod.CASH, returnResponse.totalAmount(), payable.payments().getFirst().id(), null, null)),
                null), "refund-integration-key", auth(fixture.cashier()));
        IdempotencyResult replay = refundService.createIdempotently(new RefundCreateRequest(
                returnResponse.id(),
                "Refund to original tender",
                List.of(new RefundPaymentRequest(PaymentMethod.CASH, returnResponse.totalAmount(), payable.payments().getFirst().id(), null, null)),
                null), "refund-integration-key", auth(fixture.cashier()));

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.body()).isEqualTo(first.body());
        RefundResponse refund = objectMapper.readValue(first.body(), RefundResponse.class);

        assertThat(refundRepository.findAll()).hasSize(1);
        assertThat(refund.itemTaxes()).hasSize(1);
        assertThat(refund.itemTaxes().getFirst().taxAmount()).isEqualByComparingTo(returnResponse.taxAmount());
        assertThat(saleRepository.findByIdForUpdate(payable.id()).orElseThrow().getStatus()).isEqualTo(SaleStatus.PARTIALLY_REFUNDED);
        assertThat(inventoryBalanceRepository.findByStoreIdAndProductId(fixture.store().getId(), fixture.product().getId()).orElseThrow()
                .getQuantityOnHand()).isEqualByComparingTo("4.0000");
        assertThat(inventoryTransactionRepository.findAll().stream()
                .filter(transaction -> transaction.getTransactionType() == InventoryTransactionType.RETURN)
                .toList()).hasSize(1);

        var refundLedger = cashLedgerRepository.findByRegisterSession_IdOrderByOccurredAtAscCreatedAtAsc(fixture.session().getId()).stream()
                .filter(entry -> entry.getSourceType() == CashLedgerSourceType.CASH_REFUND)
                .toList();
        assertThat(refundLedger).hasSize(1);
        assertThat(refundLedger.getFirst().getDirection()).isEqualTo(CashLedgerDirection.OUT);
        assertThat(refundLedger.getFirst().getAmount()).isEqualByComparingTo(returnResponse.totalAmount());

        assertThat(auditRecordRepository.findAll().stream()
                .filter(record -> record.getAction().equals(AuditAction.REFUND_CREATED.name()))
                .toList()).hasSize(1);
    }

    private SaleResponse payableSale(Fixture fixture) {
        SaleResponse draft = saleService.createDraft(new SaleCreateDraftRequest(fixture.session().getId(), null, "POS"), auth(fixture.cashier()));
        SaleResponse withItem = saleService.addItem(draft.id(), new SaleAddItemRequest(
                fixture.product().getId(),
                new BigDecimal("2.0000"),
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
                withItem.totalAmount(),
                "cash drawer",
                null), auth(fixture.cashier()));
    }

    private Fixture fixture() throws Exception {
        Store store = storeRepository.saveAndFlush(store());
        Register register = registerRepository.saveAndFlush(register(store));
        Device device = deviceRepository.saveAndFlush(device(store, register));
        User cashier = userRepository.saveAndFlush(new User("cashier-refund@test.example", "Refund Cashier", "hash"));
        RegisterSession session = registerSessionRepository.saveAndFlush(session(store, register, device, cashier));
        Product product = productRepository.saveAndFlush(product());
        inventoryService.recordStockChange(new InventoryStockChangeRequest(
                store.getId(),
                product.getId(),
                InventoryTransactionType.OPENING_STOCK,
                new BigDecimal("5.0000"),
                "COUNT",
                null,
                "Opening stock",
                Instant.parse("2026-07-27T11:00:00Z"),
                null), auth(cashier));
        return new Fixture(store, register, device, cashier, session, product);
    }

    private static UsernamePasswordAuthenticationToken auth(User cashier) {
        return new UsernamePasswordAuthenticationToken(
                cashier.getEmail(),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
    }

    private static Product product() {
        return new Product(new ProductValues(
                "SKU-REFUND",
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
                Set.of(ProductCapability.ALLOW_RETURN)));
    }

    private static Store store() throws Exception {
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
                "STORE-REFUND",
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

    private static Register register(Store store) throws Exception {
        Constructor<Register> constructor = Register.class.getDeclaredConstructor(
                Store.class,
                String.class,
                String.class,
                String.class,
                boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(store, "REG-REFUND", "Front Register", "Front counter", true);
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
                "browser:refund",
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
