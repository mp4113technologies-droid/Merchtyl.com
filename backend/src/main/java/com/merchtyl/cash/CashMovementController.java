package com.merchtyl.cash;

import com.merchtyl.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cash-movements")
public class CashMovementController {
    private final CashMovementService cashMovementService;

    public CashMovementController(CashMovementService cashMovementService) {
        this.cashMovementService = cashMovementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).CASH_MOVEMENT_CREATE)")
    CashMovementResponse create(@Valid @RequestBody CashMovementRequest request, Authentication authentication) {
        return cashMovementService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).CASH_MOVEMENT_VIEW)")
    PageResponse<CashMovementResponse> search(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID registerId,
            @RequestParam(required = false) UUID registerSessionId,
            @RequestParam(required = false) CashMovementType type,
            @RequestParam(required = false) Instant occurredFrom,
            @RequestParam(required = false) Instant occurredTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return cashMovementService.search(new CashMovementSearchRequest(
                storeId,
                registerId,
                registerSessionId,
                type,
                occurredFrom,
                occurredTo,
                page,
                size));
    }
}
