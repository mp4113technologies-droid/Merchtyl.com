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
import com.merchtyl.security.UserRepository;
import com.merchtyl.tax.TaxCategoryRepository;
import com.merchtyl.tax.TaxCategory;
import com.merchtyl.tax.TaxTreatment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    ProductService productService;

    @Test
    void createNormalizesSkuAndNestedCodes() {
        when(productRepository.existsBySkuIgnoreCase("COFFEE-12OZ")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
                List.of(new ProductVariantRequest(" large ", " Large ", null, new BigDecimal("1.5000"), new BigDecimal("4.0000"), true)),
                List.of(new ProductBarcodeRequest(" 012345678905 ", "large", true, true)),
                Set.of(ProductCapability.ALLOW_DISCOUNT)), null);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(captor.capture());

        assertThat(response.sku()).isEqualTo("COFFEE-12OZ");
        assertThat(response.name()).isEqualTo("House Coffee");
        assertThat(response.description()).isEqualTo("Fresh brewed");
        assertThat(response.cost()).isEqualByComparingTo("1.2500");
        assertThat(response.price()).isEqualByComparingTo("3.2500");
        assertThat(response.sellableType()).isEqualTo(SellableType.STANDARD_PRODUCT);
        assertThat(response.variants()).extracting(ProductVariantResponse::sku).containsExactly("LARGE");
        assertThat(response.barcodes()).extracting(ProductBarcodeResponse::barcode).containsExactly("012345678905");
        assertThat(response.capabilities()).contains(ProductCapability.TRACK_INVENTORY, ProductCapability.ALLOW_DISCOUNT);
        assertThat(captor.getValue().getId()).isNotNull();

        ArgumentCaptor<CreateAuditRecordCommand> audit = ArgumentCaptor.forClass(CreateAuditRecordCommand.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.PRODUCT_CREATED);
        assertThat(audit.getValue().entityType()).isEqualTo("PRODUCT");
    }

    @Test
    void createRejectsDuplicateSku() {
        when(productRepository.existsBySkuIgnoreCase("COFFEE-12OZ")).thenReturn(true);

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
                List.of(),
                Set.of()), null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("SKU already exists");

        verify(productRepository, never()).saveAndFlush(any(Product.class));
        verify(auditService, never()).record(any());
    }

    @Test
    void createRejectsBarcodeForUnknownVariantSku() {
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
                List.of(new ProductBarcodeRequest("012345678905", "missing", true, true)),
                Set.of()), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("variantSku");

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

    private ProductRequest requestWithTaxCategory(UUID taxCategoryId) {
        return new ProductRequest("taxed-product", "Taxed Product", null, SellableType.STANDARD_PRODUCT, null,
                BigDecimal.ONE, BigDecimal.TEN, null, null, true, false, false, null, taxCategoryId,
                List.of(), List.of(), Set.of());
    }

    private ProductRequest requestWithUnit(UUID unitId) {
        return new ProductRequest("unit-product", "Unit Product", null, SellableType.STANDARD_PRODUCT, unitId,
                BigDecimal.ONE, BigDecimal.TEN, null, null, true, false, false, null, null,
                List.of(), List.of(), Set.of());
    }
}
