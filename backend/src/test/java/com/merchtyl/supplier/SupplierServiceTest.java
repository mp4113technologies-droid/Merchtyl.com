package com.merchtyl.supplier;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
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

class SupplierServiceTest {
    private final SupplierRepository supplierRepository = mock(SupplierRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final SupplierService supplierService = new SupplierService(supplierRepository, userRepository, auditService);

    private final ProductSupplierRepository productSupplierRepository = mock(ProductSupplierRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductSupplierService productSupplierService = new ProductSupplierService(
            productSupplierRepository,
            productRepository,
            supplierRepository,
            userRepository,
            auditService);

    @Test
    void createNormalizesSupplierCodeAndAuditsCreation() {
        User actor = new User("manager@example.local", "Manager", "hash");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("manager@example.local");
        when(userRepository.findByEmailIgnoreCase("manager@example.local")).thenReturn(Optional.of(actor));
        when(supplierRepository.existsByCodeIgnoreCase("ACME")).thenReturn(false);
        when(supplierRepository.saveAndFlush(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierResponse response = supplierService.create(new SupplierRequest(
                " acme ",
                " ACME Foods ",
                " Jane Buyer ",
                " 555-0100 ",
                " SUPPLY@EXAMPLE.TEST ",
                " 10 Warehouse Road ",
                " Net 30 ",
                true), authentication);

        assertThat(response.code()).isEqualTo("ACME");
        assertThat(response.name()).isEqualTo("ACME Foods");
        assertThat(response.contactName()).isEqualTo("Jane Buyer");
        assertThat(response.email()).isEqualTo("supply@example.test");

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().actorUserId()).isEqualTo(actor.getId());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.SUPPLIER_CREATED);
        assertThat(audit.getValue().entityType()).isEqualTo("SUPPLIER");
        assertThat(audit.getValue().afterSnapshot().toString()).contains("ACME");
    }

    @Test
    void createRejectsDuplicateSupplierCodeBeforeSaving() {
        when(supplierRepository.existsByCodeIgnoreCase("ACME")).thenReturn(true);

        assertThatThrownBy(() -> supplierService.create(new SupplierRequest(
                "acme",
                "ACME Foods",
                null,
                null,
                null,
                null,
                null,
                true), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("code already exists");

        verify(supplierRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void updateRequiresCurrentSupplierVersion() {
        Supplier supplier = new Supplier(new SupplierValues("ACME", "ACME Foods", null, null, null, null, null, true));
        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));

        assertThatThrownBy(() -> supplierService.update(supplier.getId(), new SupplierUpdateRequest(
                "ACME",
                "ACME Foods",
                null,
                null,
                null,
                null,
                null,
                true,
                supplier.getVersion() + 1), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("modified by another transaction");

        verify(supplierRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void createProductSupplierValidatesProductAndSupplierAndAudits() {
        Product product = new Product(new ProductValues(
                "SKU-1",
                "Coffee",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                BigDecimal.ONE,
                BigDecimal.TEN,
                null,
                null,
                true,
                true,
                false,
                null,
                null,
                List.of(),
                List.of(),
                Set.of()));
        Supplier supplier = new Supplier(new SupplierValues("ACME", "ACME Foods", null, null, null, null, null, true));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));
        when(productSupplierRepository.existsByProductIdAndSupplier(product.getId(), supplier)).thenReturn(false);
        when(productSupplierRepository.saveAndFlush(any(ProductSupplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductSupplierResponse response = productSupplierService.create(new ProductSupplierRequest(
                product.getId(),
                supplier.getId(),
                " ACME-COFFEE ",
                true,
                true), null);

        assertThat(response.productId()).isEqualTo(product.getId());
        assertThat(response.supplierId()).isEqualTo(supplier.getId());
        assertThat(response.supplierSku()).isEqualTo("ACME-COFFEE");
        assertThat(response.preferred()).isTrue();

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.PRODUCT_SUPPLIER_CREATED);
        assertThat(audit.getValue().entityType()).isEqualTo("PRODUCT_SUPPLIER");
    }

    @Test
    void createProductSupplierRejectsUnknownProduct() {
        Supplier supplier = new Supplier(new SupplierValues("ACME", "ACME Foods", null, null, null, null, null, true));
        when(productRepository.findById(any())).thenReturn(Optional.empty());
        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));

        assertThatThrownBy(() -> productSupplierService.create(new ProductSupplierRequest(
                supplier.getId(),
                supplier.getId(),
                null,
                false,
                true), null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found");

        verify(productSupplierRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }
}
