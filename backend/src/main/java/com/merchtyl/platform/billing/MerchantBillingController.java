package com.merchtyl.platform.billing;

import org.springframework.http.ContentDisposition;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@PreAuthorize("@authorizationService.hasTenantPermission(authentication, T(com.merchtyl.security.PermissionCode).MERCHANT_BILLING_VIEW)")
public class MerchantBillingController {
    private final PlatformBillingService billing;
    private final PlatformInvoicePdfService pdf;

    public MerchantBillingController(PlatformBillingService billing, PlatformInvoicePdfService pdf) {
        this.billing = billing;
        this.pdf = pdf;
    }

    @GetMapping("/subscription")
    BillingDtos.SubscriptionResponse subscription(Authentication authentication) { return billing.subscription(billing.tenantFor(authentication)); }

    @GetMapping("/subscription/preview")
    BillingDtos.PricingPreview preview(Authentication authentication,@RequestParam(defaultValue="0") int additionalStores,@RequestParam(defaultValue="false") boolean foodService){return billing.subscriptionPreview(billing.tenantFor(authentication),additionalStores,foodService);}

    @GetMapping("/invoices")
    BillingDtos.Page<BillingDtos.InvoiceResponse> invoices(Authentication authentication,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return billing.invoices(billing.tenantFor(authentication), null, null, page, size);
    }

    @GetMapping("/invoices/{id}")
    BillingDtos.InvoiceResponse invoice(@PathVariable UUID id, Authentication authentication) {
        BillingDtos.InvoiceResponse invoice = billing.invoice(id);
        requireTenant(invoice, authentication);
        return invoice;
    }

    @GetMapping("/invoices/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id, Authentication authentication) {
        BillingDtos.InvoiceResponse invoice = billing.invoice(id);
        requireTenant(invoice, authentication);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(invoice.invoiceNumber() + ".pdf").build().toString())
                .body(pdf.generate(id));
    }

    private void requireTenant(BillingDtos.InvoiceResponse invoice, Authentication authentication) {
        if (!invoice.tenantId().equals(billing.tenantFor(authentication))) throw new org.springframework.security.access.AccessDeniedException("Invoice belongs to another merchant");
    }
}
