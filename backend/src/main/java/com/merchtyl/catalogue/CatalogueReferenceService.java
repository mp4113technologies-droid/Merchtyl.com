package com.merchtyl.catalogue;

import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

public abstract class CatalogueReferenceService<T extends CatalogueReference> {
    private static final int MAX_PAGE_SIZE = 100;

    private final JpaRepository<T, UUID> repository;
    private final JpaSpecificationExecutor<T> specificationExecutor;
    private final CatalogueReferenceRepository<T> referenceRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final CatalogueReferenceFactory<T> factory;
    private final CatalogueReferenceAuditActions auditActions;
    private final String entityType;
    private final String entityLabel;
    private final TenantCatalogueReferenceRepository<T> tenantRepository;
    private final StoreAccessService storeAccessService;
    private final MerchantIdentifierGenerator identifierGenerator;

    protected CatalogueReferenceService(
            JpaRepository<T, UUID> repository,
            JpaSpecificationExecutor<T> specificationExecutor,
            CatalogueReferenceRepository<T> referenceRepository,
            UserRepository userRepository,
            AuditService auditService,
            CatalogueReferenceFactory<T> factory,
            CatalogueReferenceAuditActions auditActions,
            String entityType,
            String entityLabel,
            TenantCatalogueReferenceRepository<T> tenantRepository,
            StoreAccessService storeAccessService,
            MerchantIdentifierGenerator identifierGenerator) {
        this.repository = repository;
        this.specificationExecutor = specificationExecutor;
        this.referenceRepository = referenceRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.factory = factory;
        this.auditActions = auditActions;
        this.entityType = entityType;
        this.entityLabel = entityLabel;
        this.tenantRepository = tenantRepository;
        this.storeAccessService = storeAccessService;
        this.identifierGenerator = identifierGenerator;
    }

    @Transactional
    public CatalogueReferenceResponse create(CatalogueReferenceRequest request, Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        String code = tenantRepository == null ? normalizeCode(request.code())
                : identifierGenerator.nextCatalogueCode(tenantId, entityType);
        CatalogueReferenceValues values = values(request, code);
        if (codeExists(tenantId, values.code())) {
            throw duplicateCode();
        }
        T created = factory.create(values);
        if (created instanceof TenantCatalogueReference tenantReference) tenantReference.assignTenant(tenantId);
        CatalogueReferenceResponse response = CatalogueReferenceResponse.from(save(created));
        audit(authentication, auditActions.created(), response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogueReferenceResponse> search(CatalogueReferenceSearchRequest request, Authentication authentication) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = specificationExecutor.findAll(
                specification(request).and(tenantSpecification(tenantId(authentication))),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(CatalogueReferenceResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public CatalogueReferenceResponse get(UUID id, Authentication authentication) {
        return CatalogueReferenceResponse.from(find(id, tenantId(authentication)));
    }

    @Transactional
    public CatalogueReferenceResponse update(UUID id, CatalogueReferenceUpdateRequest request, Authentication authentication) {
        UUID tenantId = tenantId(authentication);
        T reference = find(id, tenantId);
        requireCurrentVersion(reference, request.version());
        String code = tenantRepository == null ? normalizeCode(request.code()) : reference.getCode();
        CatalogueReferenceValues values = values(request, code);
        if (codeExistsExcluding(tenantId, values.code(), id)) {
            throw duplicateCode();
        }
        CatalogueReferenceResponse before = CatalogueReferenceResponse.from(reference);
        reference.update(values);
        CatalogueReferenceResponse after = CatalogueReferenceResponse.from(save(reference));
        audit(authentication, auditActions.updated(), id, before, after);
        return after;
    }

    @Transactional
    public CatalogueReferenceResponse updateStatus(UUID id, CatalogueReferenceStatusRequest request, Authentication authentication) {
        T reference = find(id, tenantId(authentication));
        requireCurrentVersion(reference, request.version());
        CatalogueReferenceResponse before = CatalogueReferenceResponse.from(reference);
        reference.setActive(request.active());
        CatalogueReferenceResponse after = CatalogueReferenceResponse.from(save(reference));
        audit(authentication, auditActions.statusChanged(), id, before, after);
        return after;
    }

    private T save(T reference) {
        try {
            return repository.saveAndFlush(reference);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateCode();
        }
    }

    private T find(UUID id, UUID tenantId) {
        return repository.findById(id)
                .filter(reference -> tenantRepository == null
                        || reference instanceof TenantCatalogueReference tenantReference
                        && tenantId.equals(tenantReference.getTenantId()))
                .orElseThrow(() -> new NotFoundException(entityLabel + " not found"));
    }

    private CatalogueReferenceValues values(CatalogueReferenceRequest request, String code) {
        return new CatalogueReferenceValues(
                code,
                cleanRequired(request.name(), "name"),
                optionalText(request.description()),
                request.active());
    }

    private CatalogueReferenceValues values(CatalogueReferenceUpdateRequest request, String code) {
        return new CatalogueReferenceValues(
                code,
                cleanRequired(request.name(), "name"),
                optionalText(request.description()),
                request.active());
    }

    private Specification<T> specification(CatalogueReferenceSearchRequest request) {
        return Specification
                .where(CatalogueReferenceService.<T>equalString("code", normalizeCodeFilter(request.code())))
                .and(containsString("name", request.name()))
                .and(equalBoolean("active", request.active()));
    }

    private Specification<T> tenantSpecification(UUID tenantId) {
        if (tenantRepository == null) return null;
        return (root, query, builder) -> builder.equal(root.get("tenantId"), tenantId);
    }

    private UUID tenantId(Authentication authentication) {
        return tenantRepository == null ? null : storeAccessService.currentTenantId(authentication);
    }

    private boolean codeExists(UUID tenantId, String code) {
        return tenantRepository == null ? referenceRepository.existsByCodeIgnoreCase(code)
                : tenantRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, code);
    }

    private boolean codeExistsExcluding(UUID tenantId, String code, UUID id) {
        return tenantRepository == null ? referenceRepository.existsByCodeIgnoreCaseAndIdNot(code, id)
                : tenantRepository.existsByTenantIdAndCodeIgnoreCaseAndIdNot(tenantId, code, id);
    }

    private void requireCurrentVersion(CatalogueReference reference, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != reference.getVersion()) {
            throw new ConflictException(entityLabel + " was modified by another transaction");
        }
    }

    private void audit(Authentication authentication, com.merchtyl.audit.AuditAction action, UUID entityId, Object before, Object after) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                entityType,
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

    private ConflictException duplicateCode() {
        return new ConflictException(entityLabel + " code already exists");
    }

    private static <T extends CatalogueReference> Specification<T> equalString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static <T extends CatalogueReference> Specification<T> containsString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), pattern);
    }

    private static <T extends CatalogueReference> Specification<T> equalBoolean(String field, Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static String normalizeCode(String code) {
        String cleaned = cleanRequired(code, "code").toUpperCase(Locale.ROOT);
        if (!cleaned.matches("^[A-Z0-9][A-Z0-9_-]*$")) {
            throw new BadRequestException("code must use letters, numbers, underscores, and hyphens");
        }
        return cleaned;
    }

    private static String normalizeCodeFilter(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
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
}
