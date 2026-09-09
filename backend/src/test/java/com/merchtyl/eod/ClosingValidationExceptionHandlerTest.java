package com.merchtyl.eod;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClosingValidationExceptionHandlerTest {
    private final ClosingValidationExceptionHandler handler = new ClosingValidationExceptionHandler();

    @Test
    void openRegisterBlockerUsesStableOpenRegisterConflictCode() {
        var response = handler.closingValidation(exception("OPEN_REGISTER_SESSION"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BUSINESS_DAY_HAS_OPEN_REGISTER_SESSIONS");
    }

    @Test
    void otherBlockersUseStableReconciliationConflictCode() {
        var response = handler.closingValidation(exception("REGISTER_RECONCILIATION_INCOMPLETE"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BUSINESS_DAY_RECONCILIATION_INCOMPLETE");
    }

    private static ClosingValidationException exception(String code) {
        return new ClosingValidationException(new ClosingValidationResponse(
                UUID.randomUUID(), false, List.of(new ClosingBlockerResponse(code, "Closing is blocked", UUID.randomUUID()))));
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/business-days/example/close");
        request.addHeader("X-Correlation-ID", "test-correlation");
        return request;
    }
}
