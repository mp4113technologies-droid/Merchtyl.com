package com.merchtyl.tax;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.security.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductTaxCategoryAssignmentService {
    private final ProductTaxCategoryAssignmentRepository assignmentRepository;
    private final ProductRepository productRepository;
    private final TaxCategoryService taxCategoryService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ProductTaxCategoryAssignmentService(
            ProductTaxCategoryAssignmentRepository assignmentRepository,
            ProductRepository productRepository,
            TaxCategoryService taxCategoryService,
            UserRepository userRepository,
            AuditService auditService) {
        this.assignmentRepository = assignmentRepository;
        this.productRepository = productRepository;
        this.taxCategoryService = taxCategoryService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ProductTaxCategoryAssignmentResponse create(ProductTaxCategoryAssignmentRequest request, Authentication authentication) {
        Product product = product(request.productId());
        TaxCategory category = taxCategoryService.find(request.taxCategoryId());
        if (assignmentRepository.existsByProduct(product)) {
            throw duplicate();
        }
        product.setTaxCategoryId(category.getId());
        ProductTaxCategoryAssignmentResponse response = ProductTaxCategoryAssignmentResponse.from(save(new ProductTaxCategoryAssignment(product, category, request.active())));
        audit(authentication, AuditAction.PRODUCT_TAX_CATEGORY_ASSIGNED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductTaxCategoryAssignmentResponse> search(ProductTaxCategoryAssignmentSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = assignmentRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize, Sort.by("product.name").and(Sort.by("id"))));
        return new PageResponse<>(page.getContent().stream().map(ProductTaxCategoryAssignmentResponse::from).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public ProductTaxCategoryAssignmentResponse get(UUID id) {
        return ProductTaxCategoryAssignmentResponse.from(find(id));
    }

    @Transactional
    public ProductTaxCategoryAssignmentResponse update(UUID id, ProductTaxCategoryAssignmentUpdateRequest request, Authentication authentication) {
        ProductTaxCategoryAssignment assignment = find(id);
        TaxGeographySupport.requireCurrentVersion(assignment.getVersion(), request.version(), "Product tax category assignment");
        Product product = product(request.productId());
        TaxCategory category = taxCategoryService.find(request.taxCategoryId());
        if (assignmentRepository.existsByProductAndIdNot(product, id)) {
            throw duplicate();
        }
        ProductTaxCategoryAssignmentResponse before = ProductTaxCategoryAssignmentResponse.from(assignment);
        assignment.getProduct().setTaxCategoryId(null);
        product.setTaxCategoryId(category.getId());
        assignment.update(product, category, request.active());
        ProductTaxCategoryAssignmentResponse after = ProductTaxCategoryAssignmentResponse.from(save(assignment));
        audit(authentication, AuditAction.PRODUCT_TAX_CATEGORY_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public ProductTaxCategoryAssignmentResponse updateStatus(UUID id, ProductTaxCategoryAssignmentStatusRequest request, Authentication authentication) {
        ProductTaxCategoryAssignment assignment = find(id);
        TaxGeographySupport.requireCurrentVersion(assignment.getVersion(), request.version(), "Product tax category assignment");
        ProductTaxCategoryAssignmentResponse before = ProductTaxCategoryAssignmentResponse.from(assignment);
        assignment.setActive(request.active());
        assignment.getProduct().setTaxCategoryId(request.active() ? assignment.getTaxCategory().getId() : null);
        ProductTaxCategoryAssignmentResponse after = ProductTaxCategoryAssignmentResponse.from(save(assignment));
        audit(authentication, AuditAction.PRODUCT_TAX_CATEGORY_STATUS_CHANGED, id, before, after);
        return after;
    }

    private ProductTaxCategoryAssignment find(UUID id) {
        return assignmentRepository.findById(id).orElseThrow(() -> new NotFoundException("Product tax category assignment not found"));
    }

    private Product product(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private ProductTaxCategoryAssignment save(ProductTaxCategoryAssignment assignment) {
        try {
            return assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private Specification<ProductTaxCategoryAssignment> specification(ProductTaxCategoryAssignmentSearchRequest request) {
        return Specification
                .where(TaxGeographySupport.<ProductTaxCategoryAssignment>equalReference("product", request.productId()))
                .and(TaxGeographySupport.equalReference("taxCategory", request.taxCategoryId()))
                .and(TaxGeographySupport.equalBoolean("active", request.active()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "PRODUCT_TAX_CATEGORY_ASSIGNMENT", entityId, before, after);
    }

    private ConflictException duplicate() {
        return new ConflictException("Product already has a tax category assignment");
    }
}
