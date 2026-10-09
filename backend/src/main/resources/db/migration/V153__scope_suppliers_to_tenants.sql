ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);

UPDATE suppliers s
SET tenant_id = ownership.tenant_id
FROM (
    SELECT ps.supplier_id, MIN(p.tenant_id::text)::uuid AS tenant_id
    FROM product_suppliers ps
    JOIN products p ON p.id = ps.product_id
    GROUP BY ps.supplier_id
    HAVING COUNT(DISTINCT p.tenant_id) = 1
) ownership
WHERE ownership.supplier_id = s.id AND s.tenant_id IS NULL;

DROP INDEX IF EXISTS uq_suppliers_code_lower;
ALTER TABLE suppliers DROP CONSTRAINT IF EXISTS uq_suppliers_code;
CREATE UNIQUE INDEX IF NOT EXISTS uq_suppliers_tenant_code_lower
    ON suppliers (tenant_id, lower(code)) WHERE tenant_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_suppliers_tenant_id ON suppliers (tenant_id);
