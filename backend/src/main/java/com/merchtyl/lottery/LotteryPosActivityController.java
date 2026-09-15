package com.merchtyl.lottery;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lottery/pos-activities")
public class LotteryPosActivityController {
    private final LotteryPosActivityService service;

    public LotteryPosActivityController(LotteryPosActivityService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("(#request.type().name() == 'SOLD' and @authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_SALE_RECORD)) or (#request.type().name() == 'WIN' and @authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).LOTTERY_PAYOUT_RECORD))")
    LotteryPosActivityResponse record(@Valid @RequestBody LotteryPosActivityRequest request, Authentication authentication) {
        return service.record(request, authentication);
    }
}
