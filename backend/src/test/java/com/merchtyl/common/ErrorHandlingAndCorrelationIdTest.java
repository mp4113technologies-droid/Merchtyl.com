package com.merchtyl.common;

import com.merchtyl.platform.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorHandlingAndCorrelationIdTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void generatedCorrelationIdIsReturnedInHeaderAndErrorBody() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                        CorrelationIdFilter.HEADER_NAME,
                        matchesPattern("[0-9a-fA-F-]{36}")))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/test/not-found"))
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.correlationId").value(matchesPattern("[0-9a-fA-F-]{36}")));
    }

    @Test
    void inboundCorrelationIdIsPreserved() throws Exception {
        mockMvc.perform(get("/test/conflict")
                        .header(CorrelationIdFilter.HEADER_NAME, "checkout-123"))
                .andExpect(status().isConflict())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "checkout-123"))
                .andExpect(jsonPath("$.correlationId").value("checkout-123"))
                .andExpect(jsonPath("$.code").value("REQUEST_CONFLICT"));
    }

    @Test
    void validationErrorsAreStructured() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("name"))
                .andExpect(jsonPath("$.violations[0].message").isNotEmpty());
    }

    @Test
    void forbiddenOperationUsesForbiddenStatus() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("You don't have permission to perform this action."))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void codeOnlyDomainConflictKeepsStableCodeAndFriendlyMessage() throws Exception {
        mockMvc.perform(get("/test/previous-day-open"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIOUS_BUSINESS_DAY_STILL_OPEN"))
                .andExpect(jsonPath("$.message").value("The previous business day is still open. Close it before opening today's business day."));
    }

    @Test
    void knownDatabaseConstraintReturnsSafeDomainErrorAndFieldViolation() throws Exception {
        mockMvc.perform(get("/test/duplicate-tenant"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_CODE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("A merchant with this tenant code already exists."))
                .andExpect(jsonPath("$.violations[0].field").value("tenantCode"))
                .andExpect(jsonPath("$.violations[0].code").value("TENANT_CODE_ALREADY_EXISTS"));
    }

    @Test
    void unknownUniqueConstraintReturnsSanitizedFallback() throws Exception {
        mockMvc.perform(get("/test/unknown-duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("This information already exists. Please review your entries and try again."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("unknown_secret_constraint"))));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {
        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Resource not found");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Resource already exists");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenOperationException("Operation is not allowed");
        }

        @GetMapping("/previous-day-open")
        void previousDayOpen() {
            throw new ConflictException("PREVIOUS_BUSINESS_DAY_STILL_OPEN");
        }

        @GetMapping("/duplicate-tenant")
        void duplicateTenant() {
            throw integrity("uq_tenants_tenant_code");
        }

        @GetMapping("/unknown-duplicate")
        void unknownDuplicate() {
            throw integrity("unknown_secret_constraint");
        }

        private static DataIntegrityViolationException integrity(String constraint) {
            var sql = new java.sql.SQLException("technical detail", "23505");
            var hibernate = new org.hibernate.exception.ConstraintViolationException("write failed", sql, constraint);
            return new DataIntegrityViolationException("persistence failed", hibernate);
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody TestRequest request) {
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
