package com.merchtyl.tax;

import com.merchtyl.product.ProductTaxClass;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record BulkProductTaxClassRequest(@NotEmpty Set<UUID> productIds, @NotNull ProductTaxClass taxClass, UUID taxCategoryId) {}
