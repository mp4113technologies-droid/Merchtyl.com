package com.merchtyl.tax;

public record TaxGroupSearchRequest(String code, String name, Boolean active, int page, int size) {
}
