package com.merchtyl.receipts;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "receipt_documents",
        uniqueConstraints = @UniqueConstraint(name = "uq_receipt_documents_receipt", columnNames = "receipt_id"))
public class ReceiptDocument extends BaseUuidEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_receipt_documents_receipt"))
    private Receipt receipt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_json", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String documentJson;

    protected ReceiptDocument() {
    }

    ReceiptDocument(Receipt receipt, String documentJson) {
        this.receipt = receipt;
        this.documentJson = documentJson;
        initializeIdAndTimestamps();
    }

    public Receipt getReceipt() {
        return receipt;
    }

    public String getDocumentJson() {
        return documentJson;
    }
}
