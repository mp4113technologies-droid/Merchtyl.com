package com.merchtyl.tax;

record TaxRuleActionValues(
        TaxRuleActionType actionType,
        TaxGroup taxGroup,
        TaxComponent taxComponent,
        String value) {
}
