package com.merchtyl.tax;

import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaxEngineTest {
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final TaxCategoryRepository taxCategoryRepository = mock(TaxCategoryRepository.class);
    private final PlaceOfSupplyResolver placeOfSupplyResolver = mock(PlaceOfSupplyResolver.class);
    private final TaxRuleEvaluator taxRuleEvaluator = mock(TaxRuleEvaluator.class);
    private final TaxCalculator taxCalculator = mock(TaxCalculator.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TaxEngine taxEngine = new TaxEngine(
            storeRepository,
            productRepository,
            taxCategoryRepository,
            placeOfSupplyResolver,
            taxRuleEvaluator,
            taxCalculator,
            userRepository,
            auditService);

    @Test
    void calculateInfersContextFromStoreAndProductThenAudits() {
        UUID storeId = UUID.fromString("00000000-0000-0000-0000-000000000601");
        UUID productTaxCategoryId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        UUID storeJurisdictionId = UUID.fromString("00000000-0000-0000-0000-000000000801");
        UUID supplyJurisdictionId = UUID.fromString("00000000-0000-0000-0000-000000000802");
        Store store = mock(Store.class);
        Product product = product(productTaxCategoryId);
        TaxRuleEvaluationResponse evaluation = new TaxRuleEvaluationResponse(List.of(), List.of(), List.of(), false, false, false, IncludedPriceBehavior.USE_RATE_SETTING, TaxRoundingStrategy.HALF_UP, List.of());
        TaxCalculationResponse calculated = response(storeId, product.getId(), productTaxCategoryId, storeJurisdictionId, supplyJurisdictionId);
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));
        when(store.isPricesIncludeTax()).thenReturn(true);
        when(store.getCurrencyCode()).thenReturn("CAD");
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(taxCategoryRepository.existsById(productTaxCategoryId)).thenReturn(true);
        when(placeOfSupplyResolver.resolveStoreJurisdiction(store, null)).thenReturn(storeJurisdictionId);
        when(placeOfSupplyResolver.resolveSupplyJurisdiction(store, null)).thenReturn(supplyJurisdictionId);
        when(taxRuleEvaluator.evaluate(any())).thenReturn(evaluation);
        when(taxCalculator.calculate(any(), any())).thenReturn(calculated);

        TaxCalculationResponse response = taxEngine.calculate(new TaxCalculationRequest(
                storeId,
                null,
                null,
                product.getId(),
                null,
                false,
                LocalDate.of(2026, 7, 22),
                "POS",
                new BigDecimal("10.00"),
                new BigDecimal("2"),
                new BigDecimal("3.00"),
                null,
                null), null);

        ArgumentCaptor<TaxRuleEvaluationRequest> evaluationCaptor = ArgumentCaptor.forClass(TaxRuleEvaluationRequest.class);
        ArgumentCaptor<TaxCalculationContext> contextCaptor = ArgumentCaptor.forClass(TaxCalculationContext.class);
        verify(taxRuleEvaluator).evaluate(evaluationCaptor.capture());
        verify(taxCalculator).calculate(contextCaptor.capture(), any());
        verify(auditService).record(any(CreateAuditRecordCommand.class));
        assertThat(response).isEqualTo(calculated);
        assertThat(evaluationCaptor.getValue().productTaxCategoryId()).isEqualTo(productTaxCategoryId);
        assertThat(evaluationCaptor.getValue().storeJurisdictionId()).isEqualTo(storeJurisdictionId);
        assertThat(evaluationCaptor.getValue().supplyJurisdictionId()).isEqualTo(supplyJurisdictionId);
        assertThat(contextCaptor.getValue().pricesIncludeTax()).isTrue();
        assertThat(contextCaptor.getValue().currencyCode()).isEqualTo("CAD");
        assertThat(contextCaptor.getValue().discountAmount()).isEqualByComparingTo("3.00");
    }

    private static Product product(UUID taxCategoryId) {
        return new Product(new ProductValues(
                "SKU-1",
                "Coffee",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                BigDecimal.ONE,
                BigDecimal.TEN,
                null,
                null,
                true,
                true,
                false,
                null,
                taxCategoryId,
                List.of(),
                List.of(),
                Set.of()));
    }

    private static TaxCalculationResponse response(UUID storeId, UUID productId, UUID productTaxCategoryId, UUID storeJurisdictionId, UUID supplyJurisdictionId) {
        return new TaxCalculationResponse(
                storeId,
                storeJurisdictionId,
                supplyJurisdictionId,
                productId,
                productTaxCategoryId,
                LocalDate.of(2026, 7, 22),
                "POS",
                "CAD",
                new BigDecimal("2"),
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                true,
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                new BigDecimal("20.00"),
                false,
                false,
                false,
                IncludedPriceBehavior.USE_RATE_SETTING,
                TaxRoundingStrategy.HALF_UP,
                List.of(),
                List.of("Calculated tax."),
                new TaxRuleEvaluationResponse(List.of(), List.of(), List.of(), false, false, false, IncludedPriceBehavior.USE_RATE_SETTING, TaxRoundingStrategy.HALF_UP, List.of()));
    }
}
