package com.merchtyl.security;

import com.merchtyl.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryPasswordGeneratorTest {

    @Test
    void generatesConfiguredLengthWithRequiredCharacterClasses() {
        TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator(properties(20));

        String password = generator.generate();

        assertThat(password).hasSize(20);
        assertThat(password).containsPattern("[A-Z]");
        assertThat(password).containsPattern("[a-z]");
        assertThat(password).containsPattern("[0-9]");
        assertThat(password).containsPattern("[!@#$%^&*?]");
        assertThat(password).doesNotContain("I", "O", "l", "1", "0");
    }

    @Test
    void enforcesMinimumLength() {
        TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator(properties(8));

        assertThat(generator.generate()).hasSize(16);
    }

    @Test
    void doesNotReusePasswordsAcrossSample() {
        TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator(properties(20));
        Set<String> generated = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            generated.add(generator.generate());
        }

        assertThat(generated).hasSize(100);
    }

    private static SecurityProperties properties(int length) {
        return new SecurityProperties(null, null, new SecurityProperties.TemporaryPassword(length, 24, 10));
    }
}
