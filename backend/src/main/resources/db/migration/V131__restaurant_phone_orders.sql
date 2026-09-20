ALTER TABLE sales DROP CONSTRAINT ck_sales_status;
ALTER TABLE sales ADD CONSTRAINT ck_sales_status CHECK (status IN (
    'DRAFT', 'PENDING_PAYMENT', 'PHONE_CONFIRMED', 'HELD', 'COMPLETED', 'VOIDED',
    'PARTIALLY_REFUNDED', 'REFUNDED', 'CANCELLED'
));

ALTER TABLE sales ADD COLUMN phone_customer_name VARCHAR(120);
ALTER TABLE sales ADD COLUMN phone_number VARCHAR(40);
ALTER TABLE sales ADD COLUMN pickup_at TIMESTAMPTZ;
ALTER TABLE sales ADD COLUMN pickup_asap BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sales ADD COLUMN order_notes VARCHAR(1000);
ALTER TABLE sales ADD COLUMN phone_confirmed_at TIMESTAMPTZ;
ALTER TABLE sales ADD COLUMN kitchen_status VARCHAR(24);
ALTER TABLE sales ADD COLUMN kitchen_updated_at TIMESTAMPTZ;

ALTER TABLE sales ADD CONSTRAINT ck_sales_phone_order_shape CHECK (
    (phone_confirmed_at IS NULL AND phone_customer_name IS NULL AND pickup_at IS NULL AND kitchen_status IS NULL)
    OR
    (phone_confirmed_at IS NOT NULL AND phone_customer_name IS NOT NULL AND btrim(phone_customer_name) <> ''
        AND pickup_at IS NOT NULL AND kitchen_status IN ('PENDING', 'IN_PROGRESS', 'READY', 'CANCELLED'))
);

CREATE INDEX idx_sales_phone_pickup_active
    ON sales (store_id, pickup_at, id)
    WHERE status = 'PHONE_CONFIRMED';
