package com.merchtyl.supplier;

public record SupplierValues(
        String code,
        String name,
        String contactName,
        String phone,
        String email,
        String address,
        String notes,
        boolean active
) {
}
