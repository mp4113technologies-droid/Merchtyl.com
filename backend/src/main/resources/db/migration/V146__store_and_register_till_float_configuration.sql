ALTER TABLE stores
    ADD COLUMN default_till_float NUMERIC(12,2),
    ADD CONSTRAINT ck_stores_default_till_float_nonnegative
        CHECK (default_till_float IS NULL OR default_till_float >= 0);

ALTER TABLE registers
    ADD COLUMN till_float_override NUMERIC(12,2),
    ADD CONSTRAINT ck_registers_till_float_override_nonnegative
        CHECK (till_float_override IS NULL OR till_float_override >= 0);

ALTER TABLE register_business_day_cash_states
    ALTER COLUMN target_float DROP NOT NULL;

-- Existing Stores and Registers remain unconfigured. Historical register-day
-- snapshots are not changed or inferred from session opening balances.
