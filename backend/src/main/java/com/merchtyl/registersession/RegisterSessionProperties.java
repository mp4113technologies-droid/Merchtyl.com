package com.merchtyl.registersession;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "merchtyl.register-sessions")
public class RegisterSessionProperties {
    private boolean singleOpenPerRegister = true;
    private boolean singleOpenPerDevice = true;

    public boolean isSingleOpenPerRegister() {
        return singleOpenPerRegister;
    }

    public void setSingleOpenPerRegister(boolean singleOpenPerRegister) {
        this.singleOpenPerRegister = singleOpenPerRegister;
    }

    public boolean isSingleOpenPerDevice() {
        return singleOpenPerDevice;
    }

    public void setSingleOpenPerDevice(boolean singleOpenPerDevice) {
        this.singleOpenPerDevice = singleOpenPerDevice;
    }
}
