package com.merchtyl.sales;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductCapability;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaleItemHandlerRegistryTest {
    private final StandardProductSaleItemHandler standardHandler = new StandardProductSaleItemHandler();
    private final WeightedProductSaleItemHandler weightedHandler = new WeightedProductSaleItemHandler();
    private final ServiceSaleItemHandler serviceHandler = new ServiceSaleItemHandler();

    @Test
    void registryLooksUpHandlersBySellableType() {
        SaleItemHandlerRegistry registry = registry();

        assertThat(registry.handlerFor(SellableType.STANDARD_PRODUCT)).isSameAs(standardHandler);
        assertThat(registry.handlerFor(SellableType.WEIGHTED_PRODUCT)).isSameAs(weightedHandler);
        assertThat(registry.handlerFor(SellableType.SERVICE)).isSameAs(serviceHandler);
    }

    @Test
    void registryRejectsUnsupportedSellableType() {
        SaleItemHandlerRegistry registry = registry();

        assertThatThrownBy(() -> registry.handlerFor(SellableType.GIFT_CARD))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No sale item handler registered");
    }

    @Test
    void registryRejectsDuplicateHandlers() {
        assertThatThrownBy(() -> new SaleItemHandlerRegistry(List.of(standardHandler, new StandardProductSaleItemHandler())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate sale item handler");
    }

    @Test
    void standardProductValidationRejectsDecimalQuantityWithoutCapability() {
        Product product = product(SellableType.STANDARD_PRODUCT, true, false, Set.of());

        assertThatThrownBy(() -> standardHandler.validate(request(product, new BigDecimal("1.5"), BigDecimal.ZERO, false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("decimal quantities");
    }

    @Test
    void standardProductValidationRequiresDiscountCapability() {
        Product product = product(SellableType.STANDARD_PRODUCT, true, false, Set.of());

        assertThatThrownBy(() -> standardHandler.validate(request(product, BigDecimal.ONE, new BigDecimal("0.50"), false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("discounts");
    }

    @Test
    void standardProductValidationAcceptsAssignedCapabilities() {
        Product product = product(
                SellableType.STANDARD_PRODUCT,
                true,
                true,
                Set.of(
                        ProductCapability.ALLOW_DISCOUNT,
                        ProductCapability.ALLOW_PRICE_OVERRIDE,
                        ProductCapability.REQUIRE_SERIAL_NUMBER,
                        ProductCapability.REQUIRE_CUSTOMER));

        assertThatCode(() -> standardHandler.validate(new SaleItemRequest(
                product,
                new BigDecimal("1.25"),
                new BigDecimal("3.25"),
                new BigDecimal("0.50"),
                true,
                false,
                "SER-001",
                null,
                UUID.fromString("00000000-0000-0000-0000-000000001501"),
                null)))
                .doesNotThrowAnyException();
    }

    @Test
    void weightedProductValidationRequiresDecimalQuantityCapability() {
        Product product = product(SellableType.WEIGHTED_PRODUCT, true, false, Set.of());

        assertThatThrownBy(() -> weightedHandler.validate(request(product, BigDecimal.ONE, BigDecimal.ZERO, false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Weighted products must allow decimal quantities");
    }

    @Test
    void weightedProductValidationAcceptsDecimalQuantityWhenEnabled() {
        Product product = product(SellableType.WEIGHTED_PRODUCT, true, true, Set.of());

        assertThatCode(() -> weightedHandler.validate(request(product, new BigDecimal("1.250"), BigDecimal.ZERO, false)))
                .doesNotThrowAnyException();
    }

    @Test
    void serviceValidationRejectsInventoryTracking() {
        Product product = product(SellableType.SERVICE, true, false, Set.of(ProductCapability.TRACK_INVENTORY));

        assertThatThrownBy(() -> serviceHandler.validate(request(product, BigDecimal.ONE, BigDecimal.ZERO, false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Services cannot track inventory");
    }

    @Test
    void registryValidateRoutesToMatchingHandler() {
        Product product = product(SellableType.SERVICE, true, false, Set.of());
        SaleItemHandlerRegistry registry = registry();

        assertThatCode(() -> registry.validate(request(product, BigDecimal.ONE, BigDecimal.ZERO, false)))
                .doesNotThrowAnyException();
    }

    @Test
    void ageRestrictedProductRequiresExplicitConfirmation() {
        Product product = product(SellableType.STANDARD_PRODUCT, true, false,
                Set.of(ProductCapability.REQUIRE_AGE_VERIFICATION));
        SaleItemHandlerRegistry registry = registry();
        SaleItemRequest unverified = request(product, BigDecimal.ONE, BigDecimal.ZERO, false);

        assertThatThrownBy(() -> registry.validate(unverified))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("AGE_VERIFICATION_REQUIRED");

        SaleItemRequest verified = new SaleItemRequest(product, BigDecimal.ONE, new BigDecimal("3.25"),
                BigDecimal.ZERO, false, true, null, null, null, null);
        assertThatCode(() -> registry.validate(verified)).doesNotThrowAnyException();
    }

    private SaleItemHandlerRegistry registry() {
        return new SaleItemHandlerRegistry(List.of(standardHandler, weightedHandler, serviceHandler));
    }

    private SaleItemRequest request(Product product, BigDecimal quantity, BigDecimal discountAmount, boolean priceOverride) {
        return new SaleItemRequest(
                product,
                quantity,
                new BigDecimal("3.25"),
                discountAmount,
                priceOverride,
                false,
                null,
                null,
                null,
                null);
    }

    private Product product(SellableType sellableType, boolean active, boolean decimalQuantityAllowed, Set<ProductCapability> capabilities) {
        return new Product(new ProductValues(
                sellableType.name() + "-SKU",
                sellableType.name(),
                null,
                sellableType,
                null,
                BigDecimal.ONE,
                new BigDecimal("3.25"),
                null,
                null,
                active,
                capabilities.contains(ProductCapability.TRACK_INVENTORY),
                decimalQuantityAllowed,
                null,
                null,
                List.of(),
                List.of(),
                capabilities));
    }
}
