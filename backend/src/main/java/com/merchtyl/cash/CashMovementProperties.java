package com.merchtyl.cash;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.Set;

@ConfigurationProperties(prefix = "merchtyl.cash-movements")
public class CashMovementProperties {
    private Set<CashMovementType> approvalRequiredTypes = EnumSet.noneOf(CashMovementType.class);

    public Set<CashMovementType> getApprovalRequiredTypes() {
        return approvalRequiredTypes;
    }

    public void setApprovalRequiredTypes(Set<CashMovementType> approvalRequiredTypes) {
        this.approvalRequiredTypes = approvalRequiredTypes == null || approvalRequiredTypes.isEmpty()
                ? EnumSet.noneOf(CashMovementType.class)
                : EnumSet.copyOf(approvalRequiredTypes);
    }

    public boolean requiresApproval(CashMovementType type) {
        return approvalRequiredTypes.contains(type);
    }
}
