package com.merchtyl.tax;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class TaxRoundingService {
    public BigDecimal roundCurrency(BigDecimal amount, TaxRoundingStrategy strategy) {
        return amount.setScale(2, roundingMode(strategy));
    }

    public BigDecimal divide(BigDecimal amount, BigDecimal divisor) {
        return amount.divide(divisor, 12, RoundingMode.HALF_UP);
    }

    private RoundingMode roundingMode(TaxRoundingStrategy strategy) {
        return switch (strategy) {
            case HALF_UP -> RoundingMode.HALF_UP;
            case HALF_EVEN -> RoundingMode.HALF_EVEN;
            case DOWN -> RoundingMode.DOWN;
            case UP -> RoundingMode.UP;
        };
    }
}
