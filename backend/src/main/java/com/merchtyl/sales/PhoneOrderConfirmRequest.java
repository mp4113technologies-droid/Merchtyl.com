package com.merchtyl.sales;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record PhoneOrderConfirmRequest(
        @NotBlank @Size(max = 120) String customerName,
        @Size(max = 40) String phoneNumber,
        boolean asap,
        LocalDateTime pickupLocalDateTime,
        @Size(max = 1000) String orderNotes
) {
}
