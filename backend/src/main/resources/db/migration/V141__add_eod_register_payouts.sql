ALTER TABLE end_of_day_register_summaries
    ADD COLUMN payouts NUMERIC(12, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE end_of_day_register_summaries
    ALTER COLUMN payouts DROP DEFAULT;
