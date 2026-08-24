CREATE TABLE lottery_payouts (
    id UUID PRIMARY KEY,
    operator_id UUID NOT NULL,
    policy_id UUID NOT NULL,
    store_id UUID NOT NULL,
    register_id UUID NOT NULL,
    device_id UUID NOT NULL,
    cashier_id UUID NOT NULL,
    register_session_id UUID,
    ticket_number VARCHAR(180) NOT NULL,
    validation_reference VARCHAR(180),
    amount NUMERIC(12, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    payout_method VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    ticket_validation_state VARCHAR(32) NOT NULL,
    age_verification_state VARCHAR(32) NOT NULL,
    identification_verification_state VARCHAR(32) NOT NULL,
    cashier_approval_limit NUMERIC(12, 2) NOT NULL,
    manager_approval_threshold NUMERIC(12, 2) NOT NULL,
    operator_referral_threshold NUMERIC(12, 2) NOT NULL,
    maximum_cash_payout NUMERIC(12, 2) NOT NULL,
    ticket_validation_required BOOLEAN NOT NULL,
    age_verification_required BOOLEAN NOT NULL,
    identification_required BOOLEAN NOT NULL,
    alternate_register_allowed BOOLEAN NOT NULL,
    business_date DATE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    validated_at TIMESTAMPTZ,
    validated_by UUID,
    authorized_at TIMESTAMPTZ,
    authorized_by UUID,
    rejected_at TIMESTAMPTZ,
    rejected_by UUID,
    rejection_reason VARCHAR(1000),
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lottery_payouts_operator FOREIGN KEY (operator_id) REFERENCES lottery_operators (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payouts_policy FOREIGN KEY (policy_id) REFERENCES lottery_payout_policies (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payouts_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payouts_register FOREIGN KEY (register_id) REFERENCES registers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payouts_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payouts_cashier FOREIGN KEY (cashier_id) REFERENCES security_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payouts_register_session FOREIGN KEY (register_session_id) REFERENCES register_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payouts_validated_by FOREIGN KEY (validated_by) REFERENCES security_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payouts_authorized_by FOREIGN KEY (authorized_by) REFERENCES security_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lottery_payouts_rejected_by FOREIGN KEY (rejected_by) REFERENCES security_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_lottery_payouts_ticket_number_nonblank CHECK (btrim(ticket_number) <> ''),
    CONSTRAINT ck_lottery_payouts_validation_reference_nonblank CHECK (validation_reference IS NULL OR btrim(validation_reference) <> ''),
    CONSTRAINT ck_lottery_payouts_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_lottery_payouts_currency_code CHECK (currency_code = upper(currency_code) AND length(currency_code) = 3),
    CONSTRAINT ck_lottery_payouts_payout_method CHECK (payout_method IN (
        'CASH',
        'STORE_CREDIT',
        'OPERATOR_VOUCHER',
        'CHEQUE_REFERRAL',
        'OPERATOR_CLAIM_REFERRAL',
        'OTHER'
    )),
    CONSTRAINT ck_lottery_payouts_status CHECK (status IN (
        'DRAFT',
        'VALIDATED',
        'AUTHORIZED',
        'PAID',
        'REFERRED_TO_OPERATOR',
        'REJECTED',
        'REVERSED'
    )),
    CONSTRAINT ck_lottery_payouts_verification_state CHECK (
        ticket_validation_state IN ('NOT_REQUIRED', 'PENDING', 'VERIFIED', 'FAILED')
        AND age_verification_state IN ('NOT_REQUIRED', 'PENDING', 'VERIFIED', 'FAILED')
        AND identification_verification_state IN ('NOT_REQUIRED', 'PENDING', 'VERIFIED', 'FAILED')
    ),
    CONSTRAINT ck_lottery_payouts_thresholds_nonnegative CHECK (
        cashier_approval_limit >= 0
        AND manager_approval_threshold >= 0
        AND operator_referral_threshold >= 0
        AND maximum_cash_payout >= 0
    ),
    CONSTRAINT ck_lottery_payouts_cash_session CHECK (payout_method <> 'CASH' OR register_session_id IS NOT NULL),
    CONSTRAINT ck_lottery_payouts_rejection_reason_nonblank CHECK (rejection_reason IS NULL OR btrim(rejection_reason) <> ''),
    CONSTRAINT ck_lottery_payouts_notes_nonblank CHECK (notes IS NULL OR btrim(notes) <> '')
);

CREATE INDEX idx_lottery_payouts_operator_id ON lottery_payouts (operator_id);
CREATE INDEX idx_lottery_payouts_policy_id ON lottery_payouts (policy_id);
CREATE INDEX idx_lottery_payouts_store_id ON lottery_payouts (store_id);
CREATE INDEX idx_lottery_payouts_register_id ON lottery_payouts (register_id);
CREATE INDEX idx_lottery_payouts_register_session_id ON lottery_payouts (register_session_id);
CREATE INDEX idx_lottery_payouts_status ON lottery_payouts (status);
CREATE INDEX idx_lottery_payouts_business_date ON lottery_payouts (business_date);
CREATE INDEX idx_lottery_payouts_created_at ON lottery_payouts (created_at DESC);

CREATE TABLE lottery_payout_approvals (
    id UUID PRIMARY KEY,
    payout_id UUID NOT NULL,
    approval_type VARCHAR(32) NOT NULL,
    approved_by UUID NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    payout_amount NUMERIC(12, 2) NOT NULL,
    threshold_amount NUMERIC(12, 2) NOT NULL,
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lottery_payout_approvals_payout FOREIGN KEY (payout_id) REFERENCES lottery_payouts (id) ON DELETE CASCADE,
    CONSTRAINT fk_lottery_payout_approvals_approved_by FOREIGN KEY (approved_by) REFERENCES security_users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_lottery_payout_approvals_type CHECK (approval_type IN ('CASHIER_LIMIT', 'MANAGER_APPROVAL', 'OPERATOR_REFERRAL')),
    CONSTRAINT ck_lottery_payout_approvals_amounts_nonnegative CHECK (payout_amount > 0 AND threshold_amount >= 0),
    CONSTRAINT ck_lottery_payout_approvals_notes_nonblank CHECK (notes IS NULL OR btrim(notes) <> '')
);

CREATE INDEX idx_lottery_payout_approvals_payout_id ON lottery_payout_approvals (payout_id);
CREATE INDEX idx_lottery_payout_approvals_approved_by ON lottery_payout_approvals (approved_by);

INSERT INTO security_permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000243', 'LOTTERY_PAYOUT_RECORD', 'Record and validate lottery payouts.'),
    ('00000000-0000-0000-0000-000000000244', 'LOTTERY_PAYOUT_APPROVE', 'Approve lottery payouts above cashier limits.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT permission_grant.permission_grant_id, security_role.id, security_permission.id
FROM (
    VALUES
        ('00000000-0000-0000-0000-000000000343'::UUID, 'OWNER', 'LOTTERY_PAYOUT_RECORD'),
        ('00000000-0000-0000-0000-000000000344'::UUID, 'OWNER', 'LOTTERY_PAYOUT_APPROVE'),
        ('00000000-0000-0000-0000-000000000443'::UUID, 'MANAGER', 'LOTTERY_PAYOUT_RECORD'),
        ('00000000-0000-0000-0000-000000000444'::UUID, 'MANAGER', 'LOTTERY_PAYOUT_APPROVE'),
        ('00000000-0000-0000-0000-000000000543'::UUID, 'CASHIER', 'LOTTERY_PAYOUT_RECORD')
) AS permission_grant(permission_grant_id, role_name, permission_code)
JOIN security_roles security_role ON security_role.name = permission_grant.role_name
JOIN security_permissions security_permission ON security_permission.code = permission_grant.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
