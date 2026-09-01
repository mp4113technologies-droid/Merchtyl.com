ALTER TABLE registers ADD COLUMN register_type VARCHAR(30) NOT NULL DEFAULT 'RETAIL';
ALTER TABLE registers ADD CONSTRAINT ck_registers_type CHECK (register_type IN ('RETAIL', 'FOOD_SERVICE'));
CREATE INDEX idx_registers_store_type_active ON registers(store_id, register_type, active);
