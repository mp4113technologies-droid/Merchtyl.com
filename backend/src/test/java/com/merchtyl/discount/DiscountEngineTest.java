package com.merchtyl.discount;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.sales.SaleAdjustmentType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscountEngineTest {
 private final DiscountEngine engine=new DiscountEngine(); private final UUID store=UUID.randomUUID(),product=UUID.randomUUID(),category=UUID.randomUUID();
 private List<DiscountEngine.Line> lines(String amount){return List.of(new DiscountEngine.Line(UUID.randomUUID(),product,null,category,new BigDecimal("3"),new BigDecimal(amount),true));}
 @Test void percentageAndMaximumCapUseEligibleSubtotal(){var definition=mock(DiscountDefinition.class);when(definition.isActive()).thenReturn(true);when(definition.isAllStores()).thenReturn(true);when(definition.getEligibleCategoryIds()).thenReturn(java.util.Set.of());when(definition.getEligibleProductIds()).thenReturn(java.util.Set.of());when(definition.getMaximumDiscountAmount()).thenReturn(new BigDecimal("10"));var result=engine.evaluate(SaleAdjustmentType.DISCOUNT_PERCENTAGE,new BigDecimal("25"),definition,store,lines("100"),Instant.now());assertThat(result.amount()).isEqualByComparingTo("10.00");assertThat(result.capped()).isTrue();}
 @Test void validatesMinimumMaximumQuantityScheduleAndStore(){var definition=mock(DiscountDefinition.class);when(definition.isActive()).thenReturn(true);when(definition.isAllStores()).thenReturn(true);when(definition.getMinimumPurchaseAmount()).thenReturn(new BigDecimal("40"));assertThatThrownBy(()->engine.evaluate(SaleAdjustmentType.DISCOUNT_AMOUNT,new BigDecimal("5"),definition,store,lines("39.99"),Instant.now())).isInstanceOf(BadRequestException.class).hasMessage("DISCOUNT_MINIMUM_PURCHASE_NOT_MET");}
 @Test void enforcesMaximumPurchaseQuantityStoreAndSchedule(){
  var definition=mock(DiscountDefinition.class);when(definition.isActive()).thenReturn(true);when(definition.getEligibleCategoryIds()).thenReturn(java.util.Set.of());when(definition.getEligibleProductIds()).thenReturn(java.util.Set.of());
  when(definition.isAllStores()).thenReturn(true);when(definition.getMaximumPurchaseAmount()).thenReturn(new BigDecimal("50"));
  assertThatThrownBy(()->engine.evaluate(SaleAdjustmentType.DISCOUNT_AMOUNT,new BigDecimal("5"),definition,store,lines("50.01"),Instant.parse("2026-09-07T12:00:00Z"))).hasMessage("DISCOUNT_MAXIMUM_PURCHASE_EXCEEDED");
  when(definition.getMaximumPurchaseAmount()).thenReturn(null);when(definition.getMinimumQuantity()).thenReturn(4);
  assertThatThrownBy(()->engine.evaluate(SaleAdjustmentType.DISCOUNT_AMOUNT,new BigDecimal("5"),definition,store,lines("50"),Instant.parse("2026-09-07T12:00:00Z"))).hasMessage("DISCOUNT_MINIMUM_QUANTITY_NOT_MET");
  when(definition.getMinimumQuantity()).thenReturn(null);when(definition.isAllStores()).thenReturn(false);when(definition.getStoreIds()).thenReturn(java.util.Set.of(UUID.randomUUID()));
  assertThatThrownBy(()->engine.evaluate(SaleAdjustmentType.DISCOUNT_AMOUNT,new BigDecimal("5"),definition,store,lines("50"),Instant.parse("2026-09-07T12:00:00Z"))).hasMessage("DISCOUNT_NOT_AVAILABLE_AT_STORE");
  when(definition.isAllStores()).thenReturn(true);when(definition.getStartsAt()).thenReturn(Instant.parse("2026-09-08T00:00:00Z"));
  assertThatThrownBy(()->engine.evaluate(SaleAdjustmentType.DISCOUNT_AMOUNT,new BigDecimal("5"),definition,store,lines("50"),Instant.parse("2026-09-07T12:00:00Z"))).hasMessage("DISCOUNT_NOT_STARTED");
  when(definition.getStartsAt()).thenReturn(null);when(definition.getEndsAt()).thenReturn(Instant.parse("2026-09-06T00:00:00Z"));
  assertThatThrownBy(()->engine.evaluate(SaleAdjustmentType.DISCOUNT_AMOUNT,new BigDecimal("5"),definition,store,lines("50"),Instant.parse("2026-09-07T12:00:00Z"))).hasMessage("DISCOUNT_EXPIRED");
 }
 @Test void categoryAndProductEligibilityOnlyDiscountMatchingLines(){UUID other=UUID.randomUUID();var definition=mock(DiscountDefinition.class);when(definition.isActive()).thenReturn(true);when(definition.isAllStores()).thenReturn(true);when(definition.getEligibleCategoryIds()).thenReturn(java.util.Set.of(category));when(definition.getEligibleProductIds()).thenReturn(java.util.Set.of(product));var lines=List.of(new DiscountEngine.Line(UUID.randomUUID(),product,null,category,BigDecimal.ONE,new BigDecimal("20"),true),new DiscountEngine.Line(UUID.randomUUID(),other,null,UUID.randomUUID(),BigDecimal.ONE,new BigDecimal("80"),true));assertThat(engine.evaluate(SaleAdjustmentType.DISCOUNT_PERCENTAGE,new BigDecimal("10"),definition,store,lines,Instant.now()).amount()).isEqualByComparingTo("2.00");}
}
