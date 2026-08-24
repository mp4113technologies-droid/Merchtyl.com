package com.merchtyl.lottery;

import com.merchtyl.device.Device;
import com.merchtyl.device.DeviceRepository;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterRepository;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import com.merchtyl.tax.Country;
import com.merchtyl.tax.CountryRepository;
import com.merchtyl.tax.TaxJurisdiction;
import com.merchtyl.tax.TaxJurisdictionRepository;
import com.merchtyl.tax.TaxJurisdictionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class LotterySettlementIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    LotterySettlementService lotterySettlementService;

    @Autowired
    LotterySettlementRepository lotterySettlementRepository;

    @Autowired
    LotteryCommissionRuleRepository lotteryCommissionRuleRepository;

    @Autowired
    LotteryPayoutReversalRepository lotteryPayoutReversalRepository;

    @Autowired
    LotterySaleCancellationRepository lotterySaleCancellationRepository;

    @Autowired
    LotteryPayoutRepository lotteryPayoutRepository;

    @Autowired
    LotterySaleRepository lotterySaleRepository;

    @Autowired
    LotteryPayoutPolicyRepository lotteryPayoutPolicyRepository;

    @Autowired
    RegisterSessionRepository registerSessionRepository;

    @Autowired
    DeviceRepository deviceRepository;

    @Autowired
    RegisterRepository registerRepository;

    @Autowired
    LotteryOperatorRepository lotteryOperatorRepository;

    @Autowired
    TaxJurisdictionRepository taxJurisdictionRepository;

    @Autowired
    CountryRepository countryRepository;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void resetData() {
        lotterySettlementRepository.deleteAll();
        lotteryPayoutReversalRepository.deleteAll();
        lotterySaleCancellationRepository.deleteAll();
        lotteryCommissionRuleRepository.deleteAll();
        lotteryPayoutRepository.deleteAll();
        lotterySaleRepository.deleteAll();
        lotteryPayoutPolicyRepository.deleteAll();
        registerSessionRepository.deleteAll();
        deviceRepository.deleteAll();
        registerRepository.deleteAll();
        lotteryOperatorRepository.deleteAll();
        taxJurisdictionRepository.deleteAll();
        countryRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void calculatesSettlementTotalsAndCommissionFromLotteryActivity() throws Exception {
        Fixture fixture = fixture();
        LotterySale recordedSale = lotterySaleRepository.saveAndFlush(sale(
                fixture,
                new BigDecimal("100.00"),
                Instant.parse("2026-08-01T16:00:00Z")));
        LotterySale cancelledSale = lotterySaleRepository.saveAndFlush(sale(
                fixture,
                new BigDecimal("50.00"),
                Instant.parse("2026-08-02T16:00:00Z")));
        cancelledSale.cancel();
        lotterySaleRepository.saveAndFlush(cancelledSale);
        lotterySaleCancellationRepository.saveAndFlush(new LotterySaleCancellation(
                cancelledSale,
                fixture.cashier(),
                "Customer cancellation",
                true,
                UUID.randomUUID(),
                Instant.parse("2026-08-02T17:00:00Z")));
        LotteryPayout paidPayout = payout(fixture, new BigDecimal("40.00"), LocalDate.parse("2026-08-03"));
        paidPayout.completeCash(fixture.cashier(), Instant.parse("2026-08-03T18:00:00Z"));
        lotteryPayoutRepository.saveAndFlush(paidPayout);
        LotteryPayout reversedPayout = payout(fixture, new BigDecimal("30.00"), LocalDate.parse("2026-08-04"));
        reversedPayout.completeCash(fixture.cashier(), Instant.parse("2026-08-04T18:00:00Z"));
        lotteryPayoutRepository.saveAndFlush(reversedPayout);
        lotteryPayoutReversalRepository.saveAndFlush(new LotteryPayoutReversal(
                reversedPayout,
                fixture.cashier(),
                "Correction",
                UUID.randomUUID(),
                Instant.parse("2026-08-04T19:00:00Z")));
        reversedPayout.reverse();
        lotteryPayoutRepository.saveAndFlush(reversedPayout);
        lotteryCommissionRuleRepository.saveAndFlush(rule(
                fixture,
                "Sales percent",
                LotteryCommissionRuleType.PERCENT_OF_SALES,
                new BigDecimal("10.0000"),
                null,
                null));
        lotteryCommissionRuleRepository.saveAndFlush(rule(
                fixture,
                "Per transaction",
                LotteryCommissionRuleType.FIXED_PER_TRANSACTION,
                null,
                new BigDecimal("1.00"),
                null));
        lotteryCommissionRuleRepository.saveAndFlush(rule(
                fixture,
                "Weekly fee",
                LotteryCommissionRuleType.FIXED_PER_PERIOD,
                null,
                new BigDecimal("5.00"),
                LotteryCommissionPeriod.WEEKLY));
        lotteryCommissionRuleRepository.saveAndFlush(rule(
                fixture,
                "Manual commission",
                LotteryCommissionRuleType.MANUAL,
                null,
                null,
                null));

        LotterySettlementResponse response = lotterySettlementService.calculate(new LotterySettlementCalculationRequest(
                fixture.operator().getId(),
                fixture.store().getId(),
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-07")));

        assertThat(recordedSale.getId()).isNotNull();
        assertThat(response.grossSales()).isEqualByComparingTo("150.00");
        assertThat(response.totalPayouts()).isEqualByComparingTo("70.00");
        assertThat(response.cancellations()).isEqualByComparingTo("50.00");
        assertThat(response.adjustments()).isEqualByComparingTo("30.00");
        assertThat(response.commission()).isEqualByComparingTo("26.00");
        assertThat(response.expectedSettlement()).isEqualByComparingTo("34.00");
        assertThat(response.currencyCode()).isEqualTo("USD");
        assertThat(lotterySettlementRepository.findAll()).hasSize(1);
    }

    @Test
    void recalculatesExistingSettlementForSameOperatorStoreAndPeriod() throws Exception {
        Fixture fixture = fixture();
        lotterySaleRepository.saveAndFlush(sale(
                fixture,
                new BigDecimal("25.00"),
                Instant.parse("2026-08-01T16:00:00Z")));
        LotterySettlementResponse first = lotterySettlementService.calculate(new LotterySettlementCalculationRequest(
                fixture.operator().getId(),
                fixture.store().getId(),
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-07")));
        lotterySaleRepository.saveAndFlush(sale(
                fixture,
                new BigDecimal("75.00"),
                Instant.parse("2026-08-02T16:00:00Z")));

        LotterySettlementResponse second = lotterySettlementService.calculate(new LotterySettlementCalculationRequest(
                fixture.operator().getId(),
                fixture.store().getId(),
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-07")));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.grossSales()).isEqualByComparingTo("100.00");
        assertThat(second.expectedSettlement()).isEqualByComparingTo("100.00");
        assertThat(lotterySettlementRepository.findAll()).hasSize(1);
    }

    private Fixture fixture() throws Exception {
        Country country = countryRepository.saveAndFlush(new Country("US", "United States", true));
        TaxJurisdiction jurisdiction = taxJurisdictionRepository.saveAndFlush(new TaxJurisdiction(
                country,
                null,
                "CA",
                "California",
                TaxJurisdictionType.STATE,
                true));
        Store store = storeRepository.saveAndFlush(store());
        Register register = registerRepository.saveAndFlush(register(store));
        Device device = deviceRepository.saveAndFlush(device(store, register));
        User cashier = userRepository.saveAndFlush(new User("cashier@settlement.test", "Cashier", "hash"));
        RegisterSession session = registerSessionRepository.saveAndFlush(session(store, register, device, cashier));
        LotteryOperator operator = lotteryOperatorRepository.saveAndFlush(new LotteryOperator(new LotteryOperatorValues(
                "STATE",
                "State Lottery",
                jurisdiction,
                "support@example.test",
                SettlementFrequency.WEEKLY,
                true)));
        LotteryPayoutPolicy policy = lotteryPayoutPolicyRepository.saveAndFlush(new LotteryPayoutPolicy(new LotteryPayoutPolicyValues(
                operator,
                jurisdiction,
                store,
                new BigDecimal("2500.00"),
                new BigDecimal("200.00"),
                new BigDecimal("500.00"),
                new BigDecimal("2500.00"),
                new BigDecimal("150.00"),
                true,
                true,
                false,
                false,
                false,
                false,
                LocalDate.parse("2026-08-01"),
                null,
                LotteryPayoutPolicyStatus.ACTIVE)));
        return new Fixture(operator, store, register, device, cashier, session, policy);
    }

    private static LotterySale sale(Fixture fixture, BigDecimal amount, Instant occurredAt) {
        return new LotterySale(
                fixture.operator(),
                null,
                UUID.randomUUID().toString(),
                LotteryGameType.DRAW_TICKET,
                amount,
                "USD",
                PaymentMethod.CASH,
                fixture.store(),
                fixture.register(),
                fixture.device(),
                fixture.cashier(),
                fixture.session(),
                UUID.randomUUID(),
                occurredAt);
    }

    private static LotteryPayout payout(Fixture fixture, BigDecimal amount, LocalDate businessDate) {
        return new LotteryPayout(
                fixture.operator(),
                fixture.policy(),
                fixture.store(),
                fixture.register(),
                fixture.device(),
                fixture.cashier(),
                fixture.session(),
                UUID.randomUUID().toString(),
                amount,
                "USD",
                LotteryPayoutMethod.CASH,
                businessDate,
                businessDate.atTime(13, 0).toInstant(java.time.ZoneOffset.UTC),
                null);
    }

    private static LotteryCommissionRule rule(
            Fixture fixture,
            String name,
            LotteryCommissionRuleType type,
            BigDecimal rate,
            BigDecimal fixedAmount,
            LotteryCommissionPeriod period) {
        return new LotteryCommissionRule(new LotteryCommissionRuleValues(
                name,
                fixture.operator(),
                fixture.operator().getJurisdiction(),
                fixture.store(),
                type,
                rate,
                fixedAmount,
                fixedAmount == null ? null : "USD",
                period,
                LocalDate.parse("2026-08-01"),
                null,
                LotteryCommissionRuleStatus.ACTIVE,
                null));
    }

    private static Store store() throws Exception {
        Constructor<Store> constructor = Store.class.getDeclaredConstructor(
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                boolean.class,
                boolean.class,
                boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                "MAIN",
                "Main Store",
                null,
                "US",
                "CA",
                "100 Market Street",
                null,
                null,
                "USD",
                "en-US",
                "America/Los_Angeles",
                false,
                false,
                true);
    }

    private static Register register(Store store) throws Exception {
        Constructor<Register> constructor = Register.class.getDeclaredConstructor(
                Store.class,
                String.class,
                String.class,
                String.class,
                boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(store, "FRONT-1", "Front Register", "Front counter", true);
    }

    private static Device device(Store store, Register register) throws Exception {
        Constructor<Device> constructor = Device.class.getDeclaredConstructor(
                Store.class,
                Register.class,
                String.class,
                String.class,
                String.class,
                boolean.class,
                Instant.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                store,
                register,
                "browser:settlement-test",
                "Front Browser",
                "BROWSER_POS",
                true,
                Instant.parse("2026-08-01T12:00:00Z"));
    }

    private static RegisterSession session(Store store, Register register, Device device, User cashier) throws Exception {
        Constructor<RegisterSession> constructor = RegisterSession.class.getDeclaredConstructor(
                Store.class,
                Register.class,
                Device.class,
                User.class,
                BigDecimal.class,
                Instant.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                store,
                register,
                device,
                cashier,
                new BigDecimal("100.00"),
                Instant.parse("2026-08-01T12:00:00Z"));
    }

    private record Fixture(
            LotteryOperator operator,
            Store store,
            Register register,
            Device device,
            User cashier,
            RegisterSession session,
            LotteryPayoutPolicy policy) {
    }
}
