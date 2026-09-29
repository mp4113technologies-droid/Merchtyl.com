ALTER TABLE cash_movements
    DROP CONSTRAINT ck_cash_movements_type,
    DROP CONSTRAINT ck_cash_movements_type_direction;

ALTER TABLE cash_movements
    ADD CONSTRAINT ck_cash_movements_type CHECK (type IN (
        'CASH_IN',
        'CASH_OUT',
        'PAYOUT',
        'PAYOUT_REVERSAL',
        'SAFE_DROP',
        'FLOAT_ADD',
        'FLOAT_REMOVE',
        'EXPENSE',
        'BANK_DEPOSIT',
        'CORRECTION'
    )),
    ADD CONSTRAINT ck_cash_movements_type_direction CHECK (
        (type IN ('CASH_IN', 'FLOAT_ADD', 'PAYOUT_REVERSAL') AND direction = 'IN')
        OR (type IN ('CASH_OUT', 'PAYOUT', 'SAFE_DROP', 'FLOAT_REMOVE', 'EXPENSE', 'BANK_DEPOSIT') AND direction = 'OUT')
        OR type = 'CORRECTION'
    );
