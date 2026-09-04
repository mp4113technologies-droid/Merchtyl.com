package com.merchtyl.eod;

import java.util.List;

public class ClosingValidationException extends RuntimeException {
    private final transient ClosingValidationResponse validation;

    public ClosingValidationException(ClosingValidationResponse validation) {
        super(validation.blockers().stream().anyMatch(blocker -> "OPEN_REGISTER_SESSION".equals(blocker.code()))
                ? "All registers must be closed before closing the business day."
                : "Business day cannot be closed until all blocking issues are resolved");
        this.validation = validation;
    }

    public ClosingValidationResponse getValidation() {
        return validation;
    }

    public List<ClosingBlockerResponse> getBlockers() {
        return validation.blockers();
    }
}
