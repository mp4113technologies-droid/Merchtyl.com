package com.merchtyl.register;

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
@RequestMapping("/api/v1/registers")
public class RegisterController {
    private final RegisterService registerService;

    public RegisterController(RegisterService registerService) {
        this.registerService = registerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_MANAGE)")
    RegisterResponse create(@Valid @RequestBody RegisterRequest request, Authentication authentication) {
        return registerService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_VIEW)")
    PageResponse<RegisterResponse> list(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return registerService.search(new RegisterSearchRequest(storeId, code, name, active, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_VIEW)")
    RegisterResponse get(@PathVariable UUID id) {
        return registerService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_MANAGE)")
    RegisterResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody RegisterUpdateRequest request,
            Authentication authentication) {
        return registerService.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REGISTER_MANAGE)")
    RegisterResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody RegisterStatusRequest request,
            Authentication authentication) {
        return registerService.updateStatus(id, request, authentication);
    }
}
