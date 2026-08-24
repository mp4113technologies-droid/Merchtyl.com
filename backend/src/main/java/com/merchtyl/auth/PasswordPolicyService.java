package com.merchtyl.auth;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PasswordPolicyService {
    public static final int MINIMUM_LENGTH = 12;
    public static final int MAXIMUM_LENGTH = 128;
    public PasswordPolicyResponse describe() {
        return new PasswordPolicyResponse(MINIMUM_LENGTH, MAXIMUM_LENGTH, true, true, true, true);
    }

    public void validate(String password) {
        List<PasswordPolicyViolation> violations = new ArrayList<>();
        if (password == null || password.length() < MINIMUM_LENGTH) {
            violations.add(new PasswordPolicyViolation("PASSWORD_TOO_SHORT", "Password must contain at least 12 characters."));
        }
        if (password != null && password.length() > MAXIMUM_LENGTH) {
            violations.add(new PasswordPolicyViolation("PASSWORD_TOO_LONG", "Password must contain no more than 128 characters."));
        }
        if (password == null || password.chars().noneMatch(Character::isUpperCase)) {
            violations.add(new PasswordPolicyViolation("PASSWORD_MISSING_UPPERCASE", "Password must contain an uppercase letter."));
        }
        if (password == null || password.chars().noneMatch(Character::isLowerCase)) {
            violations.add(new PasswordPolicyViolation("PASSWORD_MISSING_LOWERCASE", "Password must contain a lowercase letter."));
        }
        if (password == null || password.chars().noneMatch(Character::isDigit)) {
            violations.add(new PasswordPolicyViolation("PASSWORD_MISSING_NUMBER", "Password must contain a number."));
        }
        if (password == null || password.chars().allMatch(Character::isLetterOrDigit)) {
            violations.add(new PasswordPolicyViolation("PASSWORD_MISSING_SPECIAL_CHARACTER", "Password must contain a special character."));
        }
        if (!violations.isEmpty()) {
            throw new PasswordPolicyException(violations);
        }
    }
}
