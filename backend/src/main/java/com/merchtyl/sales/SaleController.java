package com.merchtyl.sales;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/sales")
@Tag(name = "Sales", description = "Point-of-sale drafts, line items, payments, and idempotent sale completion.")
public class SaleController {
    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_CREATE)")
    SaleResponse createDraft(@Valid @RequestBody SaleCreateDraftRequest request, Authentication authentication) {
        return saleService.createDraft(request, authentication);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_VIEW)")
    SaleResponse get(@PathVariable UUID id) {
        return saleService.get(id);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_VIEW)")
    @Operation(summary = "Search sales", description = "Requires SALE_VIEW. Supports store/session filters and page/size pagination.")
    PageResponse<SaleResponse> search(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID registerId,
            @RequestParam(required = false) UUID registerSessionId,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return saleService.search(new SaleSearchRequest(storeId, registerId, registerSessionId, createdBy, status, page, size));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_CREATE)")
    SaleResponse addItem(@PathVariable UUID id, @Valid @RequestBody SaleAddItemRequest request, Authentication authentication) {
        return saleService.addItem(id, request, authentication);
    }

    @PatchMapping("/{id}/items/{itemId}/quantity")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_CREATE)")
    SaleResponse updateQuantity(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @Valid @RequestBody SaleUpdateQuantityRequest request,
            Authentication authentication) {
        return saleService.updateQuantity(id, itemId, request, authentication);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_CREATE)")
    SaleResponse removeItem(@PathVariable UUID id, @PathVariable UUID itemId, Authentication authentication) {
        return saleService.removeItem(id, itemId, authentication);
    }

    @PostMapping("/{id}/items/{itemId}/price-override")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).POS_PRICE_OVERRIDE)")
    @Operation(summary = "Apply an audited price correction")
    SaleResponse overridePrice(@PathVariable UUID id, @PathVariable UUID itemId,
                               @Valid @RequestBody PriceOverrideRequest request, Authentication authentication) {
        return saleService.overridePrice(id, itemId, request, authentication);
    }

    @PostMapping("/{id}/items/{itemId}/discount")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).POS_LINE_DISCOUNT)")
    @Operation(summary = "Apply an audited line discount")
    SaleResponse discount(@PathVariable UUID id, @PathVariable UUID itemId,
                          @Valid @RequestBody LineDiscountRequest request, Authentication authentication) {
        return saleService.applyLineDiscount(id, itemId, request, authentication);
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_CREATE)")
    SaleResponse hold(@PathVariable UUID id, Authentication authentication) {
        return saleService.hold(id, authentication);
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_CREATE)")
    SaleResponse resume(@PathVariable UUID id, Authentication authentication) {
        return saleService.resume(id, authentication);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_CREATE)")
    SaleResponse cancel(@PathVariable UUID id, Authentication authentication) {
        return saleService.cancel(id, authentication);
    }

    @PostMapping("/{id}/recalculate")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_CREATE)")
    SaleResponse recalculate(@PathVariable UUID id, Authentication authentication) {
        return saleService.recalculate(id, authentication);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_CREATE)")
    SaleResponse recordPayment(@PathVariable UUID id, @Valid @RequestBody SalePaymentRequest request, Authentication authentication) {
        return saleService.recordPayment(id, request, authentication);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_CREATE)")
    @Operation(summary = "Complete a sale", description = "Requires SALE_CREATE and Idempotency-Key. Completed sales are financially posted.")
    @ApiResponse(responseCode = "200", description = "Sale completed or duplicate idempotent response returned.")
    @ApiResponse(responseCode = "409", description = "Sale cannot be completed in its current state.")
    ResponseEntity<String> complete(
            @PathVariable UUID id,
            @Parameter(description = "Required idempotency key for retry-safe sale completion.", required = true)
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            Authentication authentication) {
        IdempotencyResult result = saleService.completeIdempotently(id, idempotencyKey, authentication);
        return ResponseEntity.status(result.status())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }
}
