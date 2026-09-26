package com.merchtyl.posquickkey;

import com.merchtyl.common.*;
import com.merchtyl.product.*;
import com.merchtyl.security.StoreAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.merchtyl.posquickkey.PosQuickKeyDtos.*;

@Service
public class PosQuickKeyService {
    public static final int MAX_KEYS = 20;
    private final PosQuickKeyRepository repository;
    private final ProductVariantRepository variants;
    private final StoreProductRepository storeProducts;
    private final StoreAccessService storeAccess;

    public PosQuickKeyService(PosQuickKeyRepository repository, ProductVariantRepository variants,
                              StoreProductRepository storeProducts, StoreAccessService storeAccess) {
        this.repository = repository; this.variants = variants; this.storeProducts = storeProducts; this.storeAccess = storeAccess;
    }

    @Transactional(readOnly = true)
    public List<Response> configuration(Authentication authentication) {
        UUID tenantId = tenant(authentication);
        return repository.findByTenantIdOrderByDisplayOrderAscIdAsc(tenantId).stream()
                .map(key -> response(key, productAvailable(key.getProductVariant())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Response> forStore(UUID storeId, Authentication authentication) {
        UUID tenantId = tenant(authentication);
        storeAccess.requireStoreAccess(authentication, storeId);
        return repository.findByTenantIdOrderByDisplayOrderAscIdAsc(tenantId).stream()
                .filter(PosQuickKey::isActive)
                .filter(key -> productAvailable(key.getProductVariant()))
                .filter(key -> availableAtStore(key.getProductVariant().getProduct(), tenantId, storeId))
                .map(key -> response(key, true)).toList();
    }

    @Transactional
    public Response create(CreateRequest request, Authentication authentication) {
        UUID tenantId = tenant(authentication);
        if (repository.countByTenantId(tenantId) >= MAX_KEYS) throw new BadRequestException("Maximum 20 Quick Keys allowed");
        ProductVariant variant = variants.findCheckoutVariants(tenantId, List.of(request.productVariantId())).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Product variant not found"));
        if (!productAvailable(variant)) throw new BadRequestException("Only active products and variants can be configured as Quick Keys");
        if (repository.existsByTenantIdAndProductVariant_Id(tenantId, variant.getId())) throw new ConflictException("Quick Key already configured");
        int order = repository.findByTenantIdOrderByDisplayOrderAscIdAsc(tenantId).stream().mapToInt(PosQuickKey::getDisplayOrder).max().orElse(-1) + 1;
        return response(repository.save(new PosQuickKey(tenantId, variant, request.displayLabel(), order)), productAvailable(variant));
    }

    @Transactional
    public Response update(UUID id, UpdateRequest request, Authentication authentication) {
        UUID tenantId = tenant(authentication);
        PosQuickKey key = repository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Quick Key not found"));
        if (!key.isActive() && request.active() && !productAvailable(key.getProductVariant())) throw new BadRequestException("Unavailable product cannot be enabled");
        List<PosQuickKey> ordered = new ArrayList<>(repository.findByTenantIdOrderByDisplayOrderAscIdAsc(tenantId));
        ordered.removeIf(item -> item.getId().equals(id));
        int target = Math.min(request.displayOrder(), ordered.size());
        ordered.add(target, key);
        for (int index = 0; index < ordered.size(); index++) {
            PosQuickKey item = ordered.get(index);
            item.update(item == key ? request.displayLabel() : item.getDisplayLabel(), index, item == key ? request.active() : item.isActive());
        }
        repository.saveAll(ordered);
        return response(key, productAvailable(key.getProductVariant()));
    }

    @Transactional
    public void delete(UUID id, Authentication authentication) {
        UUID tenantId = tenant(authentication);
        PosQuickKey key = repository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException("Quick Key not found"));
        repository.delete(key);
        List<PosQuickKey> remaining = repository.findByTenantIdOrderByDisplayOrderAscIdAsc(tenantId);
        for (int index = 0; index < remaining.size(); index++) remaining.get(index).update(remaining.get(index).getDisplayLabel(), index, remaining.get(index).isActive());
        repository.saveAll(remaining);
    }

    private boolean availableAtStore(Product product, UUID tenantId, UUID storeId) {
        return product.getAvailabilityScope() == ProductAvailabilityScope.ALL_STORES || storeProducts
                .findByTenantIdAndStore_IdAndProduct_IdAndActiveTrueAndSellableTrue(tenantId, storeId, product.getId()).isPresent();
    }

    private static boolean productAvailable(ProductVariant variant) {
        Product product = variant.getProduct();
        return variant.isActive() && product.isActive() && !product.isDeleted();
    }

    private static Response response(PosQuickKey key, boolean available) {
        ProductVariant variant = key.getProductVariant(); Product product = variant.getProduct();
        return new Response(key.getId(), product.getId(), variant.getId(), product.getName(), variant.getName(),
                key.getDisplayLabel(), variant.getSku(), variant.getPrice(), product.getSellableType(),
                product.hasCapability(ProductCapability.REQUIRE_AGE_VERIFICATION), product.getMinimumAge(), key.isActive(), available,
                key.getDisplayOrder(), key.getVersion());
    }

    private UUID tenant(Authentication authentication) { return storeAccess.currentTenantUser(authentication).getTenantId(); }
}
