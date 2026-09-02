package com.merchtyl.receipts;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record KitchenTicketItemDto(
        UUID saleItemId,
        String name,
        BigDecimal quantity,
        List<String> modifiers,
        String preparationInstructions
) {
}
