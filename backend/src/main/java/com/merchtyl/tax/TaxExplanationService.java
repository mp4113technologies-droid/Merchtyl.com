package com.merchtyl.tax;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaxExplanationService {
    public List<String> summarize(TaxCalculationContext context, TaxRuleEvaluationResponse evaluation, List<TaxComponentCalculationResponse> components, BigDecimal netAmount, BigDecimal taxAmount, BigDecimal grossAmount) {
        List<String> explanations = new ArrayList<>();
        explanations.add("Calculated tax for " + context.quantity().stripTrailingZeros().toPlainString() + " unit(s) at " + context.unitPrice().stripTrailingZeros().toPlainString() + " " + context.currencyCode() + ".");
        if (context.discountAmount().signum() > 0) {
            explanations.add("Applied discount " + context.discountAmount().stripTrailingZeros().toPlainString() + " " + context.currencyCode() + " before tax.");
        }
        explanations.add(context.pricesIncludeTax() ? "Input price was treated as tax-inclusive." : "Input price was treated as tax-exclusive.");
        if (evaluation.outOfScope()) {
            explanations.add("Matched rules marked this transaction as out-of-scope; all component tax amounts are zero.");
        } else if (evaluation.exempt()) {
            explanations.add("Matched rules marked this transaction as exempt; all component tax amounts are zero.");
        } else if (evaluation.zeroRated()) {
            explanations.add("Matched rules marked this transaction as zero-rated; component rates are reported with zero tax.");
        }
        explanations.add("Rounding strategy: " + evaluation.roundingStrategy() + ".");
        explanations.add("Net " + netAmount + ", tax " + taxAmount + ", gross " + grossAmount + ".");
        if (components.isEmpty()) {
            explanations.add("No active effective tax components with rates were applicable.");
        }
        evaluation.ruleMatches().stream()
                .filter(TaxRuleMatchResponse::matched)
                .forEach(match -> explanations.add("Rule " + match.code() + " matched at priority " + match.priority() + "."));
        return explanations;
    }

    public String componentExplanation(TaxRate rate, boolean includedInPrice, BigDecimal taxableAmount, BigDecimal taxAmount) {
        String mode = includedInPrice ? "included in price" : "added to price";
        String compound = rate.isCompoundOnPreviousTax() ? " compound on previous taxes" : "";
        return rate.getTaxComponent().getCode() + " used " + rate.getPercentageRate().stripTrailingZeros().toPlainString() + "% on " + taxableAmount + ", " + mode + compound + ", producing " + taxAmount + ".";
    }
}
