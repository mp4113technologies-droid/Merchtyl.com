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

## Production deployment

The Spring Boot backend is ready for Railway's native Java build from the `backend` root directory. PostgreSQL remains an external Neon service, while the React/Vite frontend is deployed separately on Vercel.

```text
Browser -> Vercel frontend -> Railway (Spring Boot) -> PostgreSQL/Neon
                                  |
                                  +-> Resend
```

### 1. Configure the Railway backend service

Create a Railway service from this repository and set its **Root Directory** to `backend`. Use `mvn clean package -DskipTests` as the build command and `java -jar target/merchtyl-backend-*.jar` as the start command. Railway detects Maven from `backend/pom.xml`, while `backend/.java-version` pins Java 21. Configure at least:

The importable `railway.env.json` file contains the required Railway variables with safe placeholders. Replace every `REPLACE_WITH_...` value before importing it into Railway; never commit the completed file containing real secrets.

```env
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST/DATABASE?sslmode=require
SPRING_DATASOURCE_USERNAME=DATABASE_USER
SPRING_DATASOURCE_PASSWORD=DATABASE_PASSWORD
MERCHTYL_JWT_SECRET=GENERATE_A_LONG_RANDOM_SECRET
MERCHTYL_CORS_ALLOWED_ORIGINS=https://YOUR_VERCEL_FRONTEND_DOMAIN
MERCHTYL_FRONTEND_BASE_URL=https://YOUR_VERCEL_FRONTEND_DOMAIN
MERCHTYL_EMAIL_PROVIDER=resend
MERCHTYL_EMAIL_FROM_ADDRESS=notifications@YOUR_VERIFIED_DOMAIN
RESEND_ENABLED=true
RESEND_API_KEY=YOUR_RESEND_API_KEY
SWAGGER_UI_ENABLED=false
SWAGGER_API_DOCS_ENABLED=false
```

Railway supplies `PORT` automatically. The application listens on that port at `0.0.0.0`. In Railway service settings, configure the health-check path as `/actuator/health`, then generate a public domain under **Networking**. Verify the deployment before continuing:

```bash
curl https://YOUR_API_DOMAIN/actuator/health
```

Flyway runs automatically during backend startup. Do not run multiple first deployments concurrently against an empty database.

The production profile requires Resend and validates these email settings during startup. The sender must be authorized by the configured Resend account. See `docs/transactional-email-resend.md`.

### 2. Deploy the frontend separately

The frontend remains a separate Vercel project whose **Root Directory** is `frontend`, framework preset is Vite, build command is `npm run build`, and output directory is `dist`.

Add this Vercel environment variable to Production and Preview as appropriate:

```env
VITE_API_BASE_URL=https://YOUR_API_DOMAIN
```

The value is the API origin only, without `/api/v1` or a trailing slash. Vite embeds it at build time, so redeploy after changing it.

Deploy the backend from the Railway dashboard or, after installing and authenticating the Railway CLI, from the repository root:

```bash
railway link
railway up
```

### 3. Finalize allowed origins

Set the Railway backend's `MERCHTYL_CORS_ALLOWED_ORIGINS` and `MERCHTYL_FRONTEND_BASE_URL` to the exact Vercel `https://` origin and redeploy the backend. `ALLOWED_ORIGINS` remains supported as an alias. For multiple exact origins, use a comma-separated list. Do not use `*` for production.

Preview deployments use changing Vercel hostnames. Either give previews a dedicated stable custom domain and allow that origin, deploy previews against a non-production backend, or omit `VITE_API_BASE_URL` from previews that should not access the API.

### 4. Production verification

1. Open the Vercel URL directly on a nested route and confirm the SPA loads.
2. Sign in and confirm `/api/v1/auth/login` targets the deployed API.
3. Confirm the browser console contains no CORS or mixed-content errors.
4. Check `GET /actuator/health` and backend logs.
5. Send a test email and complete the platform-admin bootstrap described in `docs/platform-admin-bootstrap.md`.

### Local native-build verification

Build the same JAR Railway uses:

```bash
mvn -f backend/pom.xml clean package -DskipTests
```

Run it against an external PostgreSQL database:

```bash
PORT=8080 \
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL='jdbc:postgresql://HOST/DATABASE?sslmode=require' \
SPRING_DATASOURCE_USERNAME='DATABASE_USER' \
SPRING_DATASOURCE_PASSWORD='DATABASE_PASSWORD' \
MERCHTYL_JWT_SECRET='GENERATE_A_LONG_RANDOM_SECRET' \
MERCHTYL_CORS_ALLOWED_ORIGINS='http://localhost:5173' \
MERCHTYL_FRONTEND_BASE_URL='http://localhost:5173' \
MERCHTYL_EMAIL_PROVIDER='resend' \
MERCHTYL_EMAIL_FROM_ADDRESS='notifications@YOUR_VERIFIED_DOMAIN' \
RESEND_ENABLED='true' \
RESEND_API_KEY='YOUR_RESEND_API_KEY' \
java -jar backend/target/merchtyl-backend-*.jar
```

Check `http://localhost:8080/actuator/health` after startup.

### Transactional email

Local development uses the console email provider by default. To make that choice explicit:

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
