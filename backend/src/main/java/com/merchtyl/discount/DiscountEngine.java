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
    public record PromotionLine(UUID lineId, UUID productId, UUID productVariantId, UUID productCategoryId,
                                UUID menuItemId, UUID menuItemVariantId, UUID menuCategoryId,
                                BigDecimal quantity, BigDecimal regularUnitPrice, boolean discountAllowed) {}

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

    public Result evaluateMultiBuy(DiscountDefinition definition, UUID storeId, PromotionDomain domain,
                                   List<PromotionLine> lines, Instant now) {
        if (definition.getType() != SaleAdjustmentType.MULTI_BUY_FIXED_PRICE) throw new BadRequestException("INVALID_DISCOUNT_TYPE");
        if (!definition.isActive() || definition.getDomain() != domain || !available(definition, storeId, now)) {
            return new Result(money(BigDecimal.ZERO), List.of(), false);
        }
        int buyQuantity = definition.getBuyQuantity() == null ? 0 : definition.getBuyQuantity();
        if (buyQuantity < 2 || definition.getBundlePrice() == null || definition.getBundlePrice().signum() <= 0) {
            throw new BadRequestException("MULTI_BUY_CONFIGURATION_INVALID");
        }
        List<PromotionLine> eligible = lines.stream().filter(PromotionLine::discountAllowed)
                .filter(line -> eligible(definition.getTargets(), line)).toList();
        int eligibleUnits = eligible.stream().map(PromotionLine::quantity)
                .mapToInt(quantity -> quantity.max(BigDecimal.ZERO).setScale(0, RoundingMode.FLOOR).intValue()).sum();
        int bundles = eligibleUnits / buyQuantity;
        int bundledUnits = bundles * buyQuantity;
        if (bundles == 0) return new Result(money(BigDecimal.ZERO), List.of(), false);

        // Highest-priced eligible units are bundled first. This is deterministic and gives the customer
        // the best valid price without depending on scan/cart order.
        List<PromotionLine> ordered = eligible.stream()
                .sorted(java.util.Comparator.comparing(PromotionLine::regularUnitPrice).reversed()
                        .thenComparing(line -> line.lineId().toString()))
                .toList();
        java.util.LinkedHashMap<UUID, BigDecimal> bundledRegularByLine = new java.util.LinkedHashMap<>();
        int remaining = bundledUnits;
        for (PromotionLine line : ordered) {
            if (remaining == 0) break;
            int lineUnits = line.quantity().max(BigDecimal.ZERO).setScale(0, RoundingMode.FLOOR).intValue();
            int selected = Math.min(remaining, lineUnits);
            if (selected > 0) bundledRegularByLine.put(line.lineId(), money(line.regularUnitPrice().multiply(BigDecimal.valueOf(selected))));
            remaining -= selected;
        }
        BigDecimal regularAmount = money(bundledRegularByLine.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal discount = money(regularAmount.subtract(definition.getBundlePrice().multiply(BigDecimal.valueOf(bundles))));
        if (discount.signum() <= 0) return new Result(money(BigDecimal.ZERO), List.of(), false);
        BigDecimal allocated = money(BigDecimal.ZERO);
        java.util.ArrayList<Allocation> allocations = new java.util.ArrayList<>();
        var entries = new java.util.ArrayList<>(bundledRegularByLine.entrySet());
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            BigDecimal part = index == entries.size() - 1 ? money(discount.subtract(allocated))
                    : money(discount.multiply(entry.getValue()).divide(regularAmount, SCALE, RoundingMode.HALF_UP));
            allocations.add(new Allocation(entry.getKey(), part));
            allocated = money(allocated.add(part));
        }
        return new Result(discount, List.copyOf(allocations), false);
    }

    private static boolean eligible(Set<PromotionTarget> targets, PromotionLine line) {
        return targets.stream().anyMatch(target -> switch (target.targetType()) {
            case PRODUCT -> target.targetId().equals(line.productId());
            case PRODUCT_VARIANT -> target.targetId().equals(line.productVariantId());
            case PRODUCT_CATEGORY -> target.targetId().equals(line.productCategoryId());
            case MENU_ITEM -> target.targetId().equals(line.menuItemId());
            case MENU_ITEM_VARIANT -> target.targetId().equals(line.menuItemVariantId());
            case MENU_CATEGORY -> target.targetId().equals(line.menuCategoryId());
        });
    }

    private static boolean available(DiscountDefinition definition, UUID storeId, Instant now) {
        return (definition.isAllStores() || definition.getStoreIds().contains(storeId))
                && (definition.getStartsAt() == null || !now.isBefore(definition.getStartsAt()))
                && (definition.getEndsAt() == null || !now.isAfter(definition.getEndsAt()));
    }
    private static BigDecimal money(BigDecimal value){return value.setScale(SCALE,RoundingMode.HALF_UP);}
}
