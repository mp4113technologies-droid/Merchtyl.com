package com.merchtyl.email;

public record EmailProviderStatusResponse(
        EmailProvider provider,
        boolean configured,
        boolean enabled,
        boolean fromAddressConfigured
) {
}
