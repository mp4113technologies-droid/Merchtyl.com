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
@RequestMapping("/api/v1/sales/{saleId}/kitchen-ticket")
public class KitchenTicketController {
    private final KitchenTicketService kitchenTicketService;

    public KitchenTicketController(KitchenTicketService kitchenTicketService) {
        this.kitchenTicketService = kitchenTicketService;
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_VIEW)")
    KitchenTicketDto get(@PathVariable UUID saleId, Authentication authentication) {
        return kitchenTicketService.get(saleId, false, authentication);
    }

    @PostMapping("/reprint")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).SALE_VIEW)")
    KitchenTicketDto reprint(@PathVariable UUID saleId, Authentication authentication) {
        return kitchenTicketService.get(saleId, true, authentication);
    }
}
