package com.merchtyl.eod;

import com.merchtyl.common.ApiError;
import com.merchtyl.platform.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
class ClosingValidationExceptionHandler {
    @ExceptionHandler(ClosingValidationException.class)
    ResponseEntity<ApiError> closingValidation(ClosingValidationException exception, HttpServletRequest request) {
        boolean hasOpenRegisters = exception.getBlockers().stream()
                .anyMatch(blocker -> "OPEN_REGISTER_SESSION".equals(blocker.code()));
        String code = hasOpenRegisters
                ? "BUSINESS_DAY_HAS_OPEN_REGISTER_SESSIONS"
                : "BUSINESS_DAY_RECONCILIATION_INCOMPLETE";
        List<ApiError.FieldViolation> violations = exception.getBlockers().stream()
                .map(blocker -> new ApiError.FieldViolation("closing", blocker.code(), blocker.message()))
                .toList();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                code,
                exception.getMessage(),
                HttpStatus.CONFLICT.value(),
                request.getRequestURI(),
                request.getMethod(),
                correlationId,
                violations,
                Instant.now()));
    }
}
