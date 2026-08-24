package com.merchtyl.inventory;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
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
@Table(name = "stock_counts")
public class StockCount extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, foreignKey = @ForeignKey(name = "fk_stock_counts_store"))
    private Store store;

    @Column(nullable = false, length = 255)
    private String reference;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private StockCountStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", foreignKey = @ForeignKey(name = "fk_stock_counts_created_by_user"))
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id", foreignKey = @ForeignKey(name = "fk_stock_counts_reviewed_by_user"))
    private User reviewedByUser;

    @Column
    private Instant reviewedAt;

    @Column(length = 1000)
    private String reviewNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by_user_id", foreignKey = @ForeignKey(name = "fk_stock_counts_posted_by_user"))
    private User postedByUser;

    @Column
    private Instant postedAt;

    @Column(length = 1000)
    private String postNotes;

    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<StockCountLine> lines = new ArrayList<>();

    protected StockCount() {
    }

    public StockCount(Store store, String reference, String notes, User createdByUser) {
        this.store = store;
        this.reference = reference;
        this.notes = notes;
        this.createdByUser = createdByUser;
        this.status = StockCountStatus.DRAFT;
        initializeIdAndTimestamps();
    }

    public void addLine(StockCountLine line) {
        lines.add(line);
    }

    public void enterCountedQuantity(StockCountLine line, java.math.BigDecimal countedQuantity) {
        line.enterCountedQuantity(countedQuantity);
    }

    public void markSaved(User actor, Instant savedAt) {
        this.status = StockCountStatus.SAVED;
        this.postedByUser = actor;
        this.postedAt = savedAt;
        this.reviewedByUser = null;
        this.reviewedAt = null;
        this.reviewNotes = null;
        this.postNotes = null;
    }

    public void review(User reviewedByUser, Instant reviewedAt, String reviewNotes) {
        requireDraft();
        if (lines.isEmpty()) {
            throw new BadRequestException("At least one count line is required");
        }
        if (lines.stream().anyMatch(line -> line.getCountedQuantity() == null)) {
            throw new BadRequestException("All count lines require counted quantity before review");
        }
        this.status = StockCountStatus.IN_REVIEW;
        this.reviewedByUser = reviewedByUser;
        this.reviewedAt = reviewedAt;
        this.reviewNotes = reviewNotes;
    }

    public void post(User postedByUser, Instant postedAt, String postNotes) {
        if (status == StockCountStatus.POSTED) {
            throw new ConflictException("Stock count is already posted");
        }
        if (status != StockCountStatus.IN_REVIEW) {
            throw new ConflictException("Stock count must be in review before posting");
        }
        this.status = StockCountStatus.POSTED;
        this.postedByUser = postedByUser;
        this.postedAt = postedAt;
        this.postNotes = postNotes;
    }

    private void requireDraft() {
        if (status != StockCountStatus.DRAFT) {
            throw new ConflictException("Stock count can only be edited while in draft");
        }
    }

    public Store getStore() {
        return store;
    }

    public String getReference() {
        return reference;
    }

    public String getNotes() {
        return notes;
    }

    public StockCountStatus getStatus() {
        return status;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public User getReviewedByUser() {
        return reviewedByUser;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public User getPostedByUser() {
        return postedByUser;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public String getPostNotes() {
        return postNotes;
    }

    public List<StockCountLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
