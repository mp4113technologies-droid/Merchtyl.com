ALTER TABLE payments
    ADD COLUMN cash_rounding_adjustment NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN cash_settlement_amount NUMERIC(12, 2);

UPDATE payments
SET cash_settlement_amount = amount
WHERE method = 'CASH';

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_cash_rounding_consistency CHECK (
        (method = 'CASH' AND cash_settlement_amount IS NOT NULL)
        OR
        (method <> 'CASH' AND cash_settlement_amount IS NULL AND cash_rounding_adjustment = 0.00)
    );
