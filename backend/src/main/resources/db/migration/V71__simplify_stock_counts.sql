ALTER TABLE stock_counts DROP CONSTRAINT chk_stock_counts_review_fields;
ALTER TABLE stock_counts DROP CONSTRAINT chk_stock_counts_post_fields;
ALTER TABLE stock_counts DROP CONSTRAINT chk_stock_counts_status;
ALTER TABLE stock_counts ADD CONSTRAINT chk_stock_counts_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'POSTED', 'SAVED'));

INSERT INTO security_permissions (id, code, description)
VALUES
  (md5('permission:INVENTORY_COUNT_VIEW')::uuid, 'INVENTORY_COUNT_VIEW', 'View stock count history for authorized stores.'),
  (md5('permission:INVENTORY_COUNT_UPDATE')::uuid, 'INVENTORY_COUNT_UPDATE', 'Save physical stock counts for authorized stores.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::uuid, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN ('INVENTORY_COUNT_VIEW', 'INVENTORY_COUNT_UPDATE')
WHERE role.name IN ('OWNER', 'TENANT_OWNER', 'MANAGER', 'STORE_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
