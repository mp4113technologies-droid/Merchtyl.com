package com.merchtyl.tax;

import java.util.UUID;

public record AdministrativeAreaSearchRequest(
        UUID countryId,
        String code,
        String name,
        AdministrativeAreaType type,
        Boolean active,
        int page,
        int size
) {
}
