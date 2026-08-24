CREATE TABLE cash_ledger_entries (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    register_id UUID NOT NULL,
    register_session_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID NOT NULL,
    direction VARCHAR(8) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    business_date DATE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    operation_id UUID NOT NULL,
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cash_ledger_entries_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_ledger_entries_register FOREIGN KEY (register_id) REFERENCES registers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_ledger_entries_register_session FOREIGN KEY (register_session_id) REFERENCES register_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cash_ledger_entries_created_by FOREIGN KEY (created_by) REFERENCES security_users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_cash_ledger_entries_operation UNIQUE (operation_id),
    CONSTRAINT ck_cash_ledger_entries_source_type CHECK (source_type IN (
        'SESSION_OPENING_FLOAT',
        'SALE_CASH_RECEIPT',
        'SALE_CHANGE_GIVEN',
        'CASH_REFUND',
        'CASH_MOVEMENT',
        'SESSION_CLOSE_ADJUSTMENT'
    )),
    CONSTRAINT ck_cash_ledger_entries_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT ck_cash_ledger_entries_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_cash_ledger_entries_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_cash_ledger_entries_source_direction CHECK (
        (source_type IN ('SESSION_OPENING_FLOAT', 'SALE_CASH_RECEIPT') AND direction = 'IN')
        OR (source_type IN ('SALE_CHANGE_GIVEN', 'CASH_REFUND') AND direction = 'OUT')
        OR source_type IN ('CASH_MOVEMENT', 'SESSION_CLOSE_ADJUSTMENT')
    ),
    CONSTRAINT ck_cash_ledger_entries_notes_nonblank CHECK (notes IS NULL OR btrim(notes) <> '')
);

CREATE INDEX idx_cash_ledger_entries_store ON cash_ledger_entries (store_id);
CREATE INDEX idx_cash_ledger_entries_register ON cash_ledger_entries (register_id);
CREATE INDEX idx_cash_ledger_entries_register_session ON cash_ledger_entries (register_session_id);
CREATE INDEX idx_cash_ledger_entries_source ON cash_ledger_entries (source_type, source_id);
CREATE INDEX idx_cash_ledger_entries_business_date ON cash_ledger_entries (business_date);
CREATE INDEX idx_cash_ledger_entries_occurred_at ON cash_ledger_entries (occurred_at DESC);

CREATE OR REPLACE FUNCTION prevent_cash_ledger_entry_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'cash_ledger_entries are immutable';
END;
$$;

CREATE TRIGGER trg_cash_ledger_entries_immutable
BEFORE UPDATE OR DELETE ON cash_ledger_entries
FOR EACH ROW
EXECUTE FUNCTION prevent_cash_ledger_entry_mutation();
