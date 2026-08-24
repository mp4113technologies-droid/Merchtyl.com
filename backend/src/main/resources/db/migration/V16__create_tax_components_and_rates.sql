CREATE TABLE tax_types (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_tax_types_code UNIQUE (code),
    CONSTRAINT chk_tax_types_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT chk_tax_types_name_not_blank CHECK (btrim(name) <> '')
);

CREATE INDEX idx_tax_types_name ON tax_types (name);
CREATE INDEX idx_tax_types_active ON tax_types (active);

CREATE TABLE tax_components (
    id UUID PRIMARY KEY,
    tax_type_id UUID NOT NULL,
    tax_jurisdiction_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_tax_components_code UNIQUE (code),
    CONSTRAINT chk_tax_components_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT chk_tax_components_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT fk_tax_components_tax_type FOREIGN KEY (tax_type_id) REFERENCES tax_types(id),
    CONSTRAINT fk_tax_components_jurisdiction FOREIGN KEY (tax_jurisdiction_id) REFERENCES tax_jurisdictions(id)
);

CREATE INDEX idx_tax_components_type ON tax_components (tax_type_id);
CREATE INDEX idx_tax_components_jurisdiction ON tax_components (tax_jurisdiction_id);
CREATE INDEX idx_tax_components_name ON tax_components (name);
CREATE INDEX idx_tax_components_active ON tax_components (active);

CREATE TABLE tax_rates (
    id UUID PRIMARY KEY,
    tax_component_id UUID NOT NULL,
    percentage_rate NUMERIC(9, 6) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    included_in_price BOOLEAN NOT NULL DEFAULT FALSE,
    compound_on_previous_tax BOOLEAN NOT NULL DEFAULT FALSE,
    calculation_order INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    source VARCHAR(180),
    source_reference VARCHAR(500),
    verified_by VARCHAR(180),
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT chk_tax_rates_percentage_non_negative CHECK (percentage_rate >= 0),
    CONSTRAINT chk_tax_rates_effective_range CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_tax_rates_calculation_order CHECK (calculation_order >= 0),
    CONSTRAINT fk_tax_rates_component FOREIGN KEY (tax_component_id) REFERENCES tax_components(id)
);

CREATE INDEX idx_tax_rates_component ON tax_rates (tax_component_id);
CREATE INDEX idx_tax_rates_status ON tax_rates (status);
CREATE INDEX idx_tax_rates_effective_range ON tax_rates (effective_from, effective_to);
