ALTER TABLE sales DROP CONSTRAINT ck_sales_amounts_nonnegative;

-- Lottery wins are negative sale lines. When wins exceed tickets and other goods sold,
-- the completed transaction is a cash payout and therefore has negative sale totals.
ALTER TABLE sales ADD CONSTRAINT ck_sales_amounts_nonnegative CHECK (
    discount_amount >= 0
    AND estimated_tax_amount >= 0
);
