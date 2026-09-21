package com.merchtyl.receipts;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class KitchenPrintJobController {
    private final KitchenPrintJobService service;
    public KitchenPrintJobController(KitchenPrintJobService service){this.service=service;}

    @GetMapping("/stores/{storeId}/kitchen-print-jobs")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).FOOD_POS_ACCESS)")
    List<KitchenPrintJobDto> list(@PathVariable UUID storeId, Authentication authentication){return service.list(storeId, authentication);}

    @PostMapping("/stores/{storeId}/kitchen-print-jobs/claim")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).FOOD_POS_ACCESS)")
    ResponseEntity<KitchenPrintDispatchDto> claim(@PathVariable UUID storeId, @Valid @RequestBody KitchenPrintClaimRequest request,
            Authentication authentication) {
        return service.claimDue(storeId, request.clientId(), authentication).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/sales/{saleId}/kitchen-print-job/print-now")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).FOOD_POS_ACCESS)")
    KitchenPrintDispatchDto printNow(@PathVariable UUID saleId, @Valid @RequestBody KitchenPrintClaimRequest request,
            Authentication authentication){return service.printNow(saleId, request.clientId(), authentication);}

    @PostMapping("/kitchen-print-jobs/{jobId}/acknowledge")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).FOOD_POS_ACCESS)")
    KitchenPrintJobDto acknowledge(@PathVariable UUID jobId, @Valid @RequestBody KitchenPrintAcknowledgeRequest request,
            Authentication authentication){return service.acknowledge(jobId, request, authentication);}
}
