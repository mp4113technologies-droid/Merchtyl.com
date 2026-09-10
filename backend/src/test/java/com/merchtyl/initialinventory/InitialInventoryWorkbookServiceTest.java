package com.merchtyl.initialinventory;

import com.merchtyl.catalogue.*;import com.merchtyl.store.Store;import com.merchtyl.supplier.SupplierRepository;import com.merchtyl.tax.TaxCategoryRepository;
import org.apache.poi.ss.usermodel.*;import org.apache.poi.xssf.usermodel.XSSFWorkbook;import org.junit.jupiter.api.Test;import java.io.*;import java.math.BigDecimal;import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;import static org.mockito.Mockito.*;

class InitialInventoryWorkbookServiceTest {
 @Test void createsVersionedThreeSheetTemplateWithTextIdentifiersAndParsesVariants() throws Exception {
  CategoryRepository categories=mock(CategoryRepository.class);BrandRepository brands=mock(BrandRepository.class);UnitOfMeasureRepository units=mock(UnitOfMeasureRepository.class);TaxCategoryRepository taxes=mock(TaxCategoryRepository.class);SupplierRepository suppliers=mock(SupplierRepository.class);
  when(categories.findAll()).thenReturn(List.of());when(brands.findAll()).thenReturn(List.of());when(units.findAll()).thenReturn(List.of());when(taxes.findAll()).thenReturn(List.of());when(suppliers.findAll()).thenReturn(List.of());Store store=mock(Store.class);when(store.getName()).thenReturn("Downtown");when(store.getCode()).thenReturn("DOWN");
  InitialInventoryWorkbookService service=new InitialInventoryWorkbookService(categories,brands,units,taxes,suppliers);byte[] template=service.template(store,"Merchant");
  try(XSSFWorkbook workbook=new XSSFWorkbook(new ByteArrayInputStream(template))){assertThat(workbook.getNumberOfSheets()).isEqualTo(3);assertThat(workbook.getSheet("Inventory Setup")).isNotNull();assertThat(workbook.getSheet("Instructions").getRow(0).getCell(0).getStringCellValue()).isEqualTo("INITIAL INVENTORY SETUP");assertThat(workbook.isSheetHidden(workbook.getSheetIndex("Reference Data"))).isTrue();Sheet sheet=workbook.getSheet("Inventory Setup");assertThat(sheet.getColumnStyle(0).getDataFormatString()).isEqualTo("@");assertThat(sheet.getColumnStyle(3).getDataFormatString()).isEqualTo("@");assertThat(sheet.getColumnStyle(4).getDataFormatString()).isEqualTo("@");
   Row row=sheet.createRow(1);row.createCell(0).setCellValue("");row.createCell(1).setCellValue("Coca Cola");row.createCell(2).setCellValue("500 ml");row.createCell(3).setCellValue("001234567890");row.createCell(4).setCellValue("");row.createCell(5).setCellValue(2.79);row.createCell(6).setCellValue(1.25);row.createCell(7).setCellValue(18);row.createCell(12).setCellValue("Standard");row.createCell(14).setCellValue("NO");row.createCell(15).setCellValue("YES");try(ByteArrayOutputStream out=new ByteArrayOutputStream()){workbook.write(out);var rows=service.parse(out.toByteArray());assertThat(rows).hasSize(1);assertThat(rows.get(0).barcode()).isEqualTo("001234567890");assertThat(rows.get(0).openingQuantity()).isEqualByComparingTo(new BigDecimal("18"));assertThat(rows.get(0).sku()).isNull();}
  }
 }
}
