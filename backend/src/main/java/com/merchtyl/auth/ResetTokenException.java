package com.merchtyl.auth;

public class ResetTokenException extends RuntimeException {
    private final String code;

    public ResetTokenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
