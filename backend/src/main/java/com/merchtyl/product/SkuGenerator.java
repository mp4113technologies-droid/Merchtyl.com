package com.merchtyl.product;

import com.merchtyl.catalogue.MerchantIdentifierGenerator;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class SkuGenerator {
    private static final int MAX_BASE_LENGTH = 60;

    private final MerchantIdentifierGenerator identifiers;
    private final EntityManager entityManager;

    public SkuGenerator(MerchantIdentifierGenerator identifiers, EntityManager entityManager) {
        this.identifiers = identifiers;
        this.entityManager = entityManager;
    }

    public String base(String productName, String variantName) {
        String product = readable(productName, false);
        String variant = readable(variantName, true);
        if (product.isBlank()) product = "ITEM";
        String combined = variant.isBlank() ? product : product + "-" + variant;
        return combined.substring(0, Math.min(MAX_BASE_LENGTH, combined.length())).replaceAll("-+$", "");
    }

    public String generate(UUID tenantId, String productName, String variantName) {
        String base = base(productName, variantName);
        for (int attempts = 0; attempts < 10_000; attempts++) {
            long sequence = identifiers.nextSkuSequence(tenantId, base);
            String suffix = "-%03d".formatted(sequence);
            String candidate = base.substring(0, Math.min(base.length(), 64 - suffix.length())) + suffix;
            if (!exists(tenantId, candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to allocate SKU");
    }

    /** Retained for import preview compatibility; persisted creation uses {@link #generate}. */
    public String unique(String productName, String variantName, Predicate<String> exists) {
        String base = base(productName, variantName);
        for (int suffix = 1; suffix < 10_000; suffix++) {
            String tail = "-%03d".formatted(suffix);
            String candidate = base.substring(0, Math.min(base.length(), 64 - tail.length())) + tail;
            if (!exists.test(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to allocate SKU");
    }

    public String preserveProvided(String value) {
        String supplied = value == null ? "" : value.trim();
        if (supplied.isBlank() || supplied.length() > 64) throw new IllegalArgumentException("INVALID_SKU");
        return supplied;
    }

    private boolean exists(UUID tenantId, String sku) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*) from (
                    select 1 from products where tenant_id = :tenantId and lower(sku) = lower(:sku)
                    union all
                    select 1 from product_variants where tenant_id = :tenantId and lower(sku) = lower(:sku)
                    union all
                    select 1 from tenant_sku_registry where tenant_id = :tenantId and sku_lower = lower(:sku)
                ) used
                """).setParameter("tenantId", tenantId).setParameter("sku", sku).getSingleResult();
        return count.longValue() > 0;
    }

    private static String readable(String value, boolean compactMeasurement) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("'", "")
                .replace("’", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return compactMeasurement ? normalized.replaceAll("(?<=\\d)-(?=[A-Z])", "") : normalized;
    }
}
