package com.merchtyl.lottery;

import com.merchtyl.common.PageResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lottery/payouts")
@Tag(name = "Lottery", description = "Lottery payout validation, approval, completion, reversal, and search.")
public class LotteryPayoutController {
    private final LotteryPayoutService lotteryPayoutService;

    public LotteryPayoutController(LotteryPayoutService lotteryPayoutService) {
        this.lotteryPayoutService = lotteryPayoutService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_PAYOUT_RECORD)")
    LotteryPayoutResponse create(@Valid @RequestBody LotteryPayoutCreateRequest request, Authentication authentication) {
        return lotteryPayoutService.create(request, authentication);
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_PAYOUT_RECORD)")
    LotteryPayoutResponse validate(
            @PathVariable UUID id,
            @Valid @RequestBody LotteryPayoutValidationRequest request,
            Authentication authentication) {
        return lotteryPayoutService.validate(id, request, authentication);
    }

    @PostMapping("/{id}/authorize")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_PAYOUT_RECORD, T(com.merchtyl.security.PermissionCode).LOTTERY_PAYOUT_APPROVE)")
    LotteryPayoutResponse authorize(
            @PathVariable UUID id,
            @Valid @RequestBody LotteryPayoutAuthorizationRequest request,
            Authentication authentication) {
        return lotteryPayoutService.authorize(id, request, authentication);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_PAYOUT_APPROVE)")
    LotteryPayoutResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody LotteryPayoutRejectRequest request,
            Authentication authentication) {
        return lotteryPayoutService.reject(id, request, authentication);
    }

    @GetMapping("/available-cash")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW, T(com.merchtyl.security.PermissionCode).LOTTERY_PAYOUT_RECORD)")
    LotteryPayoutCashAvailabilityResponse availableCash(
            @RequestParam UUID registerSessionId,
            @RequestParam UUID operatorId) {
        return lotteryPayoutService.availableCash(registerSessionId, operatorId);
    }

    @PostMapping("/{id}/complete-cash")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_PAYOUT_RECORD)")
    @Operation(summary = "Complete a cash lottery payout", description = "Requires LOTTERY_PAYOUT_RECORD and Idempotency-Key.")
    @ApiResponse(responseCode = "200", description = "Cash payout completed or duplicate idempotent response returned.")
    ResponseEntity<String> completeCash(
            @PathVariable UUID id,
            @Parameter(description = "Required idempotency key for retry-safe payout completion.", required = true)
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            Authentication authentication) {
        IdempotencyResult result = lotteryPayoutService.completeCashIdempotently(id, idempotencyKey, authentication);
        return ResponseEntity.status(result.status())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_PAYOUT_APPROVE)")
    @Operation(summary = "Reverse a lottery payout", description = "Requires LOTTERY_PAYOUT_APPROVE and Idempotency-Key.")
    @ApiResponse(responseCode = "200", description = "Payout reversed or duplicate idempotent response returned.")
    ResponseEntity<String> reverse(
            @PathVariable UUID id,
            @Valid @RequestBody LotteryAdjustmentRequest request,
            @Parameter(description = "Required idempotency key for retry-safe payout reversal.", required = true)
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            Authentication authentication) {
        IdempotencyResult result = lotteryPayoutService.reverseIdempotently(id, request, idempotencyKey, authentication);
        return ResponseEntity.status(result.status())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW, T(com.merchtyl.security.PermissionCode).LOTTERY_PAYOUT_RECORD)")
    LotteryPayoutResponse get(@PathVariable UUID id) {
        return lotteryPayoutService.get(id);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW, T(com.merchtyl.security.PermissionCode).LOTTERY_PAYOUT_RECORD)")
    @Operation(summary = "Search lottery payouts", description = "Requires LOTTERY_VIEW or LOTTERY_PAYOUT_RECORD. Supports operator, store, register, session, status, and pagination filters.")
    PageResponse<LotteryPayoutResponse> search(
            @RequestParam(required = false) UUID operatorId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID registerId,
            @RequestParam(required = false) UUID registerSessionId,
            @RequestParam(required = false) LotteryPayoutStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return lotteryPayoutService.search(new LotteryPayoutSearchRequest(
                operatorId,
                storeId,
                registerId,
                registerSessionId,
                status,
                page,
                size));
    }
}
