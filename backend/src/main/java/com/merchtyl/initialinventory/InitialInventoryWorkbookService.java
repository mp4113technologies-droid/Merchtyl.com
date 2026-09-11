package com.merchtyl.initialinventory;

import com.merchtyl.catalogue.*;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.store.Store;
import com.merchtyl.supplier.Supplier;
import com.merchtyl.supplier.SupplierRepository;
import com.merchtyl.tax.TaxCategory;
import com.merchtyl.tax.TaxCategoryRepository;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import static com.merchtyl.initialinventory.InitialInventoryDtos.*;

@Service
public class InitialInventoryWorkbookService {
    public static final int TEMPLATE_VERSION=2, MAX_ROWS=5000;
    public static final long MAX_FILE_BYTES=10L*1024*1024;
    private static final String[] HEADERS={"Product Reference","Product Name *","Variant Name *","Barcode","Additional Barcodes","SKU","Selling Price *","Cost Price","Opening Quantity *","Low Stock Level","Category","Brand","Unit","Tax Category *","Supplier","Age Restricted","Active"};
    private final CategoryRepository categories; private final BrandRepository brands; private final UnitOfMeasureRepository units; private final TaxCategoryRepository taxes; private final SupplierRepository suppliers;
    public InitialInventoryWorkbookService(CategoryRepository categories,BrandRepository brands,UnitOfMeasureRepository units,TaxCategoryRepository taxes,SupplierRepository suppliers){this.categories=categories;this.brands=brands;this.units=units;this.taxes=taxes;this.suppliers=suppliers;}

    public byte[] template(Store store,String merchantName){
        try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            var setup=workbook.createSheet("Inventory Setup"); var instructions=workbook.createSheet("Instructions"); var refs=workbook.createSheet("Reference Data");
            var headerStyle=workbook.createCellStyle();headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);var font=workbook.createFont();font.setColor(IndexedColors.WHITE.getIndex());font.setBold(true);headerStyle.setFont(font);
            Row header=setup.createRow(0);for(int i=0;i<HEADERS.length;i++){Cell c=header.createCell(i);c.setCellValue(HEADERS[i]);c.setCellStyle(headerStyle);setup.setColumnWidth(i,(i==1||i==2?24:18)*256);} setup.createFreezePane(0,1);setup.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,MAX_ROWS,0,HEADERS.length-1));
            var textStyle=workbook.createCellStyle();textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));for(int col:new int[]{0,3,4,5})setup.setDefaultColumnStyle(col,textStyle);
            List<String> lines=List.of("INITIAL INVENTORY SETUP","Merchant: "+merchantName,"Store: "+store.getName(),"Store Code: "+store.getCode(),"Generated At: "+Instant.now(),"","One row represents one sellable Product Variant.","Repeat Product Name for each Variant.","Leave Product Reference blank for a new Product; existing references must not be changed.","Different variants may have different barcodes and SKUs.","Additional Barcodes is optional. Enter aliases for the same Variant separated by semicolons (;).","SKU is optional. Merchtyl generates a short SKU when blank; you may enter your own.","Opening Quantity belongs to this Store and Variant. Zero is allowed.","Barcode, Additional Barcodes, SKU, and Product Reference columns are Text.","Do not rename required columns or enter Store/Tenant IDs.","Do not use formulas other than template-provided formulas.");for(int i=0;i<lines.size();i++)instructions.createRow(i).createCell(0).setCellValue(lines.get(i));instructions.setColumnWidth(0,100*256);
            refs.createRow(0).createCell(24).setCellValue("templateVersion");refs.createRow(1).createCell(24).setCellValue(TEMPLATE_VERSION);
            writeReference(refs,0,"Categories",categories.findAll().stream().filter(Category::isActive).map(Category::getName).sorted().toList());
            writeReference(refs,1,"Brands",brands.findAll().stream().filter(Brand::isActive).map(Brand::getName).sorted().toList());
            writeReference(refs,2,"Units",units.findAll().stream().filter(UnitOfMeasure::isActive).map(UnitOfMeasure::getCode).sorted().toList());
            List<TaxCategory> visibleTaxes = taxes.findAll().stream().filter(TaxCategory::isActive)
                    .filter(tax -> tax.getTenantId() == null || tax.getTenantId().equals(store.getTenantId()))
                    .sorted(Comparator.comparing(TaxCategory::getName)).toList();
            writeReference(refs,3,"Tax Categories",visibleTaxes.stream().map(TaxCategory::getCode).toList());
            writeReference(refs,5,"Tax Category Name",visibleTaxes.stream().map(TaxCategory::getName).toList());
            writeReference(refs,6,"Tax Category Type",visibleTaxes.stream().map(tax -> tax.getCategoryType().name()).toList());
            writeReference(refs,7,"Tax Percentage",visibleTaxes.stream().map(tax -> tax.getPercentageRate() == null ? "system" : tax.getPercentageRate().stripTrailingZeros().toPlainString()).toList());
            writeReference(refs,4,"Suppliers",suppliers.findAll().stream().filter(Supplier::isActive).map(Supplier::getName).sorted().toList());
            addDropdown(setup,workbook,10,0);addDropdown(setup,workbook,11,1);addDropdown(setup,workbook,12,2);addDropdown(setup,workbook,13,3);addDropdown(setup,workbook,14,4);addListDropdown(setup,15,"YES,NO");addListDropdown(setup,16,"YES,NO");
            workbook.setSheetHidden(workbook.getSheetIndex(refs),true);workbook.write(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Unable to create inventory template",e);}
    }

    public List<RawRow> parse(byte[] bytes){
        if(bytes.length==0||bytes.length>MAX_FILE_BYTES)throw new BadRequestException("INITIAL_INVENTORY_INVALID_FILE");
        if(bytes.length<4||bytes[0]!='P'||bytes[1]!='K')throw new BadRequestException("INITIAL_INVENTORY_INVALID_FILE");
        ZipSecureFile.setMinInflateRatio(0.01);ZipSecureFile.setMaxEntrySize(25L*1024*1024);ZipSecureFile.setMaxTextSize(10L*1024*1024);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            Sheet refs=workbook.getSheet("Reference Data");if(refs==null||refs.getRow(1)==null||refs.getRow(1).getCell(24)==null||((int)refs.getRow(1).getCell(24).getNumericCellValue())!=TEMPLATE_VERSION)throw new BadRequestException("INITIAL_INVENTORY_TEMPLATE_VERSION_UNSUPPORTED");
            Sheet sheet=workbook.getSheet("Inventory Setup");if(sheet==null)throw new BadRequestException("INITIAL_INVENTORY_INVALID_FILE");requireHeaders(sheet.getRow(0));
            if(sheet.getLastRowNum()>MAX_ROWS)throw new BadRequestException("INITIAL_INVENTORY_TOO_MANY_ROWS");
            List<RawRow> result=new ArrayList<>();DataFormatter formatter=new DataFormatter(Locale.ROOT);
            for(int i=1;i<=sheet.getLastRowNum();i++){Row row=sheet.getRow(i);if(row==null||blankRow(row,formatter))continue;result.add(new RawRow(i+1,text(row,0,formatter),text(row,1,formatter),text(row,2,formatter),text(row,3,formatter),text(row,4,formatter),formulaSafeText(row,5,formatter),decimal(row,6,formatter),decimal(row,7,formatter),decimal(row,8,formatter),decimal(row,9,formatter),text(row,10,formatter),text(row,11,formatter),text(row,12,formatter),text(row,13,formatter),text(row,14,formatter),bool(row,15,formatter),bool(row,16,formatter)));}
            if(result.isEmpty())throw new BadRequestException("INITIAL_INVENTORY_VALIDATION_FAILED");return result;
        }catch(BadRequestException e){throw e;}catch(Exception e){throw new BadRequestException("INITIAL_INVENTORY_INVALID_FILE");}
    }
    private static void requireHeaders(Row row){if(row==null)throw new BadRequestException("INITIAL_INVENTORY_INVALID_FILE");for(int i=0;i<HEADERS.length;i++)if(row.getCell(i)==null||!HEADERS[i].equals(row.getCell(i).getStringCellValue()))throw new BadRequestException("INITIAL_INVENTORY_INVALID_FILE");}
    private static boolean blankRow(Row row,DataFormatter f){for(int i=0;i<HEADERS.length;i++)if(!f.formatCellValue(row.getCell(i)).isBlank())return false;return true;}
    private static String text(Row r,int c,DataFormatter f){Cell cell=r.getCell(c);if(cell==null||cell.getCellType()==CellType.FORMULA)return null;String value=f.formatCellValue(cell).trim();return value.isBlank()?null:value;}
    private static String formulaSafeText(Row r,int c,DataFormatter f){return text(r,c,f);}
    private static BigDecimal decimal(Row r,int c,DataFormatter f){String value=text(r,c,f);if(value==null)return null;try{return new BigDecimal(value.replace(",",""));}catch(NumberFormatException e){return null;}}
    private static Boolean bool(Row r,int c,DataFormatter f){String value=text(r,c,f);if(value==null)return null;if(value.equalsIgnoreCase("YES"))return true;if(value.equalsIgnoreCase("NO"))return false;return null;}
    private static void writeReference(Sheet s,int col,String title,List<String> values){Row header=s.getRow(0);if(header==null)header=s.createRow(0);header.createCell(col).setCellValue(title);for(int i=0;i<values.size();i++){Row row=s.getRow(i+1);if(row==null)row=s.createRow(i+1);row.createCell(col).setCellValue(values.get(i));}}
    private static void addDropdown(Sheet s,Workbook w,int targetCol,int refCol){String letter=org.apache.poi.ss.util.CellReference.convertNumToColString(refCol);Name name=w.createName();name.setNameName("REF_"+targetCol);name.setRefersToFormula("'Reference Data'!$"+letter+"$2:$"+letter+"$500");DataValidationHelper h=s.getDataValidationHelper();DataValidation v=h.createValidation(h.createFormulaListConstraint(name.getNameName()),new CellRangeAddressList(1,MAX_ROWS,targetCol,targetCol));v.setShowErrorBox(true);s.addValidationData(v);}
    private static void addListDropdown(Sheet s,int col,String values){DataValidationHelper h=s.getDataValidationHelper();s.addValidationData(h.createValidation(h.createExplicitListConstraint(values.split(",")),new CellRangeAddressList(1,MAX_ROWS,col,col)));}
}
