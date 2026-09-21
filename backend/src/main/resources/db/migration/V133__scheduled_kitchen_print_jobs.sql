alter table stores
    add column auto_print_asap_kitchen_tickets boolean not null default false,
    add column auto_print_scheduled_kitchen_tickets boolean not null default false,
    add column scheduled_kitchen_print_lead_minutes integer not null default 10,
    add constraint ck_stores_kitchen_print_lead check (scheduled_kitchen_print_lead_minutes between 0 and 1440);

create table kitchen_print_jobs (
    id uuid primary key,
    tenant_id uuid not null,
    store_id uuid not null references stores(id),
    sale_id uuid not null references sales(id),
    type varchar(40) not null,
    scheduled_at timestamptz not null,
    status varchar(32) not null,
    attempt_count integer not null default 0,
    origin varchar(16) not null default 'AUTOMATIC',
    claimed_by varchar(255),
    last_attempt_at timestamptz,
    printed_at timestamptz,
    last_error varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    constraint uq_kitchen_print_jobs_sale_type unique (sale_id, type),
    constraint ck_kitchen_print_jobs_type check (type in ('KITCHEN_TICKET')),
    constraint ck_kitchen_print_jobs_status check (status in ('SCHEDULED','DISPATCHED','PRINTED','FAILED','CANCELLED')),
    constraint ck_kitchen_print_jobs_origin check (origin in ('AUTOMATIC','MANUAL'))
);

create index ix_kitchen_print_jobs_due on kitchen_print_jobs(store_id, status, scheduled_at);
create index ix_kitchen_print_jobs_tenant on kitchen_print_jobs(tenant_id, store_id);
