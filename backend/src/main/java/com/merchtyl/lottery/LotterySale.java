package com.merchtyl.lottery;

import com.merchtyl.device.Device;
import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.sales.PaymentMethod;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "lottery_sales",
        uniqueConstraints = @UniqueConstraint(name = "uq_lottery_sales_operation_id", columnNames = "operation_id"))
public class LotterySale extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_sales_operator"))
    private LotteryOperator operator;

    @Column(length = 180)
    private String operatorReference;

    @Column(length = 180)
    private String ticketReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotteryGameType gameType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentMethod paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_sales_store"))
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_sales_register"))
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_sales_device"))
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lottery_sales_cashier"))
    private User cashier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "register_session_id", foreignKey = @ForeignKey(name = "fk_lottery_sales_register_session"))
    private RegisterSession registerSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotterySaleStatus status;

    @Column(nullable = false)
    private UUID operationId;

    @Column(nullable = false)
    private Instant occurredAt;

    protected LotterySale() {
    }

    LotterySale(
            LotteryOperator operator,
            String operatorReference,
            String ticketReference,
            LotteryGameType gameType,
            BigDecimal amount,
            String currencyCode,
            PaymentMethod paymentMethod,
            Store store,
            Register register,
            Device device,
            User cashier,
            RegisterSession registerSession,
            UUID operationId,
            Instant occurredAt) {
        this.operator = operator;
        this.operatorReference = operatorReference;
        this.ticketReference = ticketReference;
        this.gameType = gameType;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.paymentMethod = paymentMethod;
        this.store = store;
        this.register = register;
        this.device = device;
        this.cashier = cashier;
        this.registerSession = registerSession;
        this.status = LotterySaleStatus.RECORDED;
        this.operationId = operationId;
        this.occurredAt = occurredAt;
        initializeIdAndTimestamps();
    }

    void cancel() {
        this.status = LotterySaleStatus.CANCELLED;
    }

    public LotteryOperator getOperator() {
        return operator;
    }

    public String getOperatorReference() {
        return operatorReference;
    }

    public String getTicketReference() {
        return ticketReference;
    }

    public LotteryGameType getGameType() {
        return gameType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public Store getStore() {
        return store;
    }

    public Register getRegister() {
        return register;
    }

    public Device getDevice() {
        return device;
    }

    public User getCashier() {
        return cashier;
    }

    public RegisterSession getRegisterSession() {
        return registerSession;
    }

    public LotterySaleStatus getStatus() {
        return status;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
