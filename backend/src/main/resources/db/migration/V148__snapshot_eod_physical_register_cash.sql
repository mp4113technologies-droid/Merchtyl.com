ALTER TABLE end_of_day_reports
    ADD COLUMN initial_opening_cash NUMERIC(12,2),
    ADD COLUMN cash_before_final_settlement NUMERIC(12,2),
    ADD COLUMN cash_removed_from_tills NUMERIC(12,2),
    ADD COLUMN cash_retained_in_tills NUMERIC(12,2);

ALTER TABLE end_of_day_register_summaries
    ADD COLUMN physical_initial_float NUMERIC(12,2),
    ADD COLUMN physical_cash_removed NUMERIC(12,2),
    ADD COLUMN physical_cash_retained NUMERIC(12,2),
    ADD COLUMN physical_session_count INTEGER;

-- Existing signed reports deliberately retain their legacy snapshot semantics.
-- Physical register/day values are populated only for newly generated revisions.
