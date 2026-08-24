package com.merchtyl.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.logging.LogSanitizer;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.platform.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditRecordRepository auditRecordRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRecordRepository auditRecordRepository, ObjectMapper objectMapper) {
        this.auditRecordRepository = auditRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditRecord record(CreateAuditRecordCommand command) {
        AuditRecord record = new AuditRecord(
                command.actorUserId(),
                command.action().name(),
                cleanRequired(command.entityType(), "entityType").toUpperCase(Locale.ROOT),
                command.entityId(),
                command.storeId(),
                command.registerId(),
                snapshot(command.beforeSnapshot()),
                snapshot(command.afterSnapshot()),
                cleanOptional(command.reason()),
                correlationId());
        AuditRecord saved = auditRecordRepository.save(record);
        logBusinessEvent(command);
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditRecordResponse> search(AuditSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var pageable = PageRequest.of(pageNumber, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        var page = auditRecordRepository.findAll(specification(request), pageable);
        return new PageResponse<>(
                page.getContent().stream().map(AuditRecordResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public AuditRecordResponse get(UUID id) {
        return auditRecordRepository.findById(id)
                .map(AuditRecordResponse::from)
                .orElseThrow(() -> new NotFoundException("Audit record not found"));
    }

    private Specification<AuditRecord> specification(AuditSearchRequest request) {
        return Specification
                .where(equalString("action", request.action()))
                .and(equalString("entityType", request.entityType()))
                .and(equalUuid("entityId", request.entityId()))
                .and(equalUuid("actorUserId", request.actorUserId()))
                .and(equalUuid("storeId", request.storeId()))
                .and(equalUuid("registerId", request.registerId()))
                .and(createdAtGreaterThanOrEqualTo(request.createdFrom()))
                .and(createdAtLessThanOrEqualTo(request.createdTo()));
    }

    private static Specification<AuditRecord> equalString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), normalized);
    }

    private static Specification<AuditRecord> equalUuid(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<AuditRecord> createdAtGreaterThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), value);
    }

    private static Specification<AuditRecord> createdAtLessThanOrEqualTo(Instant value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), value);
    }

    private String snapshot(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit snapshot must be JSON serializable", exception);
        }
    }

    private static String cleanRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String correlationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return correlationId == null || correlationId.isBlank() ? null : correlationId;
    }

    private static void logBusinessEvent(CreateAuditRecordCommand command) {
        log.info("business_event action={} entity_type={} entity_id={} actor_user_id={} store_id={} register_id={} reason={}",
                command.action(),
                LogSanitizer.clean(command.entityType()),
                command.entityId(),
                command.actorUserId(),
                command.storeId(),
                command.registerId(),
                LogSanitizer.maskValue("reason", command.reason(), true));
    }
}
