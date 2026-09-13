DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM register_sessions
        WHERE status IN ('OPEN', 'CLOSING')
        GROUP BY register_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot enforce register-session exclusivity: duplicate active register sessions require reconciliation';
    END IF;

END $$;

CREATE UNIQUE INDEX uq_register_sessions_active_register
    ON register_sessions(register_id)
    WHERE status IN ('OPEN', 'CLOSING');
