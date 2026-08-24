CREATE TABLE register_sessions (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    register_id UUID NOT NULL,
    device_id UUID NOT NULL,
    assigned_cashier_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    opening_cash NUMERIC(12, 2) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_register_sessions_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT fk_register_sessions_register FOREIGN KEY (register_id) REFERENCES registers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_register_sessions_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE RESTRICT,
    CONSTRAINT fk_register_sessions_assigned_cashier FOREIGN KEY (assigned_cashier_id) REFERENCES security_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_register_sessions_status CHECK (status IN ('OPEN', 'CLOSING', 'CLOSED', 'FORCE_CLOSED')),
    CONSTRAINT ck_register_sessions_opening_cash_nonnegative CHECK (opening_cash >= 0)
);

CREATE INDEX idx_register_sessions_store_id ON register_sessions (store_id);
CREATE INDEX idx_register_sessions_register_id ON register_sessions (register_id);
CREATE INDEX idx_register_sessions_device_id ON register_sessions (device_id);
CREATE INDEX idx_register_sessions_assigned_cashier_id ON register_sessions (assigned_cashier_id);
CREATE INDEX idx_register_sessions_status ON register_sessions (status);
CREATE INDEX idx_register_sessions_opened_at ON register_sessions (opened_at DESC);
