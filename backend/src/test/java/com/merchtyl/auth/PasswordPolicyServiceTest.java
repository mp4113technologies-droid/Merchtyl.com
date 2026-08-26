package com.merchtyl.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PasswordPolicyServiceTest {
    private final PasswordPolicyService service = new PasswordPolicyService();

    @Test
    void acceptsRequiredCompliantPasswords() {
        assertThatCode(() -> service.validate("Test@123")).doesNotThrowAnyException();
        assertThatCode(() -> service.validate("Merchant@1")).doesNotThrowAnyException();
        assertThatCode(() -> service.validate("Admin#2026")).doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordsOutsideRequiredLength() {
        PasswordPolicyException exception = catchThrowableOfType(
                () -> service.validate("Ab1!"), PasswordPolicyException.class);
        PasswordPolicyException tooLong = catchThrowableOfType(
                () -> service.validate("VeryLongPasswordForMerchtyl@12345"), PasswordPolicyException.class);

        assertThat(exception.violations()).extracting(PasswordPolicyViolation::code)
                .contains("PASSWORD_TOO_SHORT");
        assertThat(tooLong.violations()).extracting(PasswordPolicyViolation::code)
                .contains("PASSWORD_TOO_LONG");
    }

    @Test
    void acceptsACompliantPasswordAndEncoderCanVerifyIt() {
        String password = "Mtyl#Blue7294!";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        assertThatCode(() -> service.validate(password)).doesNotThrowAnyException();
        assertThat(encoder.matches(password, encoder.encode(password))).isTrue();
    }

    @Test
    void reportsAllMissingCharacterClasses() {
        assertThat(codes("test123")).contains("PASSWORD_MISSING_UPPERCASE", "PASSWORD_MISSING_SPECIAL_CHARACTER");
        assertThat(codes("TEST@123")).contains("PASSWORD_MISSING_LOWERCASE");
        assertThat(codes("TestPassword")).contains("PASSWORD_MISSING_NUMBER", "PASSWORD_MISSING_SPECIAL_CHARACTER");
        assertThat(codes("Test1234")).contains("PASSWORD_MISSING_SPECIAL_CHARACTER");
    }

    @Test
    void reportsMissingLowercaseAndMaximumLength() {
        PasswordPolicyException lowercase = catchThrowableOfType(
                () -> service.validate("ABCDEFG1!"), PasswordPolicyException.class);
        PasswordPolicyException tooLong = catchThrowableOfType(
                () -> service.validate("Aa1!" + "x".repeat(17)), PasswordPolicyException.class);

        assertThat(lowercase.violations()).extracting(PasswordPolicyViolation::code)
                .contains("PASSWORD_MISSING_LOWERCASE");
        assertThat(tooLong.violations()).extracting(PasswordPolicyViolation::code)
                .contains("PASSWORD_TOO_LONG");
    }

    private Set<String> codes(String password) {
        return catchThrowableOfType(() -> service.validate(password), PasswordPolicyException.class)
                .violations().stream().map(PasswordPolicyViolation::code).collect(Collectors.toSet());
    }
}
