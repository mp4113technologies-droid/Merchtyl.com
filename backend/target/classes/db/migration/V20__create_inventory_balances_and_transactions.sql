CREATE TABLE inventory_balances (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity_on_hand NUMERIC(19, 4) NOT NULL,
    last_transaction_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_inventory_balances_store_product UNIQUE (store_id, product_id),
    CONSTRAINT fk_inventory_balances_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_inventory_balances_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE INDEX idx_inventory_balances_store ON inventory_balances (store_id);
CREATE INDEX idx_inventory_balances_product ON inventory_balances (product_id);
CREATE INDEX idx_inventory_balances_quantity ON inventory_balances (quantity_on_hand);

CREATE TABLE inventory_transactions (
    id UUID PRIMARY KEY,
    balance_id UUID NOT NULL,
    store_id UUID NOT NULL,
    product_id UUID NOT NULL,
    transaction_type VARCHAR(40) NOT NULL,
    quantity_delta NUMERIC(19, 4) NOT NULL,
    resulting_quantity NUMERIC(19, 4) NOT NULL,
    reference_type VARCHAR(80),
    reference_id UUID,
    reason VARCHAR(1000),
    actor_user_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_inventory_transactions_balance FOREIGN KEY (balance_id) REFERENCES inventory_balances(id),
    CONSTRAINT fk_inventory_transactions_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_inventory_transactions_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_inventory_transactions_actor_user FOREIGN KEY (actor_user_id) REFERENCES security_users(id),
    CONSTRAINT chk_inventory_transactions_type CHECK (transaction_type IN (
        'OPENING_STOCK',
        'PURCHASE',
        'SALE',
        'RETURN',
        'ADJUSTMENT_INCREASE',
        'ADJUSTMENT_DECREASE',
        'DAMAGED',
        'EXPIRED',
        'TRANSFER_IN',
        'TRANSFER_OUT',
        'VOID_REVERSAL'
    )),
    CONSTRAINT chk_inventory_transactions_quantity_delta_non_zero CHECK (quantity_delta <> 0),
    CONSTRAINT chk_inventory_transactions_direction CHECK (
        (transaction_type IN ('OPENING_STOCK', 'PURCHASE', 'RETURN', 'ADJUSTMENT_INCREASE', 'TRANSFER_IN') AND quantity_delta > 0)
        OR (transaction_type IN ('SALE', 'ADJUSTMENT_DECREASE', 'DAMAGED', 'EXPIRED', 'TRANSFER_OUT') AND quantity_delta < 0)
        OR transaction_type = 'VOID_REVERSAL'
    ),
    CONSTRAINT chk_inventory_transactions_reference_type_nonblank CHECK (reference_type IS NULL OR btrim(reference_type) <> ''),
    CONSTRAINT chk_inventory_transactions_reason_nonblank CHECK (reason IS NULL OR btrim(reason) <> '')
);

CREATE INDEX idx_inventory_transactions_balance ON inventory_transactions (balance_id);
CREATE INDEX idx_inventory_transactions_store ON inventory_transactions (store_id);
CREATE INDEX idx_inventory_transactions_product ON inventory_transactions (product_id);
CREATE INDEX idx_inventory_transactions_type ON inventory_transactions (transaction_type);
CREATE INDEX idx_inventory_transactions_reference_id ON inventory_transactions (reference_id);
CREATE INDEX idx_inventory_transactions_occurred_at ON inventory_transactions (occurred_at DESC);

CREATE OR REPLACE FUNCTION prevent_inventory_transaction_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'inventory_transactions are immutable';
END;
$$;

CREATE TRIGGER trg_inventory_transactions_immutable
BEFORE UPDATE OR DELETE ON inventory_transactions
FOR EACH ROW
EXECUTE FUNCTION prevent_inventory_transaction_mutation();
