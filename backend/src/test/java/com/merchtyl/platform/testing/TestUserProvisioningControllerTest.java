package com.merchtyl.platform.testing;

import com.merchtyl.audit.AuditService;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestUserProvisioningControllerTest {
    private final TestUserProvisioningService service = mock(TestUserProvisioningService.class);
    private final TestProvisioningRateLimiter rateLimiter = mock(TestProvisioningRateLimiter.class);
    private final AuditService auditService = mock(AuditService.class);
    private final HttpServletRequest servletRequest = mock(HttpServletRequest.class);

    @Test
    void disabledFeatureReturnsNotFound() {
        TestUserProvisioningController controller = controller("test-key");
        when(service.enabled()).thenReturn(false);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThatThrownBy(() -> controller.provisionUser("test-key", request(), servletRequest))
                .isInstanceOf(NotFoundException.class);

        verify(service, never()).provision(any());
    }

    @Test
    void invalidKeyReturnsForbiddenAndAuditsWithoutCallingService() {
        TestUserProvisioningController controller = controller("test-key");
        when(service.enabled()).thenReturn(true);
        when(rateLimiter.allow("127.0.0.1")).thenReturn(true);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThatThrownBy(() -> controller.provisionUser("wrong", request(), servletRequest))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(auditService).record(any());
        verify(service, never()).provision(any());
    }

    @Test
    void validKeyCallsService() {
        TestUserProvisioningController controller = controller("test-key");
        when(service.enabled()).thenReturn(true);
        when(rateLimiter.allow("127.0.0.1")).thenReturn(true);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(service.provision(any())).thenReturn(List.of(response()));

        controller.provisionUser("test-key", request(), servletRequest);

        verify(service).provision(any());
    }

    private TestUserProvisioningController controller(String key) {
        return new TestUserProvisioningController(
                new TestUserProvisioningProperties(true, key, ""),
                service,
                rateLimiter,
                auditService);
    }

    private TestUserProvisioningDtos.ProvisionUserRequest request() {
        return new TestUserProvisioningDtos.ProvisionUserRequest(
                "TEST-MERCHANT-A",
                TestUserProvisioningRole.CASHIER,
                "Cashier",
                "A",
                "cashier.a@test.merchtyl.local",
                "Test1234!",
                TestUserProvisioningStatus.ACTIVE,
                List.of("STORE-A1"),
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                null,
                TestUserExistingStrategy.FAIL);
    }

    private TestUserProvisioningDtos.ProvisionUserResponse response() {
        return new TestUserProvisioningDtos.ProvisionUserResponse(
                java.util.UUID.randomUUID(),
                "TENANT",
                "TEST-MERCHANT-A",
                TestUserProvisioningRole.CASHIER,
                "cashier.a@test.merchtyl.local",
                TestUserProvisioningStatus.ACTIVE,
                List.of("STORE-A1"),
                true,
                true);
    }
}
