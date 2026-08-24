# User identity architecture

`security_users` is Merchtyl's canonical merchant-user aggregate. Despite its historical name, it owns both authentication state and the tenant employee identity used by user management, store assignments, sales, auditing, password reset, and reporting. Roles are stored in `security_user_roles`; tenant and creator metadata are stored directly on `security_users`.

`user_accounts` was introduced by migration V1 and replaced by the security persistence model in V2. No production controller, service, authentication provider, assignment, or frontend API reads or writes it. The legacy table is retained to avoid a destructive migration, but its unused JPA entity and repository were removed so new code cannot accidentally create a second user identity.

The canonical merchant employee flow is:

1. `POST /api/v1/users` creates one `security_users` row.
2. The same transaction creates its canonical role and required store/register assignments.
3. `GET /api/v1/users` queries that same row set by authenticated tenant and employee role.
4. The frontend consumes the paginated `content` response through `listUsers`.

## Data diagnostics

Run these queries directly in a controlled development or migration environment. They are intentionally not exposed through an HTTP endpoint.

```sql
-- Legacy rows that have no canonical identity with the same normalized email.
select legacy.id, legacy.email, legacy.role
from user_accounts legacy
left join security_users canonical on lower(canonical.email) = lower(legacy.email)
where canonical.id is null;

-- Canonical tenant employees, including users without store assignments.
select users.id, users.tenant_id, roles.name, users.enabled, users.locked,
       count(assignments.id) filter (where assignments.active) active_store_assignments
from security_users users
join security_user_roles user_roles on user_roles.user_id = users.id
join security_roles roles on roles.id = user_roles.role_id
left join security_user_store_assignments assignments on assignments.user_id = users.id
where roles.name in ('STORE_MANAGER', 'CASHIER')
group by users.id, users.tenant_id, roles.name, users.enabled, users.locked;
```

Legacy rows must not be backfilled automatically because V1 contains no tenant, store assignment, or reliable creator metadata.
