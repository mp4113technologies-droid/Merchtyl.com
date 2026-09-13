ALTER TABLE sales DROP CONSTRAINT ck_sales_status;

ALTER TABLE sales
    ADD CONSTRAINT ck_sales_status CHECK (status IN (
        'DRAFT',
        'PENDING_PAYMENT',
        'HELD',
        'COMPLETED',
        'VOIDED',
        'PARTIALLY_REFUNDED',
        'REFUNDED',
        'CANCELLED'
    ));
