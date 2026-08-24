package com.merchtyl.supplier;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String code,
        String name,
        String contactName,
        String phone,
        String email,
        String address,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getContactName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getNotes(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt(),
                supplier.getVersion());
    }
}
