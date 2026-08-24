package com.merchtyl.security;

import com.merchtyl.store.Store;

import java.util.UUID;

public record AssignedStoreResponse(
        UUID storeId,
        String storeCode,
        String storeName,
        String city,
        String administrativeDivisionCode,
        AssignmentRole assignmentRole
) {
    public static AssignedStoreResponse from(Store store, AssignmentRole assignmentRole) {
        return new AssignedStoreResponse(
                store.getId(),
                store.getCode(),
                store.getName(),
                cityFromAddress(store.getAddress()),
                store.getAdministrativeAreaCode(),
                assignmentRole);
    }

    private static String cityFromAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] parts = address.split(",");
        return parts.length == 0 ? address.trim() : parts[0].trim();
    }
}
