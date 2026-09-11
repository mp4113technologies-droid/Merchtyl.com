package com.merchtyl.sales;

import com.merchtyl.product.DepositType;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductVariant;
import com.merchtyl.product.ProductVariantValues;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SaleItemDepositTest {
    @Test
    void derivesDepositFromSelectedVariantAndQuantityWithoutChangingMerchandisePrice() {
        Product product = new Product(new ProductValues("COLA", "Cola", null, SellableType.STANDARD_PRODUCT,
                null, BigDecimal.ONE, new BigDecimal("3.00"), null, null, true, true, false,
                null, null, List.of(), List.of(), Set.of()));
        ProductVariant variant = product.addVariant(new ProductVariantValues(null, "COLA-BOTTLE", "Bottle", null,
                BigDecimal.ONE, new BigDecimal("3.00"), true, true, DepositType.BOTTLE, new BigDecimal("0.10")));

        SaleItem item = new SaleItem(mock(Sale.class), product, variant, new BigDecimal("2.0000"),
                new BigDecimal("3.0000"), BigDecimal.ZERO.setScale(2), false, false,
                null, null, null, null);
        item.applyVariantDeposit();

        assertThat(item.getUnitPrice()).isEqualByComparingTo("3.0000");
        assertThat(item.getDepositType()).isEqualTo(DepositType.BOTTLE);
        assertThat(item.getDepositQuantity()).isEqualByComparingTo("2.0000");
        assertThat(item.getDepositUnitAmount()).isEqualByComparingTo("0.10");
        assertThat(item.getDepositTotal()).isEqualByComparingTo("0.20");
    }

    @Test
    void disabledVariantDepositProducesNoCharge() {
        Product product = new Product(new ProductValues("COLA", "Cola", null, SellableType.STANDARD_PRODUCT,
                null, BigDecimal.ONE, BigDecimal.TEN, null, null, true, true, false,
                null, null, List.of(), List.of(), Set.of()));
        ProductVariant variant = product.addVariant(new ProductVariantValues("COLA-CAN", "Can", null,
                BigDecimal.ONE, BigDecimal.TEN, true));
        SaleItem item = new SaleItem(mock(Sale.class), product, variant, BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ZERO.setScale(2), false, false, null, null, null, null);

        item.applyVariantDeposit();

        assertThat(item.getDepositType()).isNull();
        assertThat(item.getDepositTotal()).isEqualByComparingTo("0.00");
    }
}
