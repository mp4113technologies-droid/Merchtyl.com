ALTER TABLE stock_adjustments DROP CONSTRAINT IF EXISTS chk_stock_adjustments_approval_status;
UPDATE stock_adjustments SET approval_status = 'POSTED' WHERE approval_status = 'APPROVED';
ALTER TABLE stock_adjustments ADD CONSTRAINT chk_stock_adjustments_approval_status CHECK (approval_status IN ('POSTED'));

ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS variant_id UUID;
ALTER TABLE sale_items ADD CONSTRAINT fk_sale_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id);
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS variant_sku VARCHAR(64);
ALTER TABLE sale_items ADD COLUMN IF NOT EXISTS variant_name VARCHAR(180);
CREATE INDEX IF NOT EXISTS idx_sale_items_variant ON sale_items (variant_id);

INSERT INTO security_permissions (id, code, description)
VALUES
 (md5('permission:INVENTORY_RECEIVE')::uuid, 'INVENTORY_RECEIVE', 'Receive stock into authorized stores.'),
 (md5('permission:INVENTORY_ADJUST')::uuid, 'INVENTORY_ADJUST', 'Adjust stock in authorized stores.'),
 (md5('permission:INVENTORY_TRANSFER')::uuid, 'INVENTORY_TRANSFER', 'Transfer stock between authorized stores.'),
 (md5('permission:INVENTORY_APPROVE')::uuid, 'INVENTORY_APPROVE', 'Approve inventory operations requiring review.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions(id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::uuid, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN ('INVENTORY_RECEIVE','INVENTORY_ADJUST')
WHERE role.name IN ('OWNER','TENANT_OWNER','MANAGER','STORE_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions(id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::uuid, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code='INVENTORY_APPROVE'
WHERE role.name IN ('OWNER','TENANT_OWNER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
