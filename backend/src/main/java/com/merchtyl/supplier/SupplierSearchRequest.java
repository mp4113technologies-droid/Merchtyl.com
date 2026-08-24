package com.merchtyl.supplier;

public record SupplierSearchRequest(
        String code,
        String name,
        String contactName,
        String email,
        Boolean active,
        int page,
        int size
) {
}
