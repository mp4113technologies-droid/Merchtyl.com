package com.merchtyl.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPasswordResetRequest(@NotBlank @Size(max = 1000) String reason) {
}
