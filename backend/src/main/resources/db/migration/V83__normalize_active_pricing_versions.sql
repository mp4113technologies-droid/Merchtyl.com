WITH ranked AS (
    SELECT id,
           pricing_plan_id,
           effective_from,
           row_number() OVER (PARTITION BY pricing_plan_id ORDER BY version_number DESC, created_at DESC) AS position
    FROM platform_pricing_plan_versions
    WHERE status = 'ACTIVE'
)
UPDATE platform_pricing_plan_versions version
SET status = 'SUPERSEDED',
    effective_to = COALESCE(version.effective_to, current_version.effective_from - interval '1 day')
FROM ranked historical
JOIN ranked current_version
  ON current_version.pricing_plan_id = historical.pricing_plan_id
 AND current_version.position = 1
WHERE version.id = historical.id
  AND historical.position > 1;

CREATE UNIQUE INDEX uq_platform_pricing_plan_one_active_version
    ON platform_pricing_plan_versions(pricing_plan_id)
    WHERE status = 'ACTIVE';
