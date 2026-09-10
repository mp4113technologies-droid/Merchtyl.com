package com.merchtyl.product;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

@Service
public class SkuGenerator {
    private static final int MAX_BASE_LENGTH = 16;

    public String base(String productName, String variantName) {
        String product = alphanumeric(productName);
        String variant = alphanumeric(variantName);
        if (product.isBlank()) product = "ITEM";
        if (variant.isBlank()) variant = "VAR";
        String productCode = product.length() <= 3 ? product : product.substring(0, 3);
        String combined = productCode + variant;
        return combined.substring(0, Math.min(MAX_BASE_LENGTH, combined.length()));
    }

    public String unique(String productName, String variantName, Predicate<String> exists) {
        String base = base(productName, variantName);
        if (!exists.test(base)) return base;
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String tail = "-" + suffix;
            String candidate = base.substring(0, Math.min(base.length(), MAX_BASE_LENGTH - tail.length())) + tail;
            if (!exists.test(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to allocate SKU");
    }

    public String normalizeProvided(String value) {
        String normalized = alphanumericWithHyphen(value);
        if (normalized.isBlank() || normalized.length() > 64) throw new IllegalArgumentException("INVALID_SKU");
        return normalized;
    }

    private static String alphanumeric(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String alphanumericWithHyphen(String value) {
        return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9-]", "").replaceAll("-+", "-").replaceAll("(^-|-$)", "");
    }
}
