CREATE TABLE register_business_day_cash_states (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    register_id UUID NOT NULL,
    business_day_id UUID NOT NULL,
    initial_float NUMERIC(12,2) NOT NULL,
    target_float NUMERIC(12,2) NOT NULL,
    retained_cash NUMERIC(12,2) NOT NULL,
    cash_removed NUMERIC(12,2) NOT NULL DEFAULT 0,
    final_expected_cash NUMERIC(12,2),
    final_counted_cash NUMERIC(12,2),
    session_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_register_day_cash_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_register_day_cash_register FOREIGN KEY (register_id) REFERENCES registers(id),
    CONSTRAINT fk_register_day_cash_business_day FOREIGN KEY (business_day_id) REFERENCES business_days(id),
    CONSTRAINT uq_register_day_cash_state UNIQUE (store_id, register_id, business_day_id),
    CONSTRAINT ck_register_day_cash_initial_nonnegative CHECK (initial_float >= 0),
    CONSTRAINT ck_register_day_cash_target_nonnegative CHECK (target_float >= 0),
    CONSTRAINT ck_register_day_cash_retained_nonnegative CHECK (retained_cash >= 0),
    CONSTRAINT ck_register_day_cash_removed_nonnegative CHECK (cash_removed >= 0),
    CONSTRAINT ck_register_day_cash_session_count CHECK (session_count >= 1)
);

CREATE INDEX ix_register_day_cash_business_day ON register_business_day_cash_states(business_day_id);
CREATE INDEX ix_register_day_cash_store_register ON register_business_day_cash_states(store_id, register_id);

-- Deliberately no historical backfill: legacy sessions do not record retained/removal
-- decisions, so manufacturing register-day settlements would alter their meaning.
