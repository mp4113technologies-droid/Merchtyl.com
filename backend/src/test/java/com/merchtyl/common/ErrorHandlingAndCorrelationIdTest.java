package com.merchtyl.common;

import com.merchtyl.platform.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
                .andExpect(jsonPath("$.code").value("not_found"))
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
                .andExpect(jsonPath("$.code").value("conflict"));
    }

    @Test
    void validationErrorsAreStructured() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.violations[0].field").value("name"))
                .andExpect(jsonPath("$.violations[0].message").isNotEmpty());
    }

    @Test
    void forbiddenOperationUsesForbiddenStatus() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"))
                .andExpect(jsonPath("$.status").value(403));
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

        @PostMapping("/validate")
        void validate(@Valid @RequestBody TestRequest request) {
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
