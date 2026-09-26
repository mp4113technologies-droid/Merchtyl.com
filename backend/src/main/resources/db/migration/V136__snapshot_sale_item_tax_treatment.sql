ALTER TABLE sale_items
    ADD COLUMN historical_tax_treatment VARCHAR(32);

ALTER TABLE sale_items
    ADD CONSTRAINT ck_sale_items_historical_tax_treatment
        CHECK (historical_tax_treatment IS NULL OR historical_tax_treatment IN ('TAXABLE', 'NON_TAXABLE'));

CREATE INDEX idx_sale_items_reporting_classification
    ON sale_items (sale_id, line_type, sellable_type_snapshot, historical_tax_treatment);
