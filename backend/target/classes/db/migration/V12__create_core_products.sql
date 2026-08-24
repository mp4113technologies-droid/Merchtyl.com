CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    sellable_type VARCHAR(40) NOT NULL,
    unit_of_measure_id UUID,
    cost NUMERIC(19, 4) NOT NULL,
    price NUMERIC(19, 4) NOT NULL,
    category_id UUID,
    brand_id UUID,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    inventory_tracking_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    decimal_quantity_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    image_url VARCHAR(1000),
    tax_category_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT chk_products_sku_not_blank CHECK (btrim(sku) <> ''),
    CONSTRAINT chk_products_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT chk_products_cost_non_negative CHECK (cost >= 0),
    CONSTRAINT chk_products_price_non_negative CHECK (price >= 0),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands(id),
    CONSTRAINT fk_products_unit FOREIGN KEY (unit_of_measure_id) REFERENCES units_of_measure(id)
);

CREATE INDEX idx_products_name ON products (name);
CREATE INDEX idx_products_sellable_type ON products (sellable_type);
CREATE INDEX idx_products_active ON products (active);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_brand ON products (brand_id);

CREATE TABLE product_variants (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    cost NUMERIC(19, 4) NOT NULL,
    price NUMERIC(19, 4) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT chk_product_variants_sku_not_blank CHECK (btrim(sku) <> ''),
    CONSTRAINT chk_product_variants_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT chk_product_variants_cost_non_negative CHECK (cost >= 0),
    CONSTRAINT chk_product_variants_price_non_negative CHECK (price >= 0),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE INDEX idx_product_variants_product ON product_variants (product_id);
CREATE INDEX idx_product_variants_active ON product_variants (active);

CREATE TABLE product_barcodes (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    variant_id UUID,
    barcode VARCHAR(128) NOT NULL UNIQUE,
    primary_barcode BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT chk_product_barcodes_barcode_not_blank CHECK (btrim(barcode) <> ''),
    CONSTRAINT fk_product_barcodes_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_barcodes_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE
);

CREATE INDEX idx_product_barcodes_product ON product_barcodes (product_id);
CREATE INDEX idx_product_barcodes_variant ON product_barcodes (variant_id);
CREATE INDEX idx_product_barcodes_active ON product_barcodes (active);
