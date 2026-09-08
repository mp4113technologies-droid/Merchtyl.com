package com.merchtyl.discount;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.sales.SaleAdjustmentType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DiscountEngine {
    private static final int SCALE=2;
    public record Line(UUID lineId,UUID productId,UUID sourceItemId,UUID categoryId,BigDecimal quantity,BigDecimal subtotal,boolean discountAllowed){}
    public record Allocation(UUID lineId,BigDecimal amount){}
    public record Result(BigDecimal amount,List<Allocation> allocations,boolean capped){}

    public Result evaluate(SaleAdjustmentType type,BigDecimal value,DiscountDefinition definition,UUID storeId,List<Line> lines,Instant now){
        if(type!=SaleAdjustmentType.DISCOUNT_PERCENTAGE&&type!=SaleAdjustmentType.DISCOUNT_AMOUNT)throw new BadRequestException("INVALID_DISCOUNT_TYPE");
        if(value==null||value.signum()<=0)throw new BadRequestException("DISCOUNT_VALUE_INVALID");
        if(type==SaleAdjustmentType.DISCOUNT_PERCENTAGE&&value.compareTo(new BigDecimal("100"))>0)throw new BadRequestException("DISCOUNT_PERCENTAGE_EXCEEDS_MAXIMUM");
        BigDecimal subtotal=money(lines.stream().map(Line::subtotal).reduce(BigDecimal.ZERO,BigDecimal::add));
        BigDecimal quantity=lines.stream().map(Line::quantity).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(definition!=null){
            if(!definition.isActive())throw new BadRequestException("DISCOUNT_INACTIVE");
            if(!definition.isAllStores()&&definition.getStoreIds()!=null&&!definition.getStoreIds().contains(storeId))throw new BadRequestException("DISCOUNT_NOT_AVAILABLE_AT_STORE");
            if(definition.getStartsAt()!=null&&now.isBefore(definition.getStartsAt()))throw new BadRequestException("DISCOUNT_NOT_STARTED");
            if(definition.getEndsAt()!=null&&now.isAfter(definition.getEndsAt()))throw new BadRequestException("DISCOUNT_EXPIRED");
            if(definition.getMinimumPurchaseAmount()!=null&&subtotal.compareTo(definition.getMinimumPurchaseAmount())<0)throw new BadRequestException("DISCOUNT_MINIMUM_PURCHASE_NOT_MET");
            if(definition.getMaximumPurchaseAmount()!=null&&subtotal.compareTo(definition.getMaximumPurchaseAmount())>0)throw new BadRequestException("DISCOUNT_MAXIMUM_PURCHASE_EXCEEDED");
            if(definition.getMinimumQuantity()!=null&&quantity.compareTo(BigDecimal.valueOf(definition.getMinimumQuantity()))<0)throw new BadRequestException("DISCOUNT_MINIMUM_QUANTITY_NOT_MET");
        }
        Set<UUID> categories=definition==null||definition.getEligibleCategoryIds()==null?Set.of():definition.getEligibleCategoryIds();
        Set<UUID> products=definition==null||definition.getEligibleProductIds()==null?Set.of():definition.getEligibleProductIds();
        List<Line> eligible=lines.stream().filter(Line::discountAllowed)
                .filter(line->categories.isEmpty()&&products.isEmpty()
                        || line.categoryId()!=null&&categories.contains(line.categoryId())
                        || products.contains(line.productId())
                        || line.sourceItemId()!=null&&products.contains(line.sourceItemId())).toList();
        BigDecimal eligibleSubtotal=money(eligible.stream().map(Line::subtotal).reduce(BigDecimal.ZERO,BigDecimal::add));
        if(eligibleSubtotal.signum()<=0)throw new BadRequestException("DISCOUNT_NO_ELIGIBLE_ITEMS");
        BigDecimal raw=type==SaleAdjustmentType.DISCOUNT_PERCENTAGE?money(eligibleSubtotal.multiply(value).divide(new BigDecimal("100"),SCALE,RoundingMode.HALF_UP)):money(value);
        boolean capped=definition!=null&&definition.getMaximumDiscountAmount()!=null&&raw.compareTo(definition.getMaximumDiscountAmount())>0;
        BigDecimal amount=capped?money(definition.getMaximumDiscountAmount()):raw;
        if(amount.compareTo(eligibleSubtotal)>0)throw new BadRequestException("DISCOUNT_EXCEEDS_ELIGIBLE_SUBTOTAL");
        BigDecimal allocated=BigDecimal.ZERO.setScale(SCALE); java.util.ArrayList<Allocation> result=new java.util.ArrayList<>();
        for(int i=0;i<eligible.size();i++){Line line=eligible.get(i);BigDecimal part=i==eligible.size()-1?money(amount.subtract(allocated)):money(amount.multiply(line.subtotal()).divide(eligibleSubtotal,SCALE,RoundingMode.HALF_UP));result.add(new Allocation(line.lineId(),part));allocated=money(allocated.add(part));}
        return new Result(amount,List.copyOf(result),capped);
    }
    private static BigDecimal money(BigDecimal value){return value.setScale(SCALE,RoundingMode.HALF_UP);}
}
