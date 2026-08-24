package com.merchtyl.tax;

import com.merchtyl.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tax/rates")
public class TaxRateController {
    private final TaxRateService service;

    public TaxRateController(TaxRateService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_MANAGE)")
    TaxRateResponse create(@Valid @RequestBody TaxRateRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_VIEW)")
    PageResponse<TaxRateResponse> list(
            @RequestParam(required = false) UUID taxComponentId,
            @RequestParam(required = false) TaxRateStatus status,
            @RequestParam(required = false) Boolean includedInPrice,
            @RequestParam(required = false) Boolean compoundOnPreviousTax,
            @RequestParam(required = false) Integer calculationOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(new TaxRateSearchRequest(taxComponentId, status, includedInPrice, compoundOnPreviousTax, calculationOrder, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_VIEW)")
    TaxRateResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_MANAGE)")
    TaxRateResponse update(@PathVariable UUID id, @Valid @RequestBody TaxRateUpdateRequest request, Authentication authentication) {
        return service.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_MANAGE)")
    TaxRateResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody TaxRateStatusRequest request, Authentication authentication) {
        return service.updateStatus(id, request, authentication);
    }
}
