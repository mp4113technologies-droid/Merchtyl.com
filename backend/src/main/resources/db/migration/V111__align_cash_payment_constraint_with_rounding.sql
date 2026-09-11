ALTER TABLE payments
    DROP CONSTRAINT ck_payments_cash_fields;

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_cash_fields CHECK (
        (
            method = 'CASH'
            AND cash_tendered IS NOT NULL
            AND cash_settlement_amount IS NOT NULL
            AND cash_tendered >= cash_settlement_amount
            AND change_due = cash_tendered - cash_settlement_amount
        )
        OR
        (
            method <> 'CASH'
            AND cash_tendered IS NULL
            AND cash_settlement_amount IS NULL
            AND cash_rounding_adjustment = 0.00
            AND change_due = 0.00
        )
    );
