package com.merchtyl.receipts;

import jakarta.validation.constraints.Size;
public record KitchenPrintAcknowledgeRequest(@jakarta.validation.constraints.NotBlank @Size(max=255) String clientId,
        boolean success, @Size(max=1000) String error) {}
