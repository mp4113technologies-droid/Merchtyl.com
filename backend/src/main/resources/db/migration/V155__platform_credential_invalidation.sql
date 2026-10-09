ALTER TABLE platform_users
    ADD COLUMN IF NOT EXISTS credentials_invalidated_at TIMESTAMPTZ;
