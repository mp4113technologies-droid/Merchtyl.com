package com.merchtyl.tax;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.math.BigDecimal;

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
        TaxCategoryType type = request.categoryType() == null ? TaxCategoryType.STANDARD : request.categoryType();
        if (type == TaxCategoryType.CUSTOM_PERCENTAGE) {
            User actor = requireOwner(authentication);
            BigDecimal rate = requireRate(request.percentageRate());
            if (taxCategoryRepository.existsByTenantIdAndCodeIgnoreCase(actor.getTenantId(), code)
                    || taxCategoryRepository.existsByTenantIdIsNullAndCodeIgnoreCase(code)) throw duplicate();
            TaxCategoryResponse response = TaxCategoryResponse.from(save(TaxCategory.customPercentage(
                    actor.getTenantId(), code, TaxGeographySupport.cleanRequired(request.name(), "name"), rate,
                    TaxGeographySupport.optionalText(request.description()), request.active())));
            audit(authentication, AuditAction.TAX_CATEGORY_CREATED, response.id(), null, response);
            return response;
        }
        if (taxCategoryRepository.existsByCodeIgnoreCase(code)) {
            throw duplicate();
        }
        TaxCategoryResponse response = TaxCategoryResponse.from(save(new TaxCategory(
                group(request.taxGroupId()),
                code,
                TaxGeographySupport.cleanRequired(request.name(), "name"),
                request.treatment(),
                TaxGeographySupport.optionalText(request.description()),
                request.active())));
        audit(authentication, AuditAction.TAX_CATEGORY_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxCategoryResponse> search(TaxCategorySearchRequest request) {
        return search(request, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxCategoryResponse> search(TaxCategorySearchRequest request, Authentication authentication) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(TaxGeographySupport.MAX_PAGE_SIZE, request.size()));
        var page = taxCategoryRepository.findAll(
                specification(request).and(visibility(authentication)),
                PageRequest.of(pageNumber, pageSize, Sort.by("name").and(Sort.by("id"))));
        return new PageResponse<>(page.getContent().stream().map(TaxCategoryResponse::from).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public TaxCategoryResponse get(UUID id) {
        return get(id, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }

    @Transactional(readOnly = true)
    public TaxCategoryResponse get(UUID id, Authentication authentication) {
        TaxCategory category = find(id);
        requireVisible(category, authentication);
        return TaxCategoryResponse.from(category);
    }

    @Transactional
    public TaxCategoryResponse update(UUID id, TaxCategoryUpdateRequest request, Authentication authentication) {
        TaxCategory category = find(id);
        if (category.getCategoryType() == TaxCategoryType.CUSTOM_PERCENTAGE) {
            User actor = requireOwner(authentication);
            requireOwned(category, actor.getTenantId());
            TaxGeographySupport.requireCurrentVersion(category.getVersion(), request.version(), "Tax category");
            String code = TaxGeographySupport.normalizeCode(request.code(), 64);
            if (!category.getCode().equals(code)) throw new BadRequestException("CUSTOM_TAX_CATEGORY_CODE_IMMUTABLE");
            if (taxCategoryRepository.existsByTenantIdAndCodeIgnoreCaseAndIdNot(actor.getTenantId(), code, id)) throw duplicate();
            TaxCategoryResponse before = TaxCategoryResponse.from(category);
            category.updateCustom(TaxGeographySupport.cleanRequired(request.name(), "name"), requireRate(request.percentageRate()),
                    TaxGeographySupport.optionalText(request.description()), request.active());
            TaxCategoryResponse after = TaxCategoryResponse.from(save(category));
            audit(authentication, AuditAction.TAX_CATEGORY_UPDATED, id, before, after);
            return after;
        }
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
        if (category.getCategoryType() == TaxCategoryType.CUSTOM_PERCENTAGE) {
            User actor = requireOwner(authentication);
            requireOwned(category, actor.getTenantId());
        }
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

    private Specification<TaxCategory> visibility(Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        if (tenantId == null) return Specification.where(null);
        return (root, query, cb) -> cb.or(cb.isNull(root.get("tenantId")), cb.equal(root.get("tenantId"), tenantId));
    }

    private UUID tenantId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        return userRepository.findByEmailIgnoreCase(authentication.getName()).map(User::getTenantId).orElse(null);
    }

    private User requireOwner(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_TENANT_OWNER"))) {
            throw new ForbiddenOperationException("CUSTOM_TAX_CATEGORY_OWNER_REQUIRED");
        }
        User actor = userRepository.findByEmailIgnoreCase(authentication.getName())
                .filter(user -> user.getTenantId() != null)
                .orElseThrow(() -> new ForbiddenOperationException("CUSTOM_TAX_CATEGORY_OWNER_REQUIRED"));
        return actor;
    }

    private void requireVisible(TaxCategory category, Authentication authentication) {
        if (category.getTenantId() != null && !category.getTenantId().equals(tenantId(authentication))) {
            throw new NotFoundException("Tax category not found");
        }
    }

    private void requireOwned(TaxCategory category, UUID tenantId) {
        if (!tenantId.equals(category.getTenantId())) throw new NotFoundException("Tax category not found");
    }

    private BigDecimal requireRate(BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("CUSTOM_TAX_CATEGORY_RATE_INVALID");
        }
        return rate.setScale(4, java.math.RoundingMode.UNNECESSARY);
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        TaxGeographySupport.audit(authentication, userRepository, auditService, action, "TAX_CATEGORY", entityId, before, after);
    }

    private ConflictException duplicate() {
        return new ConflictException("Tax category code already exists");
    }
}
