package com.merchtyl.auth;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PasswordPolicyService {
    public static final int MINIMUM_LENGTH = 8;
    public static final int MAXIMUM_LENGTH = 20;
    public static final String REQUIREMENTS_MESSAGE = "Password must be between 8 and 20 characters and include at least one uppercase letter, one lowercase letter, one number, and one special character.";
    public static final String ALLOWED_SPECIAL_CHARACTERS = "!@#$%^&*()_+-=?.,";
    public PasswordPolicyResponse describe() {
        return new PasswordPolicyResponse(MINIMUM_LENGTH, MAXIMUM_LENGTH, true, true, true, true, ALLOWED_SPECIAL_CHARACTERS);
    }

    public void validate(String password) {
        List<PasswordPolicyViolation> violations = new ArrayList<>();
        if (password == null || password.length() < MINIMUM_LENGTH) {
            violations.add(new PasswordPolicyViolation("PASSWORD_TOO_SHORT", REQUIREMENTS_MESSAGE));
        }
        if (password != null && password.length() > MAXIMUM_LENGTH) {
            violations.add(new PasswordPolicyViolation("PASSWORD_TOO_LONG", REQUIREMENTS_MESSAGE));
        }
        if (password == null || password.chars().noneMatch(Character::isUpperCase)) {
            violations.add(new PasswordPolicyViolation("PASSWORD_MISSING_UPPERCASE", REQUIREMENTS_MESSAGE));
        }
        if (password == null || password.chars().noneMatch(Character::isLowerCase)) {
            violations.add(new PasswordPolicyViolation("PASSWORD_MISSING_LOWERCASE", REQUIREMENTS_MESSAGE));
        }
        if (password == null || password.chars().noneMatch(Character::isDigit)) {
            violations.add(new PasswordPolicyViolation("PASSWORD_MISSING_NUMBER", REQUIREMENTS_MESSAGE));
        }
        if (password == null || password.chars().noneMatch(character -> ALLOWED_SPECIAL_CHARACTERS.indexOf(character) >= 0)) {
            violations.add(new PasswordPolicyViolation("PASSWORD_MISSING_SPECIAL_CHARACTER", REQUIREMENTS_MESSAGE));
        }
        if (password != null && password.chars().anyMatch(character -> !Character.isLetterOrDigit(character)
                && ALLOWED_SPECIAL_CHARACTERS.indexOf(character) < 0)) {
            violations.add(new PasswordPolicyViolation("PASSWORD_INVALID_CHARACTER", REQUIREMENTS_MESSAGE));
        }
        if (!violations.isEmpty()) {
            throw new PasswordPolicyException(violations);
        }
    }
}
