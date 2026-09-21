ALTER TABLE sales DROP CONSTRAINT ck_sales_phone_order_shape;

ALTER TABLE sales ADD CONSTRAINT ck_sales_phone_order_shape CHECK (
    (phone_confirmed_at IS NULL AND phone_customer_name IS NULL AND pickup_at IS NULL AND kitchen_status IS NULL)
    OR
    (phone_confirmed_at IS NOT NULL AND phone_customer_name IS NOT NULL AND btrim(phone_customer_name) <> ''
        AND pickup_at IS NOT NULL
        AND kitchen_status IN ('PENDING', 'IN_PROGRESS', 'READY', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_sales_phone_pickup_history
    ON sales (store_id, business_date, kitchen_updated_at DESC, id DESC)
    WHERE kitchen_status = 'COMPLETED';

CREATE INDEX idx_sales_phone_pickup_operational_active
    ON sales (store_id, pickup_at, id)
    WHERE phone_confirmed_at IS NOT NULL
      AND status <> 'CANCELLED'
      AND kitchen_status IN ('PENDING', 'IN_PROGRESS', 'READY');
