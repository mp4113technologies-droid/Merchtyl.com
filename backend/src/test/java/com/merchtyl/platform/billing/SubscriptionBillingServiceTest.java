package com.merchtyl.platform.billing;

import com.merchtyl.common.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionBillingServiceTest {
    private final SubscriptionBillingService service = new SubscriptionBillingService();

    @Test
    void calculatesBaseExcessStoresFixedDiscountTaxAndRounding() {
        var result = service.calculate(input("99.00", 4, 7, 18, "FIXED_AMOUNT", "20.00", "0.13"));
        assertThat(result.lines()).extracting(SubscriptionBillingService.CalculatedLine::lineType)
                .containsExactly("BASE_SUBSCRIPTION", "ADDITIONAL_STORE");
        assertThat(result.subtotal()).isEqualByComparingTo("159.00");
        assertThat(result.discount()).isEqualByComparingTo("20.00");
        assertThat(result.tax()).isEqualByComparingTo("18.07");
        assertThat(result.total()).isEqualByComparingTo("157.07");
    }

    @Test
    void calculatesPercentageDiscount() {
        var result = service.calculate(input("79.00", 1, 2, 5, "PERCENTAGE", "10", "0"));
        assertThat(result.subtotal()).isEqualByComparingTo("79.00");
        assertThat(result.discount()).isEqualByComparingTo("7.90");
        assertThat(result.total()).isEqualByComparingTo("71.10");
    }

    @Test
    void supportsCustomBasePriceWithoutUsageCharges() {
        var result = service.calculate(input("149.00", 1, 2, 5, null, null, "0"));
        assertThat(result.total()).isEqualByComparingTo("149.00");
    }

    @Test
    void neverCreatesNegativeAdditionalStoreQuantity() {
        var result = service.calculate(input("49.00", 0, 20, 50, null, null, "0"));
        assertThat(result.lines()).extracting(SubscriptionBillingService.CalculatedLine::lineType)
                .containsExactly("BASE_SUBSCRIPTION");
        assertThat(result.total()).isEqualByComparingTo("49.00");
    }

    @Test
    void includesOneTimeOnboardingFeeOnlyWhenRequested() {
        var first = service.calculate(new SubscriptionBillingService.Input("Growth Plan", new BigDecimal("99"), 1, null, null,
                new BigDecimal("25"), null, null, 2, 0, 0, null, null, BigDecimal.ZERO, new BigDecimal("199"), true));
        var recurring = service.calculate(new SubscriptionBillingService.Input("Growth Plan", new BigDecimal("99"), 1, null, null,
                new BigDecimal("25"), null, null, 2, 0, 0, null, null, BigDecimal.ZERO, new BigDecimal("199"), false));
        assertThat(first.lines()).extracting(SubscriptionBillingService.CalculatedLine::lineType)
                .containsExactly("BASE_SUBSCRIPTION", "ONBOARDING_FEE", "ADDITIONAL_STORE");
        assertThat(first.total()).isEqualByComparingTo("323.00");
        assertThat(recurring.total()).isEqualByComparingTo("124.00");
    }

    @Test
    void rejectsInvalidPercentageAndNegativeMoney() {
        assertThatThrownBy(() -> service.calculate(input("99", 1, 1, 1, "PERCENTAGE", "101", "0"))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.calculate(input("-1", 1, 1, 1, null, null, "0"))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void chargesFoodServicePerEnabledStoreWithoutDoubleCountingStores() {
        var result=service.calculate(new SubscriptionBillingService.Input("Growth",new BigDecimal("100"),1,null,null,new BigDecimal("20"),null,null,3,0,0,null,null,BigDecimal.ZERO,BigDecimal.ZERO,false,
                List.of(new SubscriptionBillingService.CapabilityUsage("FOOD_SERVICE","Food Service add-on",2,new BigDecimal("15")))));
        assertThat(result.lines()).extracting(SubscriptionBillingService.CalculatedLine::lineType)
                .containsExactly("BASE_SUBSCRIPTION","ADDITIONAL_STORE","CAPABILITY_ADD_ON");
        assertThat(result.lines().get(1).quantity()).isEqualByComparingTo("2");
        assertThat(result.lines().get(2).quantity()).isEqualByComparingTo("2");
        assertThat(result.total()).isEqualByComparingTo("170.00");
    }

    @Test
    void retailOnlyUsageHasNoCapabilityFee() {
        var result=service.calculate(new SubscriptionBillingService.Input("Retail",new BigDecimal("50"),1,null,null,new BigDecimal("10"),null,null,1,0,0,null,null,BigDecimal.ZERO,BigDecimal.ZERO,false,
                List.of(new SubscriptionBillingService.CapabilityUsage("FOOD_SERVICE","Food Service add-on",0,new BigDecimal("25")))));
        assertThat(result.lines()).extracting(SubscriptionBillingService.CalculatedLine::lineType).containsExactly("BASE_SUBSCRIPTION");
    }

    private static SubscriptionBillingService.Input input(String base, int stores, int registers, int users, String discountType, String discount, String tax) {
        return new SubscriptionBillingService.Input("Growth Plan", new BigDecimal(base), 1, 2, 5,
                new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("5"), stores, registers, users,
                discountType, discount == null ? null : new BigDecimal(discount), new BigDecimal(tax), BigDecimal.ZERO, false);
    }
}
