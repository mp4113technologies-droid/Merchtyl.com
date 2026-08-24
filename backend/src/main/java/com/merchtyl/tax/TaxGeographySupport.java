package com.merchtyl.tax;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;

import java.util.Locale;
import java.util.UUID;

final class TaxGeographySupport {
    static final int MAX_PAGE_SIZE = 100;

    private TaxGeographySupport() {
    }

    static void requireCurrentVersion(long currentVersion, Long requestedVersion, String label) {
        if (requestedVersion == null || requestedVersion != currentVersion) {
            throw new ConflictException(label + " was modified by another transaction");
        }
    }

    static String normalizeCountryCode(String code) {
        String cleaned = cleanRequired(code, "code").toUpperCase(Locale.ROOT);
        if (!cleaned.matches("^[A-Z]{2}$")) {
            throw new BadRequestException("country code must use two letters");
        }
        return cleaned;
    }

    static String normalizeCode(String code, int maxLength) {
        String cleaned = cleanRequired(code, "code").toUpperCase(Locale.ROOT);
        if (cleaned.length() > maxLength) {
            throw new BadRequestException("code must be " + maxLength + " characters or fewer");
        }
        if (!cleaned.matches("^[A-Z0-9][A-Z0-9_-]*$")) {
            throw new BadRequestException("code must use letters, numbers, underscores, and hyphens");
        }
        return cleaned;
    }

    static String normalizeCodeFilter(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    static String cleanRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    static UUID actorUserId(Authentication authentication, UserRepository userRepository) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }

    static void audit(
            Authentication authentication,
            UserRepository userRepository,
            AuditService auditService,
            AuditAction action,
            String entityType,
            UUID entityId,
            Object before,
            Object after) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication, userRepository),
                action,
                entityType,
                entityId,
                null,
                null,
                before,
                after,
                null));
    }

    static <T> Specification<T> containsString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), pattern);
    }

    static <T> Specification<T> equalString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    static <T> Specification<T> equalBoolean(String field, Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    static <T> Specification<T> equalInteger(String field, Integer value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    static <T, E extends Enum<E>> Specification<T> equalEnum(String field, E value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    static <T> Specification<T> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }
}
