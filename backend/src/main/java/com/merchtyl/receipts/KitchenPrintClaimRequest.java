package com.merchtyl.receipts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record KitchenPrintClaimRequest(@NotBlank @Size(max=255) String clientId) {}
