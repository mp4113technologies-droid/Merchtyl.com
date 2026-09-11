package com.merchtyl.sales;

import com.merchtyl.catalogue.Category;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductCapability;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SaleItemCategorySnapshotTest {
    @Test
    void completionFreezesCategoryIdentityAndName() {
        Sale sale = mock(Sale.class);
        Product product = mock(Product.class);
        Category category = mock(Category.class);
        UUID categoryId = UUID.randomUUID();
        when(product.getSku()).thenReturn("COLA");
        when(product.getName()).thenReturn("Cola");
        when(product.getCost()).thenReturn(new BigDecimal("1.0000"));
        when(product.getPrice()).thenReturn(new BigDecimal("2.0000"));
        when(product.getCapabilities()).thenReturn(Set.of(ProductCapability.RETAIL));
        when(product.getCategory()).thenReturn(category);
        when(category.getId()).thenReturn(categoryId);
        when(category.getName()).thenReturn("Soft Drinks");
        SaleItem item = new SaleItem(sale, product, BigDecimal.ONE, new BigDecimal("2.00"),
                BigDecimal.ZERO.setScale(2), false, false, null, null, null, null);

        item.snapshotForCompletion();

        assertThat(item.getCategorySnapshotId()).isEqualTo(categoryId);
        assertThat(item.getCategoryNameSnapshot()).isEqualTo("Soft Drinks");
    }
}
