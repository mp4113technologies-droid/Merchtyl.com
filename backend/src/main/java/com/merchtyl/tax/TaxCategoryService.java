package com.merchtyl.tax;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
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
public class TaxCategoryService {
    private final TaxCategoryRepository taxCategoryRepository;
    private final TaxGroupService taxGroupService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TaxCategoryService(
            TaxCategoryRepository taxCategoryRepository,
            TaxGroupService taxGroupService,
            UserRepository userRepository,
            AuditService auditService) {
        this.taxCategoryRepository = taxCategoryRepository;
        this.taxGroupService = taxGroupService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TaxCategoryResponse create(TaxCategoryRequest request, Authentication authentication) {
        String code = TaxGeographySupport.normalizeCode(request.code(), 64);
        if (taxCategoryRepository.existsByCodeIgnoreCase(code)) {
            throw duplicate();
        }
        TaxCategory category = new TaxCategory(
                group(request.taxGroupId()),
                code,
                TaxGeographySupport.cleanRequired(request.name(), "name"),
                request.treatment(),
                TaxGeographySupport.optionalText(request.description()),
                request.active());
        category.assignMerchantOwnership(currentTenantId(authentication));
        TaxCategoryResponse response = TaxCategoryResponse.from(save(category));
        audit(authentication, AuditAction.TAX_CATEGORY_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxCategoryResponse> search(TaxCategorySearchRequest request) {
        return search(request, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxCategoryResponse> search(TaxCategorySearchRequest request, Authentication authentication) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = taxCategoryRepository.findAll(
                specification(request).and(visibleTo(authentication)),
                PageRequest.of(pageNumber, pageSize, Sort.by("name").and(Sort.by("id"))));
        return new PageResponse<>(page.getContent().stream().map(TaxCategoryResponse::from).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private Specification<TaxCategory> visibleTo(Authentication authentication) {
        if (authentication == null) return null;
        UUID tenantId = currentTenantId(authentication);
        return (root, query, builder) -> builder.or(
                builder.isTrue(root.get("systemManaged")),
                builder.equal(root.get("ownerTenantId"), tenantId));
    }

    @Transactional(readOnly = true)
    public TaxCategoryResponse get(UUID id) {
        return TaxCategoryResponse.from(find(id));
    }

    @Transactional
    public TaxCategoryResponse update(UUID id, TaxCategoryUpdateRequest request, Authentication authentication) {
        TaxCategory category = find(id);
        if (category.isSystemManaged()) throw new ConflictException("System tax categories cannot be modified");
        requireOwned(category, authentication);
        TaxGeographySupport.requireCurrentVersion(category.getVersion(), request.version(), "Tax category");
        String code = TaxGeographySupport.normalizeCode(request.code(), 64);
        if (taxCategoryRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw duplicate();
        }
        TaxCategoryResponse before = TaxCategoryResponse.from(category);
        category.update(
                group(request.taxGroupId()),
                code,
                TaxGeographySupport.cleanRequired(request.name(), "name"),
                request.treatment(),
                TaxGeographySupport.optionalText(request.description()),
                request.active());
        TaxCategoryResponse after = TaxCategoryResponse.from(save(category));
        audit(authentication, AuditAction.TAX_CATEGORY_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public TaxCategoryResponse updateStatus(UUID id, TaxCategoryStatusRequest request, Authentication authentication) {
        TaxCategory category = find(id);
        if (category.isSystemManaged()) throw new ConflictException("System tax categories cannot be modified");
        requireOwned(category, authentication);
        TaxGeographySupport.requireCurrentVersion(category.getVersion(), request.version(), "Tax category");
        TaxCategoryResponse before = TaxCategoryResponse.from(category);
        category.setActive(request.active());
        TaxCategoryResponse after = TaxCategoryResponse.from(save(category));
        audit(authentication, AuditAction.TAX_CATEGORY_STATUS_CHANGED, id, before, after);
        return after;
    }

    TaxCategory find(UUID id) {
        return taxCategoryRepository.findById(id).orElseThrow(() -> new NotFoundException("Tax category not found"));
    }

    private UUID currentTenantId(Authentication authentication) {
        if (authentication == null) return new UUID(0L, 1L);
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found"))
                .getTenantId();
    }

    private void requireOwned(TaxCategory category, Authentication authentication) {
        if (!currentTenantId(authentication).equals(category.getOwnerTenantId()))
            throw new NotFoundException("Tax category not found");
    }

    private TaxGroup group(UUID id) {
        return id == null ? null : taxGroupService.find(id);
    }

    private TaxCategory save(TaxCategory category) {
        try {
            return taxCategoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate();
        }
    }

    private Specification<TaxCategory> specification(TaxCategorySearchRequest request) {
        return Specification
                .where(TaxGeographySupport.<TaxCategory>equalReference("taxGroup", request.taxGroupId()))
                .and(TaxGeographySupport.equalString("code", TaxGeographySupport.normalizeCodeFilter(request.code())))
                .and(TaxGeographySupport.containsString("name", request.name()))
                .and(TaxGeographySupport.equalEnum("treatment", request.treatment()))
                .and(TaxGeographySupport.equalBoolean("active", request.active()));
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "TAX_CATEGORY", entityId, before, after);
    }

    private ConflictException duplicate() {
        return new ConflictException("Tax category code already exists");
    }
}
