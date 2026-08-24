CREATE TABLE devices (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    register_id UUID NOT NULL,
    device_identifier VARCHAR(128) NOT NULL,
    display_name VARCHAR(180) NOT NULL,
    device_type VARCHAR(64) NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_devices_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT fk_devices_register FOREIGN KEY (register_id) REFERENCES registers (id) ON DELETE RESTRICT,
    CONSTRAINT uq_devices_device_identifier UNIQUE (device_identifier),
    CONSTRAINT ck_devices_device_identifier_nonblank CHECK (length(trim(device_identifier)) > 0),
    CONSTRAINT ck_devices_display_name_nonblank CHECK (length(trim(display_name)) > 0),
    CONSTRAINT ck_devices_device_type_nonblank CHECK (length(trim(device_type)) > 0)
);

CREATE INDEX idx_devices_store_id ON devices (store_id);
CREATE INDEX idx_devices_register_id ON devices (register_id);
CREATE INDEX idx_devices_device_type ON devices (device_type);
CREATE INDEX idx_devices_active ON devices (active);
CREATE INDEX idx_devices_last_seen_at ON devices (last_seen_at DESC);
