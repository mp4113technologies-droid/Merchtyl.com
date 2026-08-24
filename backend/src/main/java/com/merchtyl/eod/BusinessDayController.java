package com.merchtyl.eod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.idempotency.IdempotencyOperationResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/business-days")
@Tag(name = "Business Day", description = "Business-day opening, closing validation, immutable EOD report generation, force-close, and reopen.")
public class BusinessDayController {
    private static final String START_CLOSING_ENDPOINT = "POST /api/v1/business-days/{id}/start-closing";
    private static final String CLOSE_ENDPOINT = "POST /api/v1/business-days/{id}/close";
    private static final String FORCE_CLOSE_ENDPOINT = "POST /api/v1/business-days/{id}/force-close";
    private static final String REOPEN_ENDPOINT = "POST /api/v1/business-days/{id}/reopen";

    private final BusinessDayService businessDayService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public BusinessDayController(
            BusinessDayService businessDayService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper) {
        this.businessDayService = businessDayService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/open")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).BUSINESS_DAY_OPEN)")
    @Operation(summary = "Open a business day", description = "Requires BUSINESS_DAY_OPEN. Blocks duplicate active days unless an authorized override is supplied.")
    @ApiResponse(responseCode = "200", description = "Business day opened.")
    @ApiResponse(responseCode = "409", description = "A previous business day remains active.")
    BusinessDayResponse open(@Valid @RequestBody BusinessDayOpenRequest request, Authentication authentication) {
        return businessDayService.open(request, authentication);
    }

    @GetMapping("/current")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).BUSINESS_DAY_VIEW)")
    @Operation(summary = "Get current business day", description = "Requires BUSINESS_DAY_VIEW. Returns 204 when the store has no active business day.")
    ResponseEntity<BusinessDayResponse> current(@RequestParam UUID storeId) {
        BusinessDayResponse response = businessDayService.current(storeId);
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).BUSINESS_DAY_VIEW)")
    @Operation(summary = "Search business days", description = "Requires BUSINESS_DAY_VIEW. Supports store, date range, status, and page/size filters.")
    PageResponse<BusinessDayResponse> search(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) BusinessDayStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return businessDayService.search(storeId, dateFrom, dateTo, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).BUSINESS_DAY_VIEW)")
    BusinessDayResponse get(@PathVariable UUID id) {
        return businessDayService.get(id);
    }

    @GetMapping("/{id}/validation")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).BUSINESS_DAY_VIEW)")
    @Operation(summary = "Validate closing readiness", description = "Requires BUSINESS_DAY_VIEW. Returns all closing blockers, not only the first blocker.")
    ClosingValidationResponse validation(@PathVariable UUID id) {
        return businessDayService.validateClosing(id);
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).BUSINESS_DAY_CLOSE)")
    @Operation(summary = "Preview EOD close totals", description = "Requires BUSINESS_DAY_CLOSE. Calculates close totals without mutating the business day or creating a report.")
    EndOfDayClosingPreviewResponse preview(@PathVariable UUID id) {
        return businessDayService.previewClosing(id);
    }

    @GetMapping("/closing-reminder")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).BUSINESS_DAY_VIEW)")
    ClosingReminderResponse closingReminder(@RequestParam UUID storeId) {
        return businessDayService.closingReminder(storeId);
    }

    @PostMapping("/{id}/start-closing")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).BUSINESS_DAY_CLOSE)")
    @Operation(summary = "Start business-day closing", description = "Requires BUSINESS_DAY_CLOSE and Idempotency-Key. Moves an open day to CLOSING atomically.")
    @ApiResponse(responseCode = "200", description = "Closing started or duplicate idempotent response returned.")
    ResponseEntity<String> startClosing(
            @PathVariable UUID id,
            @Parameter(description = "Required idempotency key for retry-safe closing start.", required = true)
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody(required = false) String requestBody,
            Authentication authentication) {
        return idempotent(authentication, START_CLOSING_ENDPOINT, idempotencyKey, requestBody,
                () -> businessDayService.startClosing(id, authentication));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).BUSINESS_DAY_CLOSE)")
    @Operation(summary = "Close a business day", description = "Requires BUSINESS_DAY_CLOSE and Idempotency-Key. Generates an immutable EOD report and sign-off in the same transaction where practical.")
    @ApiResponse(responseCode = "200", description = "Business day closed and report generated, or duplicate idempotent response returned.")
    @ApiResponse(responseCode = "409", description = "Stale version or closing conflict.")
    ResponseEntity<String> close(
            @PathVariable UUID id,
            @Parameter(description = "Required idempotency key for retry-safe business-day closing.", required = true)
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody String requestBody,
            Authentication authentication) {
        BusinessDayCloseRequest request = read(requestBody, BusinessDayCloseRequest.class);
        return idempotent(authentication, CLOSE_ENDPOINT, idempotencyKey, requestBody,
                () -> businessDayService.close(id, request, authentication));
    }

    @PostMapping("/{id}/force-close")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).BUSINESS_DAY_FORCE_CLOSE)")
    @Operation(summary = "Force-close a business day", description = "Requires BUSINESS_DAY_FORCE_CLOSE, Idempotency-Key, and a reason. Captures force-close indicators in the report.")
    ResponseEntity<String> forceClose(
            @PathVariable UUID id,
            @Parameter(description = "Required idempotency key for retry-safe force-close.", required = true)
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody String requestBody,
            Authentication authentication) {
        BusinessDayForceCloseRequest request = read(requestBody, BusinessDayForceCloseRequest.class);
        return idempotent(authentication, FORCE_CLOSE_ENDPOINT, idempotencyKey, requestBody,
                () -> businessDayService.forceClose(id, request, authentication));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("@authorizationService.hasPermission(authentication, T(com.merchtyl.security.PermissionCode).BUSINESS_DAY_REOPEN)")
    @Operation(summary = "Reopen a closed business day", description = "Requires BUSINESS_DAY_REOPEN, Idempotency-Key, version, and reason. Existing reports are preserved; reclose creates a new revision.")
    ResponseEntity<String> reopen(
            @PathVariable UUID id,
            @Parameter(description = "Required idempotency key for retry-safe business-day reopen.", required = true)
            @RequestHeader(IdempotencyService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody String requestBody,
            Authentication authentication) {
        BusinessDayReopenRequest request = read(requestBody, BusinessDayReopenRequest.class);
        return idempotent(authentication, REOPEN_ENDPOINT, idempotencyKey, requestBody,
                () -> businessDayService.reopen(id, request, authentication));
    }

    private ResponseEntity<String> idempotent(
            Authentication authentication,
            String endpoint,
            String idempotencyKey,
            String requestBody,
            Supplier<Object> operation) {
        IdempotencyResult result = idempotencyService.execute(
                businessDayService.currentUserId(authentication),
                endpoint,
                idempotencyKey,
                requestBody == null ? "" : requestBody,
                () -> new IdempotencyOperationResponse(
                        200,
                        MediaType.APPLICATION_JSON_VALUE,
                        write(operation.get())));
        return ResponseEntity.status(result.status())
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.body());
    }

    private <T> T read(String requestBody, Class<T> type) {
        try {
            return objectMapper.readValue(requestBody, type);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Request body is invalid JSON");
        }
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Response body must be JSON serializable", exception);
        }
    }
}
