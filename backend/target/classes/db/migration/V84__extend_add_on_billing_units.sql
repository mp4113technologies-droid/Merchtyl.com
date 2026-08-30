ALTER TABLE platform_pricing_plan_version_capabilities
    DROP CONSTRAINT ck_pricing_capability_unit;

ALTER TABLE platform_pricing_plan_version_capabilities
    ADD CONSTRAINT ck_pricing_capability_unit CHECK (
        billing_unit IS NULL OR billing_unit IN ('PER_MERCHANT', 'PER_STORE', 'PER_USER', 'PER_REGISTER')
    );

ALTER TABLE platform_invoice_lines
    ADD COLUMN capability VARCHAR(80),
    ADD COLUMN billing_unit VARCHAR(30),
    ADD CONSTRAINT ck_platform_invoice_line_billing_unit CHECK (
        billing_unit IS NULL OR billing_unit IN ('PER_MERCHANT', 'PER_STORE', 'PER_USER', 'PER_REGISTER')
    );
