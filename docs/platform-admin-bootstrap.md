# First production deployment and Platform Super Admin bootstrap

Flyway owns the production schema. On an empty PostgreSQL/Neon database, application startup applies every versioned migration before Hibernate validates the resulting schema. Production uses `spring.jpa.hibernate.ddl-auto=validate`; Flyway clean and automatic checksum repair are not enabled.

The migrations create all application tables and canonical reference data, including CA/US geography, Canadian provinces and territories, all 50 US states, CAD/USD currencies, Canadian jurisdiction/effective-date tax metadata, units of measure, feature definitions, security roles, permissions, and role-permission grants. They do not create demo merchants, stores, products, inventory, or transactions.

Bootstrap is disabled by default. For the first Railway production deployment, store these values as Railway secrets/environment variables:

To create the first platform administrator, set these values through a secure runtime secret manager or local shell environment:

```env
MERCHTYL_BOOTSTRAP_SUPERADMIN_ENABLED=true
MERCHTYL_BOOTSTRAP_SUPERADMIN_EMAIL=<admin email>
MERCHTYL_BOOTSTRAP_SUPERADMIN_NAME=Merchtyl Administrator
MERCHTYL_BOOTSTRAP_SUPERADMIN_PASSWORD=<strong temporary password>
```

The bootstrap runner creates an active `PLATFORM_SUPER_ADMIN` only when no Platform Super Admin exists. Creation is transactional, guarded by a PostgreSQL advisory transaction lock for concurrent instance startup, backed by the platform email unique constraint, and uses the normal password policy and password encoder. The password is never logged or stored in plaintext, and the account is marked for password change. An existing merchant or non-Super-Admin platform account with the configured email is never elevated.

First deployment sequence:

1. Create an empty Neon/PostgreSQL database.
2. Configure the datasource, JWT, public URLs, CORS, and email settings through Railway secrets.
3. Configure the four bootstrap variables above and deploy.
4. Wait for Flyway migration and application readiness to succeed.
5. Sign in through the Platform login and change the temporary password.
6. Disable bootstrap and remove its password secret.
7. Redeploy. Flyway will validate existing migration history and bootstrap will not recreate or reset the administrator.

After the account is created:

1. Stop the application.
2. Set `MERCHTYL_BOOTSTRAP_SUPERADMIN_ENABLED=false`.
3. Remove the bootstrap password from the runtime environment or secret manager.
4. Restart the application and sign in at `/platform/login`.

Do not commit bootstrap email, password, datasource credentials, or provider API keys to source control. Older `MERCHTYL_BOOTSTRAP_ADMIN_*` names remain temporary compatibility aliases; new deployments should use `MERCHTYL_BOOTSTRAP_SUPERADMIN_*`.

Owner invitation and support-access defaults:

```env
MERCHTYL_OWNER_INVITATION_EXPIRY_HOURS=48
MERCHTYL_SUPPORT_ACCESS_ENABLED=false
MERCHTYL_SUPPORT_ACCESS_DEFAULT_MINUTES=30
```

Support access is intentionally disabled by default. The database foundation exists for time-limited, reason-required support sessions, but no unrestricted merchant impersonation endpoint is exposed.
