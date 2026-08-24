package com.merchtyl.receipts;

import java.util.UUID;

public record ReceiptRegisterDto(
        UUID id,
        String code,
        String name
) {
}
