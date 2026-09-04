package com.merchtyl.common;

import com.merchtyl.platform.web.CorrelationIdFilter;
import com.merchtyl.platform.web.RequestLoggingFilter;
import com.merchtyl.logging.LogSanitizer;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import com.merchtyl.auth.AccountLockedException;
import com.merchtyl.auth.ResetTokenException;
import com.merchtyl.auth.PasswordPolicyException;
import com.merchtyl.auth.PasswordConfirmationException;
import com.merchtyl.auth.PasswordResetRestrictionException;
import com.merchtyl.registersession.RegisterDeviceRequiredException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final DatabaseConstraintErrorMapper databaseErrors = new DatabaseConstraintErrorMapper();

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiError> badRequest(BadRequestException exception, HttpServletRequest request) {
        DomainMessage domain = domainMessage(exception.getMessage());
        if (domain != null) return error(HttpStatus.BAD_REQUEST, domain.code(), domain.message(), request, domainViolations(domain));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please review the information and try again.", request, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException exception, HttpServletRequest request) {
        DomainMessage domain = domainMessage(exception.getMessage());
        if (domain != null) return error(HttpStatus.CONFLICT, domain.code(), domain.message(), request, domainViolations(domain));
        return error(HttpStatus.CONFLICT, "REQUEST_CONFLICT", DomainErrorCatalog.message("REQUEST_CONFLICT", exception.getMessage()), request, List.of());
    }

    private static DomainMessage domainMessage(String value) {
        if (value == null) return null;
        DomainErrorCatalog.Entry entry = DomainErrorCatalog.resolve(value);
        return entry == null ? null : new DomainMessage(entry.code(), entry.message());
    }

    private record DomainMessage(String code, String message) {}

    private static List<ApiError.FieldViolation> domainViolations(DomainMessage domain) {
        String field = switch (domain.code()) {
            case "TENANT_CODE_ALREADY_EXISTS" -> "tenantCode";
            case "MERCHANT_SLUG_ALREADY_EXISTS", "MERCHANT_SLUG_RESERVED", "MERCHANT_SLUG_INVALID" -> "merchantSlug";
            case "OWNER_EMAIL_ALREADY_EXISTS" -> "ownerEmail";
            case "BUSINESS_NUMBER_ALREADY_EXISTS" -> "businessNumber";
            case "PRICING_PLAN_NOT_FOUND", "PRICING_PLAN_NOT_ACTIVE", "PRICING_PLAN_NO_EFFECTIVE_VERSION" -> "pricingPlanId";
            case "PRICING_PLAN_CODE_ALREADY_EXISTS" -> "code";
            case "BARCODE_ALREADY_EXISTS" -> "barcode";
            case "SKU_ALREADY_EXISTS" -> "sku";
            case "REGISTER_CODE_ALREADY_EXISTS", "STORE_CODE_ALREADY_EXISTS" -> "code";
            default -> null;
        };
        return field == null ? List.of() : List.of(new ApiError.FieldViolation(field, domain.code(), domain.message()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException exception, HttpServletRequest request) {
        DomainMessage domain = domainMessage(exception.getMessage());
        return domain == null
                ? error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "We couldn't find the requested information.", request, List.of())
                : error(HttpStatus.NOT_FOUND, domain.code(), domain.message(), request, domainViolations(domain));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ApiError> tooManyRequests(TooManyRequestsException exception, HttpServletRequest request) {
        ResponseEntity<ApiError> response = error(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests", exception.getMessage(), request, List.of());
        if (exception.retryAfter() == null) {
            return response;
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, exception.retryAfter().toSeconds())))
                .body(response.getBody());
    }

    @ExceptionHandler({ForbiddenOperationException.class, AccessDeniedException.class})
    ResponseEntity<ApiError> forbidden(RuntimeException exception, HttpServletRequest request) {
        log.warn("authorization_event event={} user={} tenant={} endpoint={} method={} exception_type={}",
                exception.getMessage() != null && exception.getMessage().toLowerCase().contains("tenant")
                        ? "Cross Tenant Access Attempt"
                        : "Access Denied",
                user(),
                MDC.get("tenantId"),
                request.getRequestURI(),
                request.getMethod(),
                exception.getClass().getName());
        DomainMessage domain = domainMessage(exception.getMessage());
        return domain == null
                ? error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", DomainErrorCatalog.message("ACCESS_DENIED", exception.getMessage()), request, List.of())
                : error(HttpStatus.FORBIDDEN, domain.code(), domain.message(), request, domainViolations(domain));
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> badCredentials(HttpServletRequest request) {
        log.warn("authentication_event event=Failed Login endpoint={} method={} reason=bad_credentials",
                request.getRequestURI(),
                request.getMethod());
        return error(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", DomainErrorCatalog.message("LOGIN_FAILED", null), request, List.of());
    }

    @ExceptionHandler(AccountLockedException.class)
    ResponseEntity<ApiError> accountLocked(AccountLockedException exception, HttpServletRequest request) {
        return error(HttpStatus.LOCKED, "ACCOUNT_LOCKED", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(ResetTokenException.class)
    ResponseEntity<ApiError> resetToken(ResetTokenException exception, HttpServletRequest request) {
        HttpStatus status = "EXPIRED_RESET_TOKEN".equals(exception.code()) ? HttpStatus.GONE
                : List.of("RESET_TOKEN_ALREADY_USED", "RESET_TOKEN_REVOKED").contains(exception.code()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return error(status, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(PasswordPolicyException.class)
    ResponseEntity<ApiError> passwordPolicy(PasswordPolicyException exception, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = exception.violations().stream()
                .map(item -> new ApiError.FieldViolation("newPassword", item.code(), item.message())).toList();
        return error(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY_VIOLATION", exception.getMessage(), request, violations);
    }

    @ExceptionHandler(PasswordConfirmationException.class)
    ResponseEntity<ApiError> passwordConfirmation(PasswordConfirmationException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "PASSWORD_CONFIRMATION_MISMATCH", exception.getMessage(), request,
                List.of(new ApiError.FieldViolation("confirmPassword", "PASSWORD_CONFIRMATION_MISMATCH", exception.getMessage())));
    }

    @ExceptionHandler(PasswordResetRestrictionException.class)
    ResponseEntity<ApiError> passwordResetRestriction(PasswordResetRestrictionException exception, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(RegisterDeviceRequiredException.class)
    ResponseEntity<ApiError> registerDeviceRequired(RegisterDeviceRequiredException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "REGISTER_DEVICE_REQUIRED", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), "VALIDATION_ERROR", error.getDefaultMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", DomainErrorCatalog.message("VALIDATION_FAILED", null), request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraintValidation(ConstraintViolationException exception, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldViolation(
                        violation.getPropertyPath().toString(), "VALIDATION_ERROR",
                        violation.getMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", DomainErrorCatalog.message("VALIDATION_FAILED", null), request, violations);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ApiError> databaseConflict(RuntimeException exception, HttpServletRequest request) {
        if (exception instanceof ObjectOptimisticLockingFailureException) {
            log.warn("event=DATABASE_WRITE_FAILED category=optimistic_locking_failure exception_type={} method={} path={} correlation_id={}",
                    exception.getClass().getName(), request.getMethod(), request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY));
            return error(HttpStatus.CONFLICT, "RECORD_UPDATED_BY_ANOTHER_USER", DomainErrorCatalog.message("RECORD_UPDATED_BY_ANOTHER_USER", null), request, List.of());
        }
        DatabaseConstraintErrorMapper.Analysis analysis = databaseErrors.analyze(exception, request.getRequestURI());
        DatabaseConstraintErrorMapper.DomainError domain = analysis.domainError();
        String logMessage = "event=DATABASE_WRITE_FAILED operation={} domain={} exception_type={} database_exception_type={} sql_state={} constraint_name={} technical_detail={} method={} path={} correlation_id={} tenant={} store={} user={} stack_trace={}";
        Object[] values = {request.getMethod() + " " + request.getRequestURI(), domain.code(), exception.getClass().getName(), analysis.databaseExceptionClass(), analysis.sqlState(), analysis.constraintName(),
                LogSanitizer.clean(analysis.technicalDetail()), request.getMethod(), request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY), MDC.get("tenantId"), MDC.get("storeId"), user(),
                LogSanitizer.sanitizedStackTrace(exception)};
        if (domain.expected()) log.warn(logMessage, values); else log.error(logMessage, values);
        return error(domain.status(), domain.code(), domain.message(), request, domain.violations());
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> databaseFailure(DataAccessException exception, HttpServletRequest request) {
        DatabaseConstraintErrorMapper.Analysis analysis = databaseErrors.analyze(exception, request.getRequestURI());
        log.error("database_failure category=database_failure exception_type={} database_exception_type={} sql_state={} constraint_name={} technical_detail={} method={} path={} correlation_id={} tenant={} store={} user={} stack_trace={}",
                exception.getClass().getName(),
                analysis.databaseExceptionClass(),
                analysis.sqlState(),
                analysis.constraintName(),
                LogSanitizer.clean(analysis.technicalDetail()),
                request.getMethod(),
                request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                MDC.get("tenantId"),
                MDC.get("storeId"),
                user(),
                LogSanitizer.sanitizedStackTrace(exception));
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR", DomainErrorCatalog.message("UNEXPECTED_ERROR", null), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure method={} path={} correlation_id={} tenant={} store={} user={} exception_type={} stack_trace={}",
                request.getMethod(),
                request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                MDC.get("tenantId"),
                MDC.get("storeId"),
                user(),
                exception.getClass().getName(),
                LogSanitizer.sanitizedStackTrace(exception));
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "UNEXPECTED_ERROR",
                DomainErrorCatalog.message("UNEXPECTED_ERROR", null),
                request,
                List.of());
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ApiError.FieldViolation> violations) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        }
        request.setAttribute(RequestLoggingFilter.ERROR_CODE_ATTRIBUTE, code);
        request.setAttribute(RequestLoggingFilter.EXCEPTION_TYPE_ATTRIBUTE, exceptionType(status, code));
        ApiError body = new ApiError(
                code,
                message,
                status.value(),
                request.getRequestURI(),
                request.getMethod(),
                correlationId,
                violations,
                Instant.now());
        return ResponseEntity.status(status).body(body);
    }

    private static String user() {
        String username = MDC.get("username");
        return username == null ? "" : username;
    }

    private static String exceptionType(HttpStatus status, String code) {
        if (status.is5xxServerError()) {
            return "server_error";
        }
        return code;
    }
}
