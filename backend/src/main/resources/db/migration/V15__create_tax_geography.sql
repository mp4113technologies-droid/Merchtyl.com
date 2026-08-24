CREATE TABLE countries (
    id UUID PRIMARY KEY,
    code VARCHAR(2) NOT NULL,
    name VARCHAR(180) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_countries_code UNIQUE (code),
    CONSTRAINT chk_countries_code_format CHECK (code ~ '^[A-Z]{2}$'),
    CONSTRAINT chk_countries_name_not_blank CHECK (btrim(name) <> '')
);

CREATE INDEX idx_countries_name ON countries (name);
CREATE INDEX idx_countries_active ON countries (active);

CREATE TABLE administrative_areas (
    id UUID PRIMARY KEY,
    country_id UUID NOT NULL,
    code VARCHAR(16) NOT NULL,
    name VARCHAR(180) NOT NULL,
    type VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_administrative_areas_country_code UNIQUE (country_id, code),
    CONSTRAINT chk_administrative_areas_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT chk_administrative_areas_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT fk_administrative_areas_country FOREIGN KEY (country_id) REFERENCES countries(id)
);

CREATE INDEX idx_administrative_areas_country ON administrative_areas (country_id);
CREATE INDEX idx_administrative_areas_type ON administrative_areas (type);
CREATE INDEX idx_administrative_areas_name ON administrative_areas (name);
CREATE INDEX idx_administrative_areas_active ON administrative_areas (active);

CREATE TABLE tax_jurisdictions (
    id UUID PRIMARY KEY,
    country_id UUID NOT NULL,
    administrative_area_id UUID,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    type VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_tax_jurisdictions_country_code UNIQUE (country_id, code),
    CONSTRAINT chk_tax_jurisdictions_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT chk_tax_jurisdictions_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT fk_tax_jurisdictions_country FOREIGN KEY (country_id) REFERENCES countries(id),
    CONSTRAINT fk_tax_jurisdictions_area FOREIGN KEY (administrative_area_id) REFERENCES administrative_areas(id)
);

CREATE INDEX idx_tax_jurisdictions_country ON tax_jurisdictions (country_id);
CREATE INDEX idx_tax_jurisdictions_area ON tax_jurisdictions (administrative_area_id);
CREATE INDEX idx_tax_jurisdictions_type ON tax_jurisdictions (type);
CREATE INDEX idx_tax_jurisdictions_name ON tax_jurisdictions (name);
CREATE INDEX idx_tax_jurisdictions_active ON tax_jurisdictions (active);
