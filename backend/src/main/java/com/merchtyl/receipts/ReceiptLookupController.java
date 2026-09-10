package com.merchtyl.receipts;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/receipts")
public class ReceiptLookupController {
    private final ReceiptService receiptService;

    public ReceiptLookupController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/{receiptNumber}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_VIEW)")
    ReceiptResponse find(@PathVariable String receiptNumber, Authentication authentication) {
        return receiptService.findByReceiptNumber(receiptNumber, authentication);
    }
}
