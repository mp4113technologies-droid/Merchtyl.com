package com.merchtyl.product;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.catalogue.Brand;
import com.merchtyl.catalogue.BrandRepository;
import com.merchtyl.catalogue.Category;
import com.merchtyl.catalogue.CategoryRepository;
import com.merchtyl.catalogue.UnitOfMeasure;
import com.merchtyl.catalogue.UnitOfMeasureRepository;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.security.User;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.UserRepository;
import com.merchtyl.inventory.InventoryBalanceRepository;
import com.merchtyl.tax.TaxCategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductBarcodeRepository productBarcodeRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final TaxCategoryRepository taxCategoryRepository;
    @Autowired private StoreAccessService storeAccessService;
    @Autowired private StoreProductRepository storeProductRepository;
    @Autowired private InventoryBalanceRepository inventoryBalanceRepository;

    public ProductService(
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            ProductBarcodeRepository productBarcodeRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            UnitOfMeasureRepository unitOfMeasureRepository,
            UserRepository userRepository,
            AuditService auditService,
            TaxCategoryRepository taxCategoryRepository) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productBarcodeRepository = productBarcodeRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.unitOfMeasureRepository = unitOfMeasureRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.taxCategoryRepository = taxCategoryRepository;
    }

    @Transactional
    public ProductResponse create(ProductRequest request, Authentication authentication) {
        UUID tenantId = currentTenantId(authentication);
        log.info("product_event event=PRODUCT_CREATE_REQUESTED tenant_id={} store_ids={} requested_store_count={} actor={}",
                tenantId, request.storeIds(), request.storeIds().size(), actorName(authentication));
        if (storeAccessService != null) {
            try {
                User actor = storeAccessService.requireProductManagementScope(authentication, request.storeIds());
                int assignedStoreCount = StoreAccessService.isOwner(storeAccessService.roles(actor))
                        ? request.storeIds().size()
                        : storeAccessService.getActiveManagedStoreIds(actor.getId()).size();
                log.info("product_event event=PRODUCT_STORE_SCOPE_RESOLVED tenant_id={} store_ids={} actor_user_id={} assigned_store_count={} requested_store_count={}",
                        tenantId, request.storeIds(), actor.getId(), assignedStoreCount, request.storeIds().size());
            } catch (RuntimeException exception) {
                log.warn("product_event event=PRODUCT_CREATE_DENIED tenant_id={} store_ids={} requested_store_count={} actor={} reason={}",
                        tenantId, request.storeIds(), request.storeIds().size(), actorName(authentication), exception.getMessage());
                throw exception;
            }
        }
        ProductValues values = values(request);
        requireUniqueCodesForCreate(tenantId, values);
        Product product = new Product(values);
        product.setMinimumAge(validatedMinimumAge(request.capabilities(), request.minimumAge()));
        product.assignTenant(tenantId);
        Product saved = save(product);
        if (storeAccessService != null) request.storeIds().forEach(storeId -> {
            var store = storeAccessService.tenantStore(tenantId, storeId);
            StoreProduct mapping = new StoreProduct(tenantId, store, saved);
            mapping.update(new StoreProductRequest(storeId, request.active(), request.active(), request.price(), null,
                    null, null, true, true));
            storeProductRepository.save(mapping);
        });
        ProductResponse response = ProductResponse.from(saved);
        audit(authentication, AuditAction.PRODUCT_CREATED, response.id(), null,
                java.util.Map.of("product", response, "storeIds", request.storeIds()));
        log.info("product_event event=PRODUCT_CREATED tenant_id={} store_ids={} product_id={} actor={}", tenantId, request.storeIds(), response.id(), actorName(authentication));
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductSearchRequest request, Authentication authentication) {
        UUID tenantId = currentTenantId(authentication);
        User actor = storeAccessService.currentTenantUser(authentication);
        Set<UUID> visibleStoreIds = StoreAccessService.isOwner(storeAccessService.roles(actor))
                ? null : storeAccessService.getActiveAssignedStoreIds(actor.getId());
        if (request.storeId() != null) {
            if (storeAccessService != null) storeAccessService.requireStoreAccess(authentication, request.storeId());
        }
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = productRepository.findAll(
                specification(request).and(equalUuid("tenantId", tenantId))
                        .and(storeAvailability(tenantId, request.storeId()))
                        .and(storeAvailabilityIn(tenantId, visibleStoreIds)),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(product -> response(product, tenantId, request.storeId())).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id, Authentication authentication) {
        UUID tenantId = currentTenantId(authentication);
        Product product = find(id, tenantId);
        requireProductVisibility(product, authentication);
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public PosBarcodeLookupResponse lookupBarcode(String barcode, UUID storeId, Authentication authentication) {
        UUID tenantId = currentTenantId(authentication);
        String normalized = cleanRequired(barcode, "barcode");
        log.debug("pos_event event=POS_BARCODE_SCANNED tenant_id={} store_id={} actor={}", tenantId, storeId, actorName(authentication));
        if (storeAccessService != null) storeAccessService.requireStoreAccess(authentication, storeId);
        ProductBarcode mapping = (storeAccessService == null
                ? productBarcodeRepository.findByBarcodeIgnoreCase(normalized)
                : productBarcodeRepository.findByTenantIdAndBarcodeIgnoreCase(tenantId, normalized))
                .orElseThrow(() -> {
                    log.warn("pos_event event=POS_BARCODE_NOT_FOUND tenant_id={} store_id={}", tenantId, storeId);
                    return new NotFoundException("BARCODE_NOT_FOUND");
                });
        Product product = mapping.getProduct();
        ProductVariant variant = mapping.getVariant();
        if (!mapping.isActive() || !product.isActive() || (variant != null && !variant.isActive())) {
            throw new BadRequestException("PRODUCT_NOT_ACTIVE");
        }
        StoreProduct storeProduct = storeProductRepository
                .findByTenantIdAndStore_IdAndProduct_IdAndActiveTrueAndSellableTrue(tenantId, storeId, product.getId())
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_AVAILABLE_IN_STORE"));
        var taxCategory = product.getTaxCategoryId() == null ? null
                : taxCategoryRepository.findById(product.getTaxCategoryId()).filter(category -> category.isActive()).orElse(null);
        var quantity = inventoryBalanceRepository.findByStoreIdAndProductId(storeId, product.getId())
                .map(balance -> balance.getQuantityOnHand()).orElse(java.math.BigDecimal.ZERO.setScale(4));
        PosBarcodeLookupResponse response = new PosBarcodeLookupResponse(
                product.getId(), variant == null ? null : variant.getId(), product.getName(),
                variant == null ? null : variant.getName(), mapping.getBarcode(),
                variant == null ? product.getSku() : variant.getSku(),
                product.getUnitOfMeasure() == null ? null : product.getUnitOfMeasure().getId(),
                variant == null ? storeProduct.getSellingPrice() : variant.getPrice(),
                product.getTaxCategoryId(), taxCategory == null ? null : taxCategory.getName(), quantity, true,
                product.hasCapability(ProductCapability.REQUIRE_AGE_VERIFICATION), product.getMinimumAge());
        log.debug("pos_event event=POS_BARCODE_RESOLVED tenant_id={} store_id={} product_id={} variant_id={}",
                tenantId, storeId, product.getId(), response.variantId());
        return response;
    }

    @Transactional
    public ProductResponse update(UUID id, ProductUpdateRequest request, Authentication authentication) {
        UUID tenantId = currentTenantId(authentication);
        Product product = find(id, tenantId);
        requireAllProductStoresManaged(product, authentication);
        requireCurrentVersion(product, request.version());
        ProductValues values = values(request);
        requireUniqueCodesForUpdate(tenantId, id, values);
        ProductResponse before = ProductResponse.from(product);
        product.update(values);
        product.setMinimumAge(validatedMinimumAge(request.capabilities(), request.minimumAge()));
        ProductResponse after = ProductResponse.from(save(product));
        audit(authentication, AuditAction.PRODUCT_UPDATED, id, before, after);
        log.info("product_event event=PRODUCT_UPDATED tenant_id={} product_id={} actor={}", tenantId, id, actorName(authentication));
        return after;
    }

    @Transactional
    public ProductResponse updateStatus(UUID id, ProductStatusRequest request, Authentication authentication) {
        Product product = find(id, currentTenantId(authentication));
        requireAllProductStoresManaged(product, authentication);
        requireCurrentVersion(product, request.version());
        ProductResponse before = ProductResponse.from(product);
        product.setActive(request.active());
        ProductResponse after = ProductResponse.from(save(product));
        audit(authentication, AuditAction.PRODUCT_STATUS_CHANGED, id, before, after);
        log.info("product_event event=PRODUCT_DEACTIVATED tenant_id={} product_id={} active={} actor={}", product.getTenantId(), id, request.active(), actorName(authentication));
        return after;
    }

    private ProductValues values(ProductRequest request) {
        requireActiveTaxCategory(request.taxCategoryId());
        return new ProductValues(
                normalizeSku(request.sku(), "sku"),
                cleanRequired(request.name(), "name"),
                optionalText(request.description()),
                request.sellableType(),
                findActiveUnit(request.unitOfMeasureId()),
                request.cost(),
                request.price(),
                findOptional(request.categoryId(), categoryRepository::findById, "Category not found"),
                findOptional(request.brandId(), brandRepository::findById, "Brand not found"),
                request.active(),
                request.inventoryTrackingEnabled(),
                request.decimalQuantityAllowed(),
                optionalText(request.imageUrl()),
                request.taxCategoryId(),
                variantValues(request.variants()),
                barcodeValues(request.barcodes()),
                capabilities(request.capabilities(), request.inventoryTrackingEnabled(), request.decimalQuantityAllowed()));
    }

    private ProductValues values(ProductUpdateRequest request) {
        requireActiveTaxCategory(request.taxCategoryId());
        return new ProductValues(
                normalizeSku(request.sku(), "sku"),
                cleanRequired(request.name(), "name"),
                optionalText(request.description()),
                request.sellableType(),
                findActiveUnit(request.unitOfMeasureId()),
                request.cost(),
                request.price(),
                findOptional(request.categoryId(), categoryRepository::findById, "Category not found"),
                findOptional(request.brandId(), brandRepository::findById, "Brand not found"),
                request.active(),
                request.inventoryTrackingEnabled(),
                request.decimalQuantityAllowed(),
                optionalText(request.imageUrl()),
                request.taxCategoryId(),
                variantValues(request.variants()),
                barcodeValues(request.barcodes()),
                capabilities(request.capabilities(), request.inventoryTrackingEnabled(), request.decimalQuantityAllowed()));
    }

    private void requireActiveTaxCategory(UUID taxCategoryId) {
        if (taxCategoryId == null) return;
        var category = taxCategoryRepository.findById(taxCategoryId)
                .orElseThrow(() -> new BadRequestException("Invalid tax category"));
        if (!category.isActive()) throw new BadRequestException("Tax category is inactive");
    }

    private Integer validatedMinimumAge(Set<ProductCapability> capabilities, Integer minimumAge) {
        boolean restricted = capabilities != null && capabilities.contains(ProductCapability.REQUIRE_AGE_VERIFICATION);
        if (restricted && minimumAge == null) {
            throw new BadRequestException("Minimum age is required for age-restricted products");
        }
        return restricted ? minimumAge : null;
    }

    private UnitOfMeasure findActiveUnit(UUID unitId) {
        if (unitId == null) return null;
        UnitOfMeasure unit = unitOfMeasureRepository.findById(unitId)
                .orElseThrow(() -> new BadRequestException("Invalid unit of measure"));
        if (!unit.isActive()) throw new BadRequestException("Unit of measure is inactive");
        return unit;
    }

    private List<ProductVariantValues> variantValues(List<ProductVariantRequest> requests) {
        return nullSafe(requests).stream()
                .map(request -> new ProductVariantValues(
                        normalizeSku(request.sku(), "variant sku"),
                        cleanRequired(request.name(), "variant name"),
                        optionalText(request.description()),
                        request.cost(),
                        request.price(),
                        request.active()))
                .toList();
    }

    private List<ProductBarcodeValues> barcodeValues(List<ProductBarcodeRequest> requests) {
        return nullSafe(requests).stream()
                .map(request -> new ProductBarcodeValues(
                        cleanRequired(request.barcode(), "barcode"),
                        normalizeOptionalSku(request.variantSku(), "barcode variantSku"),
                        request.primaryBarcode(),
                        request.active()))
                .toList();
    }

    private Set<ProductCapability> capabilities(Set<ProductCapability> requestedCapabilities, boolean inventoryTrackingEnabled, boolean decimalQuantityAllowed) {
        Set<ProductCapability> capabilities = requestedCapabilities == null || requestedCapabilities.isEmpty()
                ? new HashSet<>()
                : new HashSet<>(requestedCapabilities);
        if (inventoryTrackingEnabled) {
            capabilities.add(ProductCapability.TRACK_INVENTORY);
        } else {
            capabilities.remove(ProductCapability.TRACK_INVENTORY);
        }
        if (decimalQuantityAllowed) {
            capabilities.add(ProductCapability.ALLOW_DECIMAL_QUANTITY);
        } else {
            capabilities.remove(ProductCapability.ALLOW_DECIMAL_QUANTITY);
        }
        return capabilities;
    }

    private void requireUniqueCodesForCreate(UUID tenantId, ProductValues values) {
        requireInternallyUnique(values);
        if (productSkuExists(tenantId, values.sku()) || variantSkuExists(tenantId, values.sku())) {
            throw new ConflictException("SKU already exists");
        }
        for (ProductVariantValues variant : values.variants()) {
            if (productSkuExists(tenantId, variant.sku()) || variantSkuExists(tenantId, variant.sku())) {
                throw new ConflictException("Variant SKU already exists");
            }
        }
        for (ProductBarcodeValues barcode : values.barcodes()) {
            if (barcodeExists(tenantId, barcode.barcode())) {
                throw new ConflictException("Barcode already exists");
            }
        }
    }

    private boolean productSkuExists(UUID tenantId, String sku) {
        return storeAccessService == null ? productRepository.existsBySkuIgnoreCase(sku)
                : productRepository.existsByTenantIdAndSkuIgnoreCase(tenantId, sku);
    }

    private boolean variantSkuExists(UUID tenantId, String sku) {
        return storeAccessService == null ? productVariantRepository.existsBySkuIgnoreCase(sku)
                : productVariantRepository.existsByTenantIdAndSkuIgnoreCase(tenantId, sku);
    }

    private boolean barcodeExists(UUID tenantId, String barcode) {
        return storeAccessService == null ? productBarcodeRepository.existsByBarcodeIgnoreCase(barcode)
                : productBarcodeRepository.existsByTenantIdAndBarcodeIgnoreCase(tenantId, barcode);
    }

    private void requireUniqueCodesForUpdate(UUID tenantId, UUID productId, ProductValues values) {
        requireInternallyUnique(values);
        if (productRepository.existsByTenantIdAndSkuIgnoreCaseAndIdNot(tenantId, values.sku(), productId)
                || productVariantRepository.existsByTenantIdAndSkuIgnoreCaseAndProductIdNot(tenantId, values.sku(), productId)) {
            throw new ConflictException("SKU already exists");
        }
        for (ProductVariantValues variant : values.variants()) {
            if (productRepository.existsByTenantIdAndSkuIgnoreCaseAndIdNot(tenantId, variant.sku(), productId)
                    || productVariantRepository.existsByTenantIdAndSkuIgnoreCaseAndProductIdNot(tenantId, variant.sku(), productId)) {
                throw new ConflictException("Variant SKU already exists");
            }
        }
        for (ProductBarcodeValues barcode : values.barcodes()) {
            if (productBarcodeRepository.existsByTenantIdAndBarcodeIgnoreCaseAndProductIdNot(tenantId, barcode.barcode(), productId)) {
                throw new ConflictException("Barcode already exists");
            }
        }
    }

    private void requireInternallyUnique(ProductValues values) {
        Set<String> skus = new HashSet<>();
        addUnique(skus, values.sku(), "Duplicate SKU in request");
        values.variants().forEach(variant -> addUnique(skus, variant.sku(), "Duplicate SKU in request"));

        Set<String> variantSkus = values.variants().stream()
                .map(ProductVariantValues::sku)
                .collect(Collectors.toSet());
        for (ProductBarcodeValues barcode : values.barcodes()) {
            if (barcode.variantSku() != null && !variantSkus.contains(barcode.variantSku())) {
                throw new BadRequestException("Barcode variantSku must match a product variant SKU");
            }
        }

        Set<String> barcodes = new HashSet<>();
        values.barcodes().forEach(barcode -> addUnique(barcodes, barcode.barcode().toUpperCase(Locale.ROOT), "Duplicate barcode in request"));
    }

    private Product save(Product product) {
        try {
            return productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Product unique value already exists");
        }
    }

    private Product find(UUID id, UUID tenantId) {
        if (storeAccessService == null) {
            return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
        }
        return productRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private UUID currentTenantId(Authentication authentication) {
        if (storeAccessService != null) return storeAccessService.currentTenantId(authentication);
        if (authentication == null) return new UUID(0L, 1L);
        return userRepository.findByEmailIgnoreCase(authentication.getName()).map(User::getTenantId)
                .orElse(new UUID(0L, 1L));
    }

    private String actorName(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }

    private ProductResponse response(Product product, UUID tenantId, UUID storeId) {
        ProductResponse response = ProductResponse.from(product);
        if (storeId == null || storeProductRepository == null) return response;
        return storeProductRepository.findByTenantIdAndStore_IdAndProduct_IdAndActiveTrueAndSellableTrue(tenantId, storeId, product.getId())
                .map(mapping -> response.withPrice(mapping.getSellingPrice())).orElse(response);
    }

    private Specification<Product> specification(ProductSearchRequest request) {
        return Specification
                .where(containsString("name", request.name()))
                .and(equalString("sku", normalizeSkuFilter(request.sku())))
                .and(equalEnum("sellableType", request.sellableType()))
                .and(equalReference("category", request.categoryId()))
                .and(equalReference("brand", request.brandId()))
                .and(equalReference("unitOfMeasure", request.unitOfMeasureId()))
                .and(equalBoolean("active", request.active()))
                .and(barcodeEquals(request.barcode()));
    }

    private static Specification<Product> equalUuid(String field, UUID value) {
        return (root, query, builder) -> builder.equal(root.get(field), value);
    }

    private static Specification<Product> storeAvailability(UUID tenantId, UUID storeId) {
        if (storeId == null) {
            return null;
        }
        return (root, query, builder) -> {
            var subquery = query.subquery(UUID.class);
            var mapping = subquery.from(StoreProduct.class);
            subquery.select(mapping.get("product").get("id"));
            subquery.where(
                    builder.equal(mapping.get("tenantId"), tenantId),
                    builder.equal(mapping.get("store").get("id"), storeId),
                    builder.isTrue(mapping.get("active")),
                    builder.isTrue(mapping.get("sellable")));
            return root.get("id").in(subquery);
        };
    }

    private static Specification<Product> storeAvailabilityIn(UUID tenantId, Set<UUID> storeIds) {
        if (storeIds == null) return null;
        if (storeIds.isEmpty()) return (root, query, builder) -> builder.disjunction();
        return (root, query, builder) -> {
            var subquery = query.subquery(UUID.class);
            var mapping = subquery.from(StoreProduct.class);
            subquery.select(mapping.get("product").get("id"));
            subquery.where(builder.equal(mapping.get("tenantId"), tenantId), mapping.get("store").get("id").in(storeIds),
                    builder.isTrue(mapping.get("active")));
            return root.get("id").in(subquery);
        };
    }

    private void requireProductVisibility(Product product, Authentication authentication) {
        User actor = storeAccessService.currentTenantUser(authentication);
        if (StoreAccessService.isOwner(storeAccessService.roles(actor))) return;
        Set<UUID> assigned = storeAccessService.getActiveAssignedStoreIds(actor.getId());
        boolean visible = storeProductRepository.findByTenantIdAndProduct_IdOrderByStore_NameAsc(product.getTenantId(), product.getId())
                .stream().anyMatch(mapping -> mapping.isActive() && assigned.contains(mapping.getStore().getId()));
        if (!visible) throw new com.merchtyl.common.ForbiddenOperationException("PRODUCT_STORE_ACCESS_DENIED");
    }

    private void requireAllProductStoresManaged(Product product, Authentication authentication) {
        User actor = storeAccessService.currentTenantUser(authentication);
        if (StoreAccessService.isOwner(storeAccessService.roles(actor))) return;
        Set<UUID> managed = storeAccessService.getActiveManagedStoreIds(actor.getId());
        boolean denied = storeProductRepository.findByTenantIdAndProduct_IdOrderByStore_NameAsc(product.getTenantId(), product.getId())
                .stream().anyMatch(mapping -> mapping.isActive() && !managed.contains(mapping.getStore().getId()));
        if (denied || managed.isEmpty()) throw new com.merchtyl.common.ForbiddenOperationException("PRODUCT_STORE_ACCESS_DENIED");
    }

    private void requireCurrentVersion(Product product, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != product.getVersion()) {
            throw new ConflictException("Product was modified by another transaction");
        }
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                "PRODUCT",
                entityId,
                null,
                null,
                before,
                after,
                null));
    }

    private UUID actorUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }

    private static <T> T findOptional(UUID id, Function<UUID, java.util.Optional<T>> finder, String message) {
        if (id == null) {
            return null;
        }
        return finder.apply(id).orElseThrow(() -> new NotFoundException(message));
    }

    private static void addUnique(Set<String> values, String value, String message) {
        if (!values.add(value.toUpperCase(Locale.ROOT))) {
            throw new BadRequestException(message);
        }
    }

    private static Specification<Product> containsString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), pattern);
    }

    private static Specification<Product> equalString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<Product> equalEnum(String field, SellableType value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<Product> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<Product> equalBoolean(String field, Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<Product> barcodeEquals(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim();
        return (root, query, criteriaBuilder) -> {
            if (query != null) {
                query.distinct(true);
            }
            var barcodes = root.join("barcodes");
            return criteriaBuilder.equal(criteriaBuilder.lower(barcodes.get("barcode")), cleaned.toLowerCase(Locale.ROOT));
        };
    }

    private static String normalizeSku(String sku, String field) {
        String cleaned = cleanRequired(sku, field).toUpperCase(Locale.ROOT);
        if (!cleaned.matches("^[A-Z0-9][A-Z0-9_-]*$")) {
            throw new BadRequestException(field + " must use letters, numbers, underscores, and hyphens");
        }
        return cleaned;
    }

    private static String normalizeOptionalSku(String sku, String field) {
        return sku == null || sku.isBlank() ? null : normalizeSku(sku, field);
    }

    private static String normalizeSkuFilter(String sku) {
        return sku == null || sku.isBlank() ? null : sku.trim().toUpperCase(Locale.ROOT);
    }

    private static String cleanRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
