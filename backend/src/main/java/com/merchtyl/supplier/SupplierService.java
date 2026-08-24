package com.merchtyl.supplier;

import com.merchtyl.audit.AuditAction;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class SupplierService {
    private static final int MAX_PAGE_SIZE = 100;

    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public SupplierService(SupplierRepository supplierRepository, UserRepository userRepository, AuditService auditService) {
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request, Authentication authentication) {
        SupplierValues values = values(request);
        if (supplierRepository.existsByCodeIgnoreCase(values.code())) {
            throw duplicateCode();
        }
        SupplierResponse response = SupplierResponse.from(save(new Supplier(values)));
        audit(authentication, AuditAction.SUPPLIER_CREATED, response.id(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> search(SupplierSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = supplierRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(SupplierResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        return SupplierResponse.from(find(id));
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierUpdateRequest request, Authentication authentication) {
        Supplier supplier = find(id);
        requireCurrentVersion(supplier, request.version());
        SupplierValues values = values(request);
        if (supplierRepository.existsByCodeIgnoreCaseAndIdNot(values.code(), id)) {
            throw duplicateCode();
        }
        SupplierResponse before = SupplierResponse.from(supplier);
        supplier.update(values);
        SupplierResponse after = SupplierResponse.from(save(supplier));
        audit(authentication, AuditAction.SUPPLIER_UPDATED, id, before, after);
        return after;
    }

    @Transactional
    public SupplierResponse updateStatus(UUID id, SupplierStatusRequest request, Authentication authentication) {
        Supplier supplier = find(id);
        requireCurrentVersion(supplier, request.version());
        SupplierResponse before = SupplierResponse.from(supplier);
        supplier.setActive(request.active());
        SupplierResponse after = SupplierResponse.from(save(supplier));
        audit(authentication, AuditAction.SUPPLIER_STATUS_CHANGED, id, before, after);
        return after;
    }

    private Supplier save(Supplier supplier) {
        try {
            return supplierRepository.saveAndFlush(supplier);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateCode();
        }
    }

    private Supplier find(UUID id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Supplier not found"));
    }

    private SupplierValues values(SupplierRequest request) {
        return new SupplierValues(
                normalizeCode(request.code()),
                cleanRequired(request.name(), "name"),
                optionalText(request.contactName()),
                optionalText(request.phone()),
                optionalEmail(request.email()),
                optionalText(request.address()),
                optionalText(request.notes()),
                request.active());
    }

    private SupplierValues values(SupplierUpdateRequest request) {
        return new SupplierValues(
                normalizeCode(request.code()),
                cleanRequired(request.name(), "name"),
                optionalText(request.contactName()),
                optionalText(request.phone()),
                optionalEmail(request.email()),
                optionalText(request.address()),
                optionalText(request.notes()),
                request.active());
    }

    private Specification<Supplier> specification(SupplierSearchRequest request) {
        return Specification
                .where(equalString("code", normalizeCodeFilter(request.code())))
                .and(containsString("name", request.name()))
                .and(containsString("contactName", request.contactName()))
                .and(containsString("email", request.email()))
                .and(equalBoolean("active", request.active()));
    }

    private void requireCurrentVersion(Supplier supplier, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != supplier.getVersion()) {
            throw new ConflictException("Supplier was modified by another transaction");
        }
    }

    private void audit(Authentication authentication, AuditAction action, UUID entityId, Object before, Object after) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                "SUPPLIER",
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

    private static Specification<Supplier> equalString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<Supplier> containsString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), pattern);
    }

    private static Specification<Supplier> equalBoolean(String field, Boolean value) {
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
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String cleanRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    private static String optionalEmail(String value) {
        String text = optionalText(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private static String optionalText(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private static ConflictException duplicateCode() {
        return new ConflictException("Supplier code already exists");
    }
}
