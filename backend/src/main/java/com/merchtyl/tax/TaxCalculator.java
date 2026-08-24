package com.merchtyl.tax;

import com.merchtyl.common.BadRequestException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaxCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final TaxGroupComponentRepository taxGroupComponentRepository;
    private final TaxRateRepository taxRateRepository;
    private final TaxRoundingService roundingService;
    private final TaxExplanationService explanationService;

    public TaxCalculator(
            TaxGroupComponentRepository taxGroupComponentRepository,
            TaxRateRepository taxRateRepository,
            TaxRoundingService roundingService,
            TaxExplanationService explanationService) {
        this.taxGroupComponentRepository = taxGroupComponentRepository;
        this.taxRateRepository = taxRateRepository;
        this.roundingService = roundingService;
        this.explanationService = explanationService;
    }

    public TaxCalculationResponse calculate(TaxCalculationContext context, TaxRuleEvaluationResponse evaluation) {
        if (context.unitPrice().signum() < 0) {
            throw new BadRequestException("unitPrice must be zero or greater");
        }
        if (context.quantity().signum() <= 0) {
            throw new BadRequestException("quantity must be greater than zero");
        }
        if (context.discountAmount().signum() < 0) {
            throw new BadRequestException("discountAmount must be zero or greater");
        }
        if (context.discountAmount().compareTo(context.lineSubtotal()) > 0) {
            throw new BadRequestException("discountAmount cannot exceed the line subtotal");
        }

        List<TaxRate> rates = rates(context, evaluation);
        BigDecimal grossInput = roundingService.roundCurrency(context.lineAmount(), evaluation.roundingStrategy());
        BigDecimal netAmount = extractNetAmount(context.lineAmount(), rates, evaluation);
        BigDecimal previousTaxes = BigDecimal.ZERO;
        BigDecimal includedTax = BigDecimal.ZERO;
        BigDecimal addedTax = BigDecimal.ZERO;
        List<TaxComponentCalculationResponse> components = new ArrayList<>();

        for (TaxRate rate : rates) {
            boolean included = includedInPrice(rate, evaluation);
            BigDecimal taxableAmount = rate.isCompoundOnPreviousTax() ? netAmount.add(previousTaxes) : netAmount;
            BigDecimal taxAmount = zeroTax(evaluation) ? BigDecimal.ZERO : taxableAmount.multiply(rate.getPercentageRate()).divide(ONE_HUNDRED);
            taxAmount = roundingService.roundCurrency(taxAmount, evaluation.roundingStrategy());
            previousTaxes = previousTaxes.add(taxAmount);
            if (included) {
                includedTax = includedTax.add(taxAmount);
            } else {
                addedTax = addedTax.add(taxAmount);
            }
            components.add(new TaxComponentCalculationResponse(
                    rate.getTaxComponent().getId(),
                    rate.getTaxComponent().getCode(),
                    rate.getTaxComponent().getName(),
                    rate.getId(),
                    rate.getPercentageRate(),
                    roundingService.roundCurrency(taxableAmount, evaluation.roundingStrategy()),
                    taxAmount,
                    included,
                    rate.isCompoundOnPreviousTax(),
                    rate.getCalculationOrder(),
                    rate.getEffectiveFrom(),
                    rate.getEffectiveTo(),
                    explanationService.componentExplanation(rate, included, roundingService.roundCurrency(taxableAmount, evaluation.roundingStrategy()), taxAmount)));
        }

        BigDecimal taxAmount = includedTax.add(addedTax);
        BigDecimal grossAmount = context.pricesIncludeTax() || hasIncludedRates(rates, evaluation)
                ? grossInput.add(addedTax)
                : netAmount.add(taxAmount);
        netAmount = roundingService.roundCurrency(netAmount, evaluation.roundingStrategy());
        taxAmount = roundingService.roundCurrency(taxAmount, evaluation.roundingStrategy());
        grossAmount = roundingService.roundCurrency(grossAmount, evaluation.roundingStrategy());
        return new TaxCalculationResponse(
                context.storeId(),
                context.storeJurisdictionId(),
                context.supplyJurisdictionId(),
                context.productId(),
                context.productTaxCategoryId(),
                context.transactionDate(),
                context.saleChannel(),
                context.currencyCode(),
                context.quantity(),
                context.unitPrice(),
                context.discountAmount(),
                context.pricesIncludeTax(),
                netAmount,
                taxAmount,
                grossAmount,
                evaluation.zeroRated(),
                evaluation.exempt(),
                evaluation.outOfScope(),
                evaluation.includedPriceBehavior(),
                evaluation.roundingStrategy(),
                components,
                explanationService.summarize(context, evaluation, components, netAmount, taxAmount, grossAmount),
                evaluation);
    }

    private List<TaxRate> rates(TaxCalculationContext context, TaxRuleEvaluationResponse evaluation) {
        LinkedHashSet<UUID> componentIds = new LinkedHashSet<>();
        if (!evaluation.appliedTaxGroupIds().isEmpty()) {
            taxGroupComponentRepository.findByTaxGroupIdInAndActiveTrue(evaluation.appliedTaxGroupIds()).stream()
                    .sorted(Comparator.comparing(TaxGroupComponent::getCalculationOrder)
                            .thenComparing(component -> component.getTaxComponent().getCode())
                            .thenComparing(component -> component.getTaxComponent().getId()))
                    .forEach(groupComponent -> componentIds.add(groupComponent.getTaxComponent().getId()));
        }
        componentIds.addAll(evaluation.appliedTaxComponentIds());
        componentIds.removeAll(evaluation.excludedTaxComponentIds());
        if (componentIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, TaxRate> ratesByComponent = new LinkedHashMap<>();
        taxRateRepository.findActiveRatesForComponents(componentIds, context.transactionDate())
                .forEach(rate -> ratesByComponent.putIfAbsent(rate.getTaxComponent().getId(), rate));
        return ratesByComponent.values().stream()
                .sorted(Comparator.comparing(TaxRate::getCalculationOrder)
                        .thenComparing(rate -> rate.getTaxComponent().getCode())
                        .thenComparing(TaxRate::getId))
                .toList();
    }

    private BigDecimal extractNetAmount(BigDecimal lineAmount, List<TaxRate> rates, TaxRuleEvaluationResponse evaluation) {
        if (!hasIncludedRates(rates, evaluation) || zeroTax(evaluation)) {
            return lineAmount;
        }
        BigDecimal factor = BigDecimal.ONE;
        for (TaxRate rate : rates) {
            if (!includedInPrice(rate, evaluation)) {
                continue;
            }
            BigDecimal rateFactor = rate.getPercentageRate().divide(ONE_HUNDRED);
            BigDecimal taxFactor = rate.isCompoundOnPreviousTax() ? factor.multiply(rateFactor) : rateFactor;
            factor = factor.add(taxFactor);
        }
        return roundingService.divide(lineAmount, factor);
    }

    private boolean hasIncludedRates(List<TaxRate> rates, TaxRuleEvaluationResponse evaluation) {
        return rates.stream().anyMatch(rate -> includedInPrice(rate, evaluation));
    }

    private boolean includedInPrice(TaxRate rate, TaxRuleEvaluationResponse evaluation) {
        return switch (evaluation.includedPriceBehavior()) {
            case USE_RATE_SETTING -> rate.isIncludedInPrice();
            case FORCE_INCLUDED -> true;
            case FORCE_ADDED -> false;
        };
    }

    private boolean zeroTax(TaxRuleEvaluationResponse evaluation) {
        return evaluation.zeroRated() || evaluation.exempt() || evaluation.outOfScope();
    }
}
