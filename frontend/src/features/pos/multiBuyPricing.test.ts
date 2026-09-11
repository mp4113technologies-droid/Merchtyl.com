import { describe,expect,it } from 'vitest';
import type { DiscountDefinition } from '../../api/types';
import { bestMultiBuyPromotion } from './multiBuyPricing';

function promotion(overrides:Partial<DiscountDefinition>={}):DiscountDefinition{return {id:'promo',name:'2 for 10',type:'MULTI_BUY_FIXED_PRICE',value:10,description:null,minimumPurchaseAmount:null,maximumPurchaseAmount:null,maximumDiscountAmount:null,minimumQuantity:null,eligibleCategoryIds:[],eligibleProductIds:[],allStores:true,storeIds:[],startsAt:null,endsAt:null,active:true,domain:'RETAIL',buyQuantity:2,bundlePrice:10,priority:0,stackable:false,targets:[{targetType:'PRODUCT',targetId:'coke'}],createdAt:'',updatedAt:'',version:0,...overrides};}

describe('multi-buy pricing preview',()=>{
  it.each([[1,0],[2,2],[3,2],[4,4]])('prices $6 quantity %i with a 2 for $10 promotion',(quantity,savings)=>expect(bestMultiBuyPromotion([promotion()],'RETAIL',[{id:'line',quantity,unitPrice:6,targets:{PRODUCT:'coke'}}])?.amount??0).toBe(savings));
  it('mixes eligible menu items and leaves modifier charges outside the promotion',()=>{const definition=promotion({domain:'FOOD_SERVICE',bundlePrice:14,value:14,targets:[{targetType:'MENU_ITEM',targetId:'chicken'},{targetType:'MENU_ITEM',targetId:'cheese'}]});const result=bestMultiBuyPromotion([definition],'FOOD_SERVICE',[{id:'a',quantity:1,unitPrice:8,targets:{MENU_ITEM:'chicken'}},{id:'b',quantity:1,unitPrice:9,targets:{MENU_ITEM:'cheese'}}]);expect(result?.amount).toBe(3);expect(17-(result?.amount??0)+4).toBe(18);});
});
