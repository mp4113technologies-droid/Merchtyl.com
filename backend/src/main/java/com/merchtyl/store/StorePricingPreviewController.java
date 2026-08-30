package com.merchtyl.store;

import com.merchtyl.platform.billing.BillingDtos.PricingPreview;
import com.merchtyl.platform.billing.PlatformBillingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/pricing-preview")
public class StorePricingPreviewController {
    private final PlatformBillingService billing;
    public StorePricingPreviewController(PlatformBillingService billing){this.billing=billing;}
    @GetMapping
    @PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).STORE_CREATE)")
    PricingPreview preview(@RequestParam(defaultValue="false") boolean foodService, Authentication authentication){return billing.subscriptionPreview(billing.tenantFor(authentication),1,foodService);}
}
