package com.merchtyl.security;

import com.merchtyl.config.SecurityProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class TemporaryPasswordGenerator {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWER = "abcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGITS = "23456789".toCharArray();
    private static final char[] SYMBOLS = "!@#$%^&*?".toCharArray();
    private static final char[] ALL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*?".toCharArray();

    private final SecurityProperties securityProperties;

    public TemporaryPasswordGenerator(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public String generate() {
        int length = securityProperties.temporaryPassword().length();
        char[] password = new char[length];
        password[0] = random(UPPER);
        password[1] = random(LOWER);
        password[2] = random(DIGITS);
        password[3] = random(SYMBOLS);
        for (int i = 4; i < password.length; i++) {
            password[i] = random(ALL);
        }
        for (int i = password.length - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char current = password[i];
            password[i] = password[j];
            password[j] = current;
        }
        return new String(password);
    }

    private static char random(char[] chars) {
        return chars[SECURE_RANDOM.nextInt(chars.length)];
    }
}
