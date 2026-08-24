INSERT INTO security_permissions (id, code, description)
VALUES
 (md5('permission:PRODUCT_CREATE')::uuid, 'PRODUCT_CREATE', 'Create products for authorized stores.'),
 (md5('permission:PRODUCT_UPDATE')::uuid, 'PRODUCT_UPDATE', 'Update authorized products.'),
 (md5('permission:PRODUCT_DEACTIVATE')::uuid, 'PRODUCT_DEACTIVATE', 'Deactivate products in authorized stores.'),
 (md5('permission:PRODUCT_PRICE_UPDATE')::uuid, 'PRODUCT_PRICE_UPDATE', 'Update authorized store product prices.'),
 (md5('permission:PRODUCT_BARCODE_MANAGE')::uuid, 'PRODUCT_BARCODE_MANAGE', 'Manage product barcodes.'),
 (md5('permission:PRODUCT_INVENTORY_VIEW')::uuid, 'PRODUCT_INVENTORY_VIEW', 'View product inventory.'),
 (md5('permission:PRODUCT_COST_VIEW')::uuid, 'PRODUCT_COST_VIEW', 'View product cost.'),
 (md5('permission:PRODUCT_COST_UPDATE')::uuid, 'PRODUCT_COST_UPDATE', 'Update product cost.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions(id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::uuid, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN
 ('PRODUCT_CREATE','PRODUCT_UPDATE','PRODUCT_DEACTIVATE','PRODUCT_PRICE_UPDATE','PRODUCT_BARCODE_MANAGE','PRODUCT_INVENTORY_VIEW')
WHERE role.name IN ('OWNER','TENANT_OWNER','MANAGER','STORE_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions(id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::uuid, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN ('PRODUCT_COST_VIEW','PRODUCT_COST_UPDATE')
WHERE role.name IN ('OWNER','TENANT_OWNER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions(id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::uuid, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code='PRODUCT_INVENTORY_VIEW'
WHERE role.name='CASHIER'
ON CONFLICT (role_id, permission_id) DO NOTHING;
