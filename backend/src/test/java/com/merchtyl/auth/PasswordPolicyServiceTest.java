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
    void reportsCandidatePasswordAsTooShortWithoutRelaxingPolicy() {
        PasswordPolicyException exception = catchThrowableOfType(
                () -> service.validate("Test@123456"), PasswordPolicyException.class);

        assertThat(exception.violations()).extracting(PasswordPolicyViolation::code)
                .containsExactly("PASSWORD_TOO_SHORT");
    }

    @Test
    void acceptsACompliantPasswordAndEncoderCanVerifyIt() {
        String password = "Mtyl#BlueRiver7294!";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        assertThatCode(() -> service.validate(password)).doesNotThrowAnyException();
        assertThat(encoder.matches(password, encoder.encode(password))).isTrue();
    }

    @Test
    void reportsAllMissingCharacterClasses() {
        PasswordPolicyException exception = catchThrowableOfType(
                () -> service.validate("abcdefghijkl"), PasswordPolicyException.class);

        Set<String> codes = exception.violations().stream()
                .map(PasswordPolicyViolation::code)
                .collect(Collectors.toSet());
        assertThat(codes).contains("PASSWORD_MISSING_UPPERCASE", "PASSWORD_MISSING_NUMBER",
                "PASSWORD_MISSING_SPECIAL_CHARACTER");
    }

    @Test
    void reportsMissingLowercaseAndMaximumLength() {
        PasswordPolicyException lowercase = catchThrowableOfType(
                () -> service.validate("ABCDEFGHIJK1!"), PasswordPolicyException.class);
        PasswordPolicyException tooLong = catchThrowableOfType(
                () -> service.validate("Aa1!" + "x".repeat(125)), PasswordPolicyException.class);

        assertThat(lowercase.violations()).extracting(PasswordPolicyViolation::code)
                .contains("PASSWORD_MISSING_LOWERCASE");
        assertThat(tooLong.violations()).extracting(PasswordPolicyViolation::code)
                .contains("PASSWORD_TOO_LONG");
    }
}
