package com.merchtyl.tax;

import java.util.UUID;

public record TaxGroupComponentSearchRequest(UUID taxGroupId, UUID taxComponentId, Boolean active, int page, int size) {
}
