package com.merchtyl.product;

import com.merchtyl.common.NotFoundException;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.AuthorizationService;
import com.merchtyl.security.PermissionCode;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class StoreProductService {
    private final StoreProductRepository repository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final AuthorizationService authorizationService;
    public StoreProductService(StoreProductRepository repository, ProductRepository productRepository, StoreRepository storeRepository, StoreAccessService storeAccessService, AuthorizationService authorizationService) {
        this.repository=repository; this.productRepository=productRepository; this.storeRepository=storeRepository; this.storeAccessService=storeAccessService; this.authorizationService=authorizationService;
    }
    @Transactional(readOnly=true)
    public List<StoreProductResponse> list(UUID productId, Authentication authentication) {
        UUID tenantId=storeAccessService.currentTenantId(authentication);
        product(tenantId, productId);
        return repository.findByTenantIdAndProduct_IdOrderByStore_NameAsc(tenantId, productId).stream()
                .filter(mapping -> storeAccessService.canAccessStore(storeAccessService.currentTenantUser(authentication).getId(), mapping.getStore().getId()))
                .map(StoreProductResponse::from).toList();
    }
    @Transactional
    public List<StoreProductResponse> replace(UUID productId, List<StoreProductRequest> requests, Authentication authentication) {
        UUID tenantId=storeAccessService.currentTenantId(authentication);
        Product product=product(tenantId, productId);
        requests.forEach(request -> storeAccessService.requireStoreManagement(authentication, request.storeId()));
        Set<UUID> seen=new HashSet<>();
        List<StoreProductResponse> result=new ArrayList<>();
        for(StoreProductRequest request: requests) {
            if(!seen.add(request.storeId())) throw new IllegalArgumentException("Duplicate store product association");
            Store store=storeRepository.findByIdAndTenantId(request.storeId(), tenantId).orElseThrow(() -> new NotFoundException("Store not found"));
            StoreProduct mapping=repository.findByTenantIdAndStore_IdAndProduct_Id(tenantId, store.getId(), productId).orElseGet(() -> new StoreProduct(tenantId, store, product));
            if (mapping.getId() != null && mapping.getSellingPrice().compareTo(request.sellingPrice()) != 0
                    && !authorizationService.hasPermission(authentication, PermissionCode.PRODUCT_PRICE_UPDATE))
                throw new ForbiddenOperationException("PRODUCT_ACCESS_DENIED");
            if (!Objects.equals(mapping.getCostPrice(), request.costPrice())
                    && !authorizationService.hasPermission(authentication, PermissionCode.PRODUCT_COST_UPDATE))
                throw new ForbiddenOperationException("PRODUCT_ACCESS_DENIED");
            mapping.update(request); result.add(StoreProductResponse.from(repository.save(mapping)));
        }
        return result;
    }
    private Product product(UUID tenantId, UUID productId){return productRepository.findByIdAndTenantId(productId, tenantId).orElseThrow(() -> new NotFoundException("Product not found"));}
}
