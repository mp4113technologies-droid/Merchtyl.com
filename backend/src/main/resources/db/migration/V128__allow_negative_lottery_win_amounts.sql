ALTER TABLE sale_items DROP CONSTRAINT ck_sale_items_amounts_nonnegative;

ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_amounts_nonnegative CHECK (
    unit_price >= 0
    AND discount_amount >= 0
    AND estimated_tax_amount >= 0
    AND (
        (line_type = 'LOTTERY_WIN' AND line_subtotal <= 0 AND line_total <= 0)
        OR
        (line_type <> 'LOTTERY_WIN' AND line_subtotal >= 0 AND line_total >= 0)
    )
);
