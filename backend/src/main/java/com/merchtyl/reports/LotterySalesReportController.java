package com.merchtyl.reports;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/lottery-sales")
public class LotterySalesReportController {
    private final LotterySalesReportService service;
    public LotterySalesReportController(LotterySalesReportService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REPORT_VIEW)")
    LotterySalesReportResponse summarize(@RequestParam(required = false) UUID storeId,
                                         @RequestParam(required = false) UUID registerId,
                                         @RequestParam(required = false) UUID cashierId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String source,
                                         Authentication authentication) {
        return service.summarize(new LotterySalesReportRequest(storeId, registerId, cashierId, dateFrom, dateTo, type, source), authentication);
    }
}
