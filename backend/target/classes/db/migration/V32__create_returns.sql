CREATE TABLE returns (
    id UUID PRIMARY KEY,
    original_sale_id UUID NOT NULL,
    store_id UUID NOT NULL,
    register_id UUID NOT NULL,
    register_session_id UUID NOT NULL,
    created_by UUID NOT NULL,
    business_date DATE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    total_quantity NUMERIC(12, 4) NOT NULL,
    subtotal_amount NUMERIC(12, 2) NOT NULL,
    tax_amount NUMERIC(12, 2) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_returns_original_sale FOREIGN KEY (original_sale_id) REFERENCES sales(id) ON DELETE RESTRICT,
    CONSTRAINT fk_returns_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE RESTRICT,
    CONSTRAINT fk_returns_register FOREIGN KEY (register_id) REFERENCES registers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_returns_register_session FOREIGN KEY (register_session_id) REFERENCES register_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_returns_created_by FOREIGN KEY (created_by) REFERENCES security_users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_returns_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_returns_reason_nonblank CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_returns_quantity_positive CHECK (total_quantity > 0),
    CONSTRAINT ck_returns_amounts_nonnegative CHECK (
        subtotal_amount >= 0
        AND tax_amount >= 0
        AND total_amount >= 0
    )
);

CREATE TABLE return_items (
    id UUID PRIMARY KEY,
    return_id UUID NOT NULL,
    original_sale_item_id UUID NOT NULL,
    product_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    product_sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(180) NOT NULL,
    quantity NUMERIC(12, 4) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    original_quantity NUMERIC(12, 4) NOT NULL,
    original_unit_price NUMERIC(12, 4) NOT NULL,
    original_discount_amount NUMERIC(12, 2) NOT NULL,
    original_line_subtotal NUMERIC(12, 2) NOT NULL,
    original_tax_amount NUMERIC(12, 2) NOT NULL,
    original_line_total NUMERIC(12, 2) NOT NULL,
    original_product_cost NUMERIC(19, 4),
    original_product_price NUMERIC(19, 4),
    original_product_capabilities VARCHAR(1000),
    original_product_tax_category_id UUID,
    return_subtotal_amount NUMERIC(12, 2) NOT NULL,
    return_tax_amount NUMERIC(12, 2) NOT NULL,
    return_total_amount NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_return_items_return FOREIGN KEY (return_id) REFERENCES returns(id) ON DELETE CASCADE,
    CONSTRAINT fk_return_items_sale_item FOREIGN KEY (original_sale_item_id) REFERENCES sale_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_return_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT uq_return_items_line_number UNIQUE (return_id, line_number),
    CONSTRAINT ck_return_items_product_sku_nonblank CHECK (btrim(product_sku) <> ''),
    CONSTRAINT ck_return_items_product_name_nonblank CHECK (btrim(product_name) <> ''),
    CONSTRAINT ck_return_items_reason_nonblank CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_return_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_return_items_original_quantity_positive CHECK (original_quantity > 0),
    CONSTRAINT ck_return_items_amounts_nonnegative CHECK (
        original_unit_price >= 0
        AND original_discount_amount >= 0
        AND original_line_subtotal >= 0
        AND original_tax_amount >= 0
        AND original_line_total >= 0
        AND return_subtotal_amount >= 0
        AND return_tax_amount >= 0
        AND return_total_amount >= 0
    )
);

CREATE INDEX idx_returns_original_sale ON returns(original_sale_id);
CREATE INDEX idx_returns_store ON returns(store_id);
CREATE INDEX idx_returns_register ON returns(register_id);
CREATE INDEX idx_returns_register_session ON returns(register_session_id);
CREATE INDEX idx_returns_created_by ON returns(created_by);
CREATE INDEX idx_returns_business_date ON returns(business_date);
CREATE INDEX idx_return_items_return ON return_items(return_id);
CREATE INDEX idx_return_items_original_sale_item ON return_items(original_sale_item_id);
