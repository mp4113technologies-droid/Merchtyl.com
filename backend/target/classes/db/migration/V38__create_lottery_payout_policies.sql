CREATE TABLE lottery_payout_policies (
    id UUID PRIMARY KEY,
    operator_id UUID NOT NULL,
    jurisdiction_id UUID NOT NULL,
    store_id UUID NOT NULL,
    maximum_cash_payout NUMERIC(19, 2) NOT NULL,
    cashier_approval_limit NUMERIC(19, 2) NOT NULL,
    manager_approval_threshold NUMERIC(19, 2) NOT NULL,
    operator_referral_threshold NUMERIC(19, 2) NOT NULL,
    protected_register_float NUMERIC(19, 2) NOT NULL,
    allow_cash_payout BOOLEAN NOT NULL,
    allow_store_credit BOOLEAN NOT NULL,
    require_ticket_validation BOOLEAN NOT NULL,
    require_age_verification BOOLEAN NOT NULL,
    require_customer_identification BOOLEAN NOT NULL,
    allow_alternate_register BOOLEAN NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lottery_payout_policies_operator FOREIGN KEY (operator_id) REFERENCES lottery_operators (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payout_policies_jurisdiction FOREIGN KEY (jurisdiction_id) REFERENCES tax_jurisdictions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payout_policies_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT ck_lottery_payout_policies_maximum_cash_payout CHECK (maximum_cash_payout >= 0),
    CONSTRAINT ck_lottery_payout_policies_cashier_approval_limit CHECK (cashier_approval_limit >= 0),
    CONSTRAINT ck_lottery_payout_policies_manager_approval_threshold CHECK (manager_approval_threshold >= 0),
    CONSTRAINT ck_lottery_payout_policies_operator_referral_threshold CHECK (operator_referral_threshold >= 0),
    CONSTRAINT ck_lottery_payout_policies_protected_register_float CHECK (protected_register_float >= 0),
    CONSTRAINT ck_lottery_payout_policies_effective_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_lottery_payout_policies_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'RETIRED'))
);

CREATE INDEX idx_lottery_payout_policies_operator_id ON lottery_payout_policies (operator_id);
CREATE INDEX idx_lottery_payout_policies_jurisdiction_id ON lottery_payout_policies (jurisdiction_id);
CREATE INDEX idx_lottery_payout_policies_store_id ON lottery_payout_policies (store_id);
CREATE INDEX idx_lottery_payout_policies_status ON lottery_payout_policies (status);
CREATE INDEX idx_lottery_payout_policies_effective_period ON lottery_payout_policies (effective_from, effective_to);
