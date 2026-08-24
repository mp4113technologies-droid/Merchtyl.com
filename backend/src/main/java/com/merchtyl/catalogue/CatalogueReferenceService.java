package com.merchtyl.catalogue;

import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
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

    protected CatalogueReferenceService(
            JpaRepository<T, UUID> repository,
            JpaSpecificationExecutor<T> specificationExecutor,
            CatalogueReferenceRepository<T> referenceRepository,
            UserRepository userRepository,
            AuditService auditService,
            CatalogueReferenceFactory<T> factory,
            CatalogueReferenceAuditActions auditActions,
            String entityType,
            String entityLabel) {
        this.repository = repository;
        this.specificationExecutor = specificationExecutor;
        this.referenceRepository = referenceRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.factory = factory;
        this.auditActions = auditActions;
        this.entityType = entityType;
        this.entityLabel = entityLabel;
    }

    @Transactional
    public CatalogueReferenceResponse create(CatalogueReferenceRequest request, Authentication authentication) {
        CatalogueReferenceValues values = values(request);
        if (referenceRepository.existsByCodeIgnoreCase(values.code())) {
            throw duplicateCode();
        }
        CatalogueReferenceResponse response = CatalogueReferenceResponse.from(save(factory.create(values)));
        audit(authentication, auditActions.created(), response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogueReferenceResponse> search(CatalogueReferenceSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = specificationExecutor.findAll(
                specification(request),
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
    public CatalogueReferenceResponse get(UUID id) {
        return CatalogueReferenceResponse.from(find(id));
    }

    @Transactional
    public CatalogueReferenceResponse update(UUID id, CatalogueReferenceUpdateRequest request, Authentication authentication) {
        T reference = find(id);
        requireCurrentVersion(reference, request.version());
        CatalogueReferenceValues values = values(request);
        if (referenceRepository.existsByCodeIgnoreCaseAndIdNot(values.code(), id)) {
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
        T reference = find(id);
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

    private T find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException(entityLabel + " not found"));
    }

    private CatalogueReferenceValues values(CatalogueReferenceRequest request) {
        return new CatalogueReferenceValues(
                normalizeCode(request.code()),
                cleanRequired(request.name(), "name"),
                optionalText(request.description()),
                request.active());
    }

    private CatalogueReferenceValues values(CatalogueReferenceUpdateRequest request) {
        return new CatalogueReferenceValues(
                normalizeCode(request.code()),
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
