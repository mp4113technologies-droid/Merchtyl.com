package com.merchtyl.inventoryimport;
import com.merchtyl.inventoryimport.InventoryImportDtos.Operation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class InventoryUpdateImportServiceTest {
 @Test void countedStockIsFinalTargetFromNegativeBalance(){assertAmounts(Operation.SET_COUNT,"-7","10","17","10");}
 @Test void addStockAddsToPositiveBalance(){assertAmounts(Operation.ADD_STOCK,"3","10","10","13");}
 @Test void addStockOffsetsNegativeBalance(){assertAmounts(Operation.ADD_STOCK,"-7","10","10","3");}
 @Test void addStockUsesCurrentDatabaseBalanceNotDownloadedBalance(){assertAmounts(Operation.ADD_STOCK,"5","10","10","15");}
 @Test void countedStockUsesCurrentDatabaseBalanceNotDownloadedBalance(){assertAmounts(Operation.SET_COUNT,"-5","10","15","10");}
 @Test void blankInputMakesNoChange(){assertAmounts(Operation.NO_CHANGE,"8",null,"0.0000","8");}
 private static void assertAmounts(Operation op,String current,String entered,String adjustment,String result){var value=InventoryUpdateImportService.calculate(op,new BigDecimal(current),entered==null?null:new BigDecimal(entered));assertThat(value[0]).isEqualByComparingTo(adjustment);assertThat(value[1]).isEqualByComparingTo(result);}
}
