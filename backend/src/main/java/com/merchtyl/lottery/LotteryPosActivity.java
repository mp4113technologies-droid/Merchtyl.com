package com.merchtyl.lottery;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.register.Register;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lottery_pos_activities", uniqueConstraints = @UniqueConstraint(name = "uq_lottery_pos_activities_operation", columnNames = "operation_id"))
public class LotteryPosActivity extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "store_id", nullable = false) private Store store;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "register_id", nullable = false) private Register register;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "register_session_id", nullable = false) private RegisterSession registerSession;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cashier_id", nullable = false) private User cashier;
    @Enumerated(EnumType.STRING) @Column(name = "activity_type", nullable = false, length = 16) private LotteryPosActivityType type;
    @Column(nullable = false, length = 16) private String source;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "currency_code", nullable = false, length = 3) private String currencyCode;
    @Column(name = "business_date", nullable = false) private LocalDate businessDate;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "operation_id", nullable = false) private UUID operationId;

    protected LotteryPosActivity() {}

    LotteryPosActivity(RegisterSession session, User cashier, LotteryPosActivityType type, BigDecimal amount,
                       String currencyCode, LocalDate businessDate, Instant occurredAt, UUID operationId) {
        this.store = session.getStore();
        this.register = session.getRegister();
        this.registerSession = session;
        this.cashier = cashier;
        this.type = type;
        this.source = "MANUAL_POS";
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.businessDate = businessDate;
        this.occurredAt = occurredAt;
        this.operationId = operationId;
        initializeIdAndTimestamps();
    }

    public Store getStore() { return store; }
    public Register getRegister() { return register; }
    public RegisterSession getRegisterSession() { return registerSession; }
    public User getCashier() { return cashier; }
    public LotteryPosActivityType getType() { return type; }
    public String getSource() { return source; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public LocalDate getBusinessDate() { return businessDate; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getOperationId() { return operationId; }
}
