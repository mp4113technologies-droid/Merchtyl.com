package com.merchtyl.registersession;

public class InvalidTillCredentialException extends RuntimeException {
    public InvalidTillCredentialException() { super("Invalid till unlock credential"); }
}
