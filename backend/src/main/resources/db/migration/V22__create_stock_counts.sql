ALTER TABLE inventory_transactions DROP CONSTRAINT chk_inventory_transactions_type;
ALTER TABLE inventory_transactions DROP CONSTRAINT chk_inventory_transactions_direction;

ALTER TABLE inventory_transactions ADD CONSTRAINT chk_inventory_transactions_type CHECK (transaction_type IN (
    'OPENING_STOCK',
    'PURCHASE',
    'SALE',
    'RETURN',
    'ADJUSTMENT_INCREASE',
    'ADJUSTMENT_DECREASE',
    'STOCK_COUNT_INCREASE',
    'STOCK_COUNT_DECREASE',
    'DAMAGED',
    'EXPIRED',
    'TRANSFER_IN',
    'TRANSFER_OUT',
    'VOID_REVERSAL'
));

ALTER TABLE inventory_transactions ADD CONSTRAINT chk_inventory_transactions_direction CHECK (
    (transaction_type IN ('OPENING_STOCK', 'PURCHASE', 'RETURN', 'ADJUSTMENT_INCREASE', 'STOCK_COUNT_INCREASE', 'TRANSFER_IN') AND quantity_delta > 0)
    OR (transaction_type IN ('SALE', 'ADJUSTMENT_DECREASE', 'STOCK_COUNT_DECREASE', 'DAMAGED', 'EXPIRED', 'TRANSFER_OUT') AND quantity_delta < 0)
    OR transaction_type = 'VOID_REVERSAL'
);

CREATE TABLE stock_counts (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    reference VARCHAR(255) NOT NULL,
    notes VARCHAR(2000),
    status VARCHAR(40) NOT NULL,
    created_by_user_id UUID,
    reviewed_by_user_id UUID,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_notes VARCHAR(1000),
    posted_by_user_id UUID,
    posted_at TIMESTAMP WITH TIME ZONE,
    post_notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_stock_counts_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_stock_counts_created_by_user FOREIGN KEY (created_by_user_id) REFERENCES security_users(id),
    CONSTRAINT fk_stock_counts_reviewed_by_user FOREIGN KEY (reviewed_by_user_id) REFERENCES security_users(id),
    CONSTRAINT fk_stock_counts_posted_by_user FOREIGN KEY (posted_by_user_id) REFERENCES security_users(id),
    CONSTRAINT chk_stock_counts_reference_nonblank CHECK (btrim(reference) <> ''),
    CONSTRAINT chk_stock_counts_notes_nonblank CHECK (notes IS NULL OR btrim(notes) <> ''),
    CONSTRAINT chk_stock_counts_review_notes_nonblank CHECK (review_notes IS NULL OR btrim(review_notes) <> ''),
    CONSTRAINT chk_stock_counts_post_notes_nonblank CHECK (post_notes IS NULL OR btrim(post_notes) <> ''),
    CONSTRAINT chk_stock_counts_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'POSTED')),
    CONSTRAINT chk_stock_counts_review_fields CHECK (
        (status = 'DRAFT' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL)
        OR (status IN ('IN_REVIEW', 'POSTED') AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT chk_stock_counts_post_fields CHECK (
        (status <> 'POSTED' AND posted_by_user_id IS NULL AND posted_at IS NULL)
        OR (status = 'POSTED' AND posted_at IS NOT NULL)
    )
);

CREATE INDEX idx_stock_counts_store ON stock_counts (store_id);
CREATE INDEX idx_stock_counts_status ON stock_counts (status);
CREATE INDEX idx_stock_counts_created_by_user ON stock_counts (created_by_user_id);
CREATE INDEX idx_stock_counts_reviewed_by_user ON stock_counts (reviewed_by_user_id);
CREATE INDEX idx_stock_counts_posted_by_user ON stock_counts (posted_by_user_id);
CREATE INDEX idx_stock_counts_created_at ON stock_counts (created_at DESC);

CREATE TABLE stock_count_lines (
    id UUID PRIMARY KEY,
    stock_count_id UUID NOT NULL,
    product_id UUID NOT NULL,
    expected_quantity NUMERIC(19, 4) NOT NULL,
    counted_quantity NUMERIC(19, 4),
    variance_quantity NUMERIC(19, 4),
    balance_version BIGINT,
    resulting_quantity NUMERIC(19, 4),
    inventory_transaction_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_stock_count_lines_stock_count FOREIGN KEY (stock_count_id) REFERENCES stock_counts(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_count_lines_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_stock_count_lines_inventory_transaction FOREIGN KEY (inventory_transaction_id) REFERENCES inventory_transactions(id),
    CONSTRAINT uq_stock_count_lines_count_product UNIQUE (stock_count_id, product_id),
    CONSTRAINT chk_stock_count_lines_expected_non_negative CHECK (expected_quantity >= 0),
    CONSTRAINT chk_stock_count_lines_counted_non_negative CHECK (counted_quantity IS NULL OR counted_quantity >= 0),
    CONSTRAINT chk_stock_count_lines_variance_present CHECK (
        (counted_quantity IS NULL AND variance_quantity IS NULL)
        OR (counted_quantity IS NOT NULL AND variance_quantity = counted_quantity - expected_quantity)
    ),
    CONSTRAINT chk_stock_count_lines_post_result CHECK (
        (inventory_transaction_id IS NULL)
        OR (resulting_quantity IS NOT NULL)
    )
);

CREATE INDEX idx_stock_count_lines_stock_count ON stock_count_lines (stock_count_id);
CREATE INDEX idx_stock_count_lines_product ON stock_count_lines (product_id);
CREATE INDEX idx_stock_count_lines_inventory_transaction ON stock_count_lines (inventory_transaction_id);
