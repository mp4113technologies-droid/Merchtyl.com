package com.merchtyl.lottery;

import com.merchtyl.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lottery/settlements")
@Tag(name = "Lottery", description = "Lottery settlement calculation and lifecycle.")
public class LotterySettlementController {
    private final LotterySettlementService lotterySettlementService;

    public LotterySettlementController(LotterySettlementService lotterySettlementService) {
        this.lotterySettlementService = lotterySettlementService;
    }

    @PostMapping("/calculate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_MANAGE)")
    @Operation(summary = "Calculate a lottery settlement", description = "Requires LOTTERY_MANAGE.")
    public LotterySettlementResponse calculate(
            @Valid @RequestBody LotterySettlementCalculationRequest request,
            Authentication authentication) {
        return lotterySettlementService.calculate(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW)")
    @Operation(summary = "Search lottery settlements", description = "Requires LOTTERY_VIEW. Supports settlement filters and pagination.")
    public PageResponse<LotterySettlementResponse> search(@ModelAttribute LotterySettlementSearchRequest request) {
        return lotterySettlementService.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW)")
    public LotterySettlementResponse get(@PathVariable UUID id) {
        return lotterySettlementService.get(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_SETTLEMENT_APPROVE)")
    public LotterySettlementResponse approve(
            @PathVariable UUID id,
            @Valid @RequestBody LotterySettlementLifecycleRequest request,
            Authentication authentication) {
        return lotterySettlementService.approve(id, request, authentication);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_SETTLEMENT_APPROVE)")
    public LotterySettlementResponse reopen(
            @PathVariable UUID id,
            @Valid @RequestBody LotterySettlementLifecycleRequest request,
            Authentication authentication) {
        return lotterySettlementService.reopen(id, request, authentication);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_SETTLEMENT_POST)")
    public LotterySettlementResponse post(
            @PathVariable UUID id,
            @Valid @RequestBody LotterySettlementLifecycleRequest request,
            Authentication authentication) {
        return lotterySettlementService.post(id, request, authentication);
    }
}
