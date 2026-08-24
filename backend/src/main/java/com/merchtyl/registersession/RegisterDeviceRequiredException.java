package com.merchtyl.registersession;

public class RegisterDeviceRequiredException extends RuntimeException {
    public RegisterDeviceRequiredException() {
        super("A valid active device is required to open this register while device enforcement is enabled");
    }
}
