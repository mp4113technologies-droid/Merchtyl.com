package com.merchtyl.device;

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
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).DEVICE_MANAGE)")
    DeviceResponse register(@Valid @RequestBody DeviceRegisterRequest request, Authentication authentication) {
        return deviceService.register(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).DEVICE_VIEW)")
    PageResponse<DeviceResponse> list(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID registerId,
            @RequestParam(required = false) String deviceIdentifier,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return deviceService.search(new DeviceSearchRequest(
                storeId,
                registerId,
                deviceIdentifier,
                displayName,
                deviceType,
                active,
                page,
                size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).DEVICE_VIEW)")
    DeviceResponse get(@PathVariable UUID id) {
        return deviceService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).DEVICE_MANAGE)")
    DeviceResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody DeviceUpdateRequest request,
            Authentication authentication) {
        return deviceService.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).DEVICE_MANAGE)")
    DeviceResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody DeviceStatusRequest request,
            Authentication authentication) {
        return deviceService.updateStatus(id, request, authentication);
    }

    @PostMapping("/{id}/heartbeat")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).DEVICE_VIEW)")
    DeviceResponse heartbeat(@PathVariable UUID id) {
        return deviceService.heartbeat(id);
    }
}
