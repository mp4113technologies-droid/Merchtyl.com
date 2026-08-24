package com.merchtyl.lottery;

import com.merchtyl.common.PageResponse;
import com.merchtyl.security.PermissionCode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lottery/payout-policies")
public class LotteryPayoutPolicyController {
    private final LotteryPayoutPolicyService lotteryPayoutPolicyService;

    public LotteryPayoutPolicyController(LotteryPayoutPolicyService lotteryPayoutPolicyService) {
        this.lotteryPayoutPolicyService = lotteryPayoutPolicyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_MANAGE)")
    public LotteryPayoutPolicyResponse create(@Valid @RequestBody LotteryPayoutPolicyRequest request, Authentication authentication) {
        return lotteryPayoutPolicyService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW)")
    public PageResponse<LotteryPayoutPolicyResponse> search(@ModelAttribute LotteryPayoutPolicySearchRequest request) {
        return lotteryPayoutPolicyService.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW)")
    public LotteryPayoutPolicyResponse get(@PathVariable UUID id) {
        return lotteryPayoutPolicyService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_MANAGE)")
    public LotteryPayoutPolicyResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody LotteryPayoutPolicyUpdateRequest request,
            Authentication authentication) {
        return lotteryPayoutPolicyService.update(id, request, authentication);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_MANAGE)")
    public LotteryPayoutPolicyResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody LotteryPayoutPolicyStatusRequest request,
            Authentication authentication) {
        return lotteryPayoutPolicyService.updateStatus(id, request, authentication);
    }
}
