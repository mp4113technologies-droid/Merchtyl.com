ALTER TABLE end_of_day_register_summaries
    ADD COLUMN physical_target_float NUMERIC(12,2),
    ADD COLUMN session_target_float NUMERIC(12,2),
    ADD COLUMN session_cash_removed NUMERIC(12,2),
    ADD COLUMN session_cash_retained NUMERIC(12,2),
    ADD CONSTRAINT ck_eod_register_physical_target_nonnegative CHECK (physical_target_float IS NULL OR physical_target_float >= 0),
    ADD CONSTRAINT ck_eod_register_session_target_nonnegative CHECK (session_target_float IS NULL OR session_target_float >= 0),
    ADD CONSTRAINT ck_eod_register_session_removed_nonnegative CHECK (session_cash_removed IS NULL OR session_cash_removed >= 0),
    ADD CONSTRAINT ck_eod_register_session_retained_nonnegative CHECK (session_cash_retained IS NULL OR session_cash_retained >= 0);

-- Existing signed reports deliberately retain their original snapshot. New
-- report revisions snapshot both physical-register and shift settlement data.
