package com.merchtyl.refunds;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record RefundCreateRequest(
        @NotNull UUID returnId,
        @NotNull @Size(max = 1000) String reason,
        @NotEmpty @Valid List<RefundPaymentRequest> payments,
        @Size(max = 1000) String approvalNotes
) {
}
