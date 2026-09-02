CREATE TABLE food_order_token_sequences (
    store_id UUID NOT NULL,
    business_day_id UUID NOT NULL,
    last_value BIGINT NOT NULL,
    PRIMARY KEY (store_id, business_day_id),
    CONSTRAINT fk_food_order_token_sequences_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE,
    CONSTRAINT fk_food_order_token_sequences_business_day FOREIGN KEY (business_day_id) REFERENCES business_days(id) ON DELETE CASCADE,
    CONSTRAINT ck_food_order_token_sequences_positive CHECK (last_value > 0)
);

ALTER TABLE sales ADD COLUMN food_order_token VARCHAR(16);

CREATE UNIQUE INDEX uq_sales_food_order_token_per_business_day
    ON sales (store_id, business_date, food_order_token)
    WHERE food_order_token IS NOT NULL;
