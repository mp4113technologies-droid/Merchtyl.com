package com.merchtyl.payments;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CashRoundingServiceTest {
    private final CashRoundingService service = new CashRoundingService();

    @ParameterizedTest
    @CsvSource({
            "10.00,10.00,0.00", "10.01,10.00,-0.01", "10.02,10.00,-0.02",
            "10.03,10.05,0.02", "10.04,10.05,0.01", "10.05,10.05,0.00",
            "10.06,10.05,-0.01", "10.07,10.05,-0.02", "10.08,10.10,0.02",
            "10.09,10.10,0.01", "10.10,10.10,0.00"
    })
    void roundsCadToNearestNickel(String original, String rounded, String adjustment) {
        CashRoundingResult result = service.round(new BigDecimal(original), "CAD");
        assertThat(result.roundedAmount()).isEqualByComparingTo(rounded);
        assertThat(result.adjustment()).isEqualByComparingTo(adjustment);
    }

    @ParameterizedTest
    @CsvSource({"USD", "EUR"})
    void leavesOtherCurrenciesUnrounded(String currency) {
        CashRoundingResult result = service.round(new BigDecimal("10.03"), currency);
        assertThat(result.roundedAmount()).isEqualByComparingTo("10.03");
        assertThat(result.adjustment()).isEqualByComparingTo("0.00");
    }
}
