package com.merchtyl.reports;

import com.merchtyl.catalogue.Category;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductValues;
import com.merchtyl.product.SellableType;
import com.merchtyl.refunds.Refund;
import com.merchtyl.refunds.RefundPayment;
import com.merchtyl.refunds.RefundRepository;
import com.merchtyl.returns.Return;
import com.merchtyl.returns.ReturnItem;
import com.merchtyl.sales.Payment;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.SaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final UUID STORE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID REGISTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID CASHIER_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000104");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000105");

    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final RefundRepository refundRepository = mock(RefundRepository.class);
    private final SalesReportService service = new SalesReportService(
            saleRepository,
            refundRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void summarizesSalesRefundsTaxesAndPayments() {
        Sale sale = sale();
        Refund refund = refund();
        when(saleRepository.findAll(any(Specification.class))).thenReturn(List.of(sale));
        when(refundRepository.findAll(any(Specification.class))).thenReturn(List.of(refund));

        SalesReportResponse response = service.summarize(new SalesReportRequest(
                STORE_ID,
                REGISTER_ID,
                CASHIER_ID,
                CATEGORY_ID,
                PRODUCT_ID,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31")));

        assertThat(response.grossSales()).isEqualByComparingTo("100.00");
        assertThat(response.discounts()).isEqualByComparingTo("10.00");
        assertThat(response.refunds()).isEqualByComparingTo("22.00");
        assertThat(response.taxes()).isEqualByComparingTo("6.00");
        assertThat(response.netSales()).isEqualByComparingTo("70.00");
        assertThat(response.payments()).isEqualByComparingTo("74.00");
        assertThat(response.saleCount()).isEqualTo(1);
        assertThat(response.refundCount()).isEqualTo(1);
        assertThat(response.paymentBreakdown()).singleElement().satisfies(payment -> {
            assertThat(payment.method()).isEqualTo(PaymentMethod.CASH);
            assertThat(payment.collected()).isEqualByComparingTo("96.00");
            assertThat(payment.refunded()).isEqualByComparingTo("22.00");
            assertThat(payment.net()).isEqualByComparingTo("74.00");
        });
        assertThat(response.generatedAt()).isEqualTo(NOW);
    }

    private static Sale sale() {
        Sale sale = mock(Sale.class);
        SaleItem matchingItem = saleItem(PRODUCT_ID, CATEGORY_ID, "100.00", "10.00", "8.00", "96.00");
        SaleItem otherItem = saleItem(
                UUID.fromString("00000000-0000-0000-0000-000000000205"),
                UUID.fromString("00000000-0000-0000-0000-000000000204"),
                "50.00",
                "0.00",
                "4.00",
                "54.00");
        Payment cashPayment = mock(Payment.class);
        when(cashPayment.getMethod()).thenReturn(PaymentMethod.CASH);
        when(cashPayment.getAmount()).thenReturn(new BigDecimal("150.00"));
        when(sale.getItems()).thenReturn(List.of(matchingItem, otherItem));
        when(sale.getPayments()).thenReturn(List.of(cashPayment));
        when(sale.getTotalAmount()).thenReturn(new BigDecimal("150.00"));
        return sale;
    }

    private static Refund refund() {
        Refund refund = mock(Refund.class);
        Return returnRecord = mock(Return.class);
        ReturnItem matchingItem = returnItem(PRODUCT_ID, CATEGORY_ID, "20.00", "2.00", "22.00");
        ReturnItem otherItem = returnItem(
                UUID.fromString("00000000-0000-0000-0000-000000000205"),
                UUID.fromString("00000000-0000-0000-0000-000000000204"),
                "5.00",
                "0.50",
                "5.50");
        RefundPayment cashRefund = mock(RefundPayment.class);
        when(cashRefund.getMethod()).thenReturn(PaymentMethod.CASH);
        when(cashRefund.getAmount()).thenReturn(new BigDecimal("27.00"));
        when(returnRecord.getItems()).thenReturn(List.of(matchingItem, otherItem));
        when(refund.getReturnRecord()).thenReturn(returnRecord);
        when(refund.getPayments()).thenReturn(List.of(cashRefund));
        when(refund.getTotalAmount()).thenReturn(new BigDecimal("27.00"));
        return refund;
    }

    private static SaleItem saleItem(UUID productId, UUID categoryId, String subtotal, String discount, String tax, String total) {
        SaleItem item = mock(SaleItem.class);
        Product product = product(productId, categoryId);
        when(item.getProduct()).thenReturn(product);
        when(item.getLineSubtotal()).thenReturn(new BigDecimal(subtotal));
        when(item.getDiscountAmount()).thenReturn(new BigDecimal(discount));
        when(item.getEstimatedTaxAmount()).thenReturn(new BigDecimal(tax));
        when(item.getLineTotal()).thenReturn(new BigDecimal(total));
        return item;
    }

    private static ReturnItem returnItem(UUID productId, UUID categoryId, String subtotal, String tax, String total) {
        ReturnItem item = mock(ReturnItem.class);
        Product product = product(productId, categoryId);
        when(item.getProduct()).thenReturn(product);
        when(item.getReturnSubtotalAmount()).thenReturn(new BigDecimal(subtotal));
        when(item.getReturnTaxAmount()).thenReturn(new BigDecimal(tax));
        when(item.getReturnTotalAmount()).thenReturn(new BigDecimal(total));
        return item;
    }

    private static Product product(UUID productId, UUID categoryId) {
        Category category = new Category("CAT", "Category", null, true);
        ReflectionTestUtils.setField(category, "id", categoryId);
        Product product = new Product(new ProductValues(
                "SKU-" + productId.toString().substring(0, 8),
                "Product",
                null,
                SellableType.STANDARD_PRODUCT,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                category,
                null,
                true,
                false,
                false,
                null,
                null,
                List.of(),
                List.of(),
                Set.of()));
        ReflectionTestUtils.setField(product, "id", productId);
        return product;
    }
}
