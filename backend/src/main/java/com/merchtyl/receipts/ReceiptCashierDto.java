package com.merchtyl.receipts;

import java.util.UUID;

public record ReceiptCashierDto(
        UUID id,
        String displayName,
        String email
) {
}
