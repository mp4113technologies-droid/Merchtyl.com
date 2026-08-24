package com.merchtyl.product;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class TenantProductCatalogModelTest {
    @Test
    void assigningTenantPropagatesToVariantsAndBarcodes() {
        Product product = new Product(new ProductValues("COKE-500", "Coke 500ml", null,
                SellableType.STANDARD_PRODUCT, null, BigDecimal.ONE, new BigDecimal("2.49"), null, null,
                true, true, false, null, null,
                List.of(new ProductVariantValues("COKE-500-REG", "Regular", null, BigDecimal.ONE, new BigDecimal("2.49"), true)),
                List.of(new ProductBarcodeValues("049000028904", "COKE-500-REG", true, true)),
                Set.of(ProductCapability.ALLOW_DISCOUNT)));
        UUID tenantId = UUID.randomUUID();

        product.assignTenant(tenantId);

        assertThat(product.getTenantId()).isEqualTo(tenantId);
        assertThat(product.getVariants()).allMatch(variant -> tenantId.equals(variant.getTenantId()));
        assertThat(product.getBarcodes()).allMatch(barcode -> tenantId.equals(barcode.getTenantId()));
    }
}
