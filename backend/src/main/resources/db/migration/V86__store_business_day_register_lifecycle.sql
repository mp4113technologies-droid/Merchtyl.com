ALTER TABLE business_days
    ADD COLUMN reopened_at TIMESTAMPTZ,
    ADD COLUMN reopened_by UUID;

ALTER TABLE business_days
    ADD CONSTRAINT fk_business_days_reopened_by
        FOREIGN KEY (reopened_by) REFERENCES security_users(id),
    ADD CONSTRAINT ck_business_days_reopened_actor CHECK (
        (reopened_at IS NULL AND reopened_by IS NULL)
        OR (reopened_at IS NOT NULL AND reopened_by IS NOT NULL)
    );

ALTER TABLE register_sessions
    ADD COLUMN business_day_id UUID;

UPDATE register_sessions session
SET business_day_id = day.id
FROM business_days day
WHERE day.store_id = session.store_id
  AND day.business_date = (session.opened_at AT TIME ZONE day.timezone)::DATE;

ALTER TABLE register_sessions
    ADD CONSTRAINT fk_register_sessions_business_day
        FOREIGN KEY (business_day_id) REFERENCES business_days(id);

CREATE INDEX idx_register_sessions_business_day_status
    ON register_sessions (business_day_id, status, opened_at, id);

CREATE INDEX idx_business_days_store_later_date
    ON business_days (store_id, business_date DESC, id DESC);
