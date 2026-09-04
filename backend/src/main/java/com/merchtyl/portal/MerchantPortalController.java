package com.merchtyl.portal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/merchant-portals")
public class MerchantPortalController {
    private final MerchantPortalService service;
    public MerchantPortalController(MerchantPortalService service) { this.service = service; }

    @GetMapping("/{slug}")
    MerchantPortalService.PortalResolution resolve(@PathVariable String slug) { return service.resolve(slug); }
}
