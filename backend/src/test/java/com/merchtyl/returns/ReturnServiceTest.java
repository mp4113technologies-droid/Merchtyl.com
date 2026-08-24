package com.merchtyl.returns;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
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

class ReturnServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-28T14:00:00Z");

    private final ReturnRepository returnRepository = mock(ReturnRepository.class);
    private final ReturnItemRepository returnItemRepository = mock(ReturnItemRepository.class);
    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final User manager = new User("manager@example.test", "Manager One", "hash");
    private final ReturnService service = new ReturnService(
            returnRepository,
            returnItemRepository,
            saleRepository,
            userRepository,
            auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(userRepository.findByEmailIgnoreCase("manager@example.test")).thenReturn(Optional.of(manager));
        when(returnRepository.saveAndFlush(any(Return.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(returnItemRepository.returnedQuantityForSaleItem(any())).thenReturn(BigDecimal.ZERO.setScale(4));
        when(returnItemRepository.returnedSubtotalForSaleItem(any())).thenReturn(BigDecimal.ZERO.setScale(2));
        when(returnItemRepository.returnedTaxForSaleItem(any())).thenReturn(BigDecimal.ZERO.setScale(2));
        when(returnItemRepository.returnedTotalForSaleItem(any())).thenReturn(BigDecimal.ZERO.setScale(2));
    }

    @Test
    void createsPartialReturnAndPreservesOriginalSaleItemSnapshots() throws Exception {
        Sale sale = completedSale(returnableProduct());
        SaleItem saleItem = sale.getItems().getFirst();
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));

        ReturnResponse response = service.create(new ReturnCreateRequest(
                sale.getId(),
                "Customer changed mind",
                List.of(new ReturnItemRequest(saleItem.getId(), new BigDecimal("1.0000"), null))), auth());

        assertThat(response.originalSaleId()).isEqualTo(sale.getId());
        assertThat(response.businessDate()).isEqualTo(LocalDate.parse("2026-07-28"));
        assertThat(response.occurredAt()).isEqualTo(NOW);
        assertThat(response.currencyCode()).isEqualTo("USD");
        assertThat(response.totalQuantity()).isEqualByComparingTo("1.0000");
        assertThat(response.subtotalAmount()).isEqualByComparingTo("5.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("0.75");
        assertThat(response.totalAmount()).isEqualByComparingTo("5.75");
        assertThat(response.fullReturn()).isFalse();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().originalSaleItemId()).isEqualTo(saleItem.getId());
        assertThat(response.items().getFirst().originalUnitPrice()).isEqualByComparingTo("5.0000");
        assertThat(response.items().getFirst().originalTaxAmount()).isEqualByComparingTo("1.50");
        assertThat(response.items().getFirst().originalLineTotal()).isEqualByComparingTo("11.50");
        assertThat(response.items().getFirst().originalProductCapabilities()).contains("ALLOW_RETURN");
        assertThat(response.items().getFirst().reason()).isEqualTo("Customer changed mind");

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.RETURN_CREATED);
        assertThat(audit.getValue().entityType()).isEqualTo("RETURN");
        assertThat(audit.getValue().reason()).isEqualTo("Customer changed mind");
    }

    @Test
    void reportsFullReturnWhenAllPurchasedQuantityHasBeenReturned() throws Exception {
        Sale sale = completedSale(returnableProduct());
        SaleItem saleItem = sale.getItems().getFirst();
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));
        when(returnItemRepository.returnedQuantityForSaleItem(saleItem.getId()))
                .thenReturn(BigDecimal.ZERO.setScale(4), new BigDecimal("2.0000"));

        ReturnResponse response = service.create(new ReturnCreateRequest(
                sale.getId(),
                "Defective",
                List.of(new ReturnItemRequest(saleItem.getId(), new BigDecimal("2.0000"), "Defective"))), auth());

        assertThat(response.fullReturn()).isTrue();
        assertThat(response.totalAmount()).isEqualByComparingTo("11.50");
    }

    @Test
    void finalReturnUsesRemainingOriginalAmountsInsteadOfRoundedProration() throws Exception {
        Sale sale = completedSale(
                returnableProduct(),
                new BigDecimal("3.0000"),
                new BigDecimal("0.05"),
                new BigDecimal("0.01"),
                new BigDecimal("0.06"));
        SaleItem saleItem = sale.getItems().getFirst();
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));
        when(returnItemRepository.returnedQuantityForSaleItem(saleItem.getId()))
                .thenReturn(new BigDecimal("2.0000"), new BigDecimal("3.0000"));
        when(returnItemRepository.returnedSubtotalForSaleItem(saleItem.getId())).thenReturn(new BigDecimal("0.04"));
        when(returnItemRepository.returnedTaxForSaleItem(saleItem.getId())).thenReturn(new BigDecimal("0.00"));
        when(returnItemRepository.returnedTotalForSaleItem(saleItem.getId())).thenReturn(new BigDecimal("0.04"));

        ReturnResponse response = service.create(new ReturnCreateRequest(
                sale.getId(),
                "Final item",
                List.of(new ReturnItemRequest(saleItem.getId(), new BigDecimal("1.0000"), null))), auth());

        assertThat(response.fullReturn()).isTrue();
        assertThat(response.subtotalAmount()).isEqualByComparingTo("0.01");
        assertThat(response.taxAmount()).isEqualByComparingTo("0.01");
        assertThat(response.totalAmount()).isEqualByComparingTo("0.02");
        assertThat(response.items().getFirst().returnSubtotalAmount()).isEqualByComparingTo("0.01");
        assertThat(response.items().getFirst().returnTaxAmount()).isEqualByComparingTo("0.01");
        assertThat(response.items().getFirst().returnTotalAmount()).isEqualByComparingTo("0.02");
    }

    @Test
    void preventsReturningMoreThanPurchasedAcrossPriorReturns() throws Exception {
        Sale sale = completedSale(returnableProduct());
        SaleItem saleItem = sale.getItems().getFirst();
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));
        when(returnItemRepository.returnedQuantityForSaleItem(saleItem.getId())).thenReturn(new BigDecimal("1.5000"));

        assertThatThrownBy(() -> service.create(new ReturnCreateRequest(
                sale.getId(),
                "Too many",
                List.of(new ReturnItemRequest(saleItem.getId(), new BigDecimal("1.0000"), null))), auth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Return quantity cannot exceed purchased quantity");

        verify(returnRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsProductsWithoutReturnCapabilitySnapshot() throws Exception {
        Sale sale = completedSale(nonReturnableProduct());
        SaleItem saleItem = sale.getItems().getFirst();
        when(saleRepository.findByIdForUpdate(sale.getId())).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> service.create(new ReturnCreateRequest(
                sale.getId(),
                "Not allowed",
                List.of(new ReturnItemRequest(saleItem.getId(), new BigDecimal("1.0000"), null))), auth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Product is not returnable");

        verify(returnRepository, never()).saveAndFlush(any());
    }

    @Test
    void requiresReasonAndCompletedOriginalSale() throws Exception {
        Sale draftSale = draftSale(returnableProduct());
        SaleItem saleItem = saleItem(draftSale, returnableProduct());
        invoke(draftSale, "addItem", new Class<?>[]{SaleItem.class}, saleItem);
        when(saleRepository.findByIdForUpdate(draftSale.getId())).thenReturn(Optional.of(draftSale));

        assertThatThrownBy(() -> service.create(new ReturnCreateRequest(
                draftSale.getId(),
                "Reason",
                List.of(new ReturnItemRequest(saleItem.getId(), new BigDecimal("1.0000"), null))), auth()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Returns can only be created for completed sales");

        Sale completedSale = completedSale(returnableProduct());
        SaleItem completedItem = completedSale.getItems().getFirst();
        when(saleRepository.findByIdForUpdate(completedSale.getId())).thenReturn(Optional.of(completedSale));
        assertThatThrownBy(() -> service.create(new ReturnCreateRequest(
                completedSale.getId(),
                " ",
                List.of(new ReturnItemRequest(completedItem.getId(), new BigDecimal("1.0000"), " "))), auth()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("reason is required");
    }

    private Sale completedSale(Product product) throws Exception {
        return completedSale(
                product,
                new BigDecimal("2.0000"),
                new BigDecimal("10.00"),
                new BigDecimal("1.50"),
                new BigDecimal("11.50"));
    }

    private Sale completedSale(
            Product product,
            BigDecimal quantity,
            BigDecimal subtotal,
            BigDecimal tax,
            BigDecimal total) throws Exception {
        Sale sale = draftSale(product);
        SaleItem item = saleItem(sale, product, quantity);
        invoke(item, "setCalculatedAmounts", new Class<?>[]{BigDecimal.class, BigDecimal.class, BigDecimal.class},
                subtotal, tax, total);
        invoke(item, "snapshotForCompletion");
        invoke(sale, "addItem", new Class<?>[]{SaleItem.class}, item);
        invoke(sale, "setTotals", new Class<?>[]{BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class},
                subtotal, BigDecimal.ZERO.setScale(2), tax, total);
        invoke(sale, "complete", new Class<?>[]{User.class, Instant.class}, manager, NOW);
        return sale;
    }

    private Sale draftSale(Product product) throws Exception {
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
        return constructor.newInstance(store(), register(), registerSession(), manager, null, LocalDate.parse("2026-07-28"), "POS", "USD", false);
    }

    private SaleItem saleItem(Sale sale, Product product) throws Exception {
        return saleItem(sale, product, new BigDecimal("2.0000"));
    }

    private SaleItem saleItem(Sale sale, Product product, BigDecimal quantity) throws Exception {
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
                product,
                quantity,
                new BigDecimal("5.0000"),
                BigDecimal.ZERO.setScale(2),
                false,
                false,
                null,
                null,
                null,
                null);
    }

    private Product returnableProduct() {
        return product(Set.of(ProductCapability.ALLOW_RETURN));
    }

    private Product nonReturnableProduct() {
        return product(Set.of(ProductCapability.NON_REFUNDABLE));
    }

    private Product product(Set<ProductCapability> capabilities) {
        return new Product(new ProductValues(
                "SKU-1",
                "Coffee",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                new BigDecimal("2.0000"),
                new BigDecimal("5.0000"),
                null,
                null,
                true,
                false,
                false,
                null,
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000777"),
                List.of(),
                List.of(),
                capabilities));
    }

    private Store store() {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(java.util.UUID.fromString("00000000-0000-0000-0000-000000000901"));
        return store;
    }

    private Register register() {
        Register register = mock(Register.class);
        when(register.getId()).thenReturn(java.util.UUID.fromString("00000000-0000-0000-0000-000000000902"));
        return register;
    }

    private RegisterSession registerSession() {
        RegisterSession session = mock(RegisterSession.class);
        when(session.getId()).thenReturn(java.util.UUID.fromString("00000000-0000-0000-0000-000000000903"));
        return session;
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(
                "manager@example.test",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
    }

    private static void invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        method.invoke(target, args);
    }
}
