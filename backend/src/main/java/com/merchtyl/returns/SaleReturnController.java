package com.merchtyl.returns;

import com.merchtyl.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales/{saleId}/returns")
public class SaleReturnController {
    private final ReturnService returnService;

    public SaleReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).RETURN_CREATE)")
    ReturnResponse create(@PathVariable UUID saleId, @Valid @RequestBody SaleReturnCreateRequest request, Authentication authentication) {
        return returnService.create(new ReturnCreateRequest(saleId, request.reason(), request.items()), authentication);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).RETURN_VIEW)")
    PageResponse<ReturnResponse> listForSale(
            @PathVariable UUID saleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return returnService.search(saleId, null, page, size);
    }
}
