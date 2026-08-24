CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    contact_name VARCHAR(180),
    phone VARCHAR(40),
    email VARCHAR(320),
    address VARCHAR(1000),
    notes VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_suppliers_code UNIQUE (code),
    CONSTRAINT ck_suppliers_code_nonblank CHECK (length(trim(code)) > 0),
    CONSTRAINT ck_suppliers_name_nonblank CHECK (length(trim(name)) > 0)
);

CREATE TABLE product_suppliers (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    supplier_sku VARCHAR(128),
    preferred BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_suppliers_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE CASCADE,
    CONSTRAINT uq_product_suppliers_product_supplier UNIQUE (product_id, supplier_id)
);

CREATE INDEX idx_suppliers_active ON suppliers (active);
CREATE INDEX idx_suppliers_name ON suppliers (name);
CREATE INDEX idx_product_suppliers_product_id ON product_suppliers (product_id);
CREATE INDEX idx_product_suppliers_supplier_id ON product_suppliers (supplier_id);
CREATE INDEX idx_product_suppliers_active ON product_suppliers (active);
