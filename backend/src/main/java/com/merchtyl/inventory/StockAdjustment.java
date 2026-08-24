package com.merchtyl.inventory;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.security.User;
import com.merchtyl.store.Store;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "stock_adjustments")
public class StockAdjustment extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stock_adjustments_store"))
    private Store store;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StockAdjustmentApprovalStatus approvalStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id", foreignKey = @ForeignKey(name = "fk_stock_adjustments_approved_by_user"))
    private User approvedByUser;

    @Column(nullable = false)
    private Instant approvedAt;

    @Column(length = 1000)
    private String approvalNotes;

    @OneToMany(mappedBy = "adjustment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<StockAdjustmentLine> lines = new ArrayList<>();

    protected StockAdjustment() {
    }

    public StockAdjustment(
            Store store,
            String reason,
            String notes,
            User approvedByUser,
            Instant approvedAt,
            String approvalNotes) {
        this.store = store;
        this.reason = reason;
        this.notes = notes;
        this.approvalStatus = StockAdjustmentApprovalStatus.POSTED;
        this.approvedByUser = approvedByUser;
        this.approvedAt = approvedAt;
        this.approvalNotes = approvalNotes;
        initializeIdAndTimestamps();
    }

    public void addLine(StockAdjustmentLine line) {
        lines.add(line);
    }

    public Store getStore() {
        return store;
    }

    public String getReason() {
        return reason;
    }

    public String getNotes() {
        return notes;
    }

    public StockAdjustmentApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public User getApprovedByUser() {
        return approvedByUser;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getApprovalNotes() {
        return approvalNotes;
    }

    public List<StockAdjustmentLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
