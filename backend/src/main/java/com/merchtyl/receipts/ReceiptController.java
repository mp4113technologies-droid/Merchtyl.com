package com.merchtyl.receipts;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales/{saleId}/receipt")
public class ReceiptController {
    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_VIEW)")
    ReceiptResponse get(@PathVariable UUID saleId, Authentication authentication) {
        return receiptService.getForSale(saleId, authentication);
    }

    @PostMapping("/reprint")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_VIEW)")
    ReceiptResponse reprint(@PathVariable UUID saleId, Authentication authentication) {
        return receiptService.reprintForSale(saleId, authentication);
    }
}
