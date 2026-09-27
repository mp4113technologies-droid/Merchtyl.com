-- A Product has one semantic tax treatment. Only CUSTOM may carry a direct,
-- merchant-owned category. This migration adds metadata and constraints only;
-- it deliberately does not rewrite Product rows or completed financial history.

ALTER TABLE products DROP CONSTRAINT chk_products_tax_class;
ALTER TABLE products ADD CONSTRAINT chk_products_tax_class
    CHECK (tax_class IN ('STANDARD','NON_TAXABLE','VAPE','CUSTOM'));

ALTER TABLE tax_categories
    ADD COLUMN merchant_assignable BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN owner_tenant_id UUID;

ALTER TABLE tax_categories
    ADD CONSTRAINT fk_tax_categories_owner_tenant
        FOREIGN KEY (owner_tenant_id) REFERENCES tenants(id);

UPDATE tax_categories
SET merchant_assignable = FALSE
WHERE system_managed = TRUE;

CREATE INDEX idx_tax_categories_owner_tenant ON tax_categories(owner_tenant_id);

ALTER TABLE tax_categories DROP CONSTRAINT chk_tax_categories_product_tax_class;
ALTER TABLE tax_categories ADD CONSTRAINT chk_tax_categories_product_tax_class
    CHECK (applicable_product_tax_class IS NULL OR applicable_product_tax_class IN ('STANDARD','NON_TAXABLE','VAPE','CUSTOM'));
