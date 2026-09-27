package com.merchtyl.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaxCalculatorTest {
    private final TaxGroupComponentRepository groupComponentRepository = mock(TaxGroupComponentRepository.class);
    private final TaxRateRepository taxRateRepository = mock(TaxRateRepository.class);
    private final TaxRoundingService roundingService = new TaxRoundingService();
    private final TaxExplanationService explanationService = new TaxExplanationService();
    private final TaxCalculator calculator = new TaxCalculator(groupComponentRepository, taxRateRepository, roundingService, explanationService);

    @Test
    void calculatesTaxExclusiveMultipleComponentsWithCompoundTax() {
        TaxGroup group = new TaxGroup("STANDARD", "Standard", null, true);
        TaxRate gst = rate("GST", new BigDecimal("5.000000"), false, false, 0);
        TaxRate pst = rate("PST", new BigDecimal("10.000000"), false, true, 1);
        when(groupComponentRepository.findByTaxGroupIdInAndActiveTrue(List.of(group.getId()))).thenReturn(List.of(
                new TaxGroupComponent(group, gst.getTaxComponent(), 0, true),
                new TaxGroupComponent(group, pst.getTaxComponent(), 1, true)));
        when(taxRateRepository.findActiveRatesForComponents(any(), eq(LocalDate.of(2026, 7, 22))))
                .thenReturn(List.of(gst, pst));

        TaxCalculationResponse response = calculator.calculate(context(false), evaluation(List.of(group.getId()), false, false, false, IncludedPriceBehavior.USE_RATE_SETTING, TaxRoundingStrategy.HALF_UP));

        assertThat(response.netAmount()).isEqualByComparingTo("100.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("15.50");
        assertThat(response.grossAmount()).isEqualByComparingTo("115.50");
        assertThat(response.components()).extracting(TaxComponentCalculationResponse::taxAmount)
                .containsExactly(new BigDecimal("5.00"), new BigDecimal("10.50"));
        assertThat(response.explanations()).anySatisfy(explanation -> assertThat(explanation).contains("tax-exclusive"));
    }

    @Test
    void calculatesNewfoundlandVptBeforeCompoundHstForTenDollars() {
        TaxCalculationResponse response = calculateNewfoundlandVape(new BigDecimal("10.00"));

        assertThat(response.taxAmount()).isEqualByComparingTo("3.80");
        assertThat(response.grossAmount()).isEqualByComparingTo("13.80");
        assertThat(response.components()).extracting(
                TaxComponentCalculationResponse::taxComponentCode,
                TaxComponentCalculationResponse::reportingType,
                TaxComponentCalculationResponse::taxableAmount,
                TaxComponentCalculationResponse::taxAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("CA_NL_VPT", TaxReportingType.VAPE_TAX, new BigDecimal("10.00"), new BigDecimal("2.00")),
                        org.assertj.core.groups.Tuple.tuple("CA_NL_HST", TaxReportingType.GENERAL_SALES_TAX, new BigDecimal("12.00"), new BigDecimal("1.80")));
    }

    @Test
    void calculatesNewfoundlandVptBeforeCompoundHstForOneHundredDollars() {
        TaxCalculationResponse response = calculateNewfoundlandVape(new BigDecimal("100.00"));

        assertThat(response.taxAmount()).isEqualByComparingTo("38.00");
        assertThat(response.grossAmount()).isEqualByComparingTo("138.00");
        assertThat(response.components()).extracting(TaxComponentCalculationResponse::taxAmount)
                .containsExactly(new BigDecimal("20.00"), new BigDecimal("18.00"));
    }

    @Test
    void extractsTaxInclusiveCompoundComponents() {
        TaxGroup group = new TaxGroup("STANDARD", "Standard", null, true);
        TaxRate gst = rate("GST", new BigDecimal("5.000000"), true, false, 0);
        TaxRate pst = rate("PST", new BigDecimal("10.000000"), true, true, 1);
        when(groupComponentRepository.findByTaxGroupIdInAndActiveTrue(List.of(group.getId()))).thenReturn(List.of(
                new TaxGroupComponent(group, gst.getTaxComponent(), 0, true),
                new TaxGroupComponent(group, pst.getTaxComponent(), 1, true)));
        when(taxRateRepository.findActiveRatesForComponents(any(), eq(LocalDate.of(2026, 7, 22))))
                .thenReturn(List.of(gst, pst));

        TaxCalculationResponse response = calculator.calculate(context(true, new BigDecimal("115.50")), evaluation(List.of(group.getId()), false, false, false, IncludedPriceBehavior.USE_RATE_SETTING, TaxRoundingStrategy.HALF_UP));

        assertThat(response.netAmount()).isEqualByComparingTo("100.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("15.50");
        assertThat(response.grossAmount()).isEqualByComparingTo("115.50");
        assertThat(response.components()).allSatisfy(component -> assertThat(component.includedInPrice()).isTrue());
    }

    @Test
    void zeroRatedKeepsComponentExplanationsWithZeroTax() {
        TaxRate gst = rate("GST", new BigDecimal("5.000000"), false, false, 0);
        when(taxRateRepository.findActiveRatesForComponents(any(), eq(LocalDate.of(2026, 7, 22))))
                .thenReturn(List.of(gst));

        TaxCalculationResponse response = calculator.calculate(context(false), evaluation(List.of(), true, false, false, IncludedPriceBehavior.FORCE_ADDED, TaxRoundingStrategy.HALF_UP, List.of(gst.getTaxComponent().getId())));

        assertThat(response.zeroRated()).isTrue();
        assertThat(response.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(response.grossAmount()).isEqualByComparingTo("100.00");
        assertThat(response.components()).singleElement().extracting(TaxComponentCalculationResponse::taxAmount).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void exemptAndOutOfScopeSuppressTaxAmounts() {
        TaxRate gst = rate("GST", new BigDecimal("15.000000"), false, false, 0);
        when(taxRateRepository.findActiveRatesForComponents(any(), eq(LocalDate.of(2026, 7, 22))))
                .thenReturn(List.of(gst));

        TaxCalculationResponse exempt = calculator.calculate(context(false), evaluation(List.of(), false, true, false, IncludedPriceBehavior.FORCE_ADDED, TaxRoundingStrategy.HALF_UP, List.of(gst.getTaxComponent().getId())));
        TaxCalculationResponse outOfScope = calculator.calculate(context(false), evaluation(List.of(), false, false, true, IncludedPriceBehavior.FORCE_ADDED, TaxRoundingStrategy.HALF_UP, List.of(gst.getTaxComponent().getId())));

        assertThat(exempt.exempt()).isTrue();
        assertThat(exempt.taxAmount()).isEqualByComparingTo("0.00");
        assertThat(outOfScope.outOfScope()).isTrue();
        assertThat(outOfScope.taxAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void supportsConfigurableRounding() {
        TaxRate rate = rate("ROUND", new BigDecimal("8.875000"), false, false, 0);
        when(taxRateRepository.findActiveRatesForComponents(any(), eq(LocalDate.of(2026, 7, 22))))
                .thenReturn(List.of(rate));

        TaxCalculationResponse response = calculator.calculate(context(false, new BigDecimal("1.00")), evaluation(List.of(), false, false, false, IncludedPriceBehavior.FORCE_ADDED, TaxRoundingStrategy.DOWN, List.of(rate.getTaxComponent().getId())));

        assertThat(response.taxAmount()).isEqualByComparingTo("0.08");
    }

    @Test
    void appliesDiscountBeforeCalculatingTax() {
        TaxRate rate = rate("GST", new BigDecimal("5.000000"), false, false, 0);
        when(taxRateRepository.findActiveRatesForComponents(any(), eq(LocalDate.of(2026, 7, 22))))
                .thenReturn(List.of(rate));

        TaxCalculationResponse response = calculator.calculate(
                context(false, new BigDecimal("50.00"), new BigDecimal("2"), new BigDecimal("10.00")),
                evaluation(List.of(), false, false, false, IncludedPriceBehavior.FORCE_ADDED, TaxRoundingStrategy.HALF_UP, List.of(rate.getTaxComponent().getId())));

        assertThat(response.discountAmount()).isEqualByComparingTo("10.00");
        assertThat(response.netAmount()).isEqualByComparingTo("90.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("4.50");
        assertThat(response.grossAmount()).isEqualByComparingTo("94.50");
        assertThat(response.components()).singleElement().extracting(TaxComponentCalculationResponse::taxableAmount).isEqualTo(new BigDecimal("90.00"));
        assertThat(response.explanations()).anySatisfy(explanation -> assertThat(explanation).contains("Applied discount 10"));
    }

    private static TaxCalculationContext context(boolean pricesIncludeTax) {
        return context(pricesIncludeTax, new BigDecimal("100.00"));
    }

    private static TaxCalculationContext context(boolean pricesIncludeTax, BigDecimal unitPrice) {
        return context(pricesIncludeTax, unitPrice, BigDecimal.ONE, BigDecimal.ZERO);
    }

    private static TaxCalculationContext context(boolean pricesIncludeTax, BigDecimal unitPrice, BigDecimal quantity, BigDecimal discountAmount) {
        return new TaxCalculationContext(
                UUID.fromString("00000000-0000-0000-0000-000000000601"),
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 7, 22),
                "POS",
                "CAD",
                quantity,
                unitPrice,
                discountAmount,
                pricesIncludeTax);
    }

    private static TaxRuleEvaluationResponse evaluation(List<UUID> groups, boolean zeroRated, boolean exempt, boolean outOfScope, IncludedPriceBehavior includedPriceBehavior, TaxRoundingStrategy roundingStrategy) {
        return evaluation(groups, zeroRated, exempt, outOfScope, includedPriceBehavior, roundingStrategy, List.of());
    }

    private static TaxRuleEvaluationResponse evaluation(List<UUID> groups, boolean zeroRated, boolean exempt, boolean outOfScope, IncludedPriceBehavior includedPriceBehavior, TaxRoundingStrategy roundingStrategy, List<UUID> components) {
        return new TaxRuleEvaluationResponse(groups, components, List.of(), zeroRated, exempt, outOfScope, includedPriceBehavior, roundingStrategy, List.of());
    }

    private static TaxRate rate(String code, BigDecimal percentage, boolean includedInPrice, boolean compound, int order) {
        TaxType type = new TaxType(code, code, null, true);
        Country country = new Country("CA", "Canada", true);
        TaxJurisdiction jurisdiction = new TaxJurisdiction(country, null, code, code, TaxJurisdictionType.NATIONAL, true);
        TaxComponent component = new TaxComponent(type, jurisdiction, code, code + " tax", null, true);
        return new TaxRate(new TaxRateValues(
                component,
                percentage,
                LocalDate.of(2026, 1, 1),
                null,
                includedInPrice,
                compound,
                order,
                TaxRateStatus.ACTIVE,
                null,
                null,
                null,
                null));
    }

    private TaxCalculationResponse calculateNewfoundlandVape(BigDecimal price) {
        TaxGroup group = new TaxGroup("CA_NL_VAPE_GROUP", "NL Vape", null, true);
        TaxRate vpt = rate("CA_NL_VPT", new BigDecimal("20.000000"), false, false, 1);
        vpt.getTaxComponent().setReportingType(TaxReportingType.VAPE_TAX);
        TaxRate hst = rate("CA_NL_HST", new BigDecimal("15.000000"), false, true, 2);
        when(groupComponentRepository.findByTaxGroupIdInAndActiveTrue(List.of(group.getId()))).thenReturn(List.of(
                new TaxGroupComponent(group, vpt.getTaxComponent(), 1, true),
                new TaxGroupComponent(group, hst.getTaxComponent(), 2, true)));
        when(taxRateRepository.findActiveRatesForComponents(any(), eq(LocalDate.of(2026, 7, 22))))
                .thenReturn(List.of(vpt, hst));
        return calculator.calculate(context(false, price), evaluation(List.of(group.getId()), false, false, false,
                IncludedPriceBehavior.USE_RATE_SETTING, TaxRoundingStrategy.HALF_UP));
    }
}
