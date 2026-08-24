CREATE TABLE tax_groups (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_tax_groups_code UNIQUE (code),
    CONSTRAINT chk_tax_groups_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT chk_tax_groups_name_not_blank CHECK (btrim(name) <> '')
);

CREATE INDEX idx_tax_groups_name ON tax_groups (name);
CREATE INDEX idx_tax_groups_active ON tax_groups (active);

CREATE TABLE tax_group_components (
    id UUID PRIMARY KEY,
    tax_group_id UUID NOT NULL,
    tax_component_id UUID NOT NULL,
    calculation_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_tax_group_components_group_component UNIQUE (tax_group_id, tax_component_id),
    CONSTRAINT chk_tax_group_components_calculation_order CHECK (calculation_order >= 0),
    CONSTRAINT fk_tax_group_components_group FOREIGN KEY (tax_group_id) REFERENCES tax_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_tax_group_components_component FOREIGN KEY (tax_component_id) REFERENCES tax_components(id)
);

CREATE INDEX idx_tax_group_components_group ON tax_group_components (tax_group_id);
CREATE INDEX idx_tax_group_components_component ON tax_group_components (tax_component_id);
CREATE INDEX idx_tax_group_components_active ON tax_group_components (active);

CREATE TABLE tax_categories (
    id UUID PRIMARY KEY,
    tax_group_id UUID,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    treatment VARCHAR(32) NOT NULL,
    description VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_tax_categories_code UNIQUE (code),
    CONSTRAINT chk_tax_categories_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT chk_tax_categories_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT fk_tax_categories_group FOREIGN KEY (tax_group_id) REFERENCES tax_groups(id)
);

CREATE INDEX idx_tax_categories_group ON tax_categories (tax_group_id);
CREATE INDEX idx_tax_categories_treatment ON tax_categories (treatment);
CREATE INDEX idx_tax_categories_name ON tax_categories (name);
CREATE INDEX idx_tax_categories_active ON tax_categories (active);

CREATE TABLE product_tax_category_assignments (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    tax_category_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_product_tax_category_assignments_product UNIQUE (product_id),
    CONSTRAINT fk_product_tax_category_assignments_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_tax_category_assignments_category FOREIGN KEY (tax_category_id) REFERENCES tax_categories(id)
);

CREATE INDEX idx_product_tax_category_assignments_category ON product_tax_category_assignments (tax_category_id);
CREATE INDEX idx_product_tax_category_assignments_active ON product_tax_category_assignments (active);
