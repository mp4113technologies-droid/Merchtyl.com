package com.merchtyl.auth;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Global password policy used for every Merchtyl role and password-setting flow.")
public record PasswordPolicyResponse(int minimumLength, int maximumLength, boolean requiresUppercase,
                                     boolean requiresLowercase, boolean requiresNumber, boolean requiresSpecialCharacter,
                                     String allowedSpecialCharacters) {}
