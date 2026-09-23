package com.merchtyl.inventoryimport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.common.*;
import com.merchtyl.inventory.*;
import com.merchtyl.inventoryimport.InventoryImportDtos.*;
import com.merchtyl.product.*;
import com.merchtyl.security.*;
import com.merchtyl.store.*;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryUpdateImportService {
    private static final int SCALE=4;
    private final InventoryUpdateWorkbookService workbooks; private final InventoryImportBatchRepository batches; private final StoreAccessService access; private final StoreRepository stores; private final ProductVariantRepository variants; private final StoreProductRepository storeProducts; private final InventoryBalanceRepository balances; private final InventoryService inventory; private final ObjectMapper json; private final Clock clock;
    @Autowired
    public InventoryUpdateImportService(InventoryUpdateWorkbookService workbooks,InventoryImportBatchRepository batches,StoreAccessService access,StoreRepository stores,ProductVariantRepository variants,StoreProductRepository storeProducts,InventoryBalanceRepository balances,InventoryService inventory,ObjectMapper json){this(workbooks,batches,access,stores,variants,storeProducts,balances,inventory,json,Clock.systemUTC());}
    InventoryUpdateImportService(InventoryUpdateWorkbookService workbooks,InventoryImportBatchRepository batches,StoreAccessService access,StoreRepository stores,ProductVariantRepository variants,StoreProductRepository storeProducts,InventoryBalanceRepository balances,InventoryService inventory,ObjectMapper json,Clock clock){this.workbooks=workbooks;this.batches=batches;this.access=access;this.stores=stores;this.variants=variants;this.storeProducts=storeProducts;this.balances=balances;this.inventory=inventory;this.json=json;this.clock=clock;}

    @Transactional(readOnly=true)
    public byte[] download(UUID storeId,Authentication auth){Context c=context(storeId,auth);Set<UUID> assigned=storeProducts.findByTenantIdAndStore_IdAndActiveTrueAndSellableTrue(c.tenantId,storeId).stream().map(v->v.getProduct().getId()).collect(Collectors.toSet());List<ProductVariant> eligible=variants.findAllByTenantId(c.tenantId).stream().filter(v->v.isActive()&&v.getProduct().isActive()&&v.getProduct().isInventoryTrackingEnabled()).filter(v->v.getProduct().getAvailabilityScope()==ProductAvailabilityScope.ALL_STORES||assigned.contains(v.getProduct().getId())).sorted(Comparator.comparing((ProductVariant v)->v.getProduct().getName()).thenComparing(ProductVariant::getName)).toList();Map<UUID,BigDecimal> current=balanceMap(storeId,eligible.stream().map(ProductVariant::getId).toList());List<InventoryUpdateWorkbookService.DownloadRow> rows=eligible.stream().map(v->new InventoryUpdateWorkbookService.DownloadRow(v,barcode(v),current.getOrDefault(v.getId(),zero()))).toList();return workbooks.create(c.store,rows);}

    @Transactional
    public ValidationResponse validate(UUID storeId,String filename,byte[] bytes,Authentication auth){Context c=context(storeId,auth);List<ParsedRow> parsed=workbooks.parse(storeId,bytes);InventoryImportBatch batch=new InventoryImportBatch(c.tenantId,storeId,safe(filename),c.actor.getId(),write(parsed),Instant.now(clock));batches.saveAndFlush(batch);return preview(batch,parsed);}

    @Transactional(readOnly=true)
    public ValidationResponse preview(UUID storeId,UUID importId,Authentication auth){Context c=context(storeId,auth);InventoryImportBatch batch=find(importId,c,storeId);return preview(batch,read(batch));}

    @Transactional
    public ConfirmResponse confirm(UUID storeId,UUID importId,Authentication auth){Context c=context(storeId,auth);InventoryImportBatch batch=findForUpdate(importId,c,storeId);if("COMPLETED".equals(batch.getStatus()))throw new ConflictException("INVENTORY_IMPORT_ALREADY_COMPLETED");List<ParsedRow> parsed=read(batch);ValidationResponse checked=preview(batch,parsed);if(!checked.canConfirm())throw new BadRequestException("INVENTORY_IMPORT_HAS_ERRORS");Instant now=Instant.now(clock);List<PreviewRow> applied=new ArrayList<>();for(PreviewRow row:checked.rows()){if(row.operation()==Operation.NO_CHANGE||row.adjustment().signum()==0)continue;InventoryBalance locked=balances.findVariantForUpdate(storeId,row.variantId()).orElse(null);BigDecimal authoritative=locked==null?zero():locked.getQuantityOnHand();BigDecimal delta=row.operation()==Operation.SET_COUNT?row.enteredQuantity().subtract(authoritative):row.enteredQuantity();BigDecimal result=authoritative.add(delta);if(delta.signum()==0)continue;InventoryTransactionType type=row.operation()==Operation.ADD_STOCK?InventoryTransactionType.PURCHASE:(delta.signum()>0?InventoryTransactionType.STOCK_COUNT_INCREASE:InventoryTransactionType.STOCK_COUNT_DECREASE);inventory.recordStockChange(new InventoryStockChangeRequest(storeId,row.productId(),type,delta,"INVENTORY_IMPORT",batch.getId(),row.operation()==Operation.ADD_STOCK?"Inventory workbook stock received":"Inventory workbook physical count",now,locked==null?null:locked.getVersion(),row.variantId()),auth);applied.add(new PreviewRow(row.rowNumber(),row.productId(),row.variantId(),row.productCode(),row.productName(),row.variant(),row.sku(),row.barcode(),row.downloadedStock(),authoritative,row.operation(),row.enteredQuantity(),delta,result,authoritative.compareTo(row.downloadedStock())!=0,List.of()));}
        batch.confirm(c.actor.getId(),applied.size(),now);batches.saveAndFlush(batch);return new ConfirmResponse(batch.getId(),storeId,applied.size(),applied);
    }

    private ValidationResponse preview(InventoryImportBatch batch,List<ParsedRow> parsed){Set<UUID> ids=parsed.stream().map(ParsedRow::variantId).collect(Collectors.toSet());Set<UUID> assigned=storeProducts.findByTenantIdAndStore_IdAndActiveTrueAndSellableTrue(batch.getTenantId(),batch.getStoreId()).stream().map(v->v.getProduct().getId()).collect(Collectors.toSet());Map<UUID,ProductVariant> found=variants.findAllById(ids).stream().filter(v->v.getTenantId().equals(batch.getTenantId())).filter(v->v.getProduct().getAvailabilityScope()==ProductAvailabilityScope.ALL_STORES||assigned.contains(v.getProduct().getId())).collect(Collectors.toMap(ProductVariant::getId,Function.identity()));Map<UUID,BigDecimal> current=balanceMap(batch.getStoreId(),new ArrayList<>(ids));Set<UUID> seen=new HashSet<>();List<PreviewRow> rows=new ArrayList<>();for(ParsedRow raw:parsed){ProductVariant v=found.get(raw.variantId());List<String> errors=new ArrayList<>();if(!seen.add(raw.variantId()))errors.add("Duplicate product/variant row.");if(v==null||!v.isActive()||!v.getProduct().isActive()||!v.getProduct().isInventoryTrackingEnabled())errors.add("Unknown or unavailable product/variant.");if(raw.countedStock()!=null&&raw.addStock()!=null)errors.add("Enter either Counted Stock or Add Stock, not both.");BigDecimal counted=normalize(raw.countedStock(),"Counted Stock",errors),add=normalize(raw.addStock(),"Add Stock",errors);if(counted!=null&&counted.signum()<0)errors.add("Counted Stock must be zero or greater.");if(add!=null&&add.signum()<=0)errors.add("Add Stock must be greater than zero.");BigDecimal db=current.getOrDefault(raw.variantId(),zero()),downloaded=raw.downloadedStock()==null?zero():raw.downloadedStock();Operation op=counted!=null?Operation.SET_COUNT:add!=null?Operation.ADD_STOCK:Operation.NO_CHANGE;BigDecimal entered=op==Operation.SET_COUNT?counted:op==Operation.ADD_STOCK?add:null;BigDecimal[] amounts=calculate(op,db,entered);rows.add(new PreviewRow(raw.rowNumber(),v==null?null:v.getProduct().getId(),raw.variantId(),v==null?null:v.getProduct().getProductReference(),v==null?null:v.getProduct().getName(),v==null?null:v.getName(),v==null?null:v.getSku(),v==null?null:barcode(v),downloaded,db,op,entered,amounts[0],amounts[1],downloaded.compareTo(db)!=0,List.copyOf(errors)));}
        int errors=(int)rows.stream().filter(r->!r.errors().isEmpty()).count(),changed=(int)rows.stream().filter(r->r.errors().isEmpty()&&r.adjustment().signum()!=0).count();
        int unchanged=(int)rows.stream().filter(r->r.errors().isEmpty()&&r.adjustment().signum()==0).count();
        return new ValidationResponse(batch.getId(),batch.getStoreId(),changed,unchanged,errors,errors==0,rows);
    }
    private Context context(UUID storeId,Authentication auth){var actor=access.currentTenantUser(auth);access.requireProductManagementScope(auth,Set.of(storeId));Store store=stores.findById(storeId).filter(s->actor.getTenantId().equals(s.getTenantId())).orElseThrow(()->new NotFoundException("STORE_NOT_FOUND"));return new Context(actor.getTenantId(),actor,store);}
    private InventoryImportBatch find(UUID id,Context c,UUID store){return batches.findById(id).filter(b->b.getTenantId().equals(c.tenantId)&&b.getStoreId().equals(store)).orElseThrow(()->new NotFoundException("INVENTORY_IMPORT_NOT_FOUND"));}
    private InventoryImportBatch findForUpdate(UUID id,Context c,UUID store){return batches.findByIdForUpdate(id).filter(b->b.getTenantId().equals(c.tenantId)&&b.getStoreId().equals(store)).orElseThrow(()->new NotFoundException("INVENTORY_IMPORT_NOT_FOUND"));}
    private Map<UUID,BigDecimal> balanceMap(UUID store,List<UUID> ids){if(ids.isEmpty())return Map.of();return balances.findAllByStoreIdAndVariantIdIn(store,ids).stream().collect(Collectors.toMap(v->v.getVariant().getId(),InventoryBalance::getQuantityOnHand));}
    private static BigDecimal normalize(BigDecimal value,String field,List<String> errors){if(value==null)return null;try{return value.setScale(SCALE,RoundingMode.UNNECESSARY);}catch(Exception e){errors.add(field+" supports up to 4 decimal places.");return value;}}
    static BigDecimal[] calculate(Operation operation,BigDecimal current,BigDecimal entered){if(operation==Operation.NO_CHANGE||entered==null)return new BigDecimal[]{zero(),current};BigDecimal delta=operation==Operation.SET_COUNT?entered.subtract(current):entered;return new BigDecimal[]{delta,current.add(delta)};}
    private String write(List<ParsedRow> rows){try{return json.writeValueAsString(rows);}catch(Exception e){throw new IllegalStateException(e);}} private List<ParsedRow> read(InventoryImportBatch b){try{return json.readValue(b.getPayloadJson(),new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private static String barcode(ProductVariant v){return v.getProduct().getBarcodes().stream().filter(b->b.isActive()&&b.getVariant()!=null&&b.getVariant().getId().equals(v.getId())).sorted(Comparator.comparing(b->!b.isPrimaryBarcode())).map(ProductBarcode::getBarcode).findFirst().orElse("");}
    private static BigDecimal zero(){return BigDecimal.ZERO.setScale(SCALE);}private static String safe(String f){return f==null||f.isBlank()?"inventory.xlsx":f.replaceAll("[\\r\\n]","_").substring(0,Math.min(255,f.length()));}
    private record Context(UUID tenantId,User actor,Store store){}
}
