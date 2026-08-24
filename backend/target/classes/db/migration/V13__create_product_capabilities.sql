CREATE TABLE product_capability_assignments (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    capability VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_product_capability_assignments_product_capability UNIQUE (product_id, capability),
    CONSTRAINT chk_product_capability_assignments_capability_not_blank CHECK (btrim(capability) <> ''),
    CONSTRAINT fk_product_capability_assignments_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE INDEX idx_product_capability_assignments_product ON product_capability_assignments (product_id);
CREATE INDEX idx_product_capability_assignments_capability ON product_capability_assignments (capability);
