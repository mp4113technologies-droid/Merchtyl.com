package com.merchtyl.refunds;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "merchtyl.refunds")
public class RefundProperties {
    private boolean approvalRequired;

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }
}
