package com.merchtyl.supplier;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class ProductSupplierService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductSupplierRepository productSupplierRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ProductSupplierService(
            ProductSupplierRepository productSupplierRepository,
            ProductRepository productRepository,
            SupplierRepository supplierRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.productSupplierRepository = productSupplierRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ProductSupplierResponse create(ProductSupplierRequest request, Authentication authentication) {
        ProductSupplierValues values = values(request);
        if (productSupplierRepository.existsByProductIdAndSupplier(values.product().getId(), values.supplier())) {
            throw duplicateAssociation();
        }
        ProductSupplierResponse response = ProductSupplierResponse.from(save(new ProductSupplier(values)));
        audit(authentication, AuditAction.PRODUCT_SUPPLIER_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSupplierResponse> search(ProductSupplierSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = productSupplierRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.ASC, "supplierSku").and(Sort.by(Sort.Direction.ASC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(ProductSupplierResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public ProductSupplierResponse get(UUID id) {
        return ProductSupplierResponse.from(find(id));
    }

    @Transactional
    public ProductSupplierResponse update(UUID id, ProductSupplierUpdateRequest request, Authentication authentication) {
        ProductSupplier productSupplier = find(id);
        requireCurrentVersion(productSupplier, request.version());
        ProductSupplierValues values = values(request);
        if (productSupplierRepository.existsByProductIdAndSupplierAndIdNot(values.product().getId(), values.supplier(), id)) {
            throw duplicateAssociation();
        }
        ProductSupplierResponse before = ProductSupplierResponse.from(productSupplier);
        productSupplier.update(values);
        ProductSupplierResponse after = ProductSupplierResponse.from(save(productSupplier));
        audit(authentication, AuditAction.PRODUCT_SUPPLIER_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public ProductSupplierResponse updateStatus(UUID id, ProductSupplierStatusRequest request, Authentication authentication) {
        ProductSupplier productSupplier = find(id);
        requireCurrentVersion(productSupplier, request.version());
        ProductSupplierResponse before = ProductSupplierResponse.from(productSupplier);
        productSupplier.setActive(request.active());
        ProductSupplierResponse after = ProductSupplierResponse.from(save(productSupplier));
        audit(authentication, AuditAction.PRODUCT_SUPPLIER_STATUS_CHANGED, id, before, after);
        return after;
    }

    private ProductSupplierValues values(ProductSupplierRequest request) {
        return new ProductSupplierValues(
                findProduct(request.productId()),
                findSupplier(request.supplierId()),
                optionalText(request.supplierSku()),
                request.preferred(),
                request.active());
    }

    private ProductSupplierValues values(ProductSupplierUpdateRequest request) {
        return new ProductSupplierValues(
                findProduct(request.productId()),
                findSupplier(request.supplierId()),
                optionalText(request.supplierSku()),
                request.preferred(),
                request.active());
    }

    private ProductSupplier save(ProductSupplier productSupplier) {
        try {
            return productSupplierRepository.saveAndFlush(productSupplier);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateAssociation();
        }
    }

    private ProductSupplier find(UUID id) {
        return productSupplierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product supplier not found"));
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private Supplier findSupplier(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Supplier not found"));
    }

    private Specification<ProductSupplier> specification(ProductSupplierSearchRequest request) {
        return Specification
                .where(equalUuid("productId", request.productId()))
                .and(equalSupplierId(request.supplierId()))
                .and(containsString("supplierSku", request.supplierSku()))
                .and(equalBoolean("preferred", request.preferred()))
                .and(equalBoolean("active", request.active()));
    }

    private void requireCurrentVersion(ProductSupplier productSupplier, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != productSupplier.getVersion()) {
            throw new ConflictException("Product supplier was modified by another transaction");
        }
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                "PRODUCT_SUPPLIER",
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

    private static Specification<ProductSupplier> equalUuid(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<ProductSupplier> equalSupplierId(UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("supplier").get("id"), value);
    }

    private static Specification<ProductSupplier> containsString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), pattern);
    }

    private static Specification<ProductSupplier> equalBoolean(String field, Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static String optionalText(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private static ConflictException duplicateAssociation() {
        return new ConflictException("Product supplier already exists");
    }
}
