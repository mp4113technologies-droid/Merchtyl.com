package com.merchtyl.tax;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class TaxEngine {
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final TaxCategoryRepository taxCategoryRepository;
    private final PlaceOfSupplyResolver placeOfSupplyResolver;
    private final TaxRuleEvaluator taxRuleEvaluator;
    private final TaxCalculator taxCalculator;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TaxEngine(
            StoreRepository storeRepository,
            ProductRepository productRepository,
            TaxCategoryRepository taxCategoryRepository,
            PlaceOfSupplyResolver placeOfSupplyResolver,
            TaxRuleEvaluator taxRuleEvaluator,
            TaxCalculator taxCalculator,
            UserRepository userRepository,
            AuditService auditService) {
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.taxCategoryRepository = taxCategoryRepository;
        this.placeOfSupplyResolver = placeOfSupplyResolver;
        this.taxRuleEvaluator = taxRuleEvaluator;
        this.taxCalculator = taxCalculator;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public TaxCalculationResponse calculate(TaxCalculationRequest request, Authentication authentication) {
        LocalDate transactionDate = request.transactionDate();
        if (transactionDate == null) {
            throw new BadRequestException("transactionDate is required");
        }
        if (request.unitPrice() == null || request.unitPrice().signum() < 0) {
            throw new BadRequestException("unitPrice must be zero or greater");
        }
        if (request.quantity() == null || request.quantity().signum() <= 0) {
            throw new BadRequestException("quantity must be greater than zero");
        }
        BigDecimal discountAmount = request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount();
        if (discountAmount.signum() < 0) {
            throw new BadRequestException("discountAmount must be zero or greater");
        }
        BigDecimal lineSubtotal = request.unitPrice().multiply(request.quantity());
        if (discountAmount.compareTo(lineSubtotal) > 0) {
            throw new BadRequestException("discountAmount cannot exceed the line subtotal");
        }

        Store store = request.storeId() == null ? null : storeRepository.findById(request.storeId())
                .orElseThrow(() -> new NotFoundException("Store not found"));
        Product product = request.productId() == null ? null : productRepository.findById(request.productId())
                .orElseThrow(() -> new NotFoundException("Product not found"));
        UUID productTaxCategoryId = request.productTaxCategoryId() != null
                ? request.productTaxCategoryId()
                : product == null ? null : product.getTaxCategoryId();
        if (productTaxCategoryId != null && !taxCategoryRepository.existsById(productTaxCategoryId)) {
            throw new NotFoundException("Tax category not found");
        }

        UUID storeJurisdictionId = placeOfSupplyResolver.resolveStoreJurisdiction(store, request.storeJurisdictionId());
        UUID supplyJurisdictionId = placeOfSupplyResolver.resolveSupplyJurisdiction(store, request.supplyJurisdictionId());
        boolean pricesIncludeTax = request.pricesIncludeTax() != null
                ? request.pricesIncludeTax()
                : store != null && store.isPricesIncludeTax();
        String currencyCode = request.currencyCode() == null || request.currencyCode().isBlank()
                ? store == null ? "USD" : store.getCurrencyCode()
                : request.currencyCode().trim().toUpperCase(Locale.ROOT);

        TaxRuleEvaluationRequest evaluationRequest = new TaxRuleEvaluationRequest(
                storeJurisdictionId,
                supplyJurisdictionId,
                productTaxCategoryId,
                request.productId(),
                request.customerExempt(),
                transactionDate,
                request.saleChannel());
        TaxRuleEvaluationResponse evaluation = taxRuleEvaluator.evaluate(evaluationRequest);
        TaxCalculationContext context = new TaxCalculationContext(
                request.storeId(),
                storeJurisdictionId,
                supplyJurisdictionId,
                request.productId(),
                productTaxCategoryId,
                transactionDate,
                request.saleChannel(),
                currencyCode,
                request.quantity(),
                request.unitPrice(),
                discountAmount,
                pricesIncludeTax);
        TaxCalculationResponse response = taxCalculator.calculate(context, evaluation);
        TaxGeographySupport.audit(authentication, userRepository, auditService, AuditAction.TAX_CALCULATION_PERFORMED, "TAX_CALCULATION", null, request, response);
        return response;
    }
}
