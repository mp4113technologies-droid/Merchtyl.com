package com.merchtyl.eod;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "business_day_configurations")
public class BusinessDayConfiguration extends BaseUuidEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_business_day_configurations_store"))
    private Store store;

    @Column(nullable = false)
    private boolean requireAllRegistersClosed;
    @Column(nullable = false)
    private boolean allowForceClose;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cashVarianceExplanationThreshold;
    @Column(nullable = false)
    private boolean requireManagerSignOff;
    @Column(nullable = false)
    private boolean blockNextBusinessDayUntilPreviousClose;
    @Column(nullable = false)
    private boolean automaticallyOpenBusinessDay;
    @Column(nullable = false)
    private boolean automaticallyGenerateReportAfterFinalRegisterCloses;
    @Column(nullable = false)
    private boolean enableCompactThermalEodSummary;
    @Column(nullable = false)
    private int reportRetentionDays;
    @Column
    private LocalTime closingReminderTime;

    protected BusinessDayConfiguration() {
    }

    public static BusinessDayConfiguration defaults(Store store) {
        BusinessDayConfiguration configuration = new BusinessDayConfiguration();
        configuration.store = store;
        configuration.requireAllRegistersClosed = true;
        configuration.allowForceClose = true;
        configuration.cashVarianceExplanationThreshold = new BigDecimal("5.00");
        configuration.requireManagerSignOff = true;
        configuration.blockNextBusinessDayUntilPreviousClose = true;
        configuration.automaticallyOpenBusinessDay = false;
        configuration.automaticallyGenerateReportAfterFinalRegisterCloses = false;
        configuration.enableCompactThermalEodSummary = false;
        configuration.reportRetentionDays = 2555;
        configuration.initializeIdAndTimestamps();
        return configuration;
    }

    public Store getStore() { return store; }
    public boolean isRequireAllRegistersClosed() { return requireAllRegistersClosed; }
    public boolean isAllowForceClose() { return allowForceClose; }
    public BigDecimal getCashVarianceExplanationThreshold() { return cashVarianceExplanationThreshold; }
    public boolean isRequireManagerSignOff() { return requireManagerSignOff; }
    public boolean isBlockNextBusinessDayUntilPreviousClose() { return blockNextBusinessDayUntilPreviousClose; }
    public boolean isAutomaticallyOpenBusinessDay() { return automaticallyOpenBusinessDay; }
    public boolean isAutomaticallyGenerateReportAfterFinalRegisterCloses() { return automaticallyGenerateReportAfterFinalRegisterCloses; }
    public boolean isEnableCompactThermalEodSummary() { return enableCompactThermalEodSummary; }
    public int getReportRetentionDays() { return reportRetentionDays; }
    public LocalTime getClosingReminderTime() { return closingReminderTime; }
}
