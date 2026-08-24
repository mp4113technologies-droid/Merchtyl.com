ALTER TABLE sales
    ADD COLUMN completed_by UUID,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD CONSTRAINT fk_sales_completed_by FOREIGN KEY (completed_by) REFERENCES security_users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_sales_completed_timestamp CHECK (
        (status = 'COMPLETED' AND completed_by IS NOT NULL AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED')
    );

ALTER TABLE sale_items
    ADD COLUMN completed_product_cost NUMERIC(19, 4),
    ADD COLUMN completed_product_price NUMERIC(19, 4),
    ADD COLUMN completed_product_capabilities VARCHAR(1000),
    ADD CONSTRAINT ck_sale_items_completed_snapshots_nonnegative CHECK (
        completed_product_cost IS NULL OR completed_product_cost >= 0
    ),
    ADD CONSTRAINT ck_sale_items_completed_price_nonnegative CHECK (
        completed_product_price IS NULL OR completed_product_price >= 0
    );

CREATE INDEX idx_sales_completed_at ON sales(completed_at);
