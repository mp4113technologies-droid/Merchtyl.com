ALTER TABLE products ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX idx_products_tenant_catalog ON products(tenant_id, deleted_at, active);

INSERT INTO security_permissions(id, code, description)
VALUES (md5('permission:PRODUCT_DELETE')::uuid, 'PRODUCT_DELETE', 'Permanently remove products from the current catalogue.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions(id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::uuid, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code = 'PRODUCT_DELETE'
WHERE role.name IN ('OWNER', 'TENANT_OWNER', 'MANAGER', 'STORE_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
