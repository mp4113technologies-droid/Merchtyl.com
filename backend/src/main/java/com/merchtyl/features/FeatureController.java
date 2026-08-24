package com.merchtyl.features;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/features")
public class FeatureController {
    private final FeatureService featureService;

    public FeatureController(FeatureService featureService) {
        this.featureService = featureService;
    }

    @GetMapping("/definitions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).FEATURE_VIEW)")
    List<FeatureDefinitionResponse> definitions() {
        return featureService.listDefinitions();
    }

    @GetMapping("/resolution")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).FEATURE_VIEW)")
    List<FeatureResolutionResponse> resolution(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID registerId) {
        return featureService.resolve(storeId, registerId);
    }

    @PutMapping("/{featureCode}/deployment")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).FEATURE_MANAGE)")
    FeatureResolutionResponse updateDeployment(
            @PathVariable FeatureCode featureCode,
            @Valid @RequestBody FeatureOverrideRequest request,
            Authentication authentication) {
        return featureService.updateDeployment(featureCode, request, authentication);
    }

    @PutMapping("/{featureCode}/stores/{storeId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).FEATURE_MANAGE)")
    FeatureResolutionResponse updateStore(
            @PathVariable FeatureCode featureCode,
            @PathVariable UUID storeId,
            @Valid @RequestBody FeatureOverrideRequest request,
            Authentication authentication) {
        return featureService.updateStore(featureCode, storeId, request, authentication);
    }

    @PutMapping("/{featureCode}/registers/{registerId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).FEATURE_MANAGE)")
    FeatureResolutionResponse updateRegister(
            @PathVariable FeatureCode featureCode,
            @PathVariable UUID registerId,
            @Valid @RequestBody FeatureOverrideRequest request,
            Authentication authentication) {
        return featureService.updateRegister(featureCode, registerId, request, authentication);
    }
}
