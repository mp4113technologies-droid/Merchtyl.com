package com.merchtyl.product;

import com.merchtyl.common.BadRequestException;

public final class BarcodeNormalizer {
    public static final int MAX_LENGTH = 128;

    private BarcodeNormalizer() {}

    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) throw new BadRequestException("BARCODE_REQUIRED");
        String normalized = value.trim();
        if (normalized.length() > MAX_LENGTH || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new BadRequestException("BARCODE_INVALID");
        }
        return normalized;
    }
}
