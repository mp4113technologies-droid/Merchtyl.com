# Merchtyl

Merchtyl is a browser-based retail commerce and point of sale platform.

## Project layout

- `backend`: Spring Boot API using `/api/v1`, PostgreSQL, Flyway, JWT security, DTO-based APIs, and UUID primary keys.
- `frontend`: React/Vite frontend with Material UI, TanStack Query, React Router, React Hook Form, Zod, and PWA assets.
- `docs`: Project documentation.
- `docker-compose.yml`: Backend, frontend, and Nginx wiring. The backend connects to Neon PostgreSQL.

## Local development

The backend defaults to the `dev` Spring profile. No environment variables are required for a normal local run, but a local PostgreSQL database must be available with the development credentials from `backend/src/main/resources/application-dev.yml`:

| Setting | Development default |
| --- | --- |
| Database URL | `jdbc:postgresql://localhost:55432/merchtyl` |
| Database user | `merchtyl` |
| Database password | `merchtyl_dev_password` |
| Backend port | `8080` |
| Swagger UI | enabled at `/swagger-ui.html` |
| Test provisioning | enabled for `dev` with `X-Merchtyl-Test-Key: merchtyl-local-test-key` |

Create the local database/user if needed:

```bash
mkdir -p .local/postgres
/Library/PostgreSQL/18/bin/initdb -D .local/postgres/data -U merchtyl --pwfile=<(printf 'merchtyl_dev_password')
printf "port = 55432\nlisten_addresses = 'localhost'\n" >> .local/postgres/data/postgresql.conf
/Library/PostgreSQL/18/bin/pg_ctl -D .local/postgres/data -l .local/postgres/postgres.log start
/Library/PostgreSQL/18/bin/createdb 'postgresql://merchtyl:merchtyl_dev_password@localhost:55432/postgres' merchtyl
```

Run the backend with the dev profile:

```bash
cd backend
mvn spring-boot:run
```

The same settings can still be overridden from the shell when needed:

```bash
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/merchtyl' \
SPRING_DATASOURCE_USERNAME=merchtyl \
SPRING_DATASOURCE_PASSWORD='merchtyl_dev_password' \
SPRING_PROFILES_ACTIVE=dev \
mvn spring-boot:run
```

Flyway migrations run automatically at backend startup from `backend/src/main/resources/db/migration`.
Automated integration tests use Testcontainers PostgreSQL and must not use the Neon datasource.

Production remains environment-variable driven through the `prod` profile and `application-prod.yml`.

### Transactional email

Local development uses the Resend email provider by default. To use console output for local testing, override the provider:

```bash
MERCHTYL_EMAIL_PROVIDER=console \
mvn spring-boot:run
```

Production email is sent through Resend. Before enabling it:

1. Create a Resend account.
2. Add a sending domain or subdomain, for example `notifications.merchtyl.com`.
3. Add the SPF and DKIM DNS records Resend provides.
4. Wait for domain verification.
5. Create a sending-only API key.
6. Configure `RESEND_API_KEY`.
7. Configure `MERCHTYL_EMAIL_FROM_ADDRESS`, for example `onboarding@resend.dev` for Resend sandbox testing or a sender at your verified domain for production.
8. Set `MERCHTYL_EMAIL_PROVIDER=resend`.
9. Set `RESEND_ENABLED=true`.
10. Configure `MERCHTYL_FRONTEND_BASE_URL` to the public frontend URL.
11. Restart Merchtyl.
12. Use `POST /api/v1/platform/email/test` as a Platform Super Admin.
13. Create a test merchant and confirm the owner activation email is accepted by Resend.

The sender address must be authorized by the configured Resend account. Resend acceptance means the provider accepted the message; inbox delivery confirmation requires future webhook integration.

### Development security users

The `dev` profile seeds development-only security users into the configured development database after Flyway has applied the security schema:

| Email | Role | Password |
| --- | --- | --- |
| `owner@example.local` | `OWNER` | `OwnerDev!2026` |
| `manager@example.local` | `MANAGER` | `ManagerDev!2026` |
| `cashier@example.local` | `CASHIER` | `CashierDev!2026` |

These accounts are for local and development environments only. Do not use the seeded passwords in production.

Run the frontend:

```bash
cd frontend
npm install
npm run dev
```

The API base path is `/api/v1`.

## API documentation

Swagger UI and OpenAPI documents are available when enabled:

- `GET /swagger-ui.html`
- `GET /swagger-ui/index.html`
- `GET /v3/api-docs`
- `GET /v3/api-docs.yaml`

Use `POST /api/v1/auth/login` in Swagger UI to obtain an access token, then click **Authorize** and paste the JWT value only. The UI is configured with HTTP bearer authentication, so do not prefix the token with `Bearer`.

For production deployments, disable public API documentation unless intentionally exposing it:

```env
SWAGGER_UI_ENABLED=false
SWAGGER_API_DOCS_ENABLED=false
```

## Authentication API

The backend exposes JWT authentication under `/api/v1/auth`:

- `POST /api/v1/auth/login`: verifies email/password and returns a JWT access token plus a refresh token.
- `POST /api/v1/auth/refresh`: rotates a valid refresh token and revokes the presented token.
- `POST /api/v1/auth/logout`: revokes the presented refresh token.
- `GET /api/v1/auth/me`: returns the current authenticated security user for a valid bearer access token.

Refresh tokens are stored server-side only as hashes. Reusing a revoked refresh token is rejected and revokes active refresh tokens for that user.
