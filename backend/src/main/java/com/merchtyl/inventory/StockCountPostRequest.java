package com.merchtyl.inventory;

import jakarta.validation.constraints.Size;

public record StockCountPostRequest(
        @Size(max = 1000) String postNotes
) {
}
