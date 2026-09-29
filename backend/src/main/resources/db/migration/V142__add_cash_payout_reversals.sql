ALTER TABLE cash_movements
    ADD COLUMN reversed_movement_id UUID;

ALTER TABLE cash_movements
    ADD CONSTRAINT fk_cash_movements_reversed_movement
        FOREIGN KEY (reversed_movement_id) REFERENCES cash_movements (id);

ALTER TABLE cash_movements
    ADD CONSTRAINT uk_cash_movements_reversed_movement UNIQUE (reversed_movement_id);

ALTER TABLE end_of_day_register_summaries
    ADD COLUMN payout_reversals NUMERIC(12, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE end_of_day_register_summaries
    ALTER COLUMN payout_reversals DROP DEFAULT;
