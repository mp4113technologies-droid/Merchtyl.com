package com.merchtyl.platform.billing;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

import static com.merchtyl.platform.billing.BillingDtos.*;

@RestController
@RequestMapping("/api/v1/platform/billing")
public class PlatformBillingController {
    private final PlatformBillingService billing;
    private final PlatformInvoicePdfService pdf;
    private final PlatformInvoiceEmailService email;

    public PlatformBillingController(PlatformBillingService billing, PlatformInvoicePdfService pdf, PlatformInvoiceEmailService email) {
        this.billing = billing;
        this.pdf = pdf;
        this.email = email;
    }

    @GetMapping("/overview")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_BILLING_VIEW)")
    Overview overview() { return billing.overview(); }

    @GetMapping("/plans")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_PRICING_VIEW)")
    Page<PlanResponse> plans(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return billing.plans(page, size); }

    @GetMapping("/plans/options")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_PRICING_VIEW)")
    List<PlanResponse> planOptions() { return billing.activePlanOptions(); }

    @PostMapping("/plans")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_PRICING_CREATE)")
    PlanResponse createPlan(@Valid @RequestBody PlanRequest request, Authentication authentication) { return billing.createPlan(request, authentication); }

    @PutMapping("/plans/{id}")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_PRICING_UPDATE)")
    PlanResponse updatePlan(@PathVariable UUID id, @Valid @RequestBody PlanRequest request, Authentication authentication) { return billing.updatePlan(id, request, authentication); }

    @GetMapping("/subscriptions/{tenantId}")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_SUBSCRIPTION_VIEW)")
    SubscriptionResponse subscription(@PathVariable UUID tenantId) { return billing.subscription(tenantId); }

    @PostMapping("/subscriptions/{tenantId}")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_SUBSCRIPTION_CREATE)")
    SubscriptionResponse assign(@PathVariable UUID tenantId, @Valid @RequestBody SubscriptionRequest request, Authentication authentication) { return billing.assignSubscription(tenantId, request, authentication); }

    @PostMapping("/subscriptions/{tenantId}/actions")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_SUBSCRIPTION_UPDATE)")
    SubscriptionResponse subscriptionAction(@PathVariable UUID tenantId, @Valid @RequestBody SubscriptionActionRequest request, Authentication authentication) { return billing.subscriptionAction(tenantId, request, authentication); }

    @GetMapping("/invoices")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_INVOICE_VIEW)")
    Page<InvoiceResponse> invoices(@RequestParam(required = false) UUID tenantId, @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) { return billing.invoices(tenantId, status, search, page, size); }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_INVOICE_VIEW)")
    InvoiceResponse invoice(@PathVariable UUID id) { return billing.invoice(id); }

    @PostMapping("/subscriptions/{tenantId}/invoices")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_INVOICE_CREATE)")
    InvoiceResponse generate(@PathVariable UUID tenantId, @RequestBody InvoiceGenerateRequest request, Authentication authentication) { return billing.generateInvoice(tenantId, request, authentication); }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_PAYMENT_RECORD)")
    InvoiceResponse payment(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request, Authentication authentication) { return billing.recordPayment(id, request, authentication); }

    @PostMapping("/invoices/{id}/send")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_INVOICE_SEND)")
    InvoiceResponse send(@PathVariable UUID id, Authentication authentication) { return email.send(id, authentication); }

    @PostMapping("/invoices/{id}/void")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_INVOICE_VOID)")
    InvoiceResponse voidInvoice(@PathVariable UUID id, @RequestParam(required = false) String reason, Authentication authentication) { return billing.voidInvoice(id, reason, authentication); }

    @GetMapping("/invoices/{id}/pdf")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_INVOICE_VIEW)")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) { return pdfResponse(id); }

    @GetMapping("/settings")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_BILLING_VIEW)")
    BillingSettingsResponse settings() { return billing.settings(); }

    @PutMapping("/settings")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).PLATFORM_BILLING_SETTINGS_MANAGE)")
    BillingSettingsResponse settings(@Valid @RequestBody BillingSettingsRequest request, Authentication authentication) { return billing.updateSettings(request, authentication); }

    private ResponseEntity<byte[]> pdfResponse(UUID id) {
        InvoiceResponse invoice = billing.invoice(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(invoice.invoiceNumber() + ".pdf").build().toString())
                .body(pdf.generate(id));
    }
}
