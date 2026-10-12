alter table stores
    add column cashback_enabled boolean not null default false,
    add column cashback_fee_type varchar(16) not null default 'FIXED',
    add column cashback_fee_value numeric(12, 4) not null default 0;

alter table stores
    add constraint ck_stores_cashback_fee_type
        check (cashback_fee_type in ('FIXED', 'PERCENTAGE')),
    add constraint ck_stores_cashback_fee_value
        check (cashback_fee_value >= 0),
    add constraint ck_stores_cashback_percentage
        check (cashback_fee_type <> 'PERCENTAGE' or cashback_fee_value <= 100);

alter table sales
    add column cashback_amount numeric(12, 2),
    add column cashback_fee_type varchar(16),
    add column cashback_fee_value numeric(12, 4),
    add column cashback_fee_amount numeric(12, 2);

alter table sales
    add constraint ck_sales_cashback_snapshot
        check (
            (cashback_amount is null and cashback_fee_type is null and cashback_fee_value is null and cashback_fee_amount is null)
            or
            (cashback_amount > 0 and cashback_fee_type in ('FIXED', 'PERCENTAGE')
                and cashback_fee_value >= 0 and cashback_fee_amount >= 0)
        );

alter table end_of_day_reports
    add column cashback_paid numeric(12, 2) not null default 0,
    add column cashback_fees numeric(12, 2) not null default 0,
    add column cashback_count bigint not null default 0;
