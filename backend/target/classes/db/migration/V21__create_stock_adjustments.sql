CREATE TABLE stock_adjustments (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    reason VARCHAR(255) NOT NULL,
    notes VARCHAR(2000),
    approval_status VARCHAR(40) NOT NULL,
    approved_by_user_id UUID,
    approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approval_notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_stock_adjustments_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_stock_adjustments_approved_by_user FOREIGN KEY (approved_by_user_id) REFERENCES security_users(id),
    CONSTRAINT chk_stock_adjustments_reason_nonblank CHECK (btrim(reason) <> ''),
    CONSTRAINT chk_stock_adjustments_notes_nonblank CHECK (notes IS NULL OR btrim(notes) <> ''),
    CONSTRAINT chk_stock_adjustments_approval_notes_nonblank CHECK (approval_notes IS NULL OR btrim(approval_notes) <> ''),
    CONSTRAINT chk_stock_adjustments_approval_status CHECK (approval_status IN ('APPROVED'))
);

CREATE INDEX idx_stock_adjustments_store ON stock_adjustments (store_id);
CREATE INDEX idx_stock_adjustments_approval_status ON stock_adjustments (approval_status);
CREATE INDEX idx_stock_adjustments_approved_by_user ON stock_adjustments (approved_by_user_id);
CREATE INDEX idx_stock_adjustments_created_at ON stock_adjustments (created_at DESC);

CREATE TABLE stock_adjustment_lines (
    id UUID PRIMARY KEY,
    adjustment_id UUID NOT NULL,
    product_id UUID NOT NULL,
    adjustment_type VARCHAR(40) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    quantity_delta NUMERIC(19, 4) NOT NULL,
    resulting_quantity NUMERIC(19, 4) NOT NULL,
    inventory_transaction_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_stock_adjustment_lines_adjustment FOREIGN KEY (adjustment_id) REFERENCES stock_adjustments(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_adjustment_lines_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_stock_adjustment_lines_inventory_transaction FOREIGN KEY (inventory_transaction_id) REFERENCES inventory_transactions(id),
    CONSTRAINT chk_stock_adjustment_lines_type CHECK (adjustment_type IN ('INCREASE', 'DECREASE', 'DAMAGED', 'EXPIRED')),
    CONSTRAINT chk_stock_adjustment_lines_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_stock_adjustment_lines_quantity_delta_direction CHECK (
        (adjustment_type = 'INCREASE' AND quantity_delta > 0)
        OR (adjustment_type IN ('DECREASE', 'DAMAGED', 'EXPIRED') AND quantity_delta < 0)
    )
);

CREATE INDEX idx_stock_adjustment_lines_adjustment ON stock_adjustment_lines (adjustment_id);
CREATE INDEX idx_stock_adjustment_lines_product ON stock_adjustment_lines (product_id);
CREATE INDEX idx_stock_adjustment_lines_type ON stock_adjustment_lines (adjustment_type);
CREATE INDEX idx_stock_adjustment_lines_inventory_transaction ON stock_adjustment_lines (inventory_transaction_id);
