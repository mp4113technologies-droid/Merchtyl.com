package com.merchtyl.refunds;

import com.merchtyl.common.PageResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/refunds")
@Tag(name = "Returns and Refunds", description = "Refund processing and refund history.")
public class RefundController {
    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REFUND_CREATE)")
    @Operation(summary = "Process a refund", description = "Requires REFUND_CREATE and Idempotency-Key. Posts refund payments and inventory restoration where applicable.")
    @ApiResponse(responseCode = "201", description = "Refund posted or duplicate idempotent response returned.")
    @ApiResponse(responseCode = "409", description = "Refund conflicts with return or payment state.")
    ResponseEntity<String> create(
            @Parameter(description = "Required idempotency key for retry-safe refund processing.", required = true)
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody RefundCreateRequest request,
            Authentication authentication) {
        IdempotencyResult result = refundService.createIdempotently(request, idempotencyKey, authentication);
        return ResponseEntity.status(result.status())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REFUND_VIEW)")
    RefundResponse get(@PathVariable UUID id) {
        return refundService.get(id);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REFUND_VIEW)")
    @Operation(summary = "Search refunds", description = "Requires REFUND_VIEW. Supports original sale, return, store, session, and page/size filters.")
    PageResponse<RefundResponse> search(
            @RequestParam(required = false) UUID originalSaleId,
            @RequestParam(required = false) UUID returnId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID registerSessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return refundService.search(new RefundSearchRequest(originalSaleId, returnId, storeId, registerSessionId, page, size));
    }
}
