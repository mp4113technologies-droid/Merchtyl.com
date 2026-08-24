CREATE TABLE sale_adjustments (
 id UUID PRIMARY KEY, sale_id UUID NOT NULL REFERENCES sales(id) ON DELETE RESTRICT,
 sale_item_id UUID REFERENCES sale_items(id) ON DELETE RESTRICT, type VARCHAR(40) NOT NULL,
 original_amount NUMERIC(12,4) NOT NULL, adjusted_amount NUMERIC(12,4) NOT NULL,
 difference NUMERIC(12,4) NOT NULL, percentage NUMERIC(7,4), reason_code VARCHAR(64) NOT NULL,
 reason_text VARCHAR(500), requested_by UUID NOT NULL REFERENCES security_users(id) ON DELETE RESTRICT,
 approved_by UUID REFERENCES security_users(id) ON DELETE RESTRICT, requested_at TIMESTAMPTZ NOT NULL,
 approved_at TIMESTAMPTZ, status VARCHAR(20) NOT NULL, correlation_id VARCHAR(100),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_sale_adjustments_sale ON sale_adjustments(sale_id, created_at);
CREATE INDEX idx_sale_adjustments_item ON sale_adjustments(sale_item_id);

INSERT INTO security_permissions (id, code, description)
SELECT gen_random_uuid(), code, description FROM (VALUES
 ('POS_ACCESS','Access point of sale.'), ('PORTAL_ACCESS','Return from POS to portal.'),
 ('POS_ITEM_REMOVE','Remove draft sale items.'), ('POS_QUANTITY_CHANGE','Change draft quantities.'),
 ('POS_PRICE_CHECK','Check authoritative prices.'), ('POS_PRICE_OVERRIDE','Request price overrides.'),
 ('POS_PRICE_OVERRIDE_APPROVE','Approve sensitive price overrides.'), ('POS_LINE_DISCOUNT','Apply line discounts.'),
 ('POS_SALE_DISCOUNT','Apply sale discounts.'), ('POS_DISCOUNT_APPROVE','Approve sensitive discounts.'),
 ('POS_CANCEL_DRAFT','Cancel draft sales.'), ('POS_HOLD_SALE','Hold draft sales.'),
 ('POS_RESUME_SALE','Resume held sales.'), ('POS_PAYMENT_EDIT','Edit draft payments.'),
 ('POS_SPLIT_PAYMENT','Record split payments.'), ('POS_TAX_OVERRIDE','Request tax overrides.'),
 ('POS_TAX_OVERRIDE_APPROVE','Approve tax overrides.'), ('POS_COMPLETED_SALE_VOID','Void completed sales.')
) AS p(code, description) WHERE NOT EXISTS (SELECT 1 FROM security_permissions x WHERE x.code=p.code);

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id FROM security_roles r CROSS JOIN security_permissions p
WHERE r.name IN ('OWNER','TENANT_OWNER') AND (p.code LIKE 'POS_%' OR p.code='PORTAL_ACCESS')
ON CONFLICT (role_id, permission_id) DO NOTHING;
INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id FROM security_roles r CROSS JOIN security_permissions p
WHERE r.name IN ('MANAGER','STORE_MANAGER') AND p.code IN ('PORTAL_ACCESS','POS_ACCESS','POS_ITEM_REMOVE','POS_QUANTITY_CHANGE','POS_PRICE_CHECK','POS_PRICE_OVERRIDE','POS_PRICE_OVERRIDE_APPROVE','POS_LINE_DISCOUNT','POS_SALE_DISCOUNT','POS_DISCOUNT_APPROVE','POS_CANCEL_DRAFT','POS_HOLD_SALE','POS_RESUME_SALE','POS_PAYMENT_EDIT','POS_SPLIT_PAYMENT')
ON CONFLICT (role_id, permission_id) DO NOTHING;
INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id FROM security_roles r CROSS JOIN security_permissions p
WHERE r.name='CASHIER' AND p.code IN ('POS_ACCESS','POS_ITEM_REMOVE','POS_QUANTITY_CHANGE','POS_PRICE_CHECK','POS_CANCEL_DRAFT','POS_HOLD_SALE','POS_RESUME_SALE','POS_PAYMENT_EDIT','POS_SPLIT_PAYMENT')
ON CONFLICT (role_id, permission_id) DO NOTHING;
