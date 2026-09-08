package com.merchtyl.discount;

import com.merchtyl.common.*;
import com.merchtyl.sales.SaleAdjustmentType;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.RoleName;
import com.merchtyl.store.StoreRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DiscountDefinitionService {
    private static final UUID NONE = new UUID(0, 0);
    private final DiscountDefinitionRepository repository;
    private final StoreAccessService access;
    private final StoreRepository stores;
    public DiscountDefinitionService(DiscountDefinitionRepository repository, StoreAccessService access, StoreRepository stores) { this.repository=repository; this.access=access; this.stores=stores; }

    @Transactional(readOnly=true) public List<DiscountDefinitionResponse> list(Authentication auth) {
        return repository.findAllByTenantIdOrderByNameAsc(access.currentTenantId(auth)).stream().map(DiscountDefinitionResponse::from).toList();
    }
    @Transactional(readOnly=true) public List<DiscountDefinitionResponse> activeForStore(UUID storeId, Authentication auth) {
        access.requireStoreAccess(auth, storeId);
        var now=java.time.Instant.now();
        return repository.findAllByTenantIdAndActiveTrueOrderByNameAsc(access.currentTenantId(auth)).stream()
                .filter(value->available(value,storeId,now)).map(DiscountDefinitionResponse::from).toList();
    }
    @Transactional public DiscountDefinitionResponse create(DiscountDefinitionRequest request, Authentication auth) {
        var actor=access.currentTenantUser(auth); validate(request,actor.getTenantId()); unique(actor.getTenantId(),request.name(),NONE);
        return DiscountDefinitionResponse.from(repository.save(new DiscountDefinition(actor.getTenantId(),request,actor.getId())));
    }
    @Transactional public DiscountDefinitionResponse update(UUID id, DiscountDefinitionRequest request, Authentication auth) {
        var actor=access.currentTenantUser(auth); validate(request,actor.getTenantId()); var value=find(id,actor.getTenantId()); unique(actor.getTenantId(),request.name(),id);
        value.update(request,actor.getId()); return DiscountDefinitionResponse.from(value);
    }
    @Transactional(readOnly=true) public DiscountDefinition requireActive(UUID id, UUID tenantId) {
        var value=find(id,tenantId); if(!value.isActive()) throw new ConflictException("DISCOUNT_INACTIVE"); return value;
    }
    @Transactional(readOnly=true) public DiscountDefinition requireApplicable(UUID id,UUID tenantId,UUID storeId,java.time.Instant now){
        var value=requireActive(id,tenantId); if(!available(value,storeId,now))throw new ConflictException("DISCOUNT_NOT_AVAILABLE"); return value;
    }
    @Transactional public void delete(UUID id,Authentication auth){var actor=access.currentTenantUser(auth);if(!access.roles(actor).contains(RoleName.OWNER)&&!access.roles(actor).contains(RoleName.TENANT_OWNER))throw new ForbiddenOperationException("OWNER_REQUIRED");var value=find(id,actor.getTenantId());if(repository.hasHistoricalUsage(id))throw new ConflictException("DISCOUNT_HAS_HISTORICAL_USAGE");repository.delete(value);}
    private DiscountDefinition find(UUID id,UUID tenantId){return repository.findByIdAndTenantId(id,tenantId).orElseThrow(()->new NotFoundException("DISCOUNT_NOT_FOUND"));}
    private void unique(UUID tenantId,String name,UUID id){if(repository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId,name.trim(),id))throw new ConflictException("DISCOUNT_NAME_EXISTS");}
    private void validate(DiscountDefinitionRequest request,UUID tenantId){
        if(request.type()!=SaleAdjustmentType.DISCOUNT_PERCENTAGE&&request.type()!=SaleAdjustmentType.DISCOUNT_AMOUNT)throw new BadRequestException("INVALID_DISCOUNT_TYPE");
        if(request.value()==null||request.value().signum()<=0)throw new BadRequestException("DISCOUNT_VALUE_INVALID");
        if(request.type()==SaleAdjustmentType.DISCOUNT_PERCENTAGE&&request.value().compareTo(new java.math.BigDecimal("100"))>0)throw new BadRequestException("DISCOUNT_PERCENTAGE_EXCEEDS_MAXIMUM");
        if(request.maximumPurchaseAmount()!=null&&request.minimumPurchaseAmount()!=null&&request.maximumPurchaseAmount().compareTo(request.minimumPurchaseAmount())<0)throw new BadRequestException("DISCOUNT_PURCHASE_RANGE_INVALID");
        if(request.maximumDiscountAmount()!=null&&request.type()!=SaleAdjustmentType.DISCOUNT_PERCENTAGE)throw new BadRequestException("MAXIMUM_DISCOUNT_PERCENTAGE_ONLY");
        if(request.startsAt()!=null&&request.endsAt()!=null&&!request.endsAt().isAfter(request.startsAt()))throw new BadRequestException("DISCOUNT_SCHEDULE_INVALID");
        if(!request.allStores()&&(request.storeIds()==null||request.storeIds().isEmpty()))throw new BadRequestException("DISCOUNT_STORE_REQUIRED");
        if(request.storeIds()!=null)request.storeIds().forEach(id->{var store=stores.findById(id).orElseThrow(()->new NotFoundException("STORE_NOT_FOUND"));if(!tenantId.equals(store.getTenantId()))throw new ForbiddenOperationException("DISCOUNT_STORE_TENANT_MISMATCH");});
    }
    private static boolean available(DiscountDefinition value,UUID storeId,java.time.Instant now){return (value.isAllStores()||value.getStoreIds().contains(storeId))&&(value.getStartsAt()==null||!now.isBefore(value.getStartsAt()))&&(value.getEndsAt()==null||!now.isAfter(value.getEndsAt()));}
}
