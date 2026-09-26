package com.merchtyl.posquickkey;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.merchtyl.posquickkey.PosQuickKeyDtos.*;

@RestController
@RequestMapping("/api/v1/pos/quick-keys")
public class PosQuickKeyController {
    private final PosQuickKeyService service;
    public PosQuickKeyController(PosQuickKeyService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).POS_ACCESS)")
    List<Response> forStore(@RequestParam UUID storeId, Authentication authentication) { return service.forStore(storeId, authentication); }

    @GetMapping("/configuration")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_VIEW)")
    List<Response> configuration(Authentication authentication) { return service.configuration(authentication); }

    @PostMapping("/configuration")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_UPDATE)")
    Response create(@Valid @RequestBody CreateRequest request, Authentication authentication) { return service.create(request, authentication); }

    @PutMapping("/configuration/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_UPDATE)")
    Response update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request, Authentication authentication) { return service.update(id, request, authentication); }

    @DeleteMapping("/configuration/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).PRODUCT_UPDATE)")
    void delete(@PathVariable UUID id, Authentication authentication) { service.delete(id, authentication); }
}
