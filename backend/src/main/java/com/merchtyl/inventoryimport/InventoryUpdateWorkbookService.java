package com.merchtyl.inventoryimport;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.inventoryimport.InventoryImportDtos.ParsedRow;
import com.merchtyl.product.ProductVariant;
import com.merchtyl.store.Store;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class InventoryUpdateWorkbookService {
    public static final long MAX_FILE_BYTES=10L*1024*1024; public static final int MAX_ROWS=10000, VERSION=1;
    private static final String DATA="Inventory Update",META="_Merchtyl Metadata";
    private static final String[] HEADERS={"Product Code","Product Name","Variant","SKU","Barcode","Current Stock","Counted Stock (Set To)","Add Stock (Received Qty)","Variant ID"};
    public record DownloadRow(ProductVariant variant,String barcode,BigDecimal current) {}
    public byte[] create(Store store,List<DownloadRow> rows){
        try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            Sheet sheet=wb.createSheet(DATA),instructions=wb.createSheet("Instructions"),meta=wb.createSheet(META);
            CellStyle head=wb.createCellStyle();head.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());head.setFillPattern(FillPatternType.SOLID_FOREGROUND);Font font=wb.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());head.setFont(font);
            Row h=sheet.createRow(0);for(int i=0;i<HEADERS.length;i++){Cell c=h.createCell(i);c.setCellValue(HEADERS[i]);c.setCellStyle(head);sheet.setColumnWidth(i,(i==1?30:20)*256);}sheet.createFreezePane(0,1);sheet.setAutoFilter(new CellRangeAddress(0,Math.max(1,rows.size()),0,7));
            CellStyle locked=wb.createCellStyle();locked.setLocked(true);locked.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());locked.setFillPattern(FillPatternType.SOLID_FOREGROUND);CellStyle input=wb.createCellStyle();input.setLocked(false);input.setDataFormat(wb.createDataFormat().getFormat("0.####"));
            int n=1;for(DownloadRow value:rows){ProductVariant v=value.variant();var p=v.getProduct();Row r=sheet.createRow(n++);String[] text={p.getProductReference(),p.getName(),v.getName(),v.getSku(),value.barcode()};for(int i=0;i<text.length;i++){r.createCell(i).setCellValue(text[i]==null?"":text[i]);r.getCell(i).setCellStyle(locked);}r.createCell(5).setCellValue(value.current().doubleValue());r.getCell(5).setCellStyle(locked);r.createCell(6).setCellStyle(input);r.createCell(7).setCellStyle(input);r.createCell(8).setCellValue(v.getId().toString());r.getCell(8).setCellStyle(locked);}
            sheet.setColumnHidden(8,true);sheet.protectSheet("merchtyl");
            List<String> lines=List.of("STORE INVENTORY UPDATE","Store: "+store.getName()+" ("+store.getCode()+")","Generated: "+Instant.now(),"","Current Stock is read-only and is only the quantity at download time.","Counted Stock (Set To): enter the actual physical quantity. Current -7, Counted 10 results in final stock 10.","Add Stock (Received Qty): enter only newly received units. Current 3, Add 10 results in final stock 13.","Do not enter both Counted Stock and Add Stock on the same row.","The backend re-reads current inventory at confirmation; spreadsheet values and formulas are never authoritative.");for(int i=0;i<lines.size();i++)instructions.createRow(i).createCell(0).setCellValue(lines.get(i));instructions.setColumnWidth(0,110*256);
            meta.createRow(0).createCell(0).setCellValue("templateVersion");meta.getRow(0).createCell(1).setCellValue(VERSION);meta.createRow(1).createCell(0).setCellValue("storeId");meta.getRow(1).createCell(1).setCellValue(store.getId().toString());wb.setSheetHidden(wb.getSheetIndex(meta),true);wb.write(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Unable to create inventory workbook",e);}
    }
    public List<ParsedRow> parse(UUID expectedStore,byte[] bytes){
        if(bytes.length==0||bytes.length>MAX_FILE_BYTES||bytes.length<4||bytes[0]!='P'||bytes[1]!='K')throw new BadRequestException("INVENTORY_IMPORT_INVALID_FILE");
        ZipSecureFile.setMinInflateRatio(.01);ZipSecureFile.setMaxEntrySize(25L*1024*1024);ZipSecureFile.setMaxTextSize(10L*1024*1024);
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(bytes))){Sheet meta=wb.getSheet(META),sheet=wb.getSheet(DATA);if(meta==null||sheet==null)throw new BadRequestException("INVENTORY_IMPORT_INVALID_FILE");if((int)meta.getRow(0).getCell(1).getNumericCellValue()!=VERSION)throw new BadRequestException("INVENTORY_IMPORT_TEMPLATE_VERSION_UNSUPPORTED");UUID store=UUID.fromString(meta.getRow(1).getCell(1).getStringCellValue());if(!store.equals(expectedStore))throw new BadRequestException("INVENTORY_IMPORT_STORE_MISMATCH");requireHeaders(sheet.getRow(0));if(sheet.getLastRowNum()>MAX_ROWS)throw new BadRequestException("INVENTORY_IMPORT_TOO_MANY_ROWS");DataFormatter f=new DataFormatter(Locale.ROOT);List<ParsedRow> result=new ArrayList<>();for(int i=1;i<=sheet.getLastRowNum();i++){Row r=sheet.getRow(i);if(r==null)continue;String id=f.formatCellValue(r.getCell(8)).trim();if(id.isBlank())continue;result.add(new ParsedRow(i+1,UUID.fromString(id),decimal(r.getCell(5),f,false),decimal(r.getCell(6),f,true),decimal(r.getCell(7),f,true)));}return result;
        }catch(BadRequestException e){throw e;}catch(Exception e){throw new BadRequestException("INVENTORY_IMPORT_INVALID_FILE");}
    }
    private static BigDecimal decimal(Cell c,DataFormatter f,boolean rejectFormula){if(c==null||c.getCellType()==CellType.BLANK)return null;if(rejectFormula&&c.getCellType()==CellType.FORMULA)throw new BadRequestException("INVENTORY_IMPORT_INVALID_NUMBER");String s=f.formatCellValue(c).trim();if(s.isBlank())return null;try{return new BigDecimal(s.replace(",",""));}catch(Exception e){throw new BadRequestException("INVENTORY_IMPORT_INVALID_NUMBER");}}
    private static void requireHeaders(Row row){if(row==null)throw new BadRequestException("INVENTORY_IMPORT_INVALID_FILE");for(int i=0;i<HEADERS.length;i++)if(row.getCell(i)==null||!HEADERS[i].equals(row.getCell(i).getStringCellValue()))throw new BadRequestException("INVENTORY_IMPORT_INVALID_FILE");}
}
