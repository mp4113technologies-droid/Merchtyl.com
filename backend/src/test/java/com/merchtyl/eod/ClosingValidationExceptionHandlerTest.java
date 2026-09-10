package com.merchtyl.eod;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.merchtyl.platform.web.RequestLoggingFilter;

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
    void recordsBusinessFailureForRequestLogging() {
        MockHttpServletRequest request = request();

        handler.closingValidation(exception("OPEN_REGISTER_SESSION"), request);

        assertThat(request.getAttribute(RequestLoggingFilter.ERROR_CODE_ATTRIBUTE))
                .isEqualTo("BUSINESS_DAY_HAS_OPEN_REGISTER_SESSIONS");
        assertThat(request.getAttribute(RequestLoggingFilter.EXCEPTION_TYPE_ATTRIBUTE))
                .isEqualTo("BUSINESS_DAY_HAS_OPEN_REGISTER_SESSIONS");
    }

    @Test
    void reconciliationBlockersUseStableReconciliationConflictCode() {
        var response = handler.closingValidation(exception("REGISTER_RECONCILIATION_INCOMPLETE"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BUSINESS_DAY_HAS_UNRECONCILED_REGISTER_SESSIONS");
    }

    @Test
    void draftSaleBlockerUsesStableUnfinalizedSalesConflictCode() {
        var response = handler.closingValidation(exception("UNFINALIZED_DRAFT_SALE"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BUSINESS_DAY_HAS_UNFINALIZED_SALES");
        assertThat(response.getBody().violations()).extracting("code")
                .containsExactly("UNFINALIZED_DRAFT_SALE");
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
