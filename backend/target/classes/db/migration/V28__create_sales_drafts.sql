CREATE TABLE sales (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    register_id UUID NOT NULL,
    register_session_id UUID NOT NULL,
    created_by UUID NOT NULL,
    customer_id UUID,
    status VARCHAR(32) NOT NULL,
    business_date DATE NOT NULL,
    sale_channel VARCHAR(40),
    currency_code VARCHAR(3) NOT NULL,
    prices_include_tax BOOLEAN NOT NULL,
    subtotal_amount NUMERIC(12, 2) NOT NULL,
    discount_amount NUMERIC(12, 2) NOT NULL,
    estimated_tax_amount NUMERIC(12, 2) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    held_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_sales_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_register FOREIGN KEY (register_id) REFERENCES registers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_register_session FOREIGN KEY (register_session_id) REFERENCES register_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_created_by FOREIGN KEY (created_by) REFERENCES security_users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_sales_status CHECK (status IN ('DRAFT', 'HELD', 'COMPLETED', 'VOIDED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'CANCELLED')),
    CONSTRAINT ck_sales_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_sales_amounts_nonnegative CHECK (
        subtotal_amount >= 0
        AND discount_amount >= 0
        AND estimated_tax_amount >= 0
        AND total_amount >= 0
    ),
    CONSTRAINT ck_sales_held_timestamp CHECK ((status = 'HELD' AND held_at IS NOT NULL) OR (status <> 'HELD')),
    CONSTRAINT ck_sales_cancelled_timestamp CHECK ((status = 'CANCELLED' AND cancelled_at IS NOT NULL) OR (status <> 'CANCELLED')),
    CONSTRAINT ck_sales_channel_nonblank CHECK (sale_channel IS NULL OR btrim(sale_channel) <> '')
);

CREATE TABLE sale_items (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL,
    product_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    product_sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(180) NOT NULL,
    quantity NUMERIC(12, 4) NOT NULL,
    unit_price NUMERIC(12, 4) NOT NULL,
    discount_amount NUMERIC(12, 2) NOT NULL,
    price_override BOOLEAN NOT NULL,
    age_verified BOOLEAN NOT NULL,
    serial_number VARCHAR(255),
    external_reference VARCHAR(255),
    customer_id UUID,
    payment_method_code VARCHAR(64),
    line_subtotal NUMERIC(12, 2) NOT NULL,
    estimated_tax_amount NUMERIC(12, 2) NOT NULL,
    line_total NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT uq_sale_items_line_number UNIQUE (sale_id, line_number),
    CONSTRAINT ck_sale_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_sale_items_amounts_nonnegative CHECK (
        unit_price >= 0
        AND discount_amount >= 0
        AND line_subtotal >= 0
        AND estimated_tax_amount >= 0
        AND line_total >= 0
    ),
    CONSTRAINT ck_sale_items_product_sku_nonblank CHECK (btrim(product_sku) <> ''),
    CONSTRAINT ck_sale_items_product_name_nonblank CHECK (btrim(product_name) <> ''),
    CONSTRAINT ck_sale_items_serial_nonblank CHECK (serial_number IS NULL OR btrim(serial_number) <> ''),
    CONSTRAINT ck_sale_items_external_reference_nonblank CHECK (external_reference IS NULL OR btrim(external_reference) <> ''),
    CONSTRAINT ck_sale_items_payment_method_nonblank CHECK (payment_method_code IS NULL OR btrim(payment_method_code) <> '')
);

CREATE INDEX idx_sales_store ON sales(store_id);
CREATE INDEX idx_sales_register ON sales(register_id);
CREATE INDEX idx_sales_register_session ON sales(register_session_id);
CREATE INDEX idx_sales_status ON sales(status);
CREATE INDEX idx_sales_business_date ON sales(business_date);
CREATE INDEX idx_sale_items_sale ON sale_items(sale_id);
CREATE INDEX idx_sale_items_product ON sale_items(product_id);
