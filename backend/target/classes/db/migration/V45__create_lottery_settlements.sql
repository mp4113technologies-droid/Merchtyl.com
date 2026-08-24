CREATE TABLE lottery_settlements (
    id UUID PRIMARY KEY,
    operator_id UUID NOT NULL,
    jurisdiction_id UUID NOT NULL,
    store_id UUID NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    gross_sales NUMERIC(19, 2) NOT NULL,
    total_payouts NUMERIC(19, 2) NOT NULL,
    cancellations NUMERIC(19, 2) NOT NULL,
    adjustments NUMERIC(19, 2) NOT NULL,
    commission NUMERIC(19, 2) NOT NULL,
    expected_settlement NUMERIC(19, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lottery_settlements_operator FOREIGN KEY (operator_id) REFERENCES lottery_operators (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_settlements_jurisdiction FOREIGN KEY (jurisdiction_id) REFERENCES tax_jurisdictions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_settlements_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT uq_lottery_settlements_operator_store_period UNIQUE (operator_id, store_id, period_start, period_end),
    CONSTRAINT ck_lottery_settlements_period CHECK (period_end >= period_start)
);

CREATE INDEX idx_lottery_settlements_operator_id ON lottery_settlements (operator_id);
CREATE INDEX idx_lottery_settlements_jurisdiction_id ON lottery_settlements (jurisdiction_id);
CREATE INDEX idx_lottery_settlements_store_id ON lottery_settlements (store_id);
CREATE INDEX idx_lottery_settlements_period ON lottery_settlements (period_start, period_end);
