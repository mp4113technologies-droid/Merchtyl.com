ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_type;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_type CHECK (source_type IN (
    'SESSION_OPENING_FLOAT',
    'SALE_CASH_RECEIPT',
    'SALE_CHANGE_GIVEN',
    'LOTTERY_SALE_CASH',
    'LOTTERY_PAYOUT_CASH',
    'LOTTERY_PAYOUT_REVERSAL',
    'LOTTERY_SALE_CANCELLATION_CASH',
    'CASH_REFUND',
    'CASH_MOVEMENT',
    'SESSION_CLOSE_ADJUSTMENT'
));

ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_direction;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_direction CHECK (
    (source_type IN ('SESSION_OPENING_FLOAT', 'SALE_CASH_RECEIPT', 'LOTTERY_SALE_CASH', 'LOTTERY_PAYOUT_REVERSAL') AND direction = 'IN')
    OR (source_type IN ('SALE_CHANGE_GIVEN', 'LOTTERY_PAYOUT_CASH', 'LOTTERY_SALE_CANCELLATION_CASH', 'CASH_REFUND') AND direction = 'OUT')
    OR source_type IN ('CASH_MOVEMENT', 'SESSION_CLOSE_ADJUSTMENT')
);
