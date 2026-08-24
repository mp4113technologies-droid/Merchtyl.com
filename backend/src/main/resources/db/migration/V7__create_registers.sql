CREATE TABLE registers (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    location_description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_registers_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT uq_registers_store_code UNIQUE (store_id, code),
    CONSTRAINT ck_registers_code_nonblank CHECK (length(trim(code)) > 0),
    CONSTRAINT ck_registers_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_registers_location_description_nonblank CHECK (
        location_description IS NULL OR length(trim(location_description)) > 0
    )
);

CREATE INDEX idx_registers_store_id ON registers (store_id);
CREATE INDEX idx_registers_name ON registers (name);
CREATE INDEX idx_registers_active ON registers (active);
