CREATE TABLE stores (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    legal_name VARCHAR(255),
    country_code VARCHAR(2) NOT NULL,
    administrative_area_code VARCHAR(32),
    address VARCHAR(1000) NOT NULL,
    phone VARCHAR(40),
    email VARCHAR(320),
    currency_code VARCHAR(3) NOT NULL,
    locale VARCHAR(35) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    prices_include_tax BOOLEAN NOT NULL DEFAULT FALSE,
    negative_stock_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_stores_code UNIQUE (code),
    CONSTRAINT ck_stores_code_nonblank CHECK (length(trim(code)) > 0),
    CONSTRAINT ck_stores_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_stores_country_code CHECK (country_code = upper(country_code) AND length(country_code) = 2),
    CONSTRAINT ck_stores_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_stores_address_nonblank CHECK (length(trim(address)) > 0),
    CONSTRAINT ck_stores_locale_nonblank CHECK (length(trim(locale)) > 0),
    CONSTRAINT ck_stores_timezone_nonblank CHECK (length(trim(timezone)) > 0),
    CONSTRAINT ck_stores_email_nonblank CHECK (email IS NULL OR length(trim(email)) > 0),
    CONSTRAINT ck_stores_phone_nonblank CHECK (phone IS NULL OR length(trim(phone)) > 0)
);

CREATE INDEX idx_stores_name ON stores (name);
CREATE INDEX idx_stores_country_code ON stores (country_code);
CREATE INDEX idx_stores_administrative_area_code ON stores (administrative_area_code);
CREATE INDEX idx_stores_currency_code ON stores (currency_code);
CREATE INDEX idx_stores_active ON stores (active);
