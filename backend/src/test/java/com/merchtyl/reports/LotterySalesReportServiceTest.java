package com.merchtyl.reports;

import com.merchtyl.product.SellableType;
import com.merchtyl.receipts.Receipt;
import com.merchtyl.receipts.ReceiptRepository;
import com.merchtyl.register.Register;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.SaleLineType;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import com.merchtyl.features.FeatureService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LotterySalesReportServiceTest {
    @Test
    void combinesPhysicalAndManualSoldAndKeepsWinsSeparate() {
        SaleRepository sales = mock(SaleRepository.class);
        ReceiptRepository receipts = mock(ReceiptRepository.class);
        StoreAccessService access = mock(StoreAccessService.class);
        User actor = mock(User.class), cashier = mock(User.class);
        Store store = mock(Store.class); Register register = mock(Register.class); Sale sale = mock(Sale.class);
        UUID tenantId = UUID.randomUUID(), storeId = UUID.randomUUID(), saleId = UUID.randomUUID();
        when(actor.getTenantId()).thenReturn(tenantId); when(actor.getId()).thenReturn(UUID.randomUUID());
        when(access.currentTenantUser(any(Authentication.class))).thenReturn(actor);
        when(access.canAccessStore(actor.getId(), storeId)).thenReturn(true);
        when(store.getId()).thenReturn(storeId); when(register.getCode()).thenReturn("REG-01");
        when(cashier.getDisplayName()).thenReturn("John");
        when(sale.getId()).thenReturn(saleId); when(sale.getStore()).thenReturn(store); when(sale.getRegister()).thenReturn(register);
        when(sale.getCreatedBy()).thenReturn(cashier); when(sale.getCurrencyCode()).thenReturn("CAD");
        when(sale.getCompletedAt()).thenReturn(Instant.parse("2026-09-15T14:30:00Z"));
        SaleItem ticket = item(SaleLineType.CATALOG_PRODUCT, SellableType.LOTTERY_PRODUCT, "$5 Scratch Ticket", "10.00", "2");
        SaleItem manual = item(SaleLineType.LOTTERY_SOLD, null, "Lottery Sold", "50.00", "1");
        SaleItem win = item(SaleLineType.LOTTERY_WIN, null, "Lottery Win", "-20.00", "1");
        when(sale.getItems()).thenReturn(List.of(ticket, manual, win));
        when(sales.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(sale));
        Receipt receipt = mock(Receipt.class); when(receipt.getReceiptNumber()).thenReturn("RCT-1001");
        when(receipts.findBySale_Id(saleId)).thenReturn(Optional.of(receipt));
        LotterySalesReportService service = new LotterySalesReportService(sales, receipts, access, mock(FeatureService.class),
                Clock.fixed(Instant.parse("2026-09-15T15:00:00Z"), ZoneOffset.UTC));

        LotterySalesReportResponse result = service.summarize(new LotterySalesReportRequest(
                null, null, null, LocalDate.parse("2026-09-15"), LocalDate.parse("2026-09-15"), "ALL", "ALL"), mock(Authentication.class));

        assertThat(result.physicalTicketSales()).isEqualByComparingTo("10.00");
        assertThat(result.manualLotterySold()).isEqualByComparingTo("50.00");
        assertThat(result.totalLotterySold()).isEqualByComparingTo("60.00");
        assertThat(result.lotteryWins()).isEqualByComparingTo("20.00");
        assertThat(result.netLottery()).isEqualByComparingTo("40.00");
        assertThat(result.activities()).hasSize(3).allSatisfy(row -> assertThat(row.receiptNumber()).isEqualTo("RCT-1001"));
    }

    @Test
    void twoTillsMatchEndOfDayLotteryClassificationTotals() {
        SaleRepository sales = mock(SaleRepository.class);
        ReceiptRepository receipts = mock(ReceiptRepository.class);
        StoreAccessService access = mock(StoreAccessService.class);
        User actor = mock(User.class);
        UUID tenantId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        when(actor.getTenantId()).thenReturn(tenantId);
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(access.currentTenantUser(any(Authentication.class))).thenReturn(actor);
        when(access.canAccessStore(actor.getId(), storeId)).thenReturn(true);

        Sale tillOne = lotterySale(storeId, "TILL-1", "30", "20", "-10");
        Sale tillTwo = lotterySale(storeId, "TILL-2", "40", "10", "-5");
        when(sales.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(tillOne, tillTwo));

        LotterySalesReportService service = new LotterySalesReportService(
                sales, receipts, access, mock(FeatureService.class),
                Clock.fixed(Instant.parse("2026-09-15T15:00:00Z"), ZoneOffset.UTC));
        LotterySalesReportResponse result = service.summarize(new LotterySalesReportRequest(
                null, null, null, LocalDate.parse("2026-09-15"), LocalDate.parse("2026-09-15"), "ALL", "ALL"),
                mock(Authentication.class));

        assertThat(result.physicalTicketSales()).isEqualByComparingTo("70.00");
        assertThat(result.manualLotterySold()).isEqualByComparingTo("30.00");
        assertThat(result.totalLotterySold()).isEqualByComparingTo("100.00");
        assertThat(result.lotteryWins()).isEqualByComparingTo("15.00");
        assertThat(result.netLottery()).isEqualByComparingTo("85.00");
    }

    private static Sale lotterySale(UUID storeId, String registerCode, String physical, String manual, String win) {
        Sale sale = mock(Sale.class);
        Store store = mock(Store.class);
        Register register = mock(Register.class);
        User cashier = mock(User.class);
        when(store.getId()).thenReturn(storeId);
        when(register.getCode()).thenReturn(registerCode);
        when(cashier.getDisplayName()).thenReturn("Cashier " + registerCode);
        when(sale.getId()).thenReturn(UUID.randomUUID());
        when(sale.getStore()).thenReturn(store);
        when(sale.getRegister()).thenReturn(register);
        when(sale.getCreatedBy()).thenReturn(cashier);
        when(sale.getCurrencyCode()).thenReturn("CAD");
        when(sale.getCompletedAt()).thenReturn(Instant.parse("2026-09-15T14:30:00Z"));
        List<SaleItem> items = List.of(
                item(SaleLineType.CATALOG_PRODUCT, SellableType.LOTTERY_PRODUCT, "Ticket", physical, "1"),
                item(SaleLineType.LOTTERY_SOLD, null, "Lottery Sold", manual, "1"),
                item(SaleLineType.LOTTERY_WIN, null, "Lottery Win", win, "1"));
        when(sale.getItems()).thenReturn(items);
        return sale;
    }

    private static SaleItem item(SaleLineType type, SellableType sellableType, String name, String subtotal, String quantity) {
        SaleItem item = mock(SaleItem.class);
        when(item.getLineType()).thenReturn(type); when(item.getSellableTypeSnapshot()).thenReturn(sellableType);
        when(item.getProductName()).thenReturn(name); when(item.getLineSubtotal()).thenReturn(new BigDecimal(subtotal));
        when(item.getDiscountAmount()).thenReturn(BigDecimal.ZERO); when(item.getQuantity()).thenReturn(new BigDecimal(quantity));
        return item;
    }
}
