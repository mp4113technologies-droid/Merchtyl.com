package com.merchtyl.auth;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException() {
        super("Your account is locked. Reset your password or contact your administrator.");
    }
}
