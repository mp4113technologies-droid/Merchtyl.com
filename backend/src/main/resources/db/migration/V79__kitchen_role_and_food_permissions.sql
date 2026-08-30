ALTER TABLE security_roles DROP CONSTRAINT IF EXISTS ck_security_roles_name;
ALTER TABLE security_roles ADD CONSTRAINT ck_security_roles_name CHECK (name IN (
    'OWNER', 'MANAGER', 'TENANT_OWNER', 'STORE_MANAGER', 'CASHIER', 'KITCHEN',
    'PLATFORM_SUPER_ADMIN', 'PLATFORM_SUPPORT_ADMIN'
));

ALTER TABLE security_user_store_assignments DROP CONSTRAINT IF EXISTS ck_security_user_store_assignments_role;
ALTER TABLE security_user_store_assignments ADD CONSTRAINT ck_security_user_store_assignments_role
    CHECK (assignment_role IN ('MANAGER', 'CASHIER', 'KITCHEN'));

INSERT INTO security_roles (id, name, description, system_role)
VALUES (md5('role:KITCHEN')::UUID, 'KITCHEN', 'Store-scoped kitchen and food-service operator.', TRUE)
ON CONFLICT (name) DO NOTHING;

INSERT INTO security_permissions (id, code, description) VALUES
    (md5('permission:FOOD_POS_ACCESS')::UUID, 'FOOD_POS_ACCESS', 'Access food-service point of sale.'),
    (md5('permission:FOOD_ORDER_CREATE')::UUID, 'FOOD_ORDER_CREATE', 'Create food-service orders.'),
    (md5('permission:FOOD_ORDER_VIEW')::UUID, 'FOOD_ORDER_VIEW', 'View food-service orders.'),
    (md5('permission:FOOD_ORDER_UPDATE')::UUID, 'FOOD_ORDER_UPDATE', 'Update food-service orders.'),
    (md5('permission:FOOD_ORDER_CANCEL')::UUID, 'FOOD_ORDER_CANCEL', 'Cancel food-service orders.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code IN (
    'STORE_VIEW', 'STORE_ACCESS', 'FOOD_POS_ACCESS', 'FOOD_ORDER_CREATE', 'FOOD_ORDER_VIEW',
    'FOOD_ORDER_UPDATE', 'FOOD_ORDER_CANCEL', 'SALE_CREATE', 'SALE_VIEW', 'PRODUCT_VIEW',
    'REGISTER_VIEW', 'REGISTER_SESSION_OPEN', 'REGISTER_SESSION_VIEW', 'REGISTER_SESSION_OPERATE'
)
WHERE role.name = 'KITCHEN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM security_roles role
JOIN security_permissions permission ON permission.code LIKE 'FOOD\_%' ESCAPE '\'
WHERE role.name IN ('OWNER', 'TENANT_OWNER', 'MANAGER', 'STORE_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
