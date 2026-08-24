package com.merchtyl.auth;
import java.util.List;
public class PasswordPolicyException extends RuntimeException {
    private final List<PasswordPolicyViolation> violations;
    public PasswordPolicyException(List<PasswordPolicyViolation> violations) {
        super("The password does not meet the required security policy.");
        this.violations = List.copyOf(violations);
    }
    public List<PasswordPolicyViolation> violations() { return violations; }
}
