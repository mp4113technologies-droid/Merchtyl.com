package com.merchtyl.receipts;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.sales.Sale;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "receipts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_receipts_sale", columnNames = "sale_id"),
                @UniqueConstraint(name = "uq_receipts_number", columnNames = "receipt_number")
        })
public class Receipt extends BaseUuidEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_receipts_sale"))
    private Sale sale;

    @Column(name = "receipt_number", nullable = false, updatable = false, length = 80)
    private String receiptNumber;

    @Column(nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(nullable = false)
    private int reprintCount;

    @Column
    private Instant lastReprintedAt;

    @OneToOne(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ReceiptDocument document;

    protected Receipt() {
    }

    Receipt(Sale sale, String receiptNumber, Instant generatedAt, String documentJson) {
        this.sale = sale;
        this.receiptNumber = receiptNumber;
        this.generatedAt = generatedAt;
        this.reprintCount = 0;
        this.document = new ReceiptDocument(this, documentJson);
        initializeIdAndTimestamps();
    }

    void markReprinted(Instant reprintedAt) {
        this.reprintCount++;
        this.lastReprintedAt = reprintedAt;
    }

    public Sale getSale() {
        return sale;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public int getReprintCount() {
        return reprintCount;
    }

    public Instant getLastReprintedAt() {
        return lastReprintedAt;
    }

    public ReceiptDocument getDocument() {
        return document;
    }
}
