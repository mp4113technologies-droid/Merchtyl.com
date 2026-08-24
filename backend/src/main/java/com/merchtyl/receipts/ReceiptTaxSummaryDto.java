package com.merchtyl.receipts;

import java.math.BigDecimal;

public record ReceiptTaxSummaryDto(
        String componentCode,
        String componentName,
        BigDecimal taxableAmount,
        BigDecimal taxAmount
) {
}
