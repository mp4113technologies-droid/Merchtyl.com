ALTER TABLE sale_items
    ADD COLUMN category_snapshot_id UUID,
    ADD COLUMN category_name_snapshot VARCHAR(180);

-- Freeze the best category information still available for legacy sale lines.
-- Earlier category moves/renames cannot be reconstructed retroactively.
UPDATE sale_items si
SET category_snapshot_id = p.category_id,
    category_name_snapshot = c.name
FROM products p
LEFT JOIN categories c ON c.id = p.category_id
WHERE si.product_id = p.id
  AND si.line_type = 'CATALOG_PRODUCT'
  AND si.category_snapshot_id IS NULL;

CREATE TABLE end_of_day_category_sales_summaries (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL,
    category_id UUID,
    category_name VARCHAR(180) NOT NULL,
    quantity_sold NUMERIC(19,4) NOT NULL,
    net_sales NUMERIC(12,2) NOT NULL,
    percentage NUMERIC(7,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_eod_category_sales_report
        FOREIGN KEY (report_id) REFERENCES end_of_day_reports(id) ON DELETE CASCADE
);

CREATE INDEX idx_eod_category_sales_report
    ON end_of_day_category_sales_summaries(report_id);
