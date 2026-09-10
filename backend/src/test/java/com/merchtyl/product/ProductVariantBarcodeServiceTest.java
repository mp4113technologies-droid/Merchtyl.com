package com.merchtyl.product;

import com.merchtyl.audit.AuditService;
import com.merchtyl.catalogue.*;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.security.*;
import com.merchtyl.tax.TaxCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductVariantBarcodeServiceTest {
    @Test void addsWholeNormalizedBatchWithOneOwnershipQueryAndKeepsSku() {
        Fixture fixture=new Fixture();String sku=fixture.variant.getSku();
        when(fixture.barcodes.findOwnedInBulk(fixture.tenantId,Set.of("00123","00456"))).thenReturn(List.of());
        var result=fixture.service.addVariantBarcodes(fixture.variant.getId(),new BulkVariantBarcodeRequest(List.of(" 00123 ","00456")),null);
        assertThat(result.addedCount()).isEqualTo(2);assertThat(result.barcodes()).extracting(ProductBarcodeResponse::barcode).containsExactly("00123","00456");
        assertThat(fixture.variant.getSku()).isEqualTo(sku);verify(fixture.barcodes).findOwnedInBulk(fixture.tenantId,Set.of("00123","00456"));verify(fixture.products).saveAndFlush(fixture.product);
    }

    @Test void rejectsDuplicateBeforeAnyWrite() {
        Fixture fixture=new Fixture();
        assertThatThrownBy(()->fixture.service.addVariantBarcodes(fixture.variant.getId(),new BulkVariantBarcodeRequest(List.of("ABC"," abc ")),null)).isInstanceOf(BadRequestException.class).hasMessage("BARCODE_DUPLICATE_IN_REQUEST");
        verify(fixture.products,never()).saveAndFlush(any());verify(fixture.barcodes,never()).findOwnedInBulk(any(),any());
    }

    private static final class Fixture {
        final UUID tenantId=UUID.randomUUID();final ProductRepository products=mock(ProductRepository.class);final ProductVariantRepository variants=mock(ProductVariantRepository.class);final ProductBarcodeRepository barcodes=mock(ProductBarcodeRepository.class);final Product product;final ProductVariant variant;final ProductService service;
        Fixture(){
            product=new Product(new ProductValues("COKE","Coca Cola",null,SellableType.STANDARD_PRODUCT,null,BigDecimal.ONE,BigDecimal.TEN,null,null,true,true,false,null,null,List.of(),List.of(),Set.of()));product.assignTenant(tenantId);product.assignProductReference("PRD-000001");variant=product.addVariant(new ProductVariantValues("COC500ML","500 mL",null,BigDecimal.ONE,BigDecimal.TEN,true));
            service=new ProductService(products,variants,barcodes,mock(CategoryRepository.class),mock(BrandRepository.class),mock(UnitOfMeasureRepository.class),mock(UserRepository.class),mock(AuditService.class),mock(TaxCategoryRepository.class));StoreAccessService access=mock(StoreAccessService.class);User actor=mock(User.class);when(access.currentTenantId(null)).thenReturn(tenantId);when(access.currentTenantUser(null)).thenReturn(actor);when(access.roles(actor)).thenReturn(Set.of(RoleName.OWNER));ReflectionTestUtils.setField(service,"storeAccessService",access);ReflectionTestUtils.setField(service,"storeProductRepository",mock(StoreProductRepository.class));when(variants.findById(variant.getId())).thenReturn(Optional.of(variant));when(products.findByIdAndTenantId(product.getId(),tenantId)).thenReturn(Optional.of(product));when(products.saveAndFlush(product)).thenReturn(product);
        }
    }
}
