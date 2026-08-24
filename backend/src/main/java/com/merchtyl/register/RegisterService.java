package com.merchtyl.register;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
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
public class RegisterService {
    private static final int MAX_PAGE_SIZE = 100;

    private final RegisterRepository registerRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public RegisterService(
            RegisterRepository registerRepository,
            StoreRepository storeRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.registerRepository = registerRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public RegisterResponse create(RegisterRequest request, Authentication authentication) {
        RegisterValues values = values(request);
        UUID storeId = values.store().getId();
        if (registerRepository.existsByStore_IdAndCodeIgnoreCase(storeId, values.code())) {
            throw duplicateCode();
        }

        Register register = new Register(
                values.store(),
                values.code(),
                values.name(),
                values.locationDescription(),
                values.active());
        RegisterResponse response = RegisterResponse.from(save(register));
        audit(authentication, AuditAction.REGISTER_CREATED, response.storeId(), response.id(), null, response, null);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<RegisterResponse> search(RegisterSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var pageable = PageRequest.of(pageNumber, pageSize,
                Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id")));
        var page = registerRepository.findAll(specification(request), pageable);
        return new PageResponse<>(
                page.getContent().stream().map(RegisterResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public RegisterResponse get(UUID id) {
        return RegisterResponse.from(find(id));
    }

    @Transactional
    public RegisterResponse update(UUID id, RegisterUpdateRequest request, Authentication authentication) {
        Register register = find(id);
        requireCurrentVersion(register, request.version());
        RegisterValues values = values(request);
        UUID storeId = values.store().getId();
        if (registerRepository.existsByStore_IdAndCodeIgnoreCaseAndIdNot(storeId, values.code(), id)) {
            throw duplicateCode();
        }

        RegisterResponse before = RegisterResponse.from(register);
        register.update(values);
        RegisterResponse after = RegisterResponse.from(save(register));
        audit(authentication, AuditAction.REGISTER_UPDATED, after.storeId(), id, before, after, null);
        return after;
    }

    @Transactional
    public RegisterResponse updateStatus(UUID id, RegisterStatusRequest request, Authentication authentication) {
        Register register = find(id);
        requireCurrentVersion(register, request.version());

        RegisterResponse before = RegisterResponse.from(register);
        register.setActive(request.active());
        RegisterResponse after = RegisterResponse.from(save(register));
        audit(authentication, AuditAction.REGISTER_STATUS_CHANGED, after.storeId(), id, before, after, null);
        return after;
    }

    private Register save(Register register) {
        try {
            return registerRepository.saveAndFlush(register);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateCode();
        }
    }

    private Register find(UUID id) {
        return registerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Register not found"));
    }

    private RegisterValues values(RegisterRequest request) {
        return new RegisterValues(
                findStore(request.storeId()),
                normalizeCode(request.code()),
                cleanRequired(request.name(), "name"),
                cleanOptional(request.locationDescription()),
                request.active());
    }

    private RegisterValues values(RegisterUpdateRequest request) {
        return new RegisterValues(
                findStore(request.storeId()),
                normalizeCode(request.code()),
                cleanRequired(request.name(), "name"),
                cleanOptional(request.locationDescription()),
                request.active());
    }

    private Store findStore(UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private Specification<Register> specification(RegisterSearchRequest request) {
        return Specification
                .where(equalStore(request.storeId()))
                .and(equalString("code", normalizeCodeFilter(request.code())))
                .and(containsString("name", request.name()))
                .and(equalBoolean("active", request.active()));
    }

    private void requireCurrentVersion(Register register, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != register.getVersion()) {
            throw new ConflictException("Register was modified by another transaction");
        }
    }

    private void audit(
            Authentication authentication,
            AuditAction action,
            UUID storeId,
            UUID registerId,
            RegisterResponse beforeSnapshot,
            RegisterResponse afterSnapshot,
            String reason) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                "REGISTER",
                registerId,
                storeId,
                registerId,
                beforeSnapshot,
                afterSnapshot,
                reason));
    }

    private UUID actorUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }

    private static Specification<Register> equalStore(UUID storeId) {
        if (storeId == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("store").get("id"), storeId);
    }

    private static Specification<Register> equalString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<Register> containsString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(
                criteriaBuilder.lower(root.get(field)),
                pattern);
    }

    private static Specification<Register> equalBoolean(String field, Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static String normalizeCode(String value) {
        String code = cleanRequired(value, "code").toUpperCase(Locale.ROOT);
        if (!code.matches("^[A-Z0-9][A-Z0-9_-]*$")) {
            throw new BadRequestException("code may contain only letters, numbers, underscores, and hyphens");
        }
        return code;
    }

    private static String normalizeCodeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String cleanRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ConflictException duplicateCode() {
        return new ConflictException("Register code already exists for this store");
    }
}
