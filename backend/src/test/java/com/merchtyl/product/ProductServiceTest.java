package com.merchtyl.product;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.catalogue.BrandRepository;
import com.merchtyl.catalogue.CategoryRepository;
import com.merchtyl.catalogue.UnitOfMeasureRepository;
import com.merchtyl.catalogue.UnitOfMeasure;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.security.UserRepository;
import com.merchtyl.tax.TaxCategoryRepository;
import com.merchtyl.tax.TaxCategory;
import com.merchtyl.tax.TaxTreatment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    ProductRepository productRepository;

    @Mock
    ProductVariantRepository productVariantRepository;

    @Mock
    ProductBarcodeRepository productBarcodeRepository;

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    BrandRepository brandRepository;

    @Mock
    UnitOfMeasureRepository unitOfMeasureRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    AuditService auditService;

    @Mock
    TaxCategoryRepository taxCategoryRepository;

    @Mock
    EntityManager entityManager;

    @Mock
    SkuGenerator skuGenerator;

    @InjectMocks
    ProductService productService;

    @BeforeEach
    void setUpPersistenceContext() {
        ReflectionTestUtils.setField(productService, "entityManager", entityManager);
        ReflectionTestUtils.setField(productService, "skuGenerator", skuGenerator);
        org.mockito.Mockito.lenient().when(skuGenerator.generate(any(), any(String.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenAnswer(invocation -> {
                    String name = invocation.getArgument(1);
                    String variant = invocation.getArgument(2);
                    return name.trim().toUpperCase().replace(' ', '-')
                            + (variant == null ? "" : "-" + variant.trim().toUpperCase().replace(' ', '-')) + "-001";
                });
    }

    @Test
    void createNormalizesSkuAndNestedCodes() {
        when(productRepository.existsBySkuIgnoreCase("HOUSE-COFFEE-001")).thenReturn(false);

        ProductResponse response = productService.create(new ProductRequest(
                " coffee-12oz ",
                " House Coffee ",
                " Fresh brewed ",
                SellableType.STANDARD_PRODUCT,
                null,
                new BigDecimal("1.2500"),
                new BigDecimal("3.2500"),
                null,
                null,
                true,
                true,
                false,
                " https://cdn.example.test/coffee.png ",
                null,
                List.of(
                        new ProductVariantRequest(" large ", " Large ", null, new BigDecimal("1.5000"), new BigDecimal("4.0000"), true,
                                List.of(new ProductVariantBarcodeRequest(" 012345678905 "), new ProductVariantBarcodeRequest("012345678906"))),
                        new ProductVariantRequest("small", "Small", null, new BigDecimal("1.2500"), new BigDecimal("3.5000"), true,
                                List.of(new ProductVariantBarcodeRequest("012345678907")))),
                Set.of(ProductCapability.ALLOW_DISCOUNT)), null);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(entityManager).persist(captor.capture());
        verify(entityManager).flush();
        verify(productRepository, never()).saveAndFlush(any(Product.class));

        assertThat(response.sku()).isEqualTo("HOUSE-COFFEE-001");
        assertThat(response.name()).isEqualTo("House Coffee");
        assertThat(response.description()).isEqualTo("Fresh brewed");
        assertThat(response.cost()).isEqualByComparingTo("1.2500");
        assertThat(response.price()).isEqualByComparingTo("3.2500");
        assertThat(response.sellableType()).isEqualTo(SellableType.STANDARD_PRODUCT);
        assertThat(response.variants()).extracting(ProductVariantResponse::sku).containsExactly("HOUSE-COFFEE-LARGE-001", "HOUSE-COFFEE-SMALL-001");
        assertThat(response.barcodes()).extracting(ProductBarcodeResponse::barcode)
                .containsExactly("012345678905", "012345678906", "012345678907");
        assertThat(response.barcodes()).extracting(ProductBarcodeResponse::variantSku)
                .containsExactly("HOUSE-COFFEE-LARGE-001", "HOUSE-COFFEE-LARGE-001", "HOUSE-COFFEE-SMALL-001");
        assertThat(response.capabilities()).contains(ProductCapability.TRACK_INVENTORY, ProductCapability.ALLOW_DISCOUNT);
        assertThat(captor.getValue().getId()).isNotNull();

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.PRODUCT_CREATED);
        assertThat(audit.getValue().entityType()).isEqualTo("PRODUCT");
    }

    @Test
    void productListSortsActiveFirstBeforeNameAndStableIdPagination() {
        var orders = ProductService.productListSort().toList();
        assertThat(orders).extracting(org.springframework.data.domain.Sort.Order::getProperty)
                .containsExactly("active", "name", "id");
        assertThat(orders.getFirst().getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
        assertThat(orders.get(1).getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
    }

    @Test
    void createRejectsDuplicateSku() {
        when(productRepository.existsBySkuIgnoreCase("HOUSE-COFFEE-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(new ProductRequest(
                "coffee-12oz",
                "House Coffee",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                BigDecimal.ONE,
                new BigDecimal("3.25"),
                null,
                null,
                true,
                true,
                false,
                null,
                null,
                List.of(),
                Set.of()), null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("SKU already exists");

        verify(productRepository, never()).saveAndFlush(any(Product.class));
        verify(auditService, never()).record(any());
    }

    @Test
    void createRejectsBarcodeAssignedAcrossVariants() {
        assertThatThrownBy(() -> productService.create(new ProductRequest(
                "coffee-12oz",
                "House Coffee",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                BigDecimal.ONE,
                new BigDecimal("3.25"),
                null,
                null,
                true,
                true,
                false,
                null,
                null,
                List.of(
                        new ProductVariantRequest("small", "Small", null, BigDecimal.ONE, BigDecimal.TEN, true,
                                List.of(new ProductVariantBarcodeRequest("012345678905"))),
                        new ProductVariantRequest("large", "Large", null, BigDecimal.ONE, BigDecimal.TEN, true,
                                List.of(new ProductVariantBarcodeRequest("012345678905")))),
                Set.of()), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate barcode");

        verify(productRepository, never()).saveAndFlush(any(Product.class));
        verify(auditService, never()).record(any());
    }

    @Test
    void createRejectsUnknownTaxCategory() {
        UUID taxCategoryId = UUID.randomUUID();
        when(taxCategoryRepository.findById(taxCategoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(requestWithTaxCategory(taxCategoryId), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid tax category");
    }

    @Test
    void createRejectsInactiveTaxCategory() {
        UUID taxCategoryId = UUID.randomUUID();
        when(taxCategoryRepository.findById(taxCategoryId)).thenReturn(Optional.of(new TaxCategory(
                null, "INACTIVE", "Inactive", TaxTreatment.STANDARD, null, false)));

        assertThatThrownBy(() -> productService.create(requestWithTaxCategory(taxCategoryId), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tax category is inactive");
    }

    @Test
    void createRejectsUnknownUnit() {
        UUID unitId = UUID.randomUUID();
        when(unitOfMeasureRepository.findById(unitId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(requestWithUnit(unitId), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid unit of measure");
    }

    @Test
    void createRejectsInactiveUnit() {
        UUID unitId = UUID.randomUUID();
        when(unitOfMeasureRepository.findById(unitId)).thenReturn(Optional.of(new UnitOfMeasure("OLD", "Old Unit", null, false)));

        assertThatThrownBy(() -> productService.create(requestWithUnit(unitId), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unit of measure is inactive");
    }

    @Test
    void createRejectsBarcodeReassignmentWithoutBarcodeManagePermissionBeforeWriting() {
        ProductRequest request = new ProductRequest(null, "Cola", null, SellableType.STANDARD_PRODUCT, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, true, false, false, null, null,
                List.of(new ProductVariantRequest(null, null, "500 mL", null, BigDecimal.ONE, BigDecimal.TEN, true,
                        List.of(new ProductVariantBarcodeRequest(null, "123456789", true, true,
                                UUID.randomUUID(), 2L)))), Set.of());

        assertThatThrownBy(() -> productService.create(request, null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("BARCODE_REASSIGN_NOT_ALLOWED");
        verify(productBarcodeRepository, never()).delete(any());
        verify(entityManager, never()).persist(any());
    }

    private ProductRequest requestWithTaxCategory(UUID taxCategoryId) {
        return new ProductRequest("taxed-product", "Taxed Product", null, SellableType.STANDARD_PRODUCT, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, true, false, false, null, taxCategoryId,
                List.of(), Set.of());
    }

    private ProductRequest requestWithUnit(UUID unitId) {
        return new ProductRequest("unit-product", "Unit Product", null, SellableType.STANDARD_PRODUCT, unitId,
                BigDecimal.ONE, BigDecimal.TEN, null, null, true, false, false, null, null,
                List.of(), Set.of());
    }
}
