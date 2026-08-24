package com.merchtyl.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditRecord;
import com.merchtyl.audit.AuditRecordRepository;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.GlobalExceptionHandler;
import com.merchtyl.platform.web.CorrelationIdFilter;
import com.merchtyl.platform.web.RequestLoggingFilter;
import com.merchtyl.security.AuthorizationService;
import com.merchtyl.security.PermissionCode;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class LoggingInfrastructureTest {
    @AfterEach
    void cleanMdc() {
        LoggingMdc.clearRequestContext();
    }

    @Test
    void createsCorrelationIdAndCleansMdc() throws Exception {
        MockMvc mockMvc = mockMvc(new MerchtylLoggingProperties());

        mockMvc.perform(get("/logging/ok"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, matchesPattern("[0-9a-fA-F-]{36}")))
                .andExpect(header().string(CorrelationIdFilter.REQUEST_ID_HEADER_NAME, matchesPattern("[0-9a-fA-F-]{36}")));

        assertThat(MDC.get(LoggingMdc.CORRELATION_ID)).isNull();
        assertThat(MDC.get(LoggingMdc.REQUEST_ID)).isNull();
        assertThat(MDC.get(LoggingMdc.REQUEST_URI)).isNull();
    }

    @Test
    void reusesInboundCorrelationId() throws Exception {
        MockMvc mockMvc = mockMvc(new MerchtylLoggingProperties());

        mockMvc.perform(get("/logging/ok").header(CorrelationIdFilter.HEADER_NAME, "checkout-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "checkout-123"));
    }

    @Test
    void logsRequestResponseMasksSensitiveDataAndMarksSlowRequests(CapturedOutput output) throws Exception {
        MerchtylLoggingProperties properties = new MerchtylLoggingProperties();
        properties.getPerformance().setSlowRequestThresholdMs(0);
        MockMvc mockMvc = mockMvc(properties);

        mockMvc.perform(get("/logging/slow")
                        .header("Authorization", "Bearer raw.jwt.token")
                        .header("X-Api-Key", "secret-api-key")
                        .queryParam("password", "open-sesame")
                        .queryParam("page", "1"))
                .andExpect(status().isOk());

        assertThat(output).contains("http_request_started");
        assertThat(output).contains("http_response_completed");
        assertThat(output).contains("SLOW REQUEST");
        assertThat(output).contains("password=********");
        assertThat(output).contains("Authorization=********");
        assertThat(output).contains("X-Api-Key=********");
        assertThat(output).doesNotContain("open-sesame");
        assertThat(output).doesNotContain("raw.jwt.token");
        assertThat(output).doesNotContain("secret-api-key");
    }

    @Test
    void logsExceptionWithCorrelationIdAndSanitizedResponse(CapturedOutput output) throws Exception {
        MockMvc mockMvc = mockMvc(new MerchtylLoggingProperties());

        mockMvc.perform(get("/logging/explode").header(CorrelationIdFilter.HEADER_NAME, "corr-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.correlationId").value("corr-exception"));

        assertThat(output).contains("Unhandled request failure");
        assertThat(output).contains("corr-exception");
        assertThat(output).contains("http_response_failure");
    }

    @Test
    void masksSensitiveFields() {
        assertThat(LogSanitizer.maskValue("password", "secret", true)).isEqualTo(LogSanitizer.MASK);
        assertThat(LogSanitizer.maskValue("refreshToken", "refresh", true)).isEqualTo(LogSanitizer.MASK);
        assertThat(LogSanitizer.maskValue("cardNumber", "4111111111111111", true)).isEqualTo(LogSanitizer.MASK);
        assertThat(LogSanitizer.maskQueryString("email=a@example.test&cvv=123", true))
                .isEqualTo("email=a@example.test&cvv=********");
        assertThat(LogSanitizer.maskSensitiveText("password=secret cardNumber=4111111111111111"))
                .doesNotContain("secret")
                .doesNotContain("4111111111111111");
    }

    @Test
    void logsBusinessEventsForAuthenticationMerchantInventoryAndPos(CapturedOutput output) {
        AuditRecordRepository repository = mock(AuditRecordRepository.class);
        when(repository.save(any(AuditRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuditService auditService = new AuditService(repository, new ObjectMapper());

        auditService.record(command(AuditAction.LOGIN_SUCCESS, "USER"));
        auditService.record(command(AuditAction.MERCHANT_TENANT_CREATED, "TENANT"));
        auditService.record(command(AuditAction.PRODUCT_CREATED, "PRODUCT"));
        auditService.record(command(AuditAction.SALE_DRAFT_CREATED, "SALE"));
        auditService.record(command(AuditAction.SALE_COMPLETED, "SALE"));

        assertThat(output).contains("business_event action=LOGIN_SUCCESS");
        assertThat(output).contains("business_event action=MERCHANT_TENANT_CREATED");
        assertThat(output).contains("business_event action=PRODUCT_CREATED");
        assertThat(output).contains("business_event action=SALE_DRAFT_CREATED");
        assertThat(output).contains("business_event action=SALE_COMPLETED");
    }

    @Test
    void logsAuthorizationFailures(CapturedOutput output) {
        Logger logger = (Logger) LoggerFactory.getLogger(AuthorizationLoggingAspect.class);
        Level previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        try {
            AuthorizationLoggingAspect aspect = new AuthorizationLoggingAspect();
            JoinPoint joinPoint = joinPoint(
                    "hasPermission",
                    new TestingAuthenticationToken("cashier@example.test", null, List.of()),
                    PermissionCode.SALE_CREATE);

            aspect.logPermissionFailure(joinPoint, false);

            assertThat(output).contains("authorization_failure");
            assertThat(output).contains("cashier@example.test");
            assertThat(output).contains("SALE_CREATE");
        } finally {
            logger.setLevel(previous);
        }
    }

    @Test
    void logsRepositoryFailures(CapturedOutput output) {
        RepositoryFailureLoggingAspect aspect = new RepositoryFailureLoggingAspect();

        aspect.logRepositoryFailure(
                joinPoint("save", new Object()),
                new DataIntegrityViolationException("duplicate cardNumber 4111111111111111"));

        assertThat(output).contains("repository_failure");
        assertThat(output).contains("constraint_violation");
        assertThat(output).doesNotContain("4111111111111111");
    }

    @Test
    void logsAccessDeniedThroughExceptionHandler(CapturedOutput output) throws Exception {
        MockMvc mockMvc = mockMvc(new MerchtylLoggingProperties());

        mockMvc.perform(get("/logging/denied").header(CorrelationIdFilter.HEADER_NAME, "corr-denied"))
                .andExpect(status().isForbidden());

        assertThat(output).contains("authorization_event event=Access Denied");
        assertThat(output).contains("corr-denied");
    }

    private static MockMvc mockMvc(MerchtylLoggingProperties properties) {
        return MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter(), new RequestLoggingFilter(properties))
                .build();
    }

    private static CreateAuditRecordCommand command(AuditAction action, String entityType) {
        return new CreateAuditRecordCommand(
                UUID.randomUUID(),
                action,
                entityType,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "test event");
    }

    private static JoinPoint joinPoint(String methodName, Object... args) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getDeclaringType()).thenReturn((Class) AuthorizationService.class);
        when(signature.getDeclaringTypeName()).thenReturn(AuthorizationService.class.getName());
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    @RestController
    @RequestMapping("/logging")
    static class TestController {
        @GetMapping(value = "/ok", produces = MediaType.TEXT_PLAIN_VALUE)
        String ok() {
            return "ok";
        }

        @GetMapping(value = "/slow", produces = MediaType.TEXT_PLAIN_VALUE)
        String slow() throws InterruptedException {
            Thread.sleep(5);
            return "slow";
        }

        @GetMapping("/explode")
        void explode() {
            throw new IllegalStateException("raw server detail");
        }

        @GetMapping("/denied")
        void denied() {
            throw new AccessDeniedException("missing permission");
        }
    }
}
