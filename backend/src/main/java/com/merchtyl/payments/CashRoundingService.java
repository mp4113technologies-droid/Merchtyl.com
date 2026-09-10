package com.merchtyl.payments;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Service
public class CashRoundingService {
    private static final BigDecimal CAD_INCREMENT = new BigDecimal("0.05");

    public CashRoundingResult round(BigDecimal amount, String currencyCode) {
        BigDecimal original = money(amount);
        if (!"CAD".equals(normalizeCurrency(currencyCode))) {
            return new CashRoundingResult(original, original, money(BigDecimal.ZERO));
        }
        BigDecimal rounded = original.divide(CAD_INCREMENT, 0, RoundingMode.HALF_UP).multiply(CAD_INCREMENT);
        return new CashRoundingResult(original, money(rounded), money(rounded.subtract(original)));
    }

    private static String normalizeCurrency(String currencyCode) {
        return currencyCode == null ? "" : currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("amount is required");
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
