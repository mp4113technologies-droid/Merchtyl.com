package com.merchtyl.tax;

import java.util.UUID;

public record TaxRuleActionResponse(
        UUID id,
        TaxRuleActionType actionType,
        UUID taxGroupId,
        UUID taxComponentId,
        String value) {
    static TaxRuleActionResponse from(TaxRuleAction action) {
        return new TaxRuleActionResponse(
                action.getId(),
                action.getActionType(),
                action.getTaxGroup() == null ? null : action.getTaxGroup().getId(),
                action.getTaxComponent() == null ? null : action.getTaxComponent().getId(),
                action.getValue());
    }
}
