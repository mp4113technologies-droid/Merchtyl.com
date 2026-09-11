import type { DiscountDefinition, PromotionDomain, PromotionTargetType } from '../../api/types';

export type PromotionCartLine = { id:string; quantity:number; unitPrice:number; targets:Partial<Record<PromotionTargetType,string>> };

export function bestMultiBuyPromotion(definitions:DiscountDefinition[],domain:PromotionDomain,lines:PromotionCartLine[]) {
  return definitions.filter(definition=>definition.type==='MULTI_BUY_FIXED_PRICE'&&definition.domain===domain).map(definition=>{
    const targets=new Set((definition.targets??[]).map(target=>`${target.targetType}:${target.targetId}`));
    const eligible=lines.filter(line=>Object.entries(line.targets).some(([type,id])=>id&&targets.has(`${type}:${id}`))).sort((a,b)=>b.unitPrice-a.unitPrice||a.id.localeCompare(b.id));
    const quantity=eligible.reduce((sum,line)=>sum+Math.floor(Math.max(0,line.quantity)),0);const bundles=Math.floor(quantity/(definition.buyQuantity??Number.MAX_SAFE_INTEGER));let remaining=bundles*(definition.buyQuantity??0),regular=0;
    for(const line of eligible){const units=Math.min(remaining,Math.floor(line.quantity));regular+=units*line.unitPrice;remaining-=units;}
    return {definition,amount:Math.max(0,regular-bundles*(definition.bundlePrice??0))};
  }).filter(result=>result.amount>0).sort((a,b)=>b.amount-a.amount||(b.definition.priority??0)-(a.definition.priority??0)||a.definition.name.localeCompare(b.definition.name))[0];
}
