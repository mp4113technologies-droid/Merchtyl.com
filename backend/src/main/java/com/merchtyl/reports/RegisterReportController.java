package com.merchtyl.reports;

import com.merchtyl.registersession.RegisterSessionStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/registers")
public class RegisterReportController {
    private final RegisterReportService registerReportService;

    public RegisterReportController(RegisterReportService registerReportService) {
        this.registerReportService = registerReportService;
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).REPORT_VIEW)")
    RegisterReportResponse summarize(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID registerId,
            @RequestParam(required = false) UUID cashierId,
            @RequestParam(required = false) RegisterSessionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return registerReportService.summarize(new RegisterReportRequest(
                storeId,
                registerId,
                cashierId,
                status,
                dateFrom,
                dateTo));
    }
}
