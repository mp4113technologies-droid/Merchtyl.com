package com.merchtyl.registersession;

import jakarta.validation.constraints.Pattern;

public record TillUnlockRequest(
        @Pattern(regexp = "\\d{6}", message = "PIN must be exactly 6 digits") String pin,
        String password) {
}
