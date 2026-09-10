package com.merchtyl.initialinventory;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Entity @Table(name="initial_inventory_import_rows")
public class InitialInventoryImportRow extends BaseUuidEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="import_id",nullable=false) private InitialInventoryImport importJob;
    @Column(name="row_number",nullable=false) private int rowNumber;
    @Column(name="product_reference",length=32) private String productReference;
    @Column(name="product_name",nullable=false,length=180) private String productName;
    @Column(name="variant_name",nullable=false,length=180) private String variantName;
    @Column(length=128) private String barcode;
    @Column(name="additional_barcodes",length=4000) private String additionalBarcodes;
    @Column(nullable=false,length=64) private String sku;
    @Column(name="selling_price",nullable=false,precision=19,scale=4) private BigDecimal sellingPrice;
    @Column(name="cost_price",nullable=false,precision=19,scale=4) private BigDecimal costPrice;
    @Column(name="opening_quantity",nullable=false,precision=19,scale=4) private BigDecimal openingQuantity;
    @Column(name="low_stock_level",precision=19,scale=4) private BigDecimal lowStockLevel;
    @Column(length=180) private String category; @Column(length=180) private String brand; @Column(name="unit_code",length=64) private String unitCode;
    @Column(name="tax_category",nullable=false,length=180) private String taxCategory; @Column(length=180) private String supplier;
    @Column(name="age_restricted",nullable=false) private boolean ageRestricted; @Column(nullable=false) private boolean active;
    @Column(name="product_action",nullable=false,length=32) private String productAction; @Column(name="variant_action",nullable=false,length=32) private String variantAction;
    @Column(name="product_id") private UUID productId; @Column(name="variant_id") private UUID variantId;
    @Column(name="error_codes",nullable=false,columnDefinition="text") private String errorCodes="";
    protected InitialInventoryImportRow(){}
    public InitialInventoryImportRow(int rowNumber,String productReference,String productName,String variantName,String barcode,String additionalBarcodes,String sku,BigDecimal sellingPrice,BigDecimal costPrice,BigDecimal openingQuantity,BigDecimal lowStockLevel,String category,String brand,String unitCode,String taxCategory,String supplier,boolean ageRestricted,boolean active,String productAction,String variantAction,UUID productId,UUID variantId,List<String> errors){this.rowNumber=rowNumber;this.productReference=productReference;this.productName=productName;this.variantName=variantName;this.barcode=barcode;this.additionalBarcodes=additionalBarcodes;this.sku=sku;this.sellingPrice=sellingPrice;this.costPrice=costPrice;this.openingQuantity=openingQuantity;this.lowStockLevel=lowStockLevel;this.category=category;this.brand=brand;this.unitCode=unitCode;this.taxCategory=taxCategory;this.supplier=supplier;this.ageRestricted=ageRestricted;this.active=active;this.productAction=productAction;this.variantAction=variantAction;this.productId=productId;this.variantId=variantId;this.errorCodes=String.join(",",errors);initializeIdAndTimestamps();}
    void attach(InitialInventoryImport value){importJob=value;}
    public List<String> errors(){return errorCodes.isBlank()?List.of():List.of(errorCodes.split(","));}
    public int getRowNumber(){return rowNumber;} public String getProductReference(){return productReference;} public String getProductName(){return productName;} public String getVariantName(){return variantName;} public String getBarcode(){return barcode;} public String getAdditionalBarcodes(){return additionalBarcodes;} public String getSku(){return sku;} public BigDecimal getSellingPrice(){return sellingPrice;} public BigDecimal getCostPrice(){return costPrice;} public BigDecimal getOpeningQuantity(){return openingQuantity;} public BigDecimal getLowStockLevel(){return lowStockLevel;} public String getCategory(){return category;} public String getBrand(){return brand;} public String getUnitCode(){return unitCode;} public String getTaxCategory(){return taxCategory;} public String getSupplier(){return supplier;} public boolean isAgeRestricted(){return ageRestricted;} public boolean isActive(){return active;} public String getProductAction(){return productAction;} public String getVariantAction(){return variantAction;} public UUID getProductId(){return productId;} public UUID getVariantId(){return variantId;}
}
