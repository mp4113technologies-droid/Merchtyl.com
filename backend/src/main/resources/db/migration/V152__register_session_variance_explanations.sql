ALTER TABLE register_sessions
    ADD COLUMN variance_explanation VARCHAR(1000);

ALTER TABLE register_sessions
    ADD CONSTRAINT ck_register_sessions_variance_explanation_nonblank
        CHECK (variance_explanation IS NULL OR btrim(variance_explanation) <> '');

ALTER TABLE end_of_day_register_summaries
    ADD COLUMN variance_explanation VARCHAR(1000);

