package com.merchtyl.eod;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditService;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.cash.CashLedgerBreakdownResponse;
import com.merchtyl.cash.CashMovementRepository;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.features.FeatureService;
import com.merchtyl.inventory.InventoryBalanceRepository;
import com.merchtyl.inventory.InventoryTransactionRepository;
import com.merchtyl.lottery.LotteryPayoutRepository;
import com.merchtyl.lottery.LotteryPosActivity;
import com.merchtyl.lottery.LotteryPosActivityType;
import com.merchtyl.lottery.LotteryPosActivityRepository;
import com.merchtyl.lottery.LotteryPayout;
import com.merchtyl.lottery.LotteryPayoutMethod;
import com.merchtyl.lottery.LotteryPayoutStatus;
import com.merchtyl.lottery.LotteryPayoutReversalRepository;
import com.merchtyl.lottery.LotterySaleCancellationRepository;
import com.merchtyl.lottery.LotterySale;
import com.merchtyl.lottery.LotterySaleRepository;
import com.merchtyl.lottery.LotterySaleStatus;
import com.merchtyl.lottery.LotterySettlementRepository;
import com.merchtyl.product.SellableType;
import com.merchtyl.refunds.RefundRepository;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterType;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.sales.SaleRepository;
import com.merchtyl.sales.Sale;
import com.merchtyl.sales.SaleItem;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.refunds.Refund;
import com.merchtyl.returns.ReturnItem;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessDayServiceTest {
    @Mock private BusinessDayRepository businessDays;
    @Mock private EndOfDayReportRepository reports;
    @Mock private BusinessDayConfigurationRepository configurations;
    @Mock private StoreRepository stores;
    @Mock private UserRepository users;
    @Mock private RegisterSessionRepository registerSessions;
    @Mock private SaleRepository sales;
    @Mock private RefundRepository refunds;
    @Mock private CashMovementRepository cashMovements;
    @Mock private InventoryTransactionRepository inventoryTransactions;
    @Mock private InventoryBalanceRepository inventoryBalances;
    @Mock private LotterySaleRepository lotterySales;
    @Mock private LotteryPayoutRepository lotteryPayouts;
    @Mock private LotterySaleCancellationRepository lotterySaleCancellations;
    @Mock private LotteryPayoutReversalRepository lotteryPayoutReversals;
    @Mock private LotterySettlementRepository lotterySettlements;
    @Mock private LotteryPosActivityRepository lotteryPosActivities;
    @Mock private CashLedgerService cashLedger;
    @Mock private FeatureService features;
    @Mock private AuditService audit;
    @Mock private StoreAccessService storeAccess;

    private BusinessDayService service;
    private Authentication authentication;
    private BusinessDay day;
    private Store store;
    private User actor;
    private UUID storeId;
    private UUID dayId;
    private LocalDate businessDate;

    @BeforeEach
    void setUp() {
        service = new BusinessDayService(
                businessDays, reports, configurations, stores, users, registerSessions, sales, refunds,
                cashMovements, inventoryTransactions, inventoryBalances, lotterySales, lotteryPayouts,
                lotterySaleCancellations, lotteryPayoutReversals, lotterySettlements, lotteryPosActivities, cashLedger, features,
                audit, new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-31T22:10:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "storeAccessService", storeAccess);
        authentication = mock(Authentication.class);
        day = mock(BusinessDay.class);
        store = mock(Store.class);
        actor = mock(User.class);
        storeId = UUID.randomUUID();
        dayId = UUID.randomUUID();
        businessDate = LocalDate.of(2026, 8, 31);

        lenient().when(authentication.getName()).thenReturn("owner@example.local");
        lenient().when(users.findByEmailIgnoreCase("owner@example.local")).thenReturn(Optional.of(actor));
        lenient().when(businessDays.findByIdForUpdate(dayId)).thenReturn(Optional.of(day));
        lenient().when(day.getId()).thenReturn(dayId);
        lenient().when(day.getStore()).thenReturn(store);
        lenient().when(day.getBusinessDate()).thenReturn(businessDate);
        lenient().when(day.getStatus()).thenReturn(BusinessDayStatus.CLOSED);
        lenient().when(day.getVersion()).thenReturn(3L);
        lenient().when(store.getId()).thenReturn(storeId);
        lenient().when(store.getTimezone()).thenReturn("UTC");
        lenient().when(reports.existsByBusinessDay_Id(dayId)).thenReturn(true);
    }

    @Test
    void categoryDistributionUsesSnapshotNetAmountsReturnsCustomItemsAndExcludesFoodRegisters() {
        UUID beveragesId = UUID.randomUUID();
        UUID snacksId = UUID.randomUUID();
        SaleItem beverageOne = saleItem(beveragesId, "Beverages", "2.0000", "100.00", "10.00", false);
        SaleItem beverageTwo = saleItem(beveragesId, "Beverages", "1.0000", "20.00", "0.00", false);
        SaleItem snack = saleItem(snacksId, "Snacks", "1.0000", "50.00", "5.00", false);
        SaleItem custom = saleItem(null, null, "1.0000", "15.00", "0.00", true);
        SaleItem food = saleItem(null, null, "1.0000", "200.00", "0.00", false);

        Sale retailSale = sale(RegisterType.RETAIL, beverageOne, beverageTwo, snack, custom);
        Sale foodSale = sale(RegisterType.FOOD_SERVICE, food);
        ReturnItem returnedBeverage = mock(ReturnItem.class);
        when(returnedBeverage.getOriginalSaleItem()).thenReturn(beverageOne);
        when(returnedBeverage.getQuantity()).thenReturn(new BigDecimal("0.5000"));
        when(returnedBeverage.getReturnSubtotalAmount()).thenReturn(new BigDecimal("25.00"));
        com.merchtyl.returns.Return returnRecord = mock(com.merchtyl.returns.Return.class);
        when(returnRecord.getItems()).thenReturn(List.of(returnedBeverage));
        Refund refund = mock(Refund.class);
        Register refundRegister = mock(Register.class);
        when(refundRegister.getType()).thenReturn(RegisterType.RETAIL);
        when(refund.getRegister()).thenReturn(refundRegister);
        when(refund.getReturnRecord()).thenReturn(returnRecord);

        List<EndOfDayCategorySalesSummaryResponse> result = service.categorySalesValues(
                List.of(retailSale, foodSale), List.of(refund));

        assertThat(result).extracting(EndOfDayCategorySalesSummaryResponse::categoryName)
                .containsExactly("Beverages", "Snacks", "Custom Items");
        assertThat(result).extracting(EndOfDayCategorySalesSummaryResponse::netSales)
                .containsExactly(new BigDecimal("85.00"), new BigDecimal("45.00"), new BigDecimal("15.00"));
        assertThat(result).extracting(EndOfDayCategorySalesSummaryResponse::quantitySold)
                .containsExactly(new BigDecimal("2.5000"), new BigDecimal("1.0000"), new BigDecimal("1.0000"));
        assertThat(result).extracting(EndOfDayCategorySalesSummaryResponse::percentage)
                .containsExactly(new BigDecimal("58.6207"), new BigDecimal("31.0345"), new BigDecimal("10.3448"));
        assertThat(result).noneMatch(row -> row.categoryName().equals("Uncategorized"));
        assertThat(result.stream().map(EndOfDayCategorySalesSummaryResponse::netSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("145.00");
    }

    @Test
    void categoryDistributionKeepsUncategorizedAndZeroNetActivityWithoutDividingByZero() {
        SaleItem uncategorized = saleItem(null, null, "1.0000", "12.00", "0.00", false);
        Sale retailSale = sale(RegisterType.RETAIL, uncategorized);
        ReturnItem returned = mock(ReturnItem.class);
        when(returned.getOriginalSaleItem()).thenReturn(uncategorized);
        when(returned.getQuantity()).thenReturn(new BigDecimal("1.0000"));
        when(returned.getReturnSubtotalAmount()).thenReturn(new BigDecimal("12.00"));
        com.merchtyl.returns.Return returnRecord = mock(com.merchtyl.returns.Return.class);
        when(returnRecord.getItems()).thenReturn(List.of(returned));
        Refund refund = mock(Refund.class);
        Register register = mock(Register.class);
        when(register.getType()).thenReturn(RegisterType.RETAIL);
        when(refund.getRegister()).thenReturn(register);
        when(refund.getReturnRecord()).thenReturn(returnRecord);

        List<EndOfDayCategorySalesSummaryResponse> result = service.categorySalesValues(
                List.of(retailSale), List.of(refund));

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.categoryName()).isEqualTo("Uncategorized");
            assertThat(row.quantitySold()).isEqualByComparingTo("0.0000");
            assertThat(row.netSales()).isEqualByComparingTo("0.00");
            assertThat(row.percentage()).isEqualByComparingTo("0.0000");
        });
    }

    @Test
    void legacyStandaloneLotteryRecordsAreNotAddedToCompletedSaleTotals() {
        LotterySale registerOneSold = lotterySale("R1", "200.00");
        LotterySale registerTwoSold = lotterySale("R2", "300.00");
        LotteryPayout registerOneWin = lotteryPayout("50.00");
        LotteryPayout registerTwoWin = lotteryPayout("130.00");

        BusinessDayService.EndOfDayLotteryValues result = service.lotteryValues(
                false,
                List.of(),
                List.of(registerOneSold, registerTwoSold),
                List.of(registerOneWin, registerTwoWin),
                List.of(), List.of(), List.of(), List.of());

        assertThat(result.enabled()).isTrue();
        assertThat(result.lotterySales()).isEqualByComparingTo("0.00");
        assertThat(result.lotteryPayouts()).isEqualByComparingTo("0.00");
    }

    @Test
    void lotterySectionUnifiesScannedProductsAndManualActivityAcrossRegisters() {
        SaleItem registerOneTicket = saleItem(null, null, "2.0000", "10.00", "0.00", false);
        when(registerOneTicket.getSellableTypeSnapshot()).thenReturn(SellableType.LOTTERY_PRODUCT);
        SaleItem registerTwoTicket = saleItem(null, null, "5.0000", "50.00", "0.00", false);
        when(registerTwoTicket.getSellableTypeSnapshot()).thenReturn(SellableType.LOTTERY_PRODUCT);
        Sale registerOneSale = sale(RegisterType.RETAIL, registerOneTicket);
        Sale registerTwoSale = sale(RegisterType.RETAIL, registerTwoTicket);

        BusinessDayService.EndOfDayLotteryValues result = service.lotteryValues(
                true,
                List.of(registerOneSale, registerTwoSale),
                List.of(lotterySale("R1", "90.00"), lotterySale("R2", "150.00")),
                List.of(lotteryPayout("25.00"), lotteryPayout("75.00")),
                List.of(), List.of(), List.of(), List.of());

        assertThat(result.lotterySales()).isEqualByComparingTo("60.00");
        assertThat(result.lotteryPayouts()).isEqualByComparingTo("0.00");
        assertThat(result.lotterySales().subtract(result.lotteryPayouts())).isEqualByComparingTo("60.00");
        assertThat(service.categorySalesValues(
                List.of(registerOneSale, registerTwoSale),
                List.of())).isEmpty();
    }

    @Test
    void simplePosActivitiesJoinScannedTicketsInTheSameLotteryTotals() {
        SaleItem ticket = saleItem(null, null, "2.0000", "10.00", "0.00", false);
        when(ticket.getSellableTypeSnapshot()).thenReturn(SellableType.LOTTERY_PRODUCT);
        LotteryPosActivity sold = mock(LotteryPosActivity.class);
        when(sold.getType()).thenReturn(LotteryPosActivityType.SOLD);
        when(sold.getAmount()).thenReturn(new BigDecimal("100.00"));
        LotteryPosActivity win = mock(LotteryPosActivity.class);
        when(win.getType()).thenReturn(LotteryPosActivityType.WIN);
        when(win.getAmount()).thenReturn(new BigDecimal("25.00"));

        Sale scannedSale = sale(RegisterType.RETAIL,ticket);
        BusinessDayService.EndOfDayLotteryValues result = service.lotteryValues(
                true, List.of(scannedSale), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(sold, win));

        assertThat(result.lotterySales()).isEqualByComparingTo("10.00");
        assertThat(result.lotteryPayouts()).isEqualByComparingTo("0.00");
        assertThat(result.lotterySales().subtract(result.lotteryPayouts())).isEqualByComparingTo("10.00");
    }

    @Test
    void lotterySessionsStartAtZeroAndStoreTotalIsDirectSum() throws Exception {
        Sale sessionOne=lotterySessionSale("R1","30","20","10");
        Sale sessionTwo=lotterySessionSale("R2","40","10","5");
        BusinessDayService.EndOfDayLotteryValues result=service.lotteryValues(true,List.of(sessionOne,sessionTwo),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        assertThat(result.lotterySales()).isEqualByComparingTo("100.00");
        assertThat(result.lotteryPayouts()).isEqualByComparingTo("15.00");
        assertThat(result.lotterySales().subtract(result.lotteryPayouts())).isEqualByComparingTo("85.00");
        var rows=new ObjectMapper().readValue(result.registerTotals(),BusinessDayService.LotterySessionTotal[].class);
        assertThat(rows).hasSize(2);
        assertThat(rows[0].physicalLotterySold()).isEqualByComparingTo("30.00");assertThat(rows[0].manualLotterySold()).isEqualByComparingTo("20.00");assertThat(rows[0].netLottery()).isEqualByComparingTo("40.00");
        assertThat(rows[1].physicalLotterySold()).isEqualByComparingTo("40.00");assertThat(rows[1].manualLotterySold()).isEqualByComparingTo("10.00");assertThat(rows[1].netLottery()).isEqualByComparingTo("45.00");
    }

    @Test
    void fiveRegistersAndTwoSessionsOnSameRegisterRemainIndependent() throws Exception {
        List<Sale> sales=new java.util.ArrayList<>();for(int i=1;i<=5;i++)sales.add(lotterySessionSale("R"+i,String.valueOf(i*10),String.valueOf(i),String.valueOf(i-1)));sales.add(lotterySessionSale("R1","5","2","1"));
        var result=service.lotteryValues(true,sales,List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        assertThat(result.lotterySales()).isEqualByComparingTo("172.00");assertThat(result.lotteryPayouts()).isEqualByComparingTo("11.00");assertThat(result.lotterySales().subtract(result.lotteryPayouts())).isEqualByComparingTo("161.00");
        var rows=new ObjectMapper().readValue(result.registerTotals(),BusinessDayService.LotterySessionTotal[].class);assertThat(rows).hasSize(6);assertThat(rows).filteredOn(row->row.registerCode().equals("R1")).hasSize(2);
    }

    @Test
    void completedLotteryCartLinesJoinScannedTicketsAndStayOutOfMerchandiseCategories() {
        SaleItem ticket = saleItem(null, null, "2.0000", "10.00", "0.00", false);
        when(ticket.getSellableTypeSnapshot()).thenReturn(SellableType.LOTTERY_PRODUCT);
        SaleItem sold = saleItem(null, null, "1.0000", "90.00", "0.00", false);
        when(sold.getLineType()).thenReturn(com.merchtyl.sales.SaleLineType.LOTTERY_SOLD);
        SaleItem win = saleItem(null, null, "1.0000", "-25.00", "0.00", false);
        when(win.getLineType()).thenReturn(com.merchtyl.sales.SaleLineType.LOTTERY_WIN);
        Sale completed = sale(RegisterType.RETAIL, ticket, sold, win);

        BusinessDayService.EndOfDayLotteryValues result = service.lotteryValues(
                true, List.of(completed), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());

        assertThat(result.lotterySales()).isEqualByComparingTo("100.00");
        assertThat(result.lotteryPayouts()).isEqualByComparingTo("25.00");
        assertThat(result.lotterySales().subtract(result.lotteryPayouts())).isEqualByComparingTo("75.00");
        assertThat(service.categorySalesValues(List.of(completed), List.of())).isEmpty();
    }

    @Test
    void cashLotteryActivityUsesActualLedgerPayoutRatherThanAllWinLines() {
        Sale sale = lotterySessionSale("R1", "10", "0", "20");

        BusinessDayService.EndOfDayLotteryValues result = service.lotteryValues(
                true, List.of(sale), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new BigDecimal("0.00"), new BigDecimal("10.00"));

        assertThat(result.lotterySales()).isEqualByComparingTo("10.00");
        assertThat(result.lotteryPayouts()).isEqualByComparingTo("20.00");
        assertThat(result.cashLotteryActivity()).isEqualByComparingTo("-10.00");
        assertThat(result.nonCashLotteryActivity()).isEqualByComparingTo("0.00");
    }

    @Test
    void scannedLotteryProductUsesSnapshotClassificationAndIsNotDoubleCountedAsMerchandise() {
        SaleItem scannedTicket = saleItem(null, null, "2.0000", "10.00", "0.00", false);
        when(scannedTicket.getSellableTypeSnapshot()).thenReturn(SellableType.LOTTERY_PRODUCT);
        Sale scannedSale = sale(RegisterType.RETAIL, scannedTicket);

        BusinessDayService.EndOfDayLotteryValues result = service.lotteryValues(
                false, List.of(scannedSale), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(result.enabled()).isTrue();
        assertThat(result.lotterySales()).isEqualByComparingTo("10.00");
        assertThat(result.lotteryPayouts()).isEqualByComparingTo("0.00");
        ReturnItem historicalReturn = mock(ReturnItem.class);
        when(historicalReturn.getOriginalSaleItem()).thenReturn(scannedTicket);
        com.merchtyl.returns.Return returnRecord = mock(com.merchtyl.returns.Return.class);
        when(returnRecord.getItems()).thenReturn(List.of(historicalReturn));
        Refund refund = mock(Refund.class);
        Register refundRegister = mock(Register.class);
        when(refundRegister.getType()).thenReturn(RegisterType.RETAIL);
        when(refund.getRegister()).thenReturn(refundRegister);
        when(refund.getReturnRecord()).thenReturn(returnRecord);

        assertThat(service.categorySalesValues(List.of(scannedSale), List.of(refund))).isEmpty();
    }

    private static LotterySale lotterySale(String registerCode, String amount) {
        LotterySale sale = mock(LotterySale.class);
        Register register = mock(Register.class);
        User cashier = mock(User.class);
        com.merchtyl.lottery.LotteryOperator operator = mock(com.merchtyl.lottery.LotteryOperator.class);
        lenient().when(sale.getAmount()).thenReturn(new BigDecimal(amount));
        lenient().when(sale.getStatus()).thenReturn(LotterySaleStatus.RECORDED);
        lenient().when(sale.getPaymentMethod()).thenReturn(PaymentMethod.CASH);
        lenient().when(sale.getRegister()).thenReturn(register);
        lenient().when(register.getCode()).thenReturn(registerCode);
        lenient().when(sale.getCashier()).thenReturn(cashier);
        lenient().when(cashier.getEmail()).thenReturn(registerCode.toLowerCase() + "@example.test");
        lenient().when(sale.getOperator()).thenReturn(operator);
        lenient().when(operator.getCode()).thenReturn("ATLANTIC");
        return sale;
    }

    private static LotteryPayout lotteryPayout(String amount) {
        LotteryPayout payout = mock(LotteryPayout.class);
        lenient().when(payout.getAmount()).thenReturn(new BigDecimal(amount));
        lenient().when(payout.getStatus()).thenReturn(LotteryPayoutStatus.PAID);
        lenient().when(payout.getPayoutMethod()).thenReturn(LotteryPayoutMethod.CASH);
        lenient().when(payout.getApprovals()).thenReturn(List.of());
        return payout;
    }

    private static Sale sale(RegisterType type, SaleItem... items) {
        Sale sale = mock(Sale.class);
        Register register = mock(Register.class);
        when(register.getType()).thenReturn(type);
        when(sale.getRegister()).thenReturn(register);
        lenient().when(sale.getItems()).thenReturn(List.of(items));
        return sale;
    }

    private static Sale lotterySessionSale(String registerCode,String physical,String manual,String wins){SaleItem ticket=saleItem(null,null,"1",physical,"0",false);when(ticket.getSellableTypeSnapshot()).thenReturn(SellableType.LOTTERY_PRODUCT);SaleItem sold=saleItem(null,null,"1",manual,"0",false);when(sold.getLineType()).thenReturn(com.merchtyl.sales.SaleLineType.LOTTERY_SOLD);SaleItem win=saleItem(null,null,"1","-"+wins,"0",false);when(win.getLineType()).thenReturn(com.merchtyl.sales.SaleLineType.LOTTERY_WIN);Sale sale=mock(Sale.class);Register register=mock(Register.class);RegisterSession session=mock(RegisterSession.class);when(register.getId()).thenReturn(UUID.randomUUID());when(register.getCode()).thenReturn(registerCode);when(register.getType()).thenReturn(RegisterType.RETAIL);when(sale.getRegister()).thenReturn(register);when(session.getId()).thenReturn(UUID.randomUUID());when(sale.getRegisterSession()).thenReturn(session);when(sale.getItems()).thenReturn(List.of(ticket,sold,win));return sale;}

    private static SaleItem saleItem(UUID categoryId, String categoryName, String quantity, String subtotal,
                                     String discount, boolean custom) {
        SaleItem item = mock(SaleItem.class);
        lenient().when(item.isCustomItem()).thenReturn(custom);
        lenient().when(item.getCategorySnapshotId()).thenReturn(categoryId);
        lenient().when(item.getCategoryNameSnapshot()).thenReturn(categoryName);
        lenient().when(item.getQuantity()).thenReturn(new BigDecimal(quantity));
        lenient().when(item.getLineSubtotal()).thenReturn(new BigDecimal(subtotal));
        lenient().when(item.getDiscountAmount()).thenReturn(new BigDecimal(discount));
        return item;
    }

    @Test
    void assignedStoreOperatorCanOpenTodaysBusinessDayWithoutManagementScope() {
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(configurations.findByStore_Id(storeId)).thenReturn(Optional.empty());
        when(businessDays.findByStore_IdAndStatusIn(storeId, List.of(
                BusinessDayStatus.OPEN, BusinessDayStatus.CLOSING, BusinessDayStatus.REOPENED))).thenReturn(List.of());
        when(businessDays.existsByStore_IdAndBusinessDate(storeId, businessDate)).thenReturn(false);
        when(businessDays.saveAndFlush(any(BusinessDay.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(store.getCode()).thenReturn("MAIN");
        when(store.getName()).thenReturn("Main Store");
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(actor.getDisplayName()).thenReturn("Cashier One");

        BusinessDayResponse response = service.open(
                new BusinessDayOpenRequest(storeId, businessDate, false, null), authentication);

        assertThat(response.storeId()).isEqualTo(storeId);
        assertThat(response.businessDate()).isEqualTo(businessDate);
        assertThat(response.status()).isEqualTo(BusinessDayStatus.OPEN);
        verify(storeAccess).requireStoreAccess(authentication, storeId);
        verify(storeAccess, never()).requireStoreManagement(authentication, storeId);
    }

    @Test
    void previousDayOverrideStillRequiresStoreManagement() {
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(configurations.findByStore_Id(storeId)).thenReturn(Optional.empty());
        when(businessDays.findByStore_IdAndStatusIn(storeId, List.of(
                BusinessDayStatus.OPEN, BusinessDayStatus.CLOSING, BusinessDayStatus.REOPENED))).thenReturn(List.of(day));

        assertThatThrownBy(() -> service.open(
                new BusinessDayOpenRequest(storeId, businessDate, true, "Approved exception"), authentication))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Previous business day");

        verify(storeAccess).requireStoreAccess(authentication, storeId);
        verify(storeAccess).requireStoreManagement(authentication, storeId);
    }

    @Test
    void assignedStoreOperatorCanStartClosingWithoutManagementScope() {
        when(day.getStatus()).thenReturn(BusinessDayStatus.OPEN);
        stubResponseFields();

        service.startClosing(dayId, authentication);

        verify(storeAccess).requireStoreAccess(authentication, storeId);
        verify(storeAccess, never()).requireStoreManagement(authentication, storeId);
        verify(day).startClosing(eq(actor), any(Instant.class));
    }

    @Test
    void assignedStoreOperatorCloseIsBlockedByOpenRegisterSession() {
        RegisterSession session = mock(RegisterSession.class);
        Register register = mock(Register.class);
        when(day.getStatus()).thenReturn(BusinessDayStatus.OPEN);
        when(day.getTimezone()).thenReturn("America/Moncton");
        when(session.getStatus()).thenReturn(RegisterSessionStatus.OPEN);
        when(session.getRegister()).thenReturn(register);
        when(register.getCode()).thenReturn("FRONT");
        when(cashLedger.breakdown(session)).thenReturn(cashBreakdown("100.00"));
        when(registerSessions.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(session));

        assertThatThrownBy(() -> service.close(
                dayId,
                new BusinessDayCloseRequest(3L, null, null, true),
                authentication))
                .isInstanceOf(ClosingValidationException.class)
                .hasMessage("All registers must be closed before closing the business day.");

        verify(storeAccess).requireStoreAccess(authentication, storeId);
        verify(storeAccess, never()).requireStoreManagement(authentication, storeId);
    }

    @Test
    void managerForceCloseCannotBypassClosingRegisterSession() {
        RegisterSession session = session("KITCHEN", RegisterSessionStatus.CLOSING, null, null);
        when(day.getStatus()).thenReturn(BusinessDayStatus.OPEN);
        when(day.getTimezone()).thenReturn("America/Moncton");
        when(configurations.findByStore_Id(storeId)).thenReturn(Optional.empty());
        when(registerSessions.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(session));

        assertThatThrownBy(() -> service.forceClose(
                dayId,
                new BusinessDayForceCloseRequest(3L, "Emergency close", null, null, true),
                authentication))
                .isInstanceOf(ClosingValidationException.class)
                .hasMessage("All registers must be closed before closing the business day.");

        verify(storeAccess).requireStoreManagement(authentication, storeId);
        verify(day, never()).close(any(User.class), any(Instant.class), any(String.class));
    }

    @Test
    void closingValidationIdentifiesEveryRegisterAndReconciliationPermission() {
        RegisterSession reconciled = session("A", RegisterSessionStatus.FORCE_CLOSED, "100.00", "100.00");
        RegisterSession closing = session("B", RegisterSessionStatus.CLOSING, null, null);
        RegisterSession open = session("C", RegisterSessionStatus.OPEN, null, null);
        when(businessDays.findById(dayId)).thenReturn(Optional.of(day));
        when(day.getStatus()).thenReturn(BusinessDayStatus.OPEN);
        when(day.getTimezone()).thenReturn("UTC");
        when(registerSessions.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(reconciled, closing, open));
        when(authentication.getAuthorities()).thenReturn((java.util.Collection) List.of(
                new SimpleGrantedAuthority("ROLE_MANAGER"),
                new SimpleGrantedAuthority("REGISTER_SESSION_CLOSE")));

        ClosingValidationResponse result = service.validateClosing(dayId, authentication);

        assertThat(result.registerSessions()).extracting(RegisterReconciliationResponse::registerCode)
                .containsExactly("A", "B", "C");
        assertThat(result.registerSessions()).filteredOn(RegisterReconciliationResponse::reconciliationRequired)
                .extracting(RegisterReconciliationResponse::registerCode).containsExactly("B", "C");
        assertThat(result.registerSessions().get(0).reconciliationComplete()).isTrue();
        assertThat(result.registerSessions()).filteredOn(RegisterReconciliationResponse::canReconcile)
                .hasSize(3);
        assertThat(result.closable()).isFalse();
        assertThat(result.blockers()).filteredOn(blocker -> "OPEN_REGISTER_SESSION".equals(blocker.code()))
                .extracting(ClosingBlockerResponse::relatedId)
                .containsExactly(closing.getId(), open.getId());
        verify(storeAccess).requireStoreAccess(authentication, storeId);
    }

    @Test
    void reconciledRegistersDoNotHideDraftSaleClosingBlocker() {
        RegisterSession reconciled = session("A", RegisterSessionStatus.FORCE_CLOSED, "100.00", "100.00");
        com.merchtyl.sales.Sale draft = mock(com.merchtyl.sales.Sale.class);
        UUID saleId = UUID.randomUUID();
        when(draft.getId()).thenReturn(saleId);
        when(draft.getStatus()).thenReturn(com.merchtyl.sales.SaleStatus.DRAFT);
        when(businessDays.findById(dayId)).thenReturn(Optional.of(day));
        when(day.getStatus()).thenReturn(BusinessDayStatus.REOPENED);
        when(day.getTimezone()).thenReturn("UTC");
        when(registerSessions.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(reconciled));
        when(sales.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(draft), List.of());

        ClosingValidationResponse result = service.validateClosing(dayId, authentication);

        assertThat(result.registerSessions()).allMatch(RegisterReconciliationResponse::reconciliationComplete);
        assertThat(result.closable()).isFalse();
        assertThat(result.blockers()).extracting(ClosingBlockerResponse::code)
                .containsExactly("UNFINALIZED_DRAFT_SALE");
        assertThat(result.blockers().get(0).relatedId()).isEqualTo(saleId);
    }

    @Test
    void paidDraftSaleUsesNonCancellableClosingBlocker() {
        com.merchtyl.sales.Sale draft = mock(com.merchtyl.sales.Sale.class);
        when(draft.getId()).thenReturn(UUID.randomUUID());
        when(draft.getStatus()).thenReturn(com.merchtyl.sales.SaleStatus.DRAFT);
        when(draft.getPayments()).thenReturn(List.of(mock(com.merchtyl.sales.Payment.class)));
        when(businessDays.findById(dayId)).thenReturn(Optional.of(day));
        when(day.getStatus()).thenReturn(BusinessDayStatus.OPEN);
        when(day.getTimezone()).thenReturn("UTC");
        when(sales.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(draft), List.of());

        ClosingValidationResponse result = service.validateClosing(dayId, authentication);

        assertThat(result.blockers()).extracting(ClosingBlockerResponse::code)
                .containsExactly("UNFINALIZED_PAID_DRAFT_SALE");
    }

    @Test
    void pendingPaymentUsesCheckoutLanguageInsteadOfLegacyDraftLanguage() {
        com.merchtyl.sales.Sale pending = mock(com.merchtyl.sales.Sale.class);
        when(pending.getId()).thenReturn(UUID.randomUUID());
        when(pending.getStatus()).thenReturn(com.merchtyl.sales.SaleStatus.PENDING_PAYMENT);
        when(pending.getPayments()).thenReturn(List.of(mock(com.merchtyl.sales.Payment.class)));
        when(businessDays.findById(dayId)).thenReturn(Optional.of(day));
        when(day.getStatus()).thenReturn(BusinessDayStatus.OPEN);
        when(day.getTimezone()).thenReturn("UTC");
        when(sales.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(pending), List.of());

        ClosingValidationResponse result = service.validateClosing(dayId, authentication);

        assertThat(result.blockers()).extracting(ClosingBlockerResponse::code)
                .containsExactly("UNFINISHED_PAID_CHECKOUT");
        assertThat(result.blockers().getFirst().message()).doesNotContainIgnoringCase("draft");
    }

    private RegisterSession session(String code, RegisterSessionStatus status, String counted, String expectedAtClose) {
        RegisterSession session = mock(RegisterSession.class);
        Register register = mock(Register.class);
        User opener = mock(User.class);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getRegister()).thenReturn(register);
        when(register.getId()).thenReturn(UUID.randomUUID());
        when(register.getCode()).thenReturn(code);
        when(register.getName()).thenReturn("Register " + code);
        when(session.getStatus()).thenReturn(status);
        when(session.getOpenedBy()).thenReturn(opener);
        when(session.getOpeningCash()).thenReturn(new BigDecimal("50.00"));
        when(session.getCountedCash()).thenReturn(counted == null ? null : new BigDecimal(counted));
        when(session.getExpectedCashAtClose()).thenReturn(expectedAtClose == null ? null : new BigDecimal(expectedAtClose));
        when(cashLedger.breakdown(session)).thenReturn(cashBreakdown("100.00"));
        return session;
    }

    private static CashLedgerBreakdownResponse cashBreakdown(String expected) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new CashLedgerBreakdownResponse(zero, zero, zero, zero, zero, zero, zero, zero,
                zero, zero, zero, zero, new BigDecimal(expected), List.of());
    }

    @Test
    void staleCloseVersionUsesStableBusinessDayStateChangedCode() {
        assertThatThrownBy(() -> service.close(
                dayId,
                new BusinessDayCloseRequest(0L, "Recovery close", "", true),
                authentication))
                .isInstanceOf(ConflictException.class)
                .hasMessage("BUSINESS_DAY_STATE_CHANGED");
    }

    @Test
    void blankVarianceExplanationIsAllowedWhenVarianceIsZero() {
        BusinessDayConfiguration configuration = BusinessDayConfiguration.defaults(store);

        ReflectionTestUtils.invokeMethod(service, "validateSignOff", configuration,
                BigDecimal.ZERO, "Recovery close", "", true);
    }

    @Test
    void blankVarianceExplanationUsesStableValidationCodeWhenVarianceExceedsThreshold() {
        BusinessDayConfiguration configuration = BusinessDayConfiguration.defaults(store);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateSignOff", configuration,
                new BigDecimal("5.01"), "Recovery close", "", true))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("VARIANCE_EXPLANATION_REQUIRED");
    }

    @Test
    void laterBusinessDayForSameStorePreventsReopen() {
        when(businessDays.existsByStore_IdAndBusinessDateGreaterThan(storeId, businessDate)).thenReturn(true);

        assertThatThrownBy(() -> service.reopen(dayId, new BusinessDayReopenRequest(3L, "Late sales"), authentication))
                .isInstanceOf(ConflictException.class)
                .hasMessage("LATER_BUSINESS_DAY_EXISTS");

        verify(businessDays).existsByStore_IdAndBusinessDateGreaterThan(storeId, businessDate);
        verify(audit).record(any());
    }

    @Test
    void laterBusinessDayForAnotherStoreDoesNotBlockReopen() {
        AtomicReference<BusinessDayStatus> status = new AtomicReference<>(BusinessDayStatus.CLOSED);
        when(day.getStatus()).thenAnswer(ignored -> status.get());
        when(day.getTimezone()).thenReturn("America/Moncton");
        when(day.getOpenedAt()).thenReturn(Instant.parse("2026-08-31T08:00:00Z"));
        when(day.getOpenedBy()).thenReturn(actor);
        when(day.getReopenedBy()).thenReturn(actor);
        when(day.getReopenedAt()).thenReturn(Instant.parse("2026-08-31T22:10:00Z"));
        when(day.getReopenReason()).thenReturn("Late sales");
        when(store.getCode()).thenReturn("DOWNTOWN");
        when(store.getName()).thenReturn("Downtown");
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(actor.getDisplayName()).thenReturn("Owner One");
        when(businessDays.existsByStore_IdAndBusinessDateGreaterThan(storeId, businessDate)).thenReturn(false);
        when(businessDays.findByStore_IdAndStatusIn(any(), any())).thenReturn(List.of());
        when(businessDays.saveAndFlush(day)).thenReturn(day);
        doAnswer(invocation -> {
            status.set(BusinessDayStatus.REOPENED);
            return null;
        }).when(day).reopen(actor, Instant.parse("2026-08-31T22:10:00Z"), "Late sales");

        BusinessDayResponse response = service.reopen(dayId, new BusinessDayReopenRequest(3L, "Late sales"), authentication);

        assertThat(response.id()).isEqualTo(dayId);
        assertThat(response.storeId()).isEqualTo(storeId);
        assertThat(response.status()).isEqualTo(BusinessDayStatus.REOPENED);
        verify(day).reopen(actor, Instant.parse("2026-08-31T22:10:00Z"), "Late sales");
        verify(businessDays).saveAndFlush(day);
    }

    @Test
    void closedPreviousDayProducesOpenActionForNewStoreLocalDate() {
        useClock(Clock.fixed(Instant.parse("2026-09-01T03:10:00Z"), ZoneOffset.UTC));
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(store.getTimezone()).thenReturn("America/Moncton");
        when(businessDays.findByStore_IdAndBusinessDate(storeId, LocalDate.of(2026, 9, 1))).thenReturn(Optional.empty());
        when(businessDays.findFirstByStore_IdOrderByBusinessDateDescOpenedAtDesc(storeId)).thenReturn(Optional.of(day));
        when(businessDays.findFirstByStore_IdAndStatusInOrderByBusinessDateDescOpenedAtDesc(storeId, List.of(
                BusinessDayStatus.OPEN, BusinessDayStatus.CLOSING, BusinessDayStatus.REOPENED))).thenReturn(Optional.empty());
        stubResponseFields();

        BusinessDayOperationalStateResponse response = service.operationalState(storeId, authentication);

        assertThat(response.currentBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.nextBusinessDateAt()).isEqualTo(Instant.parse("2026-09-02T03:00:00Z"));
        assertThat(response.currentBusinessDay()).isNull();
        assertThat(response.previousBusinessDay().id()).isEqualTo(dayId);
        assertThat(response.state()).isEqualTo(BusinessDayOperationalState.HISTORICAL_CLOSED);
        assertThat(response.availableAction()).isEqualTo(BusinessDayAvailableAction.OPEN);
    }

    @Test
    void openPreviousDayBlocksOpeningNewStoreLocalDateAndReturnsConcreteDay() {
        useClock(Clock.fixed(Instant.parse("2026-09-02T03:10:00Z"), ZoneOffset.UTC));
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(store.getTimezone()).thenReturn("America/Moncton");
        when(day.getBusinessDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(day.getStatus()).thenReturn(BusinessDayStatus.OPEN);
        when(businessDays.findByStore_IdAndBusinessDate(storeId, LocalDate.of(2026, 9, 2))).thenReturn(Optional.empty());
        when(businessDays.findFirstByStore_IdOrderByBusinessDateDescOpenedAtDesc(storeId)).thenReturn(Optional.of(day));
        when(businessDays.findFirstByStore_IdAndStatusInOrderByBusinessDateDescOpenedAtDesc(storeId, List.of(
                BusinessDayStatus.OPEN, BusinessDayStatus.CLOSING, BusinessDayStatus.REOPENED))).thenReturn(Optional.of(day));
        stubResponseFields();

        BusinessDayOperationalStateResponse response = service.operationalState(storeId, authentication);

        assertThat(response.currentBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(response.currentBusinessDay()).isNull();
        assertThat(response.previousBusinessDay().id()).isEqualTo(dayId);
        assertThat(response.previousBusinessDay().businessDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.state()).isEqualTo(BusinessDayOperationalState.PREVIOUS_DAY_STILL_OPEN);
        assertThat(response.availableAction()).isEqualTo(BusinessDayAvailableAction.NONE);
    }

    @Test
    void closedCurrentDayProducesReopenAction() {
        useClock(Clock.fixed(Instant.parse("2026-09-01T03:10:00Z"), ZoneOffset.UTC));
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(store.getTimezone()).thenReturn("America/Moncton");
        when(day.getBusinessDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(businessDays.findByStore_IdAndBusinessDate(storeId, LocalDate.of(2026, 9, 1))).thenReturn(Optional.of(day));
        when(businessDays.findFirstByStore_IdOrderByBusinessDateDescOpenedAtDesc(storeId)).thenReturn(Optional.of(day));
        stubResponseFields();

        BusinessDayOperationalStateResponse response = service.operationalState(storeId, authentication);

        assertThat(response.currentBusinessDay().id()).isEqualTo(dayId);
        assertThat(response.state()).isEqualTo(BusinessDayOperationalState.CLOSED_TODAY);
        assertThat(response.availableAction()).isEqualTo(BusinessDayAvailableAction.REOPEN);
    }

    @Test
    void openCurrentDayIsResolvedOnlyForTheStoreLocalDate() {
        useClock(Clock.fixed(Instant.parse("2026-09-21T03:01:00Z"), ZoneOffset.UTC));
        when(stores.findById(storeId)).thenReturn(Optional.of(store));
        when(store.getTimezone()).thenReturn("America/Moncton");
        when(day.getBusinessDate()).thenReturn(LocalDate.of(2026, 9, 21));
        when(day.getStatus()).thenReturn(BusinessDayStatus.OPEN);
        when(businessDays.findByStore_IdAndBusinessDate(storeId, LocalDate.of(2026, 9, 21))).thenReturn(Optional.of(day));
        when(businessDays.findFirstByStore_IdOrderByBusinessDateDescOpenedAtDesc(storeId)).thenReturn(Optional.of(day));
        stubResponseFields();

        BusinessDayOperationalStateResponse response = service.operationalState(storeId, authentication);

        assertThat(response.currentBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(response.nextBusinessDateAt()).isEqualTo(Instant.parse("2026-09-22T03:00:00Z"));
        assertThat(response.currentBusinessDay().businessDate()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(response.state()).isEqualTo(BusinessDayOperationalState.OPEN);
        verify(businessDays).findByStore_IdAndBusinessDate(storeId, LocalDate.of(2026, 9, 21));
    }

    @Test
    void historicalClosedDayCannotBeReopened() {
        useClock(Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.reopen(dayId, new BusinessDayReopenRequest(3L, "Late sales"), authentication))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("HISTORICAL_BUSINESS_DAY");
    }

    private void stubResponseFields() {
        when(day.getTimezone()).thenReturn("America/Moncton");
        when(day.getOpenedAt()).thenReturn(Instant.parse("2026-08-31T12:00:00Z"));
        when(day.getOpenedBy()).thenReturn(actor);
        when(store.getCode()).thenReturn("DOWNTOWN");
        when(store.getName()).thenReturn("Downtown");
        when(actor.getId()).thenReturn(UUID.randomUUID());
        when(actor.getDisplayName()).thenReturn("Owner One");
    }

    private void useClock(Clock clock) {
        service = new BusinessDayService(
                businessDays, reports, configurations, stores, users, registerSessions, sales, refunds,
                cashMovements, inventoryTransactions, inventoryBalances, lotterySales, lotteryPayouts,
                lotterySaleCancellations, lotteryPayoutReversals, lotterySettlements, lotteryPosActivities, cashLedger, features,
                audit, new ObjectMapper(), clock);
    }
}
