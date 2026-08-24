package com.merchtyl.auth;
public record PasswordPolicyResponse(int minimumLength, int maximumLength, boolean requiresUppercase,
                                     boolean requiresLowercase, boolean requiresNumber, boolean requiresSpecialCharacter) {}
