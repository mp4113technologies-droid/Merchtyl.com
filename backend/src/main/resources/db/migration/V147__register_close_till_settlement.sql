ALTER TABLE register_sessions
    ADD COLUMN target_float_at_close NUMERIC(12,2),
    ADD COLUMN cash_retained NUMERIC(12,2),
    ADD COLUMN cash_removed NUMERIC(12,2),
    ADD COLUMN retention_override_by_user_id UUID,
    ADD COLUMN retention_override_reason VARCHAR(1000),
    ADD CONSTRAINT fk_register_session_retention_override_user
        FOREIGN KEY (retention_override_by_user_id) REFERENCES security_users(id),
    ADD CONSTRAINT ck_register_session_target_float_nonnegative
        CHECK (target_float_at_close IS NULL OR target_float_at_close >= 0),
    ADD CONSTRAINT ck_register_session_cash_retained_nonnegative
        CHECK (cash_retained IS NULL OR cash_retained >= 0),
    ADD CONSTRAINT ck_register_session_cash_removed_nonnegative
        CHECK (cash_removed IS NULL OR cash_removed >= 0);

ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_type;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_type CHECK (source_type IN (
    'SESSION_OPENING_FLOAT', 'SALE_CASH_RECEIPT', 'SALE_CHANGE_GIVEN', 'LOTTERY_SALE_CASH',
    'LOTTERY_PAYOUT_CASH', 'LOTTERY_PAYOUT_REVERSAL', 'LOTTERY_SALE_CANCELLATION_CASH',
    'DEPOSIT_PAYOUT', 'CASH_REFUND', 'CASH_MOVEMENT', 'SESSION_CLOSE_ADJUSTMENT',
    'SESSION_CLOSE_TILL_REMOVAL'
));

ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_direction;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_direction CHECK (
    source_type IN ('SESSION_OPENING_FLOAT', 'SALE_CASH_RECEIPT', 'LOTTERY_SALE_CASH', 'LOTTERY_PAYOUT_REVERSAL') AND direction = 'IN'
    OR source_type IN ('SALE_CHANGE_GIVEN', 'LOTTERY_PAYOUT_CASH', 'LOTTERY_SALE_CANCELLATION_CASH',
        'DEPOSIT_PAYOUT', 'CASH_REFUND', 'SESSION_CLOSE_TILL_REMOVAL') AND direction = 'OUT'
    OR source_type IN ('CASH_MOVEMENT', 'SESSION_CLOSE_ADJUSTMENT')
);

-- No historical settlement values are manufactured for completed sessions.
