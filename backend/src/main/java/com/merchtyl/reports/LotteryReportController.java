package com.merchtyl.reports;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/lottery")
public class LotteryReportController {
    private final LotteryReportService lotteryReportService;

    public LotteryReportController(LotteryReportService lotteryReportService) {
        this.lotteryReportService = lotteryReportService;
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REPORT_VIEW)")
    LotteryReportResponse summarize(
            @RequestParam(required = false) UUID operatorId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID registerId,
            @RequestParam(required = false) UUID cashierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return lotteryReportService.summarize(new LotteryReportRequest(
                operatorId,
                storeId,
                registerId,
                cashierId,
                dateFrom,
                dateTo));
    }
}
