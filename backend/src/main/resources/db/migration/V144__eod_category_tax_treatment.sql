ALTER TABLE sale_items
    ADD COLUMN category_code_snapshot VARCHAR(64),
    ADD COLUMN tax_category_code_snapshot VARCHAR(64),
    ADD COLUMN tax_category_name_snapshot VARCHAR(180),
    ADD COLUMN product_tax_class_snapshot VARCHAR(32);

ALTER TABLE end_of_day_category_sales_summaries
    ADD COLUMN category_code VARCHAR(64),
    ADD COLUMN tax_treatment VARCHAR(32) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN tax_treatment_label VARCHAR(180) NOT NULL DEFAULT 'Historical Total',
    ADD COLUMN tax_category_code VARCHAR(64),
    ADD COLUMN tax_category_name VARCHAR(180),
    ADD COLUMN gross_sales NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN discounts NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN refunds NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN tax_collected NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Existing immutable EOD rows predate treatment-level detail. Preserve their
-- category total without inventing a historical tax classification.
UPDATE end_of_day_category_sales_summaries
SET gross_sales = net_sales
WHERE tax_treatment = 'LEGACY';
