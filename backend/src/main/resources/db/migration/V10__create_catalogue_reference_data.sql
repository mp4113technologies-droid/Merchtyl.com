CREATE TABLE categories (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_categories_code UNIQUE (code),
    CONSTRAINT ck_categories_code_nonblank CHECK (length(trim(code)) > 0),
    CONSTRAINT ck_categories_name_nonblank CHECK (length(trim(name)) > 0)
);

CREATE TABLE brands (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_brands_code UNIQUE (code),
    CONSTRAINT ck_brands_code_nonblank CHECK (length(trim(code)) > 0),
    CONSTRAINT ck_brands_name_nonblank CHECK (length(trim(name)) > 0)
);

CREATE TABLE units_of_measure (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_units_of_measure_code UNIQUE (code),
    CONSTRAINT ck_units_of_measure_code_nonblank CHECK (length(trim(code)) > 0),
    CONSTRAINT ck_units_of_measure_name_nonblank CHECK (length(trim(name)) > 0)
);

CREATE INDEX idx_categories_active ON categories (active);
CREATE INDEX idx_categories_name ON categories (name);
CREATE INDEX idx_brands_active ON brands (active);
CREATE INDEX idx_brands_name ON brands (name);
CREATE INDEX idx_units_of_measure_active ON units_of_measure (active);
CREATE INDEX idx_units_of_measure_name ON units_of_measure (name);
