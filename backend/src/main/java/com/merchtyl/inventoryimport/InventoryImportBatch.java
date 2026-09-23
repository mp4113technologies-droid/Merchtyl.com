package com.merchtyl.inventoryimport;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="inventory_import_batches")
public class InventoryImportBatch extends BaseUuidEntity {
    @Column(nullable=false) private UUID tenantId;
    @Column(nullable=false) private UUID storeId;
    @Column(nullable=false,length=255) private String filename;
    @Column(nullable=false) private UUID uploadedBy;
    @Column(nullable=false,length=32) private String status;
    @Column(nullable=false,columnDefinition="text") private String payloadJson;
    @Column(nullable=false) private Instant uploadedAt;
    private UUID confirmedBy; private Instant confirmedAt; private int changedRows;
    protected InventoryImportBatch() {}
    public InventoryImportBatch(UUID tenantId,UUID storeId,String filename,UUID uploadedBy,String payloadJson,Instant now){this.tenantId=tenantId;this.storeId=storeId;this.filename=filename;this.uploadedBy=uploadedBy;this.payloadJson=payloadJson;this.status="VALIDATED";this.uploadedAt=now;initializeIdAndTimestamps();}
    public void confirm(UUID actor,int changed,Instant now){status="COMPLETED";confirmedBy=actor;confirmedAt=now;changedRows=changed;}
    public UUID getTenantId(){return tenantId;} public UUID getStoreId(){return storeId;} public String getStatus(){return status;} public String getPayloadJson(){return payloadJson;} public int getChangedRows(){return changedRows;}
}
