package com.merchtyl.receipts;

import java.util.UUID;

public record ReceiptStoreDto(
        UUID id,
        String code,
        String name,
        String legalName,
        String address,
        String phone,
        String email
) {
}
