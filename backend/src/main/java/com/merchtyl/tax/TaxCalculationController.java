package com.merchtyl.tax;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tax/calculate")
public class TaxCalculationController {
    private final TaxEngine taxEngine;

    public TaxCalculationController(TaxEngine taxEngine) {
        this.taxEngine = taxEngine;
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_VIEW)")
    TaxCalculationResponse calculate(@Valid @RequestBody TaxCalculationRequest request, Authentication authentication) {
        return taxEngine.calculate(request, authentication);
    }
}
