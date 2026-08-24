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
@RequestMapping("/api/v1/tax/jurisdictions")
public class TaxJurisdictionController {
    private final TaxJurisdictionService service;

    public TaxJurisdictionController(TaxJurisdictionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_MANAGE)")
    TaxJurisdictionResponse create(@Valid @RequestBody TaxJurisdictionRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_VIEW)")
    PageResponse<TaxJurisdictionResponse> list(
            @RequestParam(required = false) UUID countryId,
            @RequestParam(required = false) UUID administrativeAreaId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) TaxJurisdictionType type,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(new TaxJurisdictionSearchRequest(countryId, administrativeAreaId, code, name, type, active, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_VIEW)")
    TaxJurisdictionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_MANAGE)")
    TaxJurisdictionResponse update(@PathVariable UUID id, @Valid @RequestBody TaxJurisdictionUpdateRequest request, Authentication authentication) {
        return service.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_MANAGE)")
    TaxJurisdictionResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody TaxJurisdictionStatusRequest request, Authentication authentication) {
        return service.updateStatus(id, request, authentication);
    }
}
