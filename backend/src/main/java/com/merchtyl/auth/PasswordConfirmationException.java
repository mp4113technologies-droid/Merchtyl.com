package com.merchtyl.auth;
public class PasswordConfirmationException extends RuntimeException {
    public PasswordConfirmationException() { super("The passwords do not match."); }
}
