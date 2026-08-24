package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

public record TaxRuleStatusRequest(boolean active, @NotNull Long version) {
}
