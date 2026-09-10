package com.merchtyl.initialinventory;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "initial_inventory_imports")
public class InitialInventoryImport extends BaseUuidEntity {
    @Column(name="tenant_id",nullable=false,updatable=false) private UUID tenantId;
    @Column(name="store_id",nullable=false,updatable=false) private UUID storeId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private InitialInventoryImportStatus status;
    @Column(name="template_version",nullable=false) private int templateVersion;
    @Column(name="original_filename",nullable=false,length=255) private String originalFilename;
    @Column(name="uploaded_by",nullable=false,updatable=false) private UUID uploadedBy;
    @Column(name="total_rows",nullable=false) private int totalRows;
    @Column(name="product_count",nullable=false) private int productCount;
    @Column(name="valid_rows",nullable=false) private int validRows;
    @Column(name="warning_rows",nullable=false) private int warningRows;
    @Column(name="error_rows",nullable=false) private int errorRows;
    @Column(name="result_json",columnDefinition="text") private String resultJson;
    @Column(name="failure_code",length=80) private String failureCode;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="validated_at") private Instant validatedAt;
    @Column(name="confirmed_at") private Instant confirmedAt;
    @Column(name="completed_at") private Instant completedAt;
    @OneToMany(mappedBy="importJob",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("rowNumber ASC") private List<InitialInventoryImportRow> rows=new ArrayList<>();
    protected InitialInventoryImport() {}
    public InitialInventoryImport(UUID tenantId,UUID storeId,String filename,UUID uploadedBy,int templateVersion,Instant now){this.tenantId=tenantId;this.storeId=storeId;this.originalFilename=filename;this.uploadedBy=uploadedBy;this.templateVersion=templateVersion;this.status=InitialInventoryImportStatus.VALIDATED;this.expiresAt=now.plusSeconds(3600);this.validatedAt=now;initializeIdAndTimestamps();}
    public void addRow(InitialInventoryImportRow row){rows.add(row);row.attach(this);}
    public void summarize(int products,int valid,int errors){this.totalRows=rows.size();this.productCount=products;this.validRows=valid;this.errorRows=errors;this.warningRows=0;this.status=errors==0?InitialInventoryImportStatus.VALIDATED:InitialInventoryImportStatus.VALIDATION_FAILED;}
    public void processing(Instant now){status=InitialInventoryImportStatus.PROCESSING;confirmedAt=now;}
    public void complete(String result,Instant now){status=InitialInventoryImportStatus.COMPLETED;resultJson=result;completedAt=now;}
    public void expire(){status=InitialInventoryImportStatus.EXPIRED;failureCode="INITIAL_INVENTORY_IMPORT_EXPIRED";}
    public UUID getTenantId(){return tenantId;} public UUID getStoreId(){return storeId;} public InitialInventoryImportStatus getStatus(){return status;} public int getTemplateVersion(){return templateVersion;} public String getOriginalFilename(){return originalFilename;} public UUID getUploadedBy(){return uploadedBy;} public int getTotalRows(){return totalRows;} public int getProductCount(){return productCount;} public int getValidRows(){return validRows;} public int getWarningRows(){return warningRows;} public int getErrorRows(){return errorRows;} public String getResultJson(){return resultJson;} public Instant getExpiresAt(){return expiresAt;} public List<InitialInventoryImportRow> getRows(){return Collections.unmodifiableList(rows);}
}
