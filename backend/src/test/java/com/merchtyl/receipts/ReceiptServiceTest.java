package com.merchtyl.receipts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ConflictException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.SaleStatus;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-27T12:30:00Z");

    private final ReceiptRepository receiptRepository = mock(ReceiptRepository.class);
    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final User cashier = new User("cashier@example.test", "Cashier One", "hash");
    private final ReceiptService service = new ReceiptService(
            receiptRepository,
            saleRepository,
            userRepository,
            auditService,
            objectMapper,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(userRepository.findByEmailIgnoreCase("cashier@example.test")).thenReturn(Optional.of(cashier));
        when(receiptRepository.saveAndFlush(any(Receipt.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getForSaleGeneratesReceiptFromImmutableSaleSnapshots() throws Exception {
        Sale sale = completedSale();
        when(receiptRepository.findBySale_Id(sale.getId())).thenReturn(Optional.empty());
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

        ReceiptResponse response = service.getForSale(sale.getId(), auth());

        assertThat(response.receiptNumber()).startsWith("RCT-2026-07-27-");
        assertThat(response.generatedAt()).isEqualTo(sale.getCompletedAt());
        assertThat(response.document().brandName()).isEqualTo("Merchtyl");
        assertThat(response.document().store().name()).isEqualTo("Main Store");
        assertThat(response.document().register().code()).isEqualTo("FRONT-1");
        assertThat(response.document().cashier().displayName()).isEqualTo("Cashier One");
        assertThat(response.document().items()).hasSize(1);
        assertThat(response.document().items().getFirst().completedProductCost()).isEqualByComparingTo("2.0000");
        assertThat(response.document().items().getFirst().completedProductPrice()).isEqualByComparingTo("5.0000");
        assertThat(response.document().items().getFirst().completedProductCapabilities()).contains("TRACK_INVENTORY");
        assertThat(response.document().discountAmount()).isEqualByComparingTo("1.00");
        assertThat(response.document().taxSummaries()).hasSize(1);
        assertThat(response.document().taxSummaries().getFirst().taxAmount()).isEqualByComparingTo("1.35");
        assertThat(response.document().payments().getFirst().method()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.document().cashTendered()).isEqualByComparingTo("20.00");
        assertThat(response.document().changeDue()).isEqualByComparingTo("10.65");
        verify(receiptRepository).saveAndFlush(any(Receipt.class));

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.RECEIPT_GENERATED);
        assertThat(audit.getValue().entityType()).isEqualTo("RECEIPT");
    }

    @Test
    void getForSaleRejectsNonCompletedSales() throws Exception {
        Sale sale = draftSale();
        when(receiptRepository.findBySale_Id(sale.getId())).thenReturn(Optional.empty());
        when(saleRepository.findById(sale.getId())).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> service.getForSale(sale.getId(), auth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Receipt can only be generated for a completed sale");

        verify(receiptRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void reprintIncrementsCounterAndReturnsSameDocument() throws Exception {
        Sale sale = completedSale();
        ReceiptDocumentDto document = documentFor(sale);
        Receipt receipt = new Receipt(sale, document.receiptNumber(), sale.getCompletedAt(), objectMapper.writeValueAsString(document));
        when(receiptRepository.findForUpdateBySale_Id(sale.getId())).thenReturn(Optional.of(receipt));

        ReceiptResponse response = service.reprintForSale(sale.getId(), auth());

        assertThat(response.reprintCount()).isEqualTo(1);
        assertThat(response.lastReprintedAt()).isEqualTo(NOW);
        assertThat(response.document().receiptNumber()).isEqualTo(document.receiptNumber());
        verify(receiptRepository).saveAndFlush(receipt);

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.RECEIPT_REPRINTED);
    }

    private static ReceiptDocumentDto documentFor(Sale sale) {
        return new ReceiptDocumentDto(
                "Merchtyl",
                "Point of sale receipt",
                new ReceiptStoreDto(sale.getStore().getId(), "MAIN", "Main Store", null, "100 Market Street", null, null),
                new ReceiptRegisterDto(sale.getRegister().getId(), "FRONT-1", "Front Register"),
                new ReceiptCashierDto(sale.getCompletedBy().getId(), "Cashier One", "cashier@example.test"),
                "RCT-2026-07-27-" + sale.getId().toString().substring(0, 8).toUpperCase(),
                sale.getId(),
                sale.getId().toString(),
                sale.getBusinessDate(),
                sale.getCompletedAt(),
                sale.getCurrencyCode(),
                List.of(),
                sale.getSubtotalAmount(),
                sale.getDiscountAmount(),
                List.of(),
                sale.getEstimatedTaxAmount(),
                sale.getTotalAmount(),
                List.of(),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2));
    }

    private Sale completedSale() throws Exception {
        Sale sale = draftSale();
        SaleItem item = saleItem(sale);
        invoke(item, "snapshotForCompletion");
        invoke(sale, "addItem", new Class<?>[]{SaleItem.class}, item);
        invoke(item, "setCalculatedAmounts", new Class<?>[]{BigDecimal.class, BigDecimal.class, BigDecimal.class},
                new BigDecimal("9.00"), new BigDecimal("1.35"), new BigDecimal("10.35"));
        invoke(sale, "setTotals", new Class<?>[]{BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class},
                new BigDecimal("10.00"), new BigDecimal("1.00"), new BigDecimal("1.35"), new BigDecimal("10.35"));
        invoke(sale, "addPayment", new Class<?>[]{Class.forName("com.merchtyl.sales.Payment")},
                payment(sale, new BigDecimal("10.35"), new BigDecimal("20.00"), new BigDecimal("10.65")));
        invoke(sale, "complete", new Class<?>[]{User.class, Instant.class}, cashier, NOW);
        return sale;
    }

    private Sale draftSale() throws Exception {
        Constructor<Sale> constructor = Sale.class.getDeclaredConstructor(
                Store.class,
                Register.class,
                RegisterSession.class,
                User.class,
                java.util.UUID.class,
                LocalDate.class,
                String.class,
                String.class,
                boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(store(), register(), registerSession(), cashier, null, LocalDate.parse("2026-07-27"), "POS", "USD", false);
    }

    private SaleItem saleItem(Sale sale) throws Exception {
        Constructor<SaleItem> constructor = SaleItem.class.getDeclaredConstructor(
                Sale.class,
                Product.class,
                BigDecimal.class,
                BigDecimal.class,
                BigDecimal.class,
                boolean.class,
                boolean.class,
                String.class,
                String.class,
                java.util.UUID.class,
                String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                sale,
                product(),
                new BigDecimal("2.0000"),
                new BigDecimal("5.0000"),
                new BigDecimal("1.00"),
                false,
                false,
                null,
                null,
                null,
                null);
    }

    private Object payment(Sale sale, BigDecimal amount, BigDecimal cashTendered, BigDecimal changeDue) throws Exception {
        Class<?> paymentClass = Class.forName("com.merchtyl.sales.Payment");
        Constructor<?> constructor = paymentClass.getDeclaredConstructor(
                Sale.class,
                PaymentMethod.class,
                BigDecimal.class,
                String.class,
                BigDecimal.class,
                BigDecimal.class,
                String.class,
                String.class,
                User.class,
                Instant.class);
        constructor.setAccessible(true);
        return constructor.newInstance(sale, PaymentMethod.CASH, amount, "USD", cashTendered, changeDue, null, null, cashier, NOW);
    }

    private Store store() throws Exception {
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
        return constructor.newInstance("MAIN", "Main Store", null, "US", "CA", "100 Market Street", null, null, "USD", "en-US", "America/Los_Angeles", false, false, true);
    }

    private Register register() throws Exception {
        Constructor<Register> constructor = Register.class.getDeclaredConstructor(Store.class, String.class, String.class, String.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(store(), "FRONT-1", "Front Register", "Front counter", true);
    }

    private RegisterSession registerSession() {
        return mock(RegisterSession.class);
    }

    private static Product product() {
        return new Product(new ProductValues(
                "COFFEE",
                "Coffee",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                new BigDecimal("2.0000"),
                new BigDecimal("5.0000"),
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

    private static void invoke(Object target, String method) throws Exception {
        Method reflected = target.getClass().getDeclaredMethod(method);
        reflected.setAccessible(true);
        reflected.invoke(target);
    }

    private static void invoke(Object target, String method, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method reflected = target.getClass().getDeclaredMethod(method, parameterTypes);
        reflected.setAccessible(true);
        reflected.invoke(target, args);
    }

    private static UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(
                "cashier@example.test",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
    }
}
