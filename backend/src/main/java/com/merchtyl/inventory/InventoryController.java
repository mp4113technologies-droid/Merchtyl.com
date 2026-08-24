package com.merchtyl.inventory;

import com.merchtyl.common.PageResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService inventoryService;
    private final StockAdjustmentService stockAdjustmentService;
    private final StockCountService stockCountService;

    public InventoryController(
            InventoryService inventoryService,
            StockAdjustmentService stockAdjustmentService,
            StockCountService stockCountService) {
        this.inventoryService = inventoryService;
        this.stockAdjustmentService = stockAdjustmentService;
        this.stockCountService = stockCountService;
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_RECEIVE, T(com.merchtyl.security.PermissionCode).INVENTORY_ADJUST, T(com.merchtyl.security.PermissionCode).INVENTORY_MANAGE)")
    InventoryTransactionResponse recordStockChange(
            @Valid @RequestBody InventoryStockChangeRequest request,
            Authentication authentication) {
        return inventoryService.recordStockChange(request, authentication);
    }

    @GetMapping("/balances/current")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_VIEW)")
    InventoryBalanceResponse currentStock(@RequestParam UUID storeId, @RequestParam UUID productId) {
        return inventoryService.currentStock(storeId, productId);
    }

    @GetMapping("/balances")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_VIEW)")
    PageResponse<InventoryBalanceResponse> balances(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventoryService.searchBalances(new InventoryBalanceSearchRequest(storeId, productId, page, size));
    }

    @GetMapping("/transactions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_VIEW)")
    PageResponse<InventoryTransactionResponse> transactions(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) InventoryTransactionType transactionType,
            @RequestParam(required = false) UUID referenceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventoryService.searchTransactions(new InventoryTransactionSearchRequest(
                storeId,
                productId,
                transactionType,
                referenceId,
                occurredFrom,
                occurredTo,
                page,
                size));
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_RECEIVE, T(com.merchtyl.security.PermissionCode).INVENTORY_ADJUST, T(com.merchtyl.security.PermissionCode).INVENTORY_MANAGE)")
    StockAdjustmentResponse createAdjustment(
            @Valid @RequestBody StockAdjustmentRequest request,
            Authentication authentication) {
        return stockAdjustmentService.create(request, authentication);
    }

    @GetMapping("/adjustments")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_VIEW)")
    PageResponse<StockAdjustmentResponse> adjustments(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) StockAdjustmentApprovalStatus approvalStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return stockAdjustmentService.search(new StockAdjustmentSearchRequest(
                storeId,
                approvalStatus,
                createdFrom,
                createdTo,
                page,
                size));
    }

    @PostMapping("/counts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_COUNT_UPDATE, T(com.merchtyl.security.PermissionCode).INVENTORY_MANAGE)")
    StockCountResponse createCount(
            @Valid @RequestBody StockCountCreateRequest request,
            Authentication authentication) {
        return stockCountService.create(request, authentication);
    }

    @GetMapping("/counts")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_COUNT_VIEW, T(com.merchtyl.security.PermissionCode).INVENTORY_COUNT_UPDATE)")
    PageResponse<StockCountResponse> counts(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) StockCountStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return stockCountService.search(new StockCountSearchRequest(
                storeId,
                status,
                createdFrom,
                createdTo,
                page,
                size), authentication);
    }

    @GetMapping("/counts/{id}")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_COUNT_VIEW, T(com.merchtyl.security.PermissionCode).INVENTORY_COUNT_UPDATE)")
    StockCountResponse count(@PathVariable UUID id, Authentication authentication) {
        return stockCountService.get(id, authentication);
    }

    @PatchMapping("/counts/{id}/lines")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_COUNT_UPDATE, T(com.merchtyl.security.PermissionCode).INVENTORY_MANAGE)")
    StockCountResponse enterCountedQuantities(
            @PathVariable UUID id,
            @Valid @RequestBody StockCountUpdateLinesRequest request,
            Authentication authentication) {
        return stockCountService.enterCountedQuantities(id, request, authentication);
    }

    @PostMapping("/counts/{id}/review")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_COUNT_UPDATE, T(com.merchtyl.security.PermissionCode).INVENTORY_MANAGE)")
    StockCountResponse reviewCount(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) StockCountReviewRequest request,
            Authentication authentication) {
        return stockCountService.review(id, request, authentication);
    }

    @PostMapping("/counts/{id}/post")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).INVENTORY_COUNT_UPDATE, T(com.merchtyl.security.PermissionCode).INVENTORY_MANAGE)")
    ResponseEntity<String> postCount(
            @PathVariable UUID id,
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody(required = false) StockCountPostRequest request,
            Authentication authentication) {
        IdempotencyResult result = stockCountService.postIdempotently(id, request, idempotencyKey, authentication);
        return ResponseEntity.status(result.status())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }
}
