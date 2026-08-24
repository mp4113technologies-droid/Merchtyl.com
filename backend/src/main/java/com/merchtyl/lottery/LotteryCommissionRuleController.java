package com.merchtyl.lottery;

import com.merchtyl.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/lottery/commission-rules")
public class LotteryCommissionRuleController {
    private final LotteryCommissionRuleService lotteryCommissionRuleService;

    public LotteryCommissionRuleController(LotteryCommissionRuleService lotteryCommissionRuleService) {
        this.lotteryCommissionRuleService = lotteryCommissionRuleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_COMMISSION_RULE_MANAGE)")
    public LotteryCommissionRuleResponse create(@Valid @RequestBody LotteryCommissionRuleRequest request, Authentication authentication) {
        return lotteryCommissionRuleService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW)")
    public PageResponse<LotteryCommissionRuleResponse> search(@ModelAttribute LotteryCommissionRuleSearchRequest request) {
        return lotteryCommissionRuleService.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW)")
    public LotteryCommissionRuleResponse get(@PathVariable UUID id) {
        return lotteryCommissionRuleService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_COMMISSION_RULE_MANAGE)")
    public LotteryCommissionRuleResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody LotteryCommissionRuleUpdateRequest request,
            Authentication authentication) {
        return lotteryCommissionRuleService.update(id, request, authentication);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_COMMISSION_RULE_MANAGE)")
    public void delete(@PathVariable UUID id, @RequestParam @NotNull Long version, Authentication authentication) {
        lotteryCommissionRuleService.delete(id, version, authentication);
    }
}
