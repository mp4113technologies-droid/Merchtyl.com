ALTER TABLE security_users
    ADD COLUMN pos_pin_hash VARCHAR(255),
    ADD COLUMN pos_pin_updated_at TIMESTAMPTZ;

ALTER TABLE register_sessions
    ADD COLUMN till_secured_at TIMESTAMPTZ,
    ADD COLUMN till_pin_failed_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN till_pin_locked_until TIMESTAMPTZ;

ALTER TABLE register_sessions
    ADD CONSTRAINT ck_register_sessions_till_pin_attempts
        CHECK (till_pin_failed_attempts >= 0);

