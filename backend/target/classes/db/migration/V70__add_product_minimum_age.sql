ALTER TABLE products ADD COLUMN minimum_age INTEGER;
ALTER TABLE products ADD CONSTRAINT chk_products_minimum_age CHECK (minimum_age IS NULL OR minimum_age BETWEEN 1 AND 99);
