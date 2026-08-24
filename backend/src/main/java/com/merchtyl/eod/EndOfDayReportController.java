package com.merchtyl.eod;

import com.merchtyl.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/end-of-day-reports")
@Tag(name = "Reports", description = "Immutable end-of-day report history, print rendering, and exports.")
public class EndOfDayReportController {
    private final BusinessDayService businessDayService;

    public EndOfDayReportController(BusinessDayService businessDayService) {
        this.businessDayService = businessDayService;
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).END_OF_DAY_REPORT_VIEW)")
    @Operation(summary = "Search EOD reports", description = "Requires END_OF_DAY_REPORT_VIEW. Supports store, business date range, status, closed by, report number, and page/size filters.")
    PageResponse<EndOfDayReportResponse> search(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) BusinessDayStatus status,
            @RequestParam(required = false) UUID closedBy,
            @RequestParam(required = false) String reportNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return businessDayService.searchReports(new EndOfDayReportSearchRequest(
                storeId,
                dateFrom,
                dateTo,
                status,
                closedBy,
                reportNumber,
                page,
                size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).END_OF_DAY_REPORT_VIEW)")
    @Operation(summary = "Get an immutable EOD report", description = "Requires END_OF_DAY_REPORT_VIEW. Values come from the persisted report snapshot, not live transaction recalculation.")
    EndOfDayReportResponse get(@PathVariable UUID id) {
        return businessDayService.getReport(id);
    }

    @GetMapping("/{id}/print")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).END_OF_DAY_REPORT_PRINT)")
    @Operation(summary = "Render printable EOD report", description = "Requires END_OF_DAY_REPORT_PRINT. Returns print-friendly HTML for browser printing.")
    ResponseEntity<String> print(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(businessDayService.printReport(id, authentication));
    }

    @GetMapping("/{id}/export/csv")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).END_OF_DAY_REPORT_EXPORT)")
    @Operation(summary = "Export EOD report as CSV", description = "Requires END_OF_DAY_REPORT_EXPORT. Exports values from the immutable report snapshot.")
    ResponseEntity<String> csv(@PathVariable UUID id, Authentication authentication) {
        EndOfDayReportResponse report = businessDayService.getReport(id);
        String filename = report.reportNumber() + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(businessDayService.exportCsv(id, authentication));
    }

    @GetMapping("/{id}/export/pdf")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).END_OF_DAY_REPORT_EXPORT)")
    @Operation(summary = "Export EOD report as PDF", description = "Requires END_OF_DAY_REPORT_EXPORT. Exports values from the immutable report snapshot.")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id, Authentication authentication) {
        EndOfDayReportResponse report = businessDayService.getReport(id);
        String filename = report.reportNumber() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(businessDayService.exportPdf(id, authentication));
    }
}
