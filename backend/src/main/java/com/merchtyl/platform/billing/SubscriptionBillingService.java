package com.merchtyl.platform.billing;

import com.merchtyl.common.BadRequestException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class SubscriptionBillingService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public Calculation calculate(Input input) {
        requireNonNegative(input.basePrice(), "basePrice");
        requireNonNegative(input.taxRate(), "taxRate");
        List<CalculatedLine> lines = new ArrayList<>();
        lines.add(line("BASE_SUBSCRIPTION", "Monthly Subscription — " + input.planName(), BigDecimal.ONE, input.basePrice()));
        if (input.includeOnboardingFee() && value(input.onboardingFee()).signum() > 0) {
            requireNonNegative(input.onboardingFee(), "onboardingFee");
            lines.add(line("ONBOARDING_FEE", "One-time onboarding fee", BigDecimal.ONE, input.onboardingFee()));
        }
        addUsage(lines, "ADDITIONAL_STORE", "Additional stores", input.activeStores(), input.includedStores(), input.additionalStorePrice());
        input.capabilities().forEach(capability -> {
            requireNonNegative(capability.monthlyPricePerStore(), capability.capability());
            if (capability.storeCount() > 0 && value(capability.monthlyPricePerStore()).signum() > 0) {
                lines.add(line("CAPABILITY_ADD_ON", capability.description(), BigDecimal.valueOf(capability.storeCount()), capability.monthlyPricePerStore(),capability.capability(),capability.billingUnit()));
            }
        });

        BigDecimal subtotal = lines.stream().map(CalculatedLine::lineSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = discount(input.discountType(), input.discountValue(), subtotal);
        BigDecimal taxable = subtotal.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal tax = money(taxable.multiply(value(input.taxRate())));
        BigDecimal total = money(taxable.add(tax));
        return new Calculation(List.copyOf(lines), money(subtotal), money(discount), tax, total);
    }

    private static void addUsage(List<CalculatedLine> lines, String type, String description, int actual, Integer included, BigDecimal price) {
        if (price == null || included == null) return;
        requireNonNegative(price, description);
        int additional = Math.max(0, actual - included);
        if (additional > 0) lines.add(line(type, description, BigDecimal.valueOf(additional), price));
    }

    private static CalculatedLine line(String type, String description, BigDecimal quantity, BigDecimal unitPrice) {
        return line(type,description,quantity,unitPrice,null,null);
    }

    private static CalculatedLine line(String type, String description, BigDecimal quantity, BigDecimal unitPrice, String capability, BillingUnit billingUnit) {
        BigDecimal subtotal = money(quantity.multiply(unitPrice));
        return new CalculatedLine(type, description, quantity, unitPrice, subtotal,capability,billingUnit);
    }

    private static BigDecimal discount(String type, BigDecimal value, BigDecimal subtotal) {
        if (type == null || value == null) return BigDecimal.ZERO.setScale(2);
        requireNonNegative(value, "discountValue");
        return switch (type) {
            case "FIXED_AMOUNT" -> money(value.min(subtotal));
            case "PERCENTAGE" -> {
                if (value.compareTo(ONE_HUNDRED) > 0) throw new BadRequestException("Percentage discount must be between 0 and 100");
                yield money(subtotal.multiply(value).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
            }
            default -> throw new BadRequestException("Unsupported discount type");
        };
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value != null && value.signum() < 0) throw new BadRequestException(name + " must be zero or greater");
    }

    private static BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    public record Input(
            String planName, BigDecimal basePrice, Integer includedStores, Integer includedRegisters,
            Integer includedUsers, BigDecimal additionalStorePrice, BigDecimal additionalRegisterPrice,
            BigDecimal additionalUserPrice, int activeStores, int activeRegisters, int billableUsers,
            String discountType, BigDecimal discountValue, BigDecimal taxRate,
            BigDecimal onboardingFee, boolean includeOnboardingFee, List<CapabilityUsage> capabilities) {
        public Input { capabilities = capabilities == null ? List.of() : List.copyOf(capabilities); }
        public Input(String planName, BigDecimal basePrice, Integer includedStores, Integer includedRegisters,
                     Integer includedUsers, BigDecimal additionalStorePrice, BigDecimal additionalRegisterPrice,
                     BigDecimal additionalUserPrice, int activeStores, int activeRegisters, int billableUsers,
                     String discountType, BigDecimal discountValue, BigDecimal taxRate,
                     BigDecimal onboardingFee, boolean includeOnboardingFee) {
            this(planName, basePrice, includedStores, includedRegisters, includedUsers, additionalStorePrice,
                    additionalRegisterPrice, additionalUserPrice, activeStores, activeRegisters, billableUsers,
                    discountType, discountValue, taxRate, onboardingFee, includeOnboardingFee, List.of());
        }
    }
    public record CapabilityUsage(String capability, String description, int storeCount, BigDecimal monthlyPricePerStore, BillingUnit billingUnit) {
        public CapabilityUsage(String capability,String description,int storeCount,BigDecimal monthlyPricePerStore){this(capability,description,storeCount,monthlyPricePerStore,null);}
    }
    public record CalculatedLine(String lineType, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineSubtotal, String capability, BillingUnit billingUnit) {}
    public record Calculation(List<CalculatedLine> lines, BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {}
}
