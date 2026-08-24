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
@RequestMapping("/api/v1/tax/administrative-areas")
public class AdministrativeAreaController {
    private final AdministrativeAreaService service;

    public AdministrativeAreaController(AdministrativeAreaService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_MANAGE)")
    AdministrativeAreaResponse create(@Valid @RequestBody AdministrativeAreaRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_VIEW)")
    PageResponse<AdministrativeAreaResponse> list(
            @RequestParam(required = false) UUID countryId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) AdministrativeAreaType type,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(new AdministrativeAreaSearchRequest(countryId, code, name, type, active, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_VIEW)")
    AdministrativeAreaResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_MANAGE)")
    AdministrativeAreaResponse update(@PathVariable UUID id, @Valid @RequestBody AdministrativeAreaUpdateRequest request, Authentication authentication) {
        return service.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).TAX_MANAGE)")
    AdministrativeAreaResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody AdministrativeAreaStatusRequest request, Authentication authentication) {
        return service.updateStatus(id, request, authentication);
    }
}
