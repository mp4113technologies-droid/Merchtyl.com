package com.merchtyl.register;

import java.util.UUID;

public record RegisterSearchRequest(
        UUID storeId,
        String code,
        String name,
        Boolean active,
        int page,
        int size
) {
}
