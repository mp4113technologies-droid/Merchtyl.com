package com.merchtyl.platform.admin;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformMerchantUpdateContractTest {
    @Test
    void updateEndpointRequiresThePlatformTenantUpdatePermission() throws Exception {
        Method method = PlatformAdministrationController.class.getDeclaredMethod(
                "updateTenant", java.util.UUID.class, PlatformDtos.TenantUpdateRequest.class,
                org.springframework.security.core.Authentication.class);

        assertThat(method.getAnnotation(PutMapping.class)).isNotNull();
        assertThat(method.getAnnotation(PreAuthorize.class).value()).contains("TENANT_UPDATE");
    }

    @Test
    void rejectsBlankDisplayNameAndMalformedContactEmail() {
        var request = request("   ", "not-an-email");
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(request);
            assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                    .contains("displayName", "contactEmail");
        }
    }

    @Test
    void acceptsExistingMerchantProfileFieldsWithoutAConnectableSlugField() {
        var request = request("Adviam Retail Group", "john@example.com");
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(request)).isEmpty();
        }
        assertThat(PlatformDtos.TenantUpdateRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .contains("businessNumber", "contactName", "contactEmail", "contactPhone", "billingAddress",
                        "postalCode", "industryType", "estimatedStoreCount", "notes")
                .doesNotContain("merchantSlug");
    }

    private static PlatformDtos.TenantUpdateRequest request(String displayName, String email) {
        return new PlatformDtos.TenantUpdateRequest(
                "Adviam Creatives Inc.", displayName, "BN-1", "John Doe", email, "+1 506 555 0100",
                "10 Main Street, Moncton", "E1C 1A1", "Retail", 3, "Preferred merchant",
                "CA", "NB", "CAD", "America/Moncton", "CA-NB", "Profile correction", 0L);
    }
}
