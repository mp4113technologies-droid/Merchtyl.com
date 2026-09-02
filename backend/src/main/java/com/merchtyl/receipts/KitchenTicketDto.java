package com.merchtyl.receipts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KitchenTicketDto(
        PrintDocumentType documentType,
        UUID saleId,
        String tokenNumber,
        String storeName,
        String registerName,
        String cashierName,
        Instant orderTime,
        String orderType,
        String tableNumber,
        List<KitchenTicketItemDto> items,
        String orderNotes,
        boolean reprint
) {
}
