CREATE TABLE pos_quick_keys (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    product_variant_id UUID NOT NULL,
    display_label VARCHAR(80),
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pos_quick_keys_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id) ON DELETE RESTRICT,
    CONSTRAINT uq_pos_quick_keys_tenant_variant UNIQUE (tenant_id, product_variant_id),
    CONSTRAINT ck_pos_quick_keys_order CHECK (display_order >= 0),
    CONSTRAINT ck_pos_quick_keys_label CHECK (display_label IS NULL OR btrim(display_label) <> '')
);

CREATE INDEX idx_pos_quick_keys_tenant_active_order ON pos_quick_keys (tenant_id, active, display_order);
