package com.merchtyl.initialinventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.catalogue.*;
import com.merchtyl.inventory.*;
import com.merchtyl.product.*;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import com.merchtyl.store.*;
import com.merchtyl.supplier.*;
import com.merchtyl.tax.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InitialInventoryServiceTest {
    @Test
    void validationGroupsVariantsWithoutMutatingBusinessData() throws Exception {
        var imports=mock(InitialInventoryImportRepository.class);var access=mock(StoreAccessService.class);var stores=mock(StoreRepository.class);
        var products=mock(ProductRepository.class);var variants=mock(ProductVariantRepository.class);var barcodes=mock(ProductBarcodeRepository.class);var storeProducts=mock(StoreProductRepository.class);
        var balances=mock(InventoryBalanceRepository.class);var transactions=mock(InventoryTransactionRepository.class);var categories=mock(CategoryRepository.class);var brands=mock(BrandRepository.class);
        var units=mock(UnitOfMeasureRepository.class);var taxes=mock(TaxCategoryRepository.class);var suppliers=mock(SupplierRepository.class);var productSuppliers=mock(ProductSupplierRepository.class);
        var references=mock(ProductReferenceGenerator.class);var auth=mock(Authentication.class);var actor=mock(User.class);var store=mock(Store.class);
        UUID tenantId=UUID.randomUUID(),storeId=UUID.randomUUID(),actorId=UUID.randomUUID();
        when(access.currentTenantId(auth)).thenReturn(tenantId);when(access.requireProductManagementScope(auth,Set.of(storeId))).thenReturn(actor);when(access.tenantStore(tenantId,storeId)).thenReturn(store);when(actor.getId()).thenReturn(actorId);
        when(products.findAllByTenantId(tenantId)).thenReturn(List.of());when(variants.findAllByTenantId(tenantId)).thenReturn(List.of());when(barcodes.findAllByTenantId(tenantId)).thenReturn(List.of());when(transactions.existsByStoreId(storeId)).thenReturn(false);when(imports.existsByTenantIdAndStoreIdAndStatus(any(),any(),any())).thenReturn(false);
        TaxCategory standard=mock(TaxCategory.class);when(standard.isActive()).thenReturn(true);when(standard.getName()).thenReturn("Standard");when(standard.getCode()).thenReturn("STANDARD");when(taxes.findAll()).thenReturn(List.of(standard));when(categories.findAll()).thenReturn(List.of());when(brands.findAll()).thenReturn(List.of());when(units.findAll()).thenReturn(List.of());when(suppliers.findAll()).thenReturn(List.of());
        InitialInventoryWorkbookService workbookService=new InitialInventoryWorkbookService(categories,brands,units,taxes,suppliers);
        InitialInventoryService service=new InitialInventoryService(workbookService,imports,access,stores,products,variants,barcodes,storeProducts,balances,transactions,categories,brands,units,taxes,suppliers,productSuppliers,references,new SkuGenerator(),new ObjectMapper());

        var response=service.validate(storeId,"opening.xlsx",workbook(),auth);

        assertThat(response.productGroups()).isEqualTo(1);assertThat(response.totalVariantRows()).isEqualTo(3);assertThat(response.validRows()).isEqualTo(3);assertThat(response.canImport()).isTrue();
        assertThat(response.rows()).extracting(InitialInventoryDtos.RowPreview::sku).containsExactly("COC355ML","COC500ML","COC2L");
        verify(imports).save(any(InitialInventoryImport.class));verify(products,never()).save(any());verify(products,never()).saveAndFlush(any());verify(variants,never()).save(any());verify(storeProducts,never()).save(any());verify(balances,never()).save(any());verify(transactions,never()).save(any());
    }

    private static byte[] workbook() throws Exception {
        try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            var setup=workbook.createSheet("Inventory Setup");var refs=workbook.createSheet("Reference Data");
            String[] headers={"Product Reference","Product Name *","Variant Name *","Barcode","Additional Barcodes","SKU","Selling Price *","Cost Price","Opening Quantity *","Low Stock Level","Category","Brand","Unit","Tax Category *","Supplier","Age Restricted","Active"};
            var header=setup.createRow(0);for(int i=0;i<headers.length;i++)header.createCell(i).setCellValue(headers[i]);
            add(setup,1,"355 ml","B1",1.99,24);add(setup,2,"500 ml","B2",2.79,18);add(setup,3,"2 L","B3",4.49,8);
            refs.createRow(0).createCell(24).setCellValue("templateVersion");refs.createRow(1).createCell(24).setCellValue(2);workbook.write(out);return out.toByteArray();
        }
    }

    private static void add(org.apache.poi.ss.usermodel.Sheet sheet,int index,String variant,String barcode,double price,int quantity){var row=sheet.createRow(index);row.createCell(1).setCellValue("Coca Cola");row.createCell(2).setCellValue(variant);row.createCell(3).setCellValue(barcode);row.createCell(6).setCellValue(price);row.createCell(8).setCellValue(quantity);row.createCell(13).setCellValue("Standard");row.createCell(15).setCellValue("NO");row.createCell(16).setCellValue("YES");}
}
