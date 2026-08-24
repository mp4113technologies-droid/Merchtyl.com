package com.merchtyl.lottery;

import com.merchtyl.common.PageResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.merchtyl.sales.PaymentMethod;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lottery/sales")
@Tag(name = "Lottery", description = "Lottery sale recording, search, and cancellation.")
public class LotterySaleController {
    private final LotterySaleService lotterySaleService;

    public LotterySaleController(LotterySaleService lotterySaleService) {
        this.lotterySaleService = lotterySaleService;
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_SALE_RECORD)")
    @Operation(summary = "Record a lottery sale", description = "Requires LOTTERY_SALE_RECORD and Idempotency-Key.")
    @ApiResponse(responseCode = "201", description = "Lottery sale recorded or duplicate idempotent response returned.")
    ResponseEntity<String> record(
            @Valid @RequestBody LotterySaleRequest request,
            @Parameter(description = "Required idempotency key for retry-safe lottery sale recording.", required = true)
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            Authentication authentication) {
        IdempotencyResult result = lotterySaleService.recordIdempotently(request, idempotencyKey, authentication);
        return ResponseEntity.status(result.status())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW, T(com.merchtyl.security.PermissionCode).LOTTERY_SALE_RECORD)")
    LotterySaleResponse get(@PathVariable UUID id) {
        return lotterySaleService.get(id);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_VIEW, T(com.merchtyl.security.PermissionCode).LOTTERY_SALE_RECORD)")
    @Operation(summary = "Search lottery sales", description = "Requires LOTTERY_VIEW or LOTTERY_SALE_RECORD. Supports operator, store, register, cashier, status, payment, time range, and pagination filters.")
    PageResponse<LotterySaleResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID operatorId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID registerId,
            @RequestParam(required = false) UUID cashierId,
            @RequestParam(required = false) UUID registerSessionId,
            @RequestParam(required = false) LotteryGameType gameType,
            @RequestParam(required = false) LotterySaleStatus status,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return lotterySaleService.search(new LotterySaleSearchRequest(
                search,
                operatorId,
                storeId,
                registerId,
                cashierId,
                registerSessionId,
                gameType,
                status,
                paymentMethod,
                occurredFrom,
                occurredTo,
                page,
                size));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_SALE_CANCEL)")
    @Operation(summary = "Cancel a lottery sale", description = "Requires LOTTERY_SALE_CANCEL and Idempotency-Key.")
    @ApiResponse(responseCode = "200", description = "Lottery sale cancelled or duplicate idempotent response returned.")
    ResponseEntity<String> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody LotteryAdjustmentRequest request,
            @Parameter(description = "Required idempotency key for retry-safe lottery sale cancellation.", required = true)
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            Authentication authentication) {
        IdempotencyResult result = lotterySaleService.cancelIdempotently(id, request, idempotencyKey, authentication);
        return ResponseEntity.status(result.status())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }
}
