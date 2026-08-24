ALTER TABLE lottery_payouts
    ADD COLUMN paid_at TIMESTAMPTZ,
    ADD COLUMN paid_by UUID;

ALTER TABLE lottery_payouts
    ADD CONSTRAINT fk_lottery_payouts_paid_by
        FOREIGN KEY (paid_by) REFERENCES security_users (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_lottery_payouts_paid_state CHECK (
        (status = 'PAID' AND paid_at IS NOT NULL AND paid_by IS NOT NULL)
        OR status <> 'PAID'
    );

CREATE INDEX idx_lottery_payouts_paid_by ON lottery_payouts (paid_by);
CREATE INDEX idx_lottery_payouts_paid_at ON lottery_payouts (paid_at DESC);

ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_type;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_type CHECK (source_type IN (
    'SESSION_OPENING_FLOAT',
    'SALE_CASH_RECEIPT',
    'SALE_CHANGE_GIVEN',
    'LOTTERY_SALE_CASH',
    'LOTTERY_PAYOUT_CASH',
    'CASH_REFUND',
    'CASH_MOVEMENT',
    'SESSION_CLOSE_ADJUSTMENT'
));

ALTER TABLE cash_ledger_entries DROP CONSTRAINT ck_cash_ledger_entries_source_direction;
ALTER TABLE cash_ledger_entries ADD CONSTRAINT ck_cash_ledger_entries_source_direction CHECK (
    (source_type IN ('SESSION_OPENING_FLOAT', 'SALE_CASH_RECEIPT', 'LOTTERY_SALE_CASH') AND direction = 'IN')
    OR (source_type IN ('SALE_CHANGE_GIVEN', 'LOTTERY_PAYOUT_CASH', 'CASH_REFUND') AND direction = 'OUT')
    OR source_type IN ('CASH_MOVEMENT', 'SESSION_CLOSE_ADJUSTMENT')
);
