package com.merchtyl.receipts;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record KitchenTicketItemDto(
        UUID saleItemId,
        String name,
        BigDecimal quantity,
        String variantName,
        List<String> modifiers,
        List<String> removedComponents,
        List<String> extraComponents,
        String preparationInstructions
) {
}
