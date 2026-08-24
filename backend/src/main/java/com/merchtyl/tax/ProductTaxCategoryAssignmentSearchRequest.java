package com.merchtyl.tax;

import java.util.UUID;

public record ProductTaxCategoryAssignmentSearchRequest(UUID productId, UUID taxCategoryId, Boolean active, int page, int size) {
}
