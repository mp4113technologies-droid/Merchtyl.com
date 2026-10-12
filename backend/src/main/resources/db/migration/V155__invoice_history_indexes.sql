CREATE INDEX IF NOT EXISTS idx_receipts_number_upper_pattern
    ON receipts (UPPER(receipt_number) varchar_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_sales_invoice_history
    ON sales (store_id, completed_at DESC, id DESC)
    WHERE status IN ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED');

CREATE INDEX IF NOT EXISTS idx_receipts_history_generated_sale
    ON receipts (generated_at DESC, sale_id);
