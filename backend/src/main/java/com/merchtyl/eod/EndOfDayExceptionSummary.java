package com.merchtyl.eod;

import com.merchtyl.platform.persistence.BaseUuidEntity;
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

@Entity
@Table(
        name = "end_of_day_exception_summaries",
        uniqueConstraints = @UniqueConstraint(name = "uq_eod_exception_summaries_type", columnNames = {"report_id", "exception_type"}))
public class EndOfDayExceptionSummary extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_exception_summaries_report"))
    private EndOfDayReport report;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 80)
    private EndOfDayExceptionType exceptionType;

    @Column(nullable = false, updatable = false)
    private long count;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String details;

    protected EndOfDayExceptionSummary() {
    }

    public EndOfDayExceptionSummary(EndOfDayReport report, EndOfDayExceptionType exceptionType, long count, BigDecimal totalAmount, String details) {
        this.report = report;
        this.exceptionType = exceptionType;
        this.count = count;
        this.totalAmount = totalAmount;
        this.details = details;
        initializeIdAndTimestamps();
    }

    public EndOfDayExceptionType getExceptionType() { return exceptionType; }
    public long getCount() { return count; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getDetails() { return details; }
}
