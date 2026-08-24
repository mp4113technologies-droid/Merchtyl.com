package com.merchtyl.inventory;

import jakarta.validation.constraints.Size;

public record StockCountReviewRequest(
        @Size(max = 1000) String reviewNotes
) {
}
