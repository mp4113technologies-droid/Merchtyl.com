package com.merchtyl.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestEmailRequest(
        @NotBlank @Email @Size(max = 320) String recipient
) {
}
