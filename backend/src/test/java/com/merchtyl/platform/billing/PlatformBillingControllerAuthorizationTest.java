package com.merchtyl.platform.billing;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformBillingControllerAuthorizationTest {
    @Test
    void everyPlatformBillingEndpointHasBackendAuthorization() {
        assertThat(Arrays.stream(PlatformBillingController.class.getDeclaredMethods())
                .filter(method -> !method.getName().equals("pdfResponse"))
                .filter(method -> method.getAnnotations().length > 0)
                .allMatch(method -> method.isAnnotationPresent(PreAuthorize.class))).isTrue();
    }

    @Test
    void merchantControllerRequiresTenantBillingPermission() {
        PreAuthorize authorization = MerchantBillingController.class.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).contains("MERCHANT_BILLING_VIEW");
    }
}
