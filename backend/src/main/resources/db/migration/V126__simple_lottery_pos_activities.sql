CREATE TABLE lottery_pos_activities (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    store_id UUID NOT NULL REFERENCES stores(id),
    register_id UUID NOT NULL REFERENCES registers(id),
    register_session_id UUID NOT NULL REFERENCES register_sessions(id),
    cashier_id UUID NOT NULL REFERENCES security_users(id),
    activity_type VARCHAR(16) NOT NULL CHECK (activity_type IN ('SOLD', 'WIN')),
    source VARCHAR(16) NOT NULL CHECK (source = 'MANUAL_POS'),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency_code VARCHAR(3) NOT NULL,
    business_date DATE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    operation_id UUID NOT NULL,
    CONSTRAINT uq_lottery_pos_activities_operation UNIQUE (operation_id)
);

CREATE INDEX idx_lottery_pos_activities_store_business_date
    ON lottery_pos_activities (store_id, business_date, occurred_at);
CREATE INDEX idx_lottery_pos_activities_session
    ON lottery_pos_activities (register_session_id, occurred_at);
