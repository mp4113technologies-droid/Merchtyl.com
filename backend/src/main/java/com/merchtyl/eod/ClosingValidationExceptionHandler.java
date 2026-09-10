package com.merchtyl.eod;

import com.merchtyl.common.ApiError;
import com.merchtyl.platform.web.CorrelationIdFilter;
import com.merchtyl.platform.web.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ClosingValidationExceptionHandler {
    @ExceptionHandler(ClosingValidationException.class)
    ResponseEntity<ApiError> closingValidation(ClosingValidationException exception, HttpServletRequest request) {
        String code = closingErrorCode(exception);
        List<ApiError.FieldViolation> violations = exception.getBlockers().stream()
                .map(blocker -> new ApiError.FieldViolation("closing", blocker.code(), blocker.message()))
                .toList();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        }
        request.setAttribute(RequestLoggingFilter.ERROR_CODE_ATTRIBUTE, code);
        request.setAttribute(RequestLoggingFilter.EXCEPTION_TYPE_ATTRIBUTE, code);
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

    private static String closingErrorCode(ClosingValidationException exception) {
        if (hasBlocker(exception, "OPEN_REGISTER_SESSION")) {
            return "BUSINESS_DAY_HAS_OPEN_REGISTER_SESSIONS";
        }
        if (hasBlocker(exception, "MISSING_COUNTED_CASH", "MISSING_RECONCILIATION", "REGISTER_RECONCILIATION_INCOMPLETE")) {
            return "BUSINESS_DAY_HAS_UNRECONCILED_REGISTER_SESSIONS";
        }
        if (hasBlocker(exception, "UNFINALIZED_DRAFT_SALE", "UNFINALIZED_PAID_DRAFT_SALE", "UNFINALIZED_HELD_SALE")) {
            return "BUSINESS_DAY_HAS_UNFINALIZED_SALES";
        }
        return "BUSINESS_DAY_CLOSING_BLOCKED";
    }

    private static boolean hasBlocker(ClosingValidationException exception, String... codes) {
        return exception.getBlockers().stream().anyMatch(blocker ->
                java.util.Arrays.stream(codes).anyMatch(code -> code.equals(blocker.code())));
    }
}
