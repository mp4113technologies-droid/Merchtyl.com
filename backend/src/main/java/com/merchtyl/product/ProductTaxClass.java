package com.merchtyl.product;

/** Semantic product classification. Jurisdiction-specific rates never belong on the product. */
public enum ProductTaxClass {
    STANDARD,
    NON_TAXABLE,
    VAPE,
    CUSTOM
}
