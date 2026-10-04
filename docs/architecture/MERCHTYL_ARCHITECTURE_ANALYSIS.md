# Merchtyl Architecture Analysis

## Scope and evidence

This review reflects repository revision `fbebd405a5be4d0effa6ef6e27f4a8b2771060ad`. It traces the Spring Boot backend, React/Vite frontend, Flyway migrations, security filters, JPA repositories, JDBC services, deployment configuration, and frontend API integration. The unrelated untracked `archify/` directory and generated build trees were excluded. No application code was changed.

## Current Architecture

Merchtyl is a modular monolith: a React 18/Vite SPA calls one Java 21 Spring Boot API, which persists to a shared PostgreSQL schema. The backend is organized by domain package (authentication, security, platform administration, billing, store, register/session, products, inventory, sales, food menu, lottery, EOD, reporting, tax, receipts, returns/refunds). Spring Data JPA is dominant in store operations; platform administration and billing use substantial `JdbcTemplate` SQL over Flyway-managed tables.

The tenancy root is `tenants`. Tenant users carry `security_users.tenant_id`; stores carry `stores.tenant_id`; most store-operational records reference a Store rather than duplicating `tenant_id`. There is no database row-level security. Isolation is therefore a service/repository responsibility.

Authentication is stateless JWT authentication. Tenant access tokens include `uid`, `tenantId`, `roles`, `accountScope=TENANT`, and `typ=access`; platform tokens use `accountScope=PLATFORM`. `JwtAuthenticationFilter` validates token signature, issuer, type and account state, then loads current role/permission authorities from the database. `MerchantContextFilter` checks `X-Merchant-Slug` only when supplied. `@PreAuthorize` performs permission gates, while `StoreAccessService` enforces tenant status, store ownership and user-store assignments on paths that invoke it.

The platform administration domain implements platform users, merchant onboarding, owner invitation/activation, lifecycle states, subscriptions, pricing plans and versions, plan capability entitlements, billing contacts, invoices, invoice payments, PDF generation and email delivery. Merchants are represented by JDBC-managed `tenants`, `merchant_profiles`, onboarding, subscription and billing tables rather than a Merchant JPA aggregate.

Store operations are anchored by a store-local Business Day. Register opening requires an OPEN or REOPENED current business day, Store access, a compatible Store capability/register type, and active-session exclusivity. A register session is created directly in `OPEN`; it transitions to `CLOSING`, can return to `OPEN`, and finishes as `CLOSED` or `FORCE_CLOSED`. Reconciliation snapshots expected/count cash and till retention/removal.

Retail checkout creates a `Sale` in `PENDING_PAYMENT`. Products may have variants, multiple barcodes, tenant ownership and Store-specific availability/pricing. Payments are appended individually, enabling split/partial tender. Completion is idempotent, locks the sale, validates payment sufficiency and register state, posts inventory and cash-ledger effects, snapshots sale items, and marks the sale complete.

Food service reuses the Sale aggregate. There is no separate RestaurantOrder or KitchenOrder entity. Food-menu selections snapshot menu item, variant, modifiers, included components and preparation instructions into sale items. Phone/pickup orders use `PHONE_CONFIRMED`; `Sale.kitchenStatus` carries PENDING, IN_PROGRESS, READY, COMPLETED or CANCELLED. Customer receipts and kitchen tickets are separate documents; scheduled/ASAP kitchen printing uses persistent `KitchenPrintJob` rows.

Lottery is implemented as a substantial domain: operators, sales/cancellations, payout policies, payouts/approvals/referrals/reversals, commission rules, settlement lifecycle, reports and EOD aggregation. Simple POS `SOLD`/`WIN` activities and Sale cart `LOTTERY_SOLD`/`LOTTERY_WIN` lines coexist with the richer dedicated lottery records.

The production topology documented in the repository is Browser → Vercel SPA → Railway Spring Boot API → Neon PostgreSQL, with Resend for transactional email. QZ Tray is a local browser/printer integration. No committed Docker/Kubernetes deployment definition was found.

## Implementation classification

- **Implemented:** platform/tenant onboarding; merchant lifecycle; pricing plans and versioned entitlements; subscriptions and invoices; users/RBAC/store assignments; Store/register/capability management; Business Day and register reconciliation; retail catalog/POS/payments/receipts; inventory adjustments/counts/imports; food menus, modifiers, pickup orders and kitchen tickets/jobs; lottery operations; EOD reports; returns/refunds; tax configuration; reporting.
- **Partially implemented or structurally split:** capability enforcement is distributed rather than universal; lottery has parallel operational models; frontend authorization often has legacy role fallbacks; local POS recovery/print queues exist but financial posting is online; billing supports external-provider identifiers but does not contain a direct payment-gateway integration.
- **Unused/dead or superseded:** `SaleService.createDraft` and several draft mutation methods remain, but `POST /api/v1/sales/drafts` returns `DRAFT_SALES_DISABLED`; the primary flow is atomic `/sales/checkout`. Legacy role names (`OWNER`, `MANAGER`) coexist with `TENANT_OWNER`, `STORE_MANAGER`.
- **Missing as distinct models:** no standalone Merchant JPA entity, RestaurantOrder entity, KitchenOrder entity, payment allocation entity, or request-wide StoreContext object. These are design choices or gaps, not inferred components.

## Strong Areas

- Financial completion and stock-count posting use idempotency records, transactions and row locking/version checks.
- Register-session exclusivity is enforced in both service logic and a partial unique PostgreSQL index.
- Register and Business Day states are explicit, audited and guarded by reconciliation checks.
- JWT refresh rotation and password-reset timestamps reduce replay of stale tenant credentials.
- Store access validates tenant status and store tenancy before assignment checks.
- Sale items, tax, prices, payment cash rounding, receipts and EOD reports preserve operational snapshots.
- EOD has broad aggregation coverage: payments, tax, categories, registers, cash, refunds, inventory, lottery, cashiers and exceptions.
- Flyway migrations contain extensive constraints and operational indexes; JPA runs with `ddl-auto=validate` and Open Session in View disabled.
- The frontend has explicit portal, mobile, POS-device, register-type, session and Business Day guards.

## Architectural Risks

### 1. Critical: authenticated cross-tenant read exposure

- **Files/methods:** `SaleController.get/search`; `SaleService.get`, `search`, `findSale`; `InventoryController` read/search endpoints; `InventoryService.currentStock`, `searchBalances`, `searchTransactions`; similar no-auth read methods in register, stock-adjustment and several lottery services.
- **Current behavior:** method permissions authenticate/authorize the caller, but some services then call generic `findById` or an optional-filter `findAll` specification without tenant/store predicates.
- **Why problematic:** a tenant user with the relevant permission could enumerate another merchant's records by UUID or omit filters and receive records from multiple tenants.
- **Fix:** make tenant/store scope mandatory in repository APIs. Pass `Authentication`/a typed `TenantPrincipal` into every tenant-domain query, derive accessible Store IDs centrally, and add tenant/store predicates inside repositories/specification factories. Add negative integration tests using two tenants. Consider PostgreSQL row-level security as defense in depth.

### 2. High: no authoritative request-wide tenant/store context

- **Files/methods:** `MerchantContextFilter.doFilterInternal`; `StoreAccessService.currentTenantId/requireStoreAccess`; domain controllers and services.
- **Current behavior:** the merchant slug is only compared when the client sends it. Store context is an ordinary request ID and is validated independently by participating services.
- **Why problematic:** new endpoints can easily omit the check, as several read paths already do. The JWT `tenantId` claim is issued but database user lookup, not a typed principal, is the dominant runtime source.
- **Fix:** construct an immutable principal containing account scope, user ID and tenant ID in the JWT filter. Resolve Store context through a single mandatory authorization component and expose only scoped repository interfaces to tenant-domain services.

### 3. High: oversized orchestration services and frontend modules

- **Files:** `BusinessDayService` (~2,400 lines), `PlatformAdministrationService` (~2,000), `SaleService` (~1,440), `ProductService` (~1,080), `frontend/src/api/client.ts` (~3,586), `frontend/src/api/types.ts` (~2,834), and multiple 1,000–3,000-line pages.
- **Current behavior:** aggregation, validation, authorization, persistence orchestration, rendering/export and DTO mapping are concentrated in a few classes/files.
- **Why problematic:** broad change blast radius, difficult focused tests, hidden coupling and increased regression risk.
- **Fix:** split by use case and policy: e.g. `SaleCheckoutService`, `PaymentService`, `SaleCompletionService`, `EodClosingValidator`, `EodAggregator`, `EodExporter`; split frontend clients/types and pages by domain.

### 4. High: inventory read isolation and sale-negative-stock semantics

- **Files/methods:** `InventoryService.currentStock/searchBalances/searchTransactions`; `recordStockChange` lines handling resulting quantity.
- **Current behavior:** reads can be unscoped. Manual negative movements are blocked when `negativeStockAllowed=false`, but `SALE` is explicitly exempt and can produce negative stock.
- **Why problematic:** the read exposure is a confidentiality issue; the SALE exception may contradict operator expectations and can hide stock-control failures.
- **Fix:** scope all reads and make the sale exception an explicit, named policy (with configurable hard/soft enforcement and audited override) rather than an implicit conditional exception.

### 5. High: payment model lacks allocation and correction workflow

- **Files/methods:** `SaleService.recordPayment`, `appendCashLedgerEntries`; `Payment`.
- **Current behavior:** split tender is multiple Payment rows. Payments cannot exceed remaining balance. Mixed tenders have no line-to-tender allocation, and the code documents that it cannot accurately attribute a cash portion to lottery lines.
- **Why problematic:** reconciliation becomes ambiguous for regulated or separately accounted merchandise. There is no first-class payment reversal/edit aggregate in the core sale payment flow.
- **Fix:** introduce payment-attempt/allocation/reversal records with immutable state transitions and explicit links to sale portions where required.

### 6. Medium: duplicate/parallel lottery transaction models

- **Files:** `SaleLineType`, `LotteryPosActivity`, `LotterySale`, `LotteryPayout`, EOD aggregation.
- **Current behavior:** simple POS activities, sale cart lottery lines and dedicated lottery sale/payout records coexist.
- **Why problematic:** reconciliation semantics and source-of-truth ownership are harder to reason about; double counting is a future risk.
- **Fix:** publish one canonical lottery ledger/event model and treat other records as projections with explicit source IDs and database uniqueness constraints.

### 7. Medium: mixed date-selection strategies in EOD

- **Files/methods:** `BusinessDayService.saleSpec`, `cashMovementSpec`, `inventoryTransactionSpec`, `lotterySaleSpec`.
- **Current behavior:** sales/refunds are selected by `businessDate`; cash/inventory/some lottery data use Store-timezone instant ranges.
- **Why problematic:** late posting, backdated activity and DST boundaries can place related records in different EOD slices.
- **Fix:** attach `business_day_id` to every financially relevant record and aggregate primarily by that immutable FK; retain timestamps for audit/order.

### 8. Medium: localStorage token persistence

- **File:** `frontend/src/app/session.tsx`.
- **Current behavior:** access and refresh tokens are JSON-serialized to portal-scoped localStorage.
- **Why problematic:** any successful XSS can exfiltrate both token types.
- **Fix:** prefer a secure, HttpOnly, SameSite refresh cookie with short-lived access tokens held in memory; preserve CSP and harden dependency/content injection paths.

### 9. Medium: public test-provisioning matchers exist in the main security chain

- **Files:** `SecurityConfig.securityFilterChain`; `TestUserProvisioningController` and properties.
- **Current behavior:** testing user-provision endpoints are `permitAll`; production relies on configuration disabling the controller/service or an internal provisioning key.
- **Why problematic:** configuration drift can expose a powerful endpoint.
- **Fix:** register the controller only under an explicit non-production profile and reject production startup if test provisioning is enabled.

### 10. Medium: configuration defaults can expose API documentation

- **Files:** `application-prod.yml`, `railway.env.json`.
- **Current behavior:** the production profile defaults Swagger/API docs to enabled, while the recommended Railway environment turns them off.
- **Why problematic:** a missed environment override exposes endpoint metadata.
- **Fix:** default both flags to false in production and opt in only for controlled environments.

### 11. Medium: field injection and nullable optional collaborators

- **Files:** `SaleService`, `RegisterSessionService`, `InventoryService`, and others.
- **Current behavior:** core dependencies such as Store access, repositories, feature services and policy services are sometimes field-injected and guarded with null fallbacks for tests/legacy constructors.
- **Why problematic:** production invariants can silently weaken when wiring changes; tests may exercise a materially different path.
- **Fix:** use constructor injection for required collaborators and explicit strategy interfaces/stubs for optional behavior.

### 12. Medium: global email uniqueness couples tenant identities

- **Files:** `User` unique constraint on `email`; `UserRepository.findByEmailIgnoreCase`.
- **Current behavior:** a login email can belong to only one tenant.
- **Why problematic:** the model prevents one person/email from having accounts in multiple merchants unless this is a deliberate product constraint.
- **Fix:** either document global identity as intentional and model tenant memberships separately, or change uniqueness to tenant/email and require tenant realm during authentication.

## Security Risks

The strongest controls are signed JWT validation, current database authority loading, account-scope authorities, method permissions, account/tenant status checks, store assignment validation, audit records and token rotation. The principal security weakness is inconsistency: permissions answer *what* a user may do, but several services do not consistently answer *to which tenant/store record*. Generic repository methods remain callable in tenant-domain services.

Privilege escalation should be tested around legacy/current role aliases, manager store assignments, operator transfer/override/release, platform support permissions, feature-management endpoints and direct object IDs. Merchant portal hostname/slug checks should be treated as realm binding, not the primary tenancy boundary.

## Data Integrity Risks

- **Register sessions:** strong row locking, optimistic versions and a partial unique active-register index exist. Device/cashier exclusivity has more service-level than database-level enforcement.
- **Business days:** Store/date uniqueness and closing validation are strong. The override opening configuration can permit multiple active days when explicitly configured, increasing operational complexity.
- **Payments:** overpayment through `Payment.amount` is blocked and completion requires sufficient total, but allocation/reversal semantics are limited.
- **Inventory:** optimistic versioning is present; the lookup/update path can still face create races (mapped to conflict) and sale-driven negative stock is deliberate.
- **Sales:** completion is transactional and idempotent. Unscoped reads remain a confidentiality issue, and unfinished paid checkouts deliberately block EOD.
- **Restaurant orders:** food orders share Sale status plus a nullable kitchen status; illegal cross-product combinations are partly constrained by code/SQL but remain more complex than a dedicated aggregate.
- **Lottery:** broad status models and cash-ledger sources exist; parallel record types heighten double-count/reconciliation risk.

## Potential Bugs and suspicious flows

| File / method | Current behavior | Why suspicious | Suggested fix |
|---|---|---|---|
| `SaleService.get/search/findSale` | Uses `findById` and an unscoped specification | Cross-merchant sale disclosure | Require principal and accessible Store predicate in every query |
| `InventoryService.currentStock/searchBalances/searchTransactions` | Reads by arbitrary Store/Product IDs or optional filters | Cross-merchant inventory disclosure and enumeration | Verify Store access and add mandatory tenant/store scope |
| `RegisterService.get/search` and several lottery `get/search` methods | Public service signatures omit Authentication | Controller permission can be valid while object scope is not | Replace with scoped query services; remove unscoped overloads from production API paths |
| `InventoryService.recordStockChange` | Allows `SALE` to make quantity negative even when Store disallows negative stock | Setting name may imply the opposite to users | Make explicit policy and surface/audit the exception |
| `SaleService.recordPayment` | Cash `amount` is limited to remaining balance; `cashTendered` can exceed it | Correct for change, but partial cash rounding uses a different rounding mode path | Add currency-specific tests for every split-tender ordering and rounding boundary |
| `SaleService.appendCashLedgerEntries` | Mixed tenders cannot attribute cash between lottery and merchandise | EOD/regulatory attribution may be approximate | Persist payment allocations or split sub-ledgers |
| `BusinessDayService` date specs | Mixes `businessDate` equality with timezone instant windows | DST/late-posting discrepancies | Persist and query `business_day_id` universally |
| `SecurityConfig` test endpoints | Paths are permitted by the main chain | Misconfiguration could expose provisioning | Profile-gate controller and fail closed in production |
| `frontend/src/app/session.tsx` | Stores refresh token in localStorage | XSS yields long-lived credentials | HttpOnly refresh cookie; access token in memory |
| `SaleController.createDraft` vs `SaleService.createDraft` | Endpoint always returns disabled while service and mutations remain | Dead/superseded code confuses invariants and tests | Remove or isolate legacy draft API after migration confirmation |

## Recommended architectural priorities

1. Close all tenant/store-scoping gaps and add two-tenant authorization integration tests across every read/write controller.
2. Introduce a typed authenticated principal and mandatory scoped repository/query abstractions.
3. Decompose Sale, Business Day and Platform Administration orchestration into use-case services and explicit policies.
4. Normalize financial records on `business_day_id` and formalize payment allocation/reversal semantics.
5. Clarify canonical lottery transaction ownership and negative-stock policy.
6. Harden production configuration defaults and browser token storage.

