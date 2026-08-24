CREATE TABLE payments (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL,
    method VARCHAR(32) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    cash_tendered NUMERIC(12, 2),
    change_due NUMERIC(12, 2) NOT NULL,
    manual_reference VARCHAR(120),
    notes VARCHAR(500),
    created_by UUID NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payments_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES security_users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'DEBIT', 'CREDIT', 'GIFT_CARD', 'STORE_CREDIT', 'OTHER')),
    CONSTRAINT ck_payments_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payments_change_nonnegative CHECK (change_due >= 0),
    CONSTRAINT ck_payments_cash_fields CHECK (
        (method = 'CASH' AND cash_tendered IS NOT NULL AND cash_tendered >= amount AND change_due = cash_tendered - amount)
        OR (method <> 'CASH' AND cash_tendered IS NULL AND change_due = 0)
    ),
    CONSTRAINT ck_payments_manual_reference_nonblank CHECK (manual_reference IS NULL OR btrim(manual_reference) <> ''),
    CONSTRAINT ck_payments_notes_nonblank CHECK (notes IS NULL OR btrim(notes) <> '')
);

CREATE INDEX idx_payments_sale ON payments(sale_id);
CREATE INDEX idx_payments_method ON payments(method);
CREATE INDEX idx_payments_completed_at ON payments(completed_at);
