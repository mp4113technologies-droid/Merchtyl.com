ALTER TABLE sale_items DROP CONSTRAINT ck_sale_items_line_type;
ALTER TABLE sale_items DROP CONSTRAINT ck_sale_items_shape;
ALTER TABLE sale_items DROP CONSTRAINT ck_sale_items_amounts_nonnegative;

ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_line_type
    CHECK (line_type IN ('CATALOG_PRODUCT', 'CUSTOM_ITEM', 'LOTTERY_SOLD', 'LOTTERY_WIN', 'DEPOSIT_PAYOUT'));

ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_shape CHECK (
    (line_type = 'CATALOG_PRODUCT' AND product_id IS NOT NULL AND custom_item_tax_treatment IS NULL)
    OR
    (line_type = 'CUSTOM_ITEM' AND product_id IS NULL AND variant_id IS NULL
        AND product_sku IS NULL AND custom_item_tax_treatment IS NOT NULL)
    OR
    (line_type IN ('LOTTERY_SOLD', 'LOTTERY_WIN', 'DEPOSIT_PAYOUT')
        AND product_id IS NULL AND variant_id IS NULL
        AND product_sku IS NULL AND custom_item_tax_treatment IS NULL)
);

ALTER TABLE sale_items ADD CONSTRAINT ck_sale_items_amounts_nonnegative CHECK (
    unit_price >= 0
    AND discount_amount >= 0
    AND estimated_tax_amount >= 0
    AND (
        (line_type IN ('LOTTERY_WIN', 'DEPOSIT_PAYOUT') AND line_subtotal <= 0 AND line_total <= 0)
        OR
        (line_type NOT IN ('LOTTERY_WIN', 'DEPOSIT_PAYOUT') AND line_subtotal >= 0 AND line_total >= 0)
    )
);

ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_type;
ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_direction;

ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_type CHECK (source_type IN (
    'SESSION_OPENING_FLOAT',
    'SALE_CASH_RECEIPT',
    'SALE_CHANGE_GIVEN',
    'LOTTERY_SALE_CASH',
    'LOTTERY_PAYOUT_CASH',
    'LOTTERY_PAYOUT_REVERSAL',
    'LOTTERY_SALE_CANCELLATION_CASH',
    'DEPOSIT_PAYOUT',
    'CASH_REFUND',
    'CASH_MOVEMENT',
    'SESSION_CLOSE_ADJUSTMENT'
));

ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_direction CHECK (
    (source_type IN ('SESSION_OPENING_FLOAT', 'SALE_CASH_RECEIPT', 'LOTTERY_SALE_CASH', 'LOTTERY_PAYOUT_REVERSAL') AND direction = 'IN')
    OR (source_type IN ('SALE_CHANGE_GIVEN', 'LOTTERY_PAYOUT_CASH', 'LOTTERY_SALE_CANCELLATION_CASH', 'DEPOSIT_PAYOUT', 'CASH_REFUND') AND direction = 'OUT')
    OR source_type IN ('CASH_MOVEMENT', 'SESSION_CLOSE_ADJUSTMENT')
);

INSERT INTO security_permissions (id, code, description)
SELECT gen_random_uuid(), 'POS_DEPOSIT_PAYOUT', 'Add and complete custom-amount deposit payouts at Retail POS.'
WHERE NOT EXISTS (SELECT 1 FROM security_permissions WHERE code = 'POS_DEPOSIT_PAYOUT');

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT gen_random_uuid(), role.id, permission.id
FROM security_roles role
CROSS JOIN security_permissions permission
WHERE role.name IN ('OWNER', 'TENANT_OWNER', 'MANAGER', 'STORE_MANAGER', 'CASHIER')
  AND permission.code = 'POS_DEPOSIT_PAYOUT'
ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE end_of_day_reports ADD COLUMN deposits_collected NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE end_of_day_reports ADD COLUMN deposit_payouts NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE end_of_day_reports ADD COLUMN net_deposits NUMERIC(12,2) NOT NULL DEFAULT 0;
