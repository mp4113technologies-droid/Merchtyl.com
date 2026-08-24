DELETE FROM product_capability_assignments assignment
USING products product
WHERE assignment.product_id = product.id
  AND assignment.capability = 'REQUIRE_AGE_VERIFICATION'
  AND product.minimum_age IS NULL;
