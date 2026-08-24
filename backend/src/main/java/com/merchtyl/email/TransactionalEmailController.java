package com.merchtyl.email;

import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.platform.admin.PlatformUserRepository;
import com.merchtyl.security.PermissionCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
@Tag(name = "Transactional Email", description = "Platform-scoped transactional email delivery status and retry operations.")
public class TransactionalEmailController {
    private final EmailDeliveryService emailDeliveryService;
    private final PlatformUserRepository platformUserRepository;

    public TransactionalEmailController(EmailDeliveryService emailDeliveryService, PlatformUserRepository platformUserRepository) {
        this.emailDeliveryService = emailDeliveryService;
        this.platformUserRepository = platformUserRepository;
    }

    @GetMapping("/tenants/{tenantId}/email-deliveries")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).EMAIL_DELIVERY_VIEW)")
    @Operation(summary = "List transactional email deliveries for a merchant tenant")
    List<EmailDeliveryResponse> listTenantDeliveries(@PathVariable UUID tenantId) {
        return emailDeliveryService.listTenantDeliveries(tenantId);
    }

    @GetMapping("/email-deliveries/{deliveryId}")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).EMAIL_DELIVERY_VIEW)")
    @Operation(summary = "Get transactional email delivery details")
    EmailDeliveryResponse getDelivery(@PathVariable UUID deliveryId) {
        return emailDeliveryService.getDelivery(deliveryId);
    }

    @PostMapping("/email-deliveries/{deliveryId}/retry")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).EMAIL_DELIVERY_RETRY)")
    @Operation(summary = "Retry a failed or retry-scheduled transactional email")
    EmailDeliveryResponse retryDelivery(@PathVariable UUID deliveryId, Authentication authentication) {
        return emailDeliveryService.retryDelivery(deliveryId, actorId(authentication));
    }

    @GetMapping("/email/status")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).EMAIL_DELIVERY_VIEW)")
    @Operation(summary = "Get sanitized transactional email provider status")
    EmailProviderStatusResponse providerStatus() {
        return emailDeliveryService.providerStatus();
    }

    @PostMapping("/email/test")
    @PreAuthorize("@authorizationService.hasPlatformPermission(authentication, T(com.merchtyl.security.PermissionCode).EMAIL_DELIVERY_RETRY)")
    @Operation(summary = "Send a platform transactional email configuration test")
    EmailDeliveryResponse sendTestEmail(@Valid @RequestBody TestEmailRequest request, Authentication authentication) {
        return emailDeliveryService.sendTestEmail(request.recipient(), actorId(authentication));
    }

    private UUID actorId(Authentication authentication) {
        return platformUserRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ForbiddenOperationException("Platform user not found"))
                .id();
    }
}
