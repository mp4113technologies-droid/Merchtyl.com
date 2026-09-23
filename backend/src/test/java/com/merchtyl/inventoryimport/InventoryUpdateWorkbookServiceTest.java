package com.merchtyl.inventoryimport;
import com.merchtyl.common.BadRequestException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class InventoryUpdateWorkbookServiceTest {
 private final InventoryUpdateWorkbookService service=new InventoryUpdateWorkbookService();
 @Test void parsesCountedAndAddColumnsAsDistinctInputs() throws Exception {UUID store=UUID.randomUUID(),variant=UUID.randomUUID();var rows=service.parse(store,workbook(store,variant,3,10d,null));assertThat(rows).singleElement().satisfies(r->{assertThat(r.downloadedStock()).isEqualByComparingTo("3");assertThat(r.countedStock()).isEqualByComparingTo("10");assertThat(r.addStock()).isNull();});}
 @Test void rejectsWorkbookForAnotherStore() throws Exception {assertThatThrownBy(()->service.parse(UUID.randomUUID(),workbook(UUID.randomUUID(),UUID.randomUUID(),3,null,10d))).isInstanceOf(BadRequestException.class).hasMessage("INVENTORY_IMPORT_STORE_MISMATCH");}
 @Test void rejectsFormulaAsAuthoritativeInput() throws Exception {UUID store=UUID.randomUUID();byte[] bytes=workbook(store,UUID.randomUUID(),3,null,null,true);assertThatThrownBy(()->service.parse(store,bytes)).isInstanceOf(BadRequestException.class).hasMessage("INVENTORY_IMPORT_INVALID_NUMBER");}
 private static byte[] workbook(UUID store,UUID variant,double downloaded,Double counted,Double add)throws Exception{return workbook(store,variant,downloaded,counted,add,false);}
 private static byte[] workbook(UUID store,UUID variant,double downloaded,Double counted,Double add,boolean formula)throws Exception{try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()){var sheet=wb.createSheet("Inventory Update");String[] headers={"Product Code","Product Name","Variant","SKU","Barcode","Current Stock","Counted Stock (Set To)","Add Stock (Received Qty)","Variant ID"};var h=sheet.createRow(0);for(int i=0;i<headers.length;i++)h.createCell(i).setCellValue(headers[i]);var row=sheet.createRow(1);row.createCell(5).setCellValue(downloaded);if(formula)row.createCell(6).setCellFormula("5+5");else if(counted!=null)row.createCell(6).setCellValue(counted);if(add!=null)row.createCell(7).setCellValue(add);row.createCell(8).setCellValue(variant.toString());var meta=wb.createSheet("_Merchtyl Metadata");meta.createRow(0).createCell(0).setCellValue("templateVersion");meta.getRow(0).createCell(1).setCellValue(1);meta.createRow(1).createCell(0).setCellValue("storeId");meta.getRow(1).createCell(1).setCellValue(store.toString());wb.write(out);return out.toByteArray();}}
}
