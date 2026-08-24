package com.merchtyl.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.platform.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTest {
    private final AuditRecordRepository auditRecordRepository = mock(AuditRecordRepository.class);
    private final AuditService auditService = new AuditService(auditRecordRepository, new ObjectMapper());

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void recordSerializesSnapshotsAndCapturesCorrelationId() {
        UUID actorUserId = UUID.fromString("00000000-0000-0000-0000-000000000901");
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-audit-123");
        when(auditRecordRepository.save(any(AuditRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditService.record(new CreateAuditRecordCommand(
                actorUserId,
                AuditAction.USER_ACTIVATED,
                "user",
                actorUserId,
                null,
                null,
                Map.of("enabled", false),
                Map.of("enabled", true),
                "rehired"));

        ArgumentCaptor<AuditRecord> record = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecordRepository).save(record.capture());
        assertThat(record.getValue().getActorUserId()).isEqualTo(actorUserId);
        assertThat(record.getValue().getAction()).isEqualTo(AuditAction.USER_ACTIVATED.name());
        assertThat(record.getValue().getEntityType()).isEqualTo("USER");
        assertThat(record.getValue().getBeforeSnapshot()).isEqualTo("{\"enabled\":false}");
        assertThat(record.getValue().getAfterSnapshot()).isEqualTo("{\"enabled\":true}");
        assertThat(record.getValue().getCorrelationId()).isEqualTo("corr-audit-123");
    }

    @Test
    void searchBoundsPageSizeAndMapsResults() {
        AuditRecord record = new AuditRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000902"),
                AuditAction.LOGIN_SUCCESS.name(),
                "USER",
                UUID.fromString("00000000-0000-0000-0000-000000000903"),
                null,
                null,
                null,
                "{\"status\":\"success\"}",
                null,
                "corr-audit-456");
        record.prePersist();
        when(auditRecordRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<AuditRecord>>any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record)));

        var response = auditService.search(new AuditSearchRequest(
                "login_success",
                "user",
                record.getEntityId(),
                record.getActorUserId(),
                null,
                null,
                Instant.parse("2026-07-21T00:00:00Z"),
                Instant.parse("2026-07-22T00:00:00Z"),
                -3,
                500));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRecordRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<AuditRecord>>any(),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().action()).isEqualTo(AuditAction.LOGIN_SUCCESS.name());
    }
}
