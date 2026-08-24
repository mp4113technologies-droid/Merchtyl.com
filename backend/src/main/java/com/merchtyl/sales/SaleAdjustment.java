package com.merchtyl.sales;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.security.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sale_adjustments")
public class SaleAdjustment extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_id")
    private SaleItem saleItem;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SaleAdjustmentType type;
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal originalAmount;
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal adjustedAmount;
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal difference;
    @Column(precision = 7, scale = 4)
    private BigDecimal percentage;
    @Column(nullable = false, length = 64)
    private String reasonCode;
    @Column(length = 500)
    private String reasonText;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;
    @Column(nullable = false)
    private Instant requestedAt;
    private Instant approvedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleAdjustmentStatus status;
    @Column(length = 100)
    private String correlationId;

    protected SaleAdjustment() {}

    SaleAdjustment(Sale sale, SaleItem saleItem, SaleAdjustmentType type, BigDecimal originalAmount,
                   BigDecimal adjustedAmount, BigDecimal percentage, String reasonCode, String reasonText,
                   User requestedBy, User approvedBy, Instant now, String correlationId) {
        this.sale = sale;
        this.saleItem = saleItem;
        this.type = type;
        this.originalAmount = originalAmount;
        this.adjustedAmount = adjustedAmount;
        this.difference = adjustedAmount.subtract(originalAmount);
        this.percentage = percentage;
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
        this.requestedBy = requestedBy;
        this.approvedBy = approvedBy;
        this.requestedAt = now;
        this.approvedAt = approvedBy == null ? null : now;
        this.status = approvedBy == null ? SaleAdjustmentStatus.PENDING : SaleAdjustmentStatus.APPROVED;
        this.correlationId = correlationId;
        initializeIdAndTimestamps();
    }
}
