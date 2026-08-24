CREATE TABLE receipts (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL,
    receipt_number VARCHAR(80) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    reprint_count INTEGER NOT NULL DEFAULT 0,
    last_reprinted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_receipts_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT,
    CONSTRAINT uq_receipts_sale UNIQUE (sale_id),
    CONSTRAINT uq_receipts_number UNIQUE (receipt_number),
    CONSTRAINT ck_receipts_number_nonblank CHECK (btrim(receipt_number) <> ''),
    CONSTRAINT ck_receipts_reprint_count_nonnegative CHECK (reprint_count >= 0),
    CONSTRAINT ck_receipts_reprint_timestamp CHECK (
        (reprint_count = 0 AND last_reprinted_at IS NULL)
        OR (reprint_count > 0 AND last_reprinted_at IS NOT NULL)
    )
);

CREATE TABLE receipt_documents (
    id UUID PRIMARY KEY,
    receipt_id UUID NOT NULL,
    document_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_receipt_documents_receipt FOREIGN KEY (receipt_id) REFERENCES receipts(id) ON DELETE CASCADE,
    CONSTRAINT uq_receipt_documents_receipt UNIQUE (receipt_id)
);

CREATE INDEX idx_receipts_generated_at ON receipts(generated_at);
