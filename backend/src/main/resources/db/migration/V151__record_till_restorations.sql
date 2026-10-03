ALTER TABLE cash_ledger_entries
    ALTER COLUMN register_session_id DROP NOT NULL;

ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_type;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_type CHECK (source_type IN (
    'SESSION_OPENING_FLOAT', 'SALE_CASH_RECEIPT', 'SALE_CHANGE_GIVEN', 'LOTTERY_SALE_CASH',
    'LOTTERY_PAYOUT_CASH', 'LOTTERY_PAYOUT_REVERSAL', 'LOTTERY_SALE_CANCELLATION_CASH',
    'DEPOSIT_PAYOUT', 'CASH_REFUND', 'CASH_MOVEMENT', 'SESSION_CLOSE_ADJUSTMENT',
    'SESSION_CLOSE_TILL_REMOVAL', 'TILL_RESTORE'
));

ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_direction;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_direction CHECK (
    source_type IN ('SESSION_OPENING_FLOAT', 'SALE_CASH_RECEIPT', 'LOTTERY_SALE_CASH',
        'LOTTERY_PAYOUT_REVERSAL', 'TILL_RESTORE') AND direction = 'IN'
    OR source_type IN ('SALE_CHANGE_GIVEN', 'LOTTERY_PAYOUT_CASH', 'LOTTERY_SALE_CANCELLATION_CASH',
        'DEPOSIT_PAYOUT', 'CASH_REFUND', 'SESSION_CLOSE_TILL_REMOVAL') AND direction = 'OUT'
    OR source_type IN ('CASH_MOVEMENT', 'SESSION_CLOSE_ADJUSTMENT')
);

ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_session_scope CHECK (
    register_session_id IS NOT NULL OR source_type = 'TILL_RESTORE'
);

COMMENT ON COLUMN cash_ledger_entries.register_session_id IS
    'Null only for physical till restoration between register sessions.';
