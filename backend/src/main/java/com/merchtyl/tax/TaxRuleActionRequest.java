package com.merchtyl.tax;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TaxRuleActionRequest(
        @NotNull TaxRuleActionType actionType,
        UUID taxGroupId,
        UUID taxComponentId,
        String value) {
}
