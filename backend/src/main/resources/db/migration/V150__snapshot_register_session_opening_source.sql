ALTER TABLE register_sessions
    ADD COLUMN opening_source VARCHAR(32);

ALTER TABLE register_sessions
    ADD CONSTRAINT ck_register_sessions_opening_source
        CHECK (opening_source IS NULL OR opening_source IN ('STORE_DEFAULT', 'REGISTER_OVERRIDE', 'MANUAL_ENTRY'));

COMMENT ON COLUMN register_sessions.opening_source IS
    'Authoritative source used for this session opening; null only for historical sessions created before this snapshot was introduced.';
