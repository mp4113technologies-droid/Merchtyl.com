-- LOTTERY_SALES is a deployment switch, not a commercial entitlement. Lottery
-- access remains constrained by the effective subscription, store capability,
-- and operation permission. Keep explicit tenant/store/register overrides.
UPDATE feature_definitions
SET default_enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE code = 'LOTTERY_SALES'
  AND default_enabled = FALSE;
