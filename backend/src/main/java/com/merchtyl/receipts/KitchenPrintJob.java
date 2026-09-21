package com.merchtyl.receipts;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kitchen_print_jobs", uniqueConstraints =
        @UniqueConstraint(name = "uq_kitchen_print_jobs_sale_type", columnNames = {"sale_id", "type"}))
public class KitchenPrintJob extends BaseUuidEntity {
    @Column(name="tenant_id", nullable=false) private UUID tenantId;
    @Column(name="store_id", nullable=false) private UUID storeId;
    @Column(name="sale_id", nullable=false) private UUID saleId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private PrintDocumentType type;
    @Column(name="scheduled_at", nullable=false) private Instant scheduledAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private KitchenPrintJobStatus status;
    @Column(name="attempt_count", nullable=false) private int attemptCount;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private KitchenPrintOrigin origin;
    @Column(name="claimed_by", length=255) private String claimedBy;
    @Column(name="last_attempt_at") private Instant lastAttemptAt;
    @Column(name="printed_at") private Instant printedAt;
    @Column(name="last_error", length=1000) private String lastError;

    protected KitchenPrintJob() {}
    KitchenPrintJob(UUID tenantId, UUID storeId, UUID saleId, Instant scheduledAt) {
        this.tenantId=tenantId; this.storeId=storeId; this.saleId=saleId;
        this.type=PrintDocumentType.KITCHEN_TICKET; this.scheduledAt=scheduledAt;
        this.status=KitchenPrintJobStatus.SCHEDULED; this.origin=KitchenPrintOrigin.AUTOMATIC;
    }
    void reschedule(Instant at) { if (status != KitchenPrintJobStatus.PRINTED) { scheduledAt=at; status=KitchenPrintJobStatus.SCHEDULED; claimedBy=null; lastError=null; } }
    void claim(String clientId, Instant now, KitchenPrintOrigin printOrigin) { status=KitchenPrintJobStatus.DISPATCHED; claimedBy=clientId; lastAttemptAt=now; attemptCount++; origin=printOrigin; lastError=null; }
    void acknowledge(boolean success, String error, Instant now) { if (success) { status=KitchenPrintJobStatus.PRINTED; printedAt=now; lastError=null; } else { status=KitchenPrintJobStatus.FAILED; lastError=error; scheduledAt=now.plusSeconds(Math.min(300, 15L * Math.max(1, attemptCount))); } claimedBy=null; }
    void cancel() { if (status != KitchenPrintJobStatus.PRINTED) { status=KitchenPrintJobStatus.CANCELLED; claimedBy=null; } }
    void recover(Instant retryAt) { status=KitchenPrintJobStatus.FAILED; claimedBy=null; scheduledAt=retryAt; lastError="Print client did not acknowledge dispatch"; }
    public UUID getTenantId(){return tenantId;} public UUID getStoreId(){return storeId;} public UUID getSaleId(){return saleId;}
    public PrintDocumentType getType(){return type;} public Instant getScheduledAt(){return scheduledAt;} public KitchenPrintJobStatus getStatus(){return status;}
    public int getAttemptCount(){return attemptCount;} public KitchenPrintOrigin getOrigin(){return origin;} public String getClaimedBy(){return claimedBy;}
    public Instant getLastAttemptAt(){return lastAttemptAt;} public Instant getPrintedAt(){return printedAt;} public String getLastError(){return lastError;}
}
