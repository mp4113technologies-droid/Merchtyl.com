CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    return_id UUID NOT NULL,
    original_sale_id UUID NOT NULL,
    store_id UUID NOT NULL,
    register_id UUID NOT NULL,
    register_session_id UUID NOT NULL,
    created_by UUID NOT NULL,
    business_date DATE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    subtotal_amount NUMERIC(12, 2) NOT NULL,
    tax_amount NUMERIC(12, 2) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    approval_notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_refunds_return UNIQUE (return_id),
    CONSTRAINT fk_refunds_return FOREIGN KEY (return_id) REFERENCES returns(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_original_sale FOREIGN KEY (original_sale_id) REFERENCES sales(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_register FOREIGN KEY (register_id) REFERENCES registers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_register_session FOREIGN KEY (register_session_id) REFERENCES register_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_created_by FOREIGN KEY (created_by) REFERENCES security_users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refunds_approved_by FOREIGN KEY (approved_by) REFERENCES security_users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_refunds_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_refunds_amounts_nonnegative CHECK (
        subtotal_amount >= 0
        AND tax_amount >= 0
        AND total_amount >= 0
    ),
    CONSTRAINT ck_refunds_total_positive CHECK (total_amount > 0),
    CONSTRAINT ck_refunds_reason_nonblank CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_refunds_approval_consistency CHECK (
        (approved_by IS NULL AND approved_at IS NULL)
        OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_refunds_approval_notes_nonblank CHECK (approval_notes IS NULL OR btrim(approval_notes) <> '')
);

CREATE TABLE refund_payments (
    id UUID PRIMARY KEY,
    refund_id UUID NOT NULL,
    original_payment_id UUID,
    line_number INTEGER NOT NULL,
    method VARCHAR(32) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    manual_reference VARCHAR(120),
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refund_payments_refund FOREIGN KEY (refund_id) REFERENCES refunds(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refund_payments_original_payment FOREIGN KEY (original_payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
    CONSTRAINT uq_refund_payments_line_number UNIQUE (refund_id, line_number),
    CONSTRAINT ck_refund_payments_method CHECK (method IN ('CASH', 'DEBIT', 'CREDIT', 'GIFT_CARD', 'STORE_CREDIT', 'OTHER')),
    CONSTRAINT ck_refund_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_refund_payments_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_refund_payments_manual_reference_nonblank CHECK (manual_reference IS NULL OR btrim(manual_reference) <> ''),
    CONSTRAINT ck_refund_payments_notes_nonblank CHECK (notes IS NULL OR btrim(notes) <> '')
);

CREATE TABLE refund_item_taxes (
    id UUID PRIMARY KEY,
    refund_id UUID NOT NULL,
    return_item_id UUID NOT NULL,
    original_sale_item_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    product_tax_category_id UUID,
    tax_component_code VARCHAR(40) NOT NULL,
    tax_component_name VARCHAR(120) NOT NULL,
    taxable_amount NUMERIC(12, 2) NOT NULL,
    tax_amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refund_item_taxes_refund FOREIGN KEY (refund_id) REFERENCES refunds(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refund_item_taxes_return_item FOREIGN KEY (return_item_id) REFERENCES return_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_refund_item_taxes_original_sale_item FOREIGN KEY (original_sale_item_id) REFERENCES sale_items(id) ON DELETE RESTRICT,
    CONSTRAINT uq_refund_item_taxes_line_number UNIQUE (refund_id, line_number),
    CONSTRAINT ck_refund_item_taxes_code_nonblank CHECK (btrim(tax_component_code) <> ''),
    CONSTRAINT ck_refund_item_taxes_name_nonblank CHECK (btrim(tax_component_name) <> ''),
    CONSTRAINT ck_refund_item_taxes_amounts_nonnegative CHECK (taxable_amount >= 0 AND tax_amount >= 0),
    CONSTRAINT ck_refund_item_taxes_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3)
);

CREATE INDEX idx_refunds_original_sale ON refunds(original_sale_id);
CREATE INDEX idx_refunds_store ON refunds(store_id);
CREATE INDEX idx_refunds_register_session ON refunds(register_session_id);
CREATE INDEX idx_refunds_occurred_at ON refunds(occurred_at DESC);
CREATE INDEX idx_refund_payments_refund ON refund_payments(refund_id);
CREATE INDEX idx_refund_payments_original_payment ON refund_payments(original_payment_id);
CREATE INDEX idx_refund_item_taxes_refund ON refund_item_taxes(refund_id);
CREATE INDEX idx_refund_item_taxes_return_item ON refund_item_taxes(return_item_id);

CREATE OR REPLACE FUNCTION prevent_refund_compensating_record_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'refund compensating records are immutable';
END;
$$;

CREATE TRIGGER trg_refund_payments_immutable
BEFORE UPDATE OR DELETE ON refund_payments
FOR EACH ROW
EXECUTE FUNCTION prevent_refund_compensating_record_mutation();

CREATE TRIGGER trg_refund_item_taxes_immutable
BEFORE UPDATE OR DELETE ON refund_item_taxes
FOR EACH ROW
EXECUTE FUNCTION prevent_refund_compensating_record_mutation();
