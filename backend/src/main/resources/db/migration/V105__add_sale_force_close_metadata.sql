ALTER TABLE sales ADD COLUMN IF NOT EXISTS force_closed_at TIMESTAMPTZ;
ALTER TABLE sales ADD COLUMN IF NOT EXISTS force_closed_by UUID;
ALTER TABLE sales ADD COLUMN IF NOT EXISTS force_close_reason_code VARCHAR(48);
ALTER TABLE sales ADD COLUMN IF NOT EXISTS force_close_note VARCHAR(500);
ALTER TABLE sales ADD CONSTRAINT fk_sales_force_closed_by FOREIGN KEY (force_closed_by) REFERENCES security_users(id) ON DELETE RESTRICT;
