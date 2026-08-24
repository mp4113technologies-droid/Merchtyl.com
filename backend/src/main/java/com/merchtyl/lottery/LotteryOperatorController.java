package com.merchtyl.lottery;

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
@RequestMapping("/api/v1/lottery/operators")
public class LotteryOperatorController {
    private final LotteryOperatorService lotteryOperatorService;

    public LotteryOperatorController(LotteryOperatorService lotteryOperatorService) {
        this.lotteryOperatorService = lotteryOperatorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_MANAGE)")
    LotteryOperatorResponse create(@Valid @RequestBody LotteryOperatorRequest request, Authentication authentication) {
        return lotteryOperatorService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW)")
    PageResponse<LotteryOperatorResponse> list(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) UUID jurisdictionId,
            @RequestParam(required = false) SettlementFrequency settlementFrequency,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return lotteryOperatorService.search(new LotteryOperatorSearchRequest(
                code,
                name,
                jurisdictionId,
                settlementFrequency,
                active,
                page,
                size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW)")
    LotteryOperatorResponse get(@PathVariable UUID id) {
        return lotteryOperatorService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_MANAGE)")
    LotteryOperatorResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody LotteryOperatorUpdateRequest request,
            Authentication authentication) {
        return lotteryOperatorService.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_MANAGE)")
    LotteryOperatorResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody LotteryOperatorStatusRequest request,
            Authentication authentication) {
        return lotteryOperatorService.updateStatus(id, request, authentication);
    }
}
