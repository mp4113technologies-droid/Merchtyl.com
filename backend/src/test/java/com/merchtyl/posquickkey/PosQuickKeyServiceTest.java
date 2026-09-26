package com.merchtyl.posquickkey;

import com.merchtyl.common.NotFoundException;
import com.merchtyl.product.*;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosQuickKeyServiceTest {
    @Mock PosQuickKeyRepository repository;
    @Mock ProductVariantRepository variants;
    @Mock StoreProductRepository storeProducts;
    @Mock StoreAccessService storeAccess;
    @Mock Authentication authentication;
    @Mock User user;

    private PosQuickKeyService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        when(storeAccess.currentTenantUser(authentication)).thenReturn(user);
        when(user.getTenantId()).thenReturn(tenantId);
        service = new PosQuickKeyService(repository, variants, storeProducts, storeAccess);
    }

    @Test
    void createsTenantVariantReferenceWithoutCopyingFinancialConfiguration() {
        ProductVariant variant = variant(SellableType.LOTTERY_PRODUCT, ProductAvailabilityScope.ALL_STORES, true);
        when(repository.countByTenantId(tenantId)).thenReturn(0L);
        when(variants.findCheckoutVariants(tenantId, List.of(variant.getId()))).thenReturn(List.of(variant));
        when(repository.findByTenantIdOrderByDisplayOrderAscIdAsc(tenantId)).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new PosQuickKeyDtos.CreateRequest(variant.getId(), "Lotto $5"), authentication);

        assertThat(response.sellableType()).isEqualTo(SellableType.LOTTERY_PRODUCT);
        assertThat(response.price()).isEqualByComparingTo("5.00");
        var saved = org.mockito.ArgumentCaptor.forClass(PosQuickKey.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getValue().getProductVariant()).isSameAs(variant);
    }

    @Test
    void rejectsVariantOutsideCurrentTenant() {
        UUID foreignVariant = UUID.randomUUID();
        when(repository.countByTenantId(tenantId)).thenReturn(0L);
        when(variants.findCheckoutVariants(tenantId, List.of(foreignVariant))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(new PosQuickKeyDtos.CreateRequest(foreignVariant, null), authentication))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void returnsOnlyActiveAvailableStoreKeysInConfiguredOrder() {
        UUID storeId = UUID.randomUUID();
        PosQuickKey first = key(variant(SellableType.STANDARD_PRODUCT, ProductAvailabilityScope.ALL_STORES, true), 0, true);
        PosQuickKey unavailable = key(variant(SellableType.STANDARD_PRODUCT, ProductAvailabilityScope.SELECTED_STORES, true), 1, true);
        PosQuickKey inactive = key(variant(SellableType.STANDARD_PRODUCT, ProductAvailabilityScope.ALL_STORES, true), 2, false);
        when(repository.findByTenantIdOrderByDisplayOrderAscIdAsc(tenantId)).thenReturn(List.of(first, unavailable, inactive));
        when(storeProducts.findByTenantIdAndStore_IdAndProduct_IdAndActiveTrueAndSellableTrue(
                tenantId, storeId, unavailable.getProductVariant().getProduct().getId())).thenReturn(Optional.empty());

        var responses = service.forStore(storeId, authentication);

        verify(storeAccess).requireStoreAccess(authentication, storeId);
        assertThat(responses).extracting(PosQuickKeyDtos.Response::productVariantId)
                .containsExactly(first.getProductVariant().getId());
    }

    @Test
    void configurationMarksDisabledAndDeletedProductsUnavailable() {
        ProductVariant disabledVariant = variant(SellableType.STANDARD_PRODUCT, ProductAvailabilityScope.ALL_STORES, false);
        ProductVariant deletedVariant = variant(SellableType.STANDARD_PRODUCT, ProductAvailabilityScope.ALL_STORES, true);
        when(deletedVariant.getProduct().isDeleted()).thenReturn(true);
        when(repository.findByTenantIdOrderByDisplayOrderAscIdAsc(tenantId)).thenReturn(List.of(
                key(disabledVariant, 0, true), key(deletedVariant, 1, true)));

        assertThat(service.configuration(authentication)).extracting(PosQuickKeyDtos.Response::productAvailable)
                .containsExactly(false, false);
    }

    @Test
    void permitsReorderingAnExistingActiveKeyAfterItsProductBecomesUnavailable() {
        ProductVariant variant = variant(SellableType.STANDARD_PRODUCT, ProductAvailabilityScope.ALL_STORES, true);
        when(variant.getProduct().isDeleted()).thenReturn(true);
        PosQuickKey key = key(variant, 0, true);
        when(repository.findByIdAndTenantId(key.getId(), tenantId)).thenReturn(Optional.of(key));
        when(repository.findByTenantIdOrderByDisplayOrderAscIdAsc(tenantId)).thenReturn(List.of(key));

        var response = service.update(key.getId(), new PosQuickKeyDtos.UpdateRequest("Old favorite", 0, true), authentication);

        assertThat(response.productAvailable()).isFalse();
        assertThat(response.displayLabel()).isEqualTo("Old favorite");
        verify(repository).saveAll(List.of(key));
    }

    private PosQuickKey key(ProductVariant variant, int order, boolean active) {
        PosQuickKey key = new PosQuickKey(tenantId, variant, null, order);
        key.update(null, order, active);
        return key;
    }

    private ProductVariant variant(SellableType type, ProductAvailabilityScope scope, boolean active) {
        Product product = mock(Product.class);
        ProductVariant variant = mock(ProductVariant.class);
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        lenient().when(product.getId()).thenReturn(productId);
        lenient().when(product.getName()).thenReturn(type == SellableType.LOTTERY_PRODUCT ? "Lotto" : "Coffee");
        lenient().when(product.getSellableType()).thenReturn(type);
        lenient().when(product.getAvailabilityScope()).thenReturn(scope);
        lenient().when(product.isActive()).thenReturn(true);
        lenient().when(product.isDeleted()).thenReturn(false);
        lenient().when(product.hasCapability(ProductCapability.REQUIRE_AGE_VERIFICATION)).thenReturn(false);
        lenient().when(variant.getId()).thenReturn(variantId);
        lenient().when(variant.getProduct()).thenReturn(product);
        lenient().when(variant.getName()).thenReturn("Regular");
        lenient().when(variant.getSku()).thenReturn("SKU-1");
        lenient().when(variant.getPrice()).thenReturn(new BigDecimal("5.00"));
        lenient().when(variant.isActive()).thenReturn(active);
        return variant;
    }
}
