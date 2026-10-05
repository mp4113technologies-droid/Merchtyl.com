# Merchtyl Codebase Feature Inventory

Generated from a static inspection of the repository on 2026-10-04. This is a discovery report, not a target-state design. “Implemented” means meaningful code exists across the necessary layers; runtime deployment, external hardware, email-provider credentials, and production data were not exercised.

## 1. Executive Summary

Merchtyl is a multi-tenant Canadian retail and food-service POS platform with a React merchant/platform portal and a Spring Boot/PostgreSQL backend. The strongest end-to-end areas are merchant/store/user administration, retail checkout, restaurant ordering, products, inventory, register sessions, physical-till settlement, business-day/EOD reporting, returns/refunds, and platform subscription administration.

The code distinguishes a physical `Register`, an operator `RegisterSession`, a store-scoped `BusinessDay`, and reconciliation. It does **not** have a standalone Till entity. Physical drawer state is `RegisterBusinessDayCashState`, keyed to Register + BusinessDay; session opening, expected/count, and settlement are snapshots on `RegisterSession` plus an append-style cash ledger.

Important qualifications:

- Device enforcement exists but defaults off. No MAC-address verification exists.
- Draft creation is intentionally disabled; the browser maintains a local cart and `/sales/checkout` creates an authoritative `PENDING_PAYMENT` sale. Explicit held sales remain server-persisted.
- Payments record tenders manually; no real debit/credit/gift-card processor integration was found.
- Inventory transfer transaction types and permissions exist, but no merchant transfer workflow was found.
- Full lottery operator/policy/commission/settlement APIs exist primarily backend-only; the merchant UI exposes simple POS sold/win activity and reports.
- Restaurant “kitchen” operations live inside the Restaurant POS pickup-order panel; no separate `/kitchen` route was found.
- Platform invoices, payment recording, PDF generation, and email sending exist, but there is no payment-gateway/autopay integration. Immediate proration is explicitly not implemented.

### Status legend

`IMPLEMENTED`, `PARTIALLY_IMPLEMENTED`, `BACKEND_ONLY`, `FRONTEND_ONLY`, `CONFIGURED_OR_HIDDEN`, `LEGACY_OR_UNUSED`, `PLANNED_NOT_IMPLEMENTED`, and `UNCLEAR — requires runtime verification` have the meanings required by the analysis prompt.

## 2. Repository Architecture

| Area | Path | Purpose |
|---|---|---|
| Backend | `backend/src/main/java/com/merchtyl` | Spring Boot REST API, domain/services, security, reports, email, billing |
| Schema | `backend/src/main/resources/db/migration` | Flyway migrations V1–V152; PostgreSQL constraints and seed data |
| Backend tests | `backend/src/test` | Unit, controller-authorization, concurrency, migration, and integration evidence |
| Merchant UI | `frontend/src/features` | POS, management, inventory, reporting, configuration screens |
| Platform UI | `frontend/src/features/platform` | Merchant onboarding/lifecycle, admins, pricing, subscriptions, invoices, audit |
| Routing/shell | `frontend/src/app/App.tsx` | Host-based portal boundary, route tree, navigation, mobile/POS guards |
| API client/types | `frontend/src/api` | Typed REST client and DTOs |
| Printing docs | `docs/POS_THERMAL_PRINTING.md`, `docs/qz-tray-setup.md` | Browser/QZ Tray setup and operational limitations |
| Deployment | `nginx`, `frontend/vercel.json`, `railway*.json` | Web/backend hosting configuration |

The untracked nested `archify/` directory is a separate project and was excluded from Merchtyl product findings.

## 3. User Roles & Permissions

Roles in code: `PLATFORM_SUPER_ADMIN`, `PLATFORM_SUPPORT_ADMIN`, `TENANT_OWNER`, `OWNER`, `STORE_MANAGER`, `MANAGER`, `CASHIER`, and `KITCHEN`. `TENANT_OWNER`/`OWNER` and `STORE_MANAGER`/`MANAGER` coexist as canonical and compatibility names.

- JWT access tokens (default 60 minutes) and persisted rotating/revocable refresh tokens (default 14 days); logout revokes refresh state.
- Password login, forgot/reset flow, lockout after configurable failures, rate limiting, temporary-password first-login change, and 6-digit POS PIN unlock are implemented.
- Backend `@PreAuthorize` checks use granular permission codes, tenant scope, store assignments, and platform scope; UI visibility is secondary.
- Store assignment status/role and optional register assignments exist. Tenant IDs are derived from authentication for tenant APIs.
- Owners can span tenant stores; managers/cashiers/kitchen users are constrained by assignments and seeded permission sets.
- Platform support and super-admin permissions differ; platform routes are host/context protected.
- Merchant portals resolve from subdomain slug; public, platform, merchant, development, unknown, inactive-merchant contexts have separate boundaries.

**Status: IMPLEMENTED.** Evidence: `security/PermissionCode.java`, `security/RoleName.java`, `security/AuthorizationService.java`, `auth/AuthService.java`, `config/SecurityConfig.java`, `frontend/src/app/session.tsx`, `frontend/src/app/App.tsx`.

## 4. Merchant Management

Platform admins can create merchants with legal/operating identity, code, slug, industry, contact/address/geography defaults, first store, owner, capabilities, and subscription. The workflow validates geography, provisions owner temporary credentials, and tracks onboarding stages. List/detail supports server pagination, search/status filters, store display, and status history.

Lifecycle actions include activate, suspend, reactivate, close, reopen, and deletion of an empty eligible tenant. Eligibility is checked before deletion. Owners may be invited/resend-invited, disabled, unlocked, sent password reset, or issued new temporary credentials. Email-delivery history and retries are visible from merchant detail. Store capabilities can be previewed against commercial entitlements before update.

Merchant codes and slugs are unique; slug routing is active. Limits and charges derive from subscription plan quantities rather than simple hard-coded merchant fields.

**Status: IMPLEMENTED.** Evidence: `platform/admin/PlatformAdministrationController.java`, `PlatformAdministrationService.java`, migrations V50–V59, V75–V95, `frontend/src/features/platform/PlatformPages.tsx`.

## 5. Store Management

Create/list/detail/edit/status screens support code/name, active state, address, country/province, currency, timezone, tax region, prices-include-tax, negative-stock policy, default till float, and `RETAIL`, `FOOD_SERVICE`, `LOTTERY` capabilities. Tenant defaults prefill geography. Historical sales/tax/report snapshots are preserved when geography changes.

Store selection is persisted locally but validated against accessible stores. Capabilities require both subscription entitlement and store enablement. Food service additionally stores kitchen display and automatic/scheduled kitchen-print settings. Store create/update and capabilities are subject to plan quantities and platform capability controls.

**Status: IMPLEMENTED / CONFIGURED_OR_HIDDEN.** Evidence: `store/Store.java`, `StoreController.java`, `StoreService.java`, `StoreCapabilityService.java`, `FoodServiceController.java`, `frontend/src/features/stores/StorePages.tsx`.

## 6. Register Management

Registers are store-scoped with code, name, active status, operational type (`RETAIL` or `FOOD_SERVICE`), and optional till-float override. Effective float is register override then store default. Create/edit/status/list/search/pagination are implemented. Register type is checked against effective store capability and user POS permission.

Device records and register-device association APIs exist. Device enforcement is configurable and defaults false; when enabled an open request requires device ID. Availability reports available/in use/restore-required state. Active/inactive registers cannot be opened. Session transfer, authorized override, and release preserve operator history and revoke displaced refresh tokens where appropriate.

**Status: IMPLEMENTED; device binding CONFIGURED_OR_HIDDEN.** Evidence: `register/Register.java`, `RegisterService.java`, `registersession/RegisterSessionService.java`, `device/DeviceService.java`, migrations V7–V9, V60, V63–V65, V89, V120, V146.

## 7. Shift / RegisterSession Lifecycle

Statuses are `OPEN`, `CLOSING`, `CLOSED`, `FORCE_CLOSED`. Opening requires permission, active/accessible store/register, matching type permission, and an open BusinessDay. Code enforces one active session per user and, through service/database constraints, one current session per register; device uniqueness is configurable. A user can resume their current session.

Closing is a two-step capable workflow (`start-closing`, preview, close; cancel-closing exists), using ledger-derived expected cash, counted cash, variance, explanation, target float, cash retained and cash removed. Unexplained non-zero variance is rejected. Force close requires permission/reason. Closed sessions are immutable operational history.

Secure Till locks the open session without ending it and unlocks with the assigned operator’s PIN or password. PIN failures create a temporary lock. Transfer changes current operator; override is manager/owner-controlled; release lets an authorized operator take an open session while retaining opener/history.

**Status: IMPLEMENTED.** Evidence: `registersession/RegisterSession.java`, `RegisterSessionController.java`, `RegisterSessionService.java`, `RegisterSessionOperatorHistory.java`, related controller/service tests.

## 8. Till / Cash Management

There is no dedicated Till entity. The layers are:

| Concept | Authority |
|---|---|
| Configuration | Store default float and Register override |
| Physical state | `RegisterBusinessDayCashState` per Register + BusinessDay |
| Session snapshot | opening cash/source, expected, counted, variance, target/retained/removed cash on RegisterSession |
| Ledger | `CashLedgerEntry` sources for opening, sale cash/change, lottery, refunds, manual movement, closing removal, restore |
| Reporting | register-session details plus physical-register EOD aggregate |

First physical open uses configured float or explicit manual amount. Later shifts inherit the target and retained cash. If cash was left below target, `TILL_RESTORE_REQUIRED` blocks opening until an exact restoration is posted; above target yields `TILL_SETTLEMENT_REQUIRED`. Close computes cash-to-bag and retained cash; retention above ordinary policy needs manager authorization.

Manual movement types: cash in/out, payout/reversal, safe drop, float add/remove, expense, bank deposit, correction. Configured types require approval permission. Search supports store/register/session/type/date/pagination. Payout reversals are audited and ledger-posted.

**Status: IMPLEMENTED.** Evidence: `cash/CashLedgerService.java`, `CashMovementService.java`, `registersession/RegisterBusinessDayCashState.java`, migrations V25–V27 and V141–V152.

## 9. Reconciliations

Expected cash comes from the authoritative cash ledger, not UI arithmetic. Close requires counted cash and reconciliation; variance explanation is mandatory for non-zero variance. Settlement preview is non-mutating. Force close records force metadata. Business-day validation lists every open/closing/unreconciled/missing-count blocker and returns per-session reconciliation details.

The current design reconciles every session but treats the final reconciled session/state as physical EOD cash for a register reopened during one BusinessDay. This explicitly mitigates float and drawer double-counting.

**Status: IMPLEMENTED.** Evidence: `RegisterSessionService.java`, `BusinessDayService.java`, `CashLedgerService.java`, `BusinessDayRegisterSessionBlockingTest.java`.

## 10. Business Day

BusinessDay is Store-scoped and uses the Store timezone to derive business date. States are `OPEN`, `CLOSING`, `CLOSED`, `REOPENED`; operational states additionally distinguish no day today, historical closed, previous day still open, and closed today.

Store operators with seeded permissions can open; manager/owner roles can close depending on resolved permissions. Duplicate active days and opening while a previous day remains open are blocked. Closing validation checks sessions, reconciliation/counts, unfinished sales, pending lottery payouts/settlements, and variance explanations. Preview is read-only; start/close/force-close/reopen are versioned and idempotent. Force close requires reason. Reopen is limited to today, requires an existing report, and a later day must not exist; reclose creates a report revision rather than overwriting history.

A closing-reminder calculation exists. Configuration includes closing time and whether a report becomes ready after the final register closes; code does not silently close the day. The UI exposes open, current state, history, validation, close, force close, and reopen.

**Status: IMPLEMENTED.** Evidence: `eod/BusinessDayService.java`, `BusinessDayController.java`, `BusinessDayConfiguration.java`, `frontend/src/features/eod/BusinessDayPages.tsx`.

## 11. Retail POS

The desktop-only Retail POS supports scanner input, manual barcode entry, name/SKU/barcode search, quick keys, product/variant selection, quantity +/- and removal, taxable/non-taxable custom items, age confirmation, inventory warnings, local-cart recovery, held sales, discounts, multi-buy preview, container deposits, lottery sold/win, deposit payout, checkout, multiple/partial payments, cash denominations/keypad, cash tender/change/rounding, completion, receipt printing/reprint, payout slip, new sale, error retry, and Secure Till.

The backend performs authoritative product availability, price, promotion, deposit, tax, payment, inventory, and completion calculations. Completion requires an idempotency key. Negative-stock behavior follows Store configuration; age verification is captured with the line request. Keyboard support includes scanner/Enter behavior and shell shortcuts (`?`, Alt+M, Alt+S).

**Status: IMPLEMENTED.** Evidence: `frontend/src/features/pos/PosPages.tsx`, `hardware/barcodeScanner.ts`, `sales/SaleService.java`, `product/ProductService.java`, `payments/CashRoundingService.java`.

## 12. Products / Variants

Product is merchant-owned catalogue identity (name/description, category, brand, unit, tax class, type, age restriction, active/deleted state, availability scope). ProductVariant is the sellable SKU/price/cost/inventory/deposit unit. Products may be merchant-wide or store-selected through `StoreProduct` mappings.

Create/detail/edit, activate/deactivate, safe delete, variant creation/edit, price/cost fields, barcode aliases, primary barcode, category/brand/unit, tax treatment, deposit configuration, minimum age, type, search/filter/sort/pagination, and store availability are implemented. Deletion detaches barcodes safely and supports restoration behavior through update/status semantics; no bulk product UI or generic catalogue CSV import/export was found.

**Status: IMPLEMENTED; bulk/import PARTIALLY_IMPLEMENTED (absent except inventory workbooks).** Evidence: `product/Product*.java`, `StoreProduct*.java`, migrations V12–V13, V62, V66–V70, V100, V112, V118, V122.

## 13. Categories / Brands

Merchant-scoped create/list/edit/status management exists with search/pagination and generated human-readable codes. Global merchant namespace migrations prevent collisions. A default Lottery category is provisioned and protected by lottery semantics. Delete endpoints are not exposed; active/inactive is the lifecycle.

**Status: IMPLEMENTED.** Evidence: `catalogue/CategoryService.java`, `BrandService.java`, `CatalogueReferencePages.tsx`, migrations V10, V112, V124–V125.

## 14. Barcodes

Variants support multiple aliases plus primary barcode. Ownership lookup detects duplicates; management supports assign, update, remove, and transfer/reassignment semantics. Barcode strings are text and preserve leading zeroes. Scan-to-cart resolves store availability and the active variant; unknown scans offer custom item when permitted. Inactive/deleted product semantics are validated by backend.

**Status: IMPLEMENTED.** Evidence: `ProductBarcode.java`, `ProductVariantBarcodeController.java`, `ProductVariantBarcodeService.java`, `barcodeScanner.ts`, barcode tests.

## 15. Inventory

Inventory balance is Store + ProductVariant scoped with optimistic/concurrency controls and an immutable transaction history. Supported posting types include opening stock, purchase, sale, return, adjustments, stock counts, damaged, expired, transfer markers, and void reversal.

UI workflows: current inventory, history, low stock, negative stock, adjustment report, damaged/expired reports; manual increase/decrease/damaged/expired adjustments; counts with draft/save/review/post behavior; initial inventory XLSX download/upload/validate/preview/confirm; and inventory-update XLSX batch upload/validate/preview/confirm. Sale completion deducts tracked variants; refunds/returns restore eligible stock. Store policy controls zero/negative stock selling.

Transfer enums/permissions exist but no controller/page orchestrating store-to-store transfers was found. Receiving has permission/schema vocabulary but no dedicated receiving UI. Low-stock is report-driven, with no alert notification service.

**Status: IMPLEMENTED overall; transfers/receiving BACKEND_ONLY or PLANNED_NOT_IMPLEMENTED.** Evidence: `inventory/*`, `initialinventory/*`, `inventoryimport/*`, migrations V20–V22, V69–V73, V100, V103–V104, V134.

## 16. Lottery

Two active models coexist:

- Simple POS activity: manual Lottery Sold and Lottery Win lines, including negative/net payout checkout, cash-ledger impact, receipt/EOD/report inclusion, and physical `LOTTERY_PRODUCT` barcode sales.
- Full operational domain: operators, payout policies, sale cancellation, payout approval/reversal, commission rules, and settlement lifecycle.

The UI exposes simple sold/win buttons, lottery product scanning, reports/dashboard, and cash payout confirmation. The full operator/policy/commission/settlement APIs have no corresponding merchant screens and are **BACKEND_ONLY**. Store and subscription LOTTERY capability gate simple UI/reporting.

Implemented formula: `totalLotterySold = physical lottery product sales + manual sold`; `lotteryWins = manual/payout win value`; `netLottery = totalLotterySold - lotteryWins`. EOD separately records cancellations, reversals, cash/non-cash activity, commission, settlement, referrals, approvals and rejections.

**Status: IMPLEMENTED simple path; BACKEND_ONLY full settlement domain.** Evidence: `lottery/*`, `LotteryReportsPage.tsx`, `SalesClassificationService.java`, migrations V37–V46, V88, V123–V129, V135.

## 17. Taxes

The tax domain includes countries, administrative areas, jurisdictions, tax types/components/rates, groups/group components, categories, product-category assignments, rules/conditions/actions, and a calculation endpoint. Full CRUD/status screens exist for these reference areas plus a development-only simulator.

Canadian seed data and province-aware vape taxes are present. Products use semantic `STANDARD`, `NON_TAXABLE`, `VAPE`, `CUSTOM`; tax rules resolve jurisdictional rates. Stores define tax region and prices-including-tax. Checkout snapshots line tax treatment and component taxes, preserving historical behavior. Deposits and lottery exclusions are handled by classification/tax logic rather than UI assumptions.

**Status: IMPLEMENTED.** Evidence: `tax/*`, migrations V14–V19, V49, V136, V138–V140, `TaxGeographyPages.tsx`.

## 18. Discounts / Promotions

POS supports audited percentage/fixed line discounts and sale discounts, permission-controlled approval concepts, saved discount definitions, active-store filtering, and automatic shared multi-buy definitions. Definitions can target products/categories and Retail/Restaurant/shared domains. Restaurant and retail both load active definitions. Tax is recalculated authoritatively after discount; deposit and excluded lottery amounts do not receive ordinary discounts. Sale adjustments snapshot names/types/values/reasons/status.

No rich campaign scheduler, coupon-code redemption, or bundle-builder UI was found. Product `BUNDLE` exists as a sellable type but is not a separate bundle authoring workflow.

**Status: IMPLEMENTED core; advanced campaigns PARTIALLY_IMPLEMENTED.** Evidence: `discount/*`, `sales/SaleAdjustment.java`, `DiscountDefinitionsPage.tsx`, migrations V61, V97–V99, V101, V113.

## 19. Deposits

Variants can define deposit amount/type. Checkout creates deposit snapshots, excludes deposits from discount/multi-buy and ordinary tax treatment, prints and reports them. POS can add a `DEPOSIT_PAYOUT` negative line with explicit permission; negative sales complete as cash payout with confirmation/slip. EOD and sales reports separate deposit collected/payout values.

**Status: IMPLEMENTED.** Evidence: `product/ProductVariant.java`, `sales/SaleItem.java`, `SaleItemDepositTest.java`, migrations V114, V130.

## 20. Payments

Methods are CASH, DEBIT, CREDIT, GIFT_CARD, STORE_CREDIT, OTHER. A Sale has multiple Payment records; partial and split tender are supported, with permissions for split/edit. Cash stores tendered, applied, rounded amount, and change; Canadian cash rounding is snapshotted. Completion requires payment completeness, except valid net-payout flows.

Refund payments are separate records and restore cash ledger/inventory as applicable. Reports summarize payment mix. All non-cash types are manual tender records with optional reference/notes; no terminal, gift-card balance, or external processor integration was found.

**Status: IMPLEMENTED manual tendering; external integrations PLANNED_NOT_IMPLEMENTED/not found.** Evidence: `sales/Payment.java`, `SaleService.java`, `refunds/RefundService.java`, `CashRoundingService.java`, `PaymentDialog` in `PosPages.tsx`.

## 21. Sales / Refunds

Sale statuses: draft, pending payment, phone confirmed, held, completed, voided, partially refunded, refunded, cancelled. Local cart checkout creates a pending authoritative sale; line edit/recalculate/payment/complete flows are protected by status. Search filters by store/register/session/user/status. Completed financial records snapshot price, category, type, tax, deposit, food selections, currency, business date, and users.

Returns select original sale lines/quantities and reason. Refund creation is separately idempotent, supports partial returns/refunds, payment allocation, optional approval configuration, cash ledger posting, and inventory restoration. Completed-sale void permission exists in the security/domain model, but no obvious merchant-facing void screen/action was found.

**Status: IMPLEMENTED returns/refunds; completed void BACKEND_ONLY/PARTIAL.** Evidence: `sales/*`, `returns/*`, `refunds/*`, `ReturnPages.tsx`, related integration tests.

## 22. Held Sales

Retail and restaurant can explicitly hold an authoritative sale, list held sales, resume it, or cancel. Held records persist server-side and retain lottery/custom/food component snapshots. Resume is permission and register/session constrained. Local unsubmitted carts are separately recovered from browser storage and are not held sales.

Phone orders differ: they become `PHONE_CONFIRMED`, carry customer/pickup/kitchen fields, are visible store-wide for pickup, and may be claimed at another eligible register.

**Status: IMPLEMENTED.** Evidence: `SaleService.java`, `PosPages.tsx`, `FoodPosPage.tsx`.

## 23. Receipts / Printing

Backend generates immutable receipt documents and human-readable receipt numbers, lookup, and reprint audit behavior. Retail receipt includes lines, taxes, deposits, discounts, payments, cash/change, lottery, and branding. Restaurant also generates kitchen tickets with variants/modifiers/components/notes and order token. Payout slips are browser-rendered.

Browser print and QZ Tray support thermal printers. Preferences cover browser/QZ transport and configured printer; QZ supports installed printer connectivity (including printers exposed by the host such as USB/LAN), but Merchtyl does not itself implement a network print server. Kitchen jobs have queue, claim, acknowledgement, retryable failure, stale-claim recovery, scheduled lead time, print-now, and auto-print settings. Printing failure does not roll back payment/inventory.

**Status: IMPLEMENTED; hardware success UNCLEAR — requires runtime verification.** Evidence: `receipts/*`, `receiptPrinter.ts`, `foodOrderPrinter.ts`, `posPrintQueue.ts`, printer docs.

## 24. Restaurant POS

Desktop Food Service POS offers category tiles, images, search, availability/sold-out, variants, required/optional modifier choice groups, min/max, included ingredient removal, paid extras, line/order notes, taxable/non-taxable custom food items, discounts, held orders, phone orders, pickup queue/history, checkout/split payments, kitchen status, receipts/tickets/reprints, and Secure Till. An open `FOOD_SERVICE` register session is required.

**Status: IMPLEMENTED.** Evidence: `FoodPosPage.tsx`, `foodmenu/FoodMenuService.java`, `sales/SaleService.java`, food menu/sale snapshot tests.

## 25. Restaurant Menu Management

Store-scoped categories and items support display order, image URL, description, price, active/available, inventory tracking, made-to-order, catalogue product association, variants, reusable choice groups/options, assignment min/max/required, included components, removable ingredients, extras and price deltas. Create/update/delete/availability APIs and a management page exist.

**Status: IMPLEMENTED.** Evidence: `foodmenu/*`, `FoodMenuPage.tsx`, migrations V80, V90–V91, V115, V117, V119.

## 26. Phone / Pickup Orders

Restaurant carts can be confirmed unpaid with customer name, optional phone, ASAP or scheduled local pickup, and notes. A food token is assigned and kitchen ticket queued. Active pickup orders are store-wide; history is current-session scoped. Another register can claim an order for payment. Operators can cancel and optionally rebuild; cancellation prints an updated kitchen ticket. Kitchen states progress pending → in progress → ready → completed; completed moves to history. Payment status is unpaid/partial/paid.

**Status: IMPLEMENTED.** Evidence: `FoodPosPage.tsx`, `SaleController.java`, `SaleService.java`, migrations V92, V131–V133.

## 27. Kitchen

The KITCHEN role and food permissions exist. The queue, status buttons, ticket printing, notes/modifiers, scheduled/asap filtering, job claim/acknowledgement, failure retry, and store filtering are implemented inside Restaurant POS/Pickup Orders. There is no standalone kitchen route or dedicated KDS screen.

**Status: PARTIALLY_IMPLEMENTED as a distinct product surface; operations IMPLEMENTED within Food POS.** Evidence: `FoodPosPage.tsx`, `KitchenPrintJobService.java`, `KitchenTicketService.java`, V79 and V133.

## 28. Dashboard

Owner dashboard supports store selection and refresh. Cards: today’s net sales/count/payments, net lottery/sold/wins, low+negative inventory alerts, open register sessions, refunds/count, and tax collected. Charts: payment mix by payment method and today’s lottery trend bucketed from activity timestamps. Cashiers get the Store Menu instead; blank states are handled.

**Status: IMPLEMENTED.** Evidence: `OwnerDashboardPage.tsx`, sales/lottery/inventory report APIs and register-session search.

## 29. Reports

| Report | Scope/filters | Output/drill-down | Status |
|---|---|---|---|
| Sales | Store/date; aggregate completed sales/refunds/tax/deposits/payment mix | On-screen aggregate; transaction data via sales search | IMPLEMENTED |
| Register | Store/register/session/date | Expected/count/variance and session summaries | IMPLEMENTED |
| Lottery sales | Store/date | Physical/manual sold, wins, net, trend | IMPLEMENTED |
| Lottery operational | Store/operator/date/status | Sales/payout/commission/settlement-oriented backend report | BACKEND_ONLY |
| Inventory | Store, view mode, search, stock condition/history | Current, history, low, negative, damaged, expired, adjustments | IMPLEMENTED |
| EOD | Store/date/status/report number/closed-by/page | Detail, print HTML, CSV, PDF | IMPLEMENTED |
| Category distribution | Embedded EOD | Sales/tax/deposit classification | IMPLEMENTED |
| Cashier | Embedded EOD | sales/refunds/cash/lottery/registers used | IMPLEMENTED |
| Exceptions | Embedded EOD | voids, overrides, variance, force/reopen conditions | IMPLEMENTED |

Dedicated non-EOD CSV/PDF export was not found. Evidence: `reports/*`, `BusinessDayService.java`, report pages.

## 30. EOD

Close creates an immutable numbered/revisioned report and sign-off. Sections include gross/net sales, discounts, refunds, tax, deposits, payments, cash expected/counted/variance, physical-register reconciliation, every session, lottery detail, inventory movement, category distribution, cashier performance, and exceptions. HTML print, CSV, and PDF use persisted snapshot data.

EOD combines all financially posted transactions and all sessions for the Store BusinessDay. Opening float is summed from the first session per physical register; terminal reconciled values represent final physical drawer state. Reopened register shifts are retained as session detail but not double-counted as separate physical tills. This is explicitly documented in service code and snapshot structures.

**Status: IMPLEMENTED.** Evidence: `eod/BusinessDayService.java`, `EndOfDayReport*.java`, `BusinessDayPages.tsx`, EOD tests.

## 31. Users / Employees

List/create/detail/edit/enable-disable/reactivate, role assignment, store assignment, employee number, 6-digit POS PIN, password/temporary credentials, owner/manager/cashier/kitchen roles, creator visibility, search/status/store/pagination, and admin password-reset/unlock are implemented. Backend prevents unauthorized manager creation and cross-tenant/store access. Self-profile-specific editing is not a distinct screen.

**Status: IMPLEMENTED.** Evidence: `security/UserAdministrationController.java`, `UserAdministrationService.java`, `UserPages.tsx`, migrations V2–V3, V9, V51–V53, V59, V79, V102.

## 32. Authentication

Merchant and platform login screens share JWT infrastructure but platform auth has platform-scoped checks. Refresh, logout, `/auth/me`, password policy, forgot/reset, first-login change, invitation activation, lockout/unlock, rate limiting, cached-session clearing, and merchant-host redirects exist. Store context is selected after login as needed; portal slug comes from hostname. POS secure-till is not a new JWT session.

**Status: IMPLEMENTED.** Evidence: `auth/*`, `security/RefreshToken*`, `AuthPage.tsx`, `PasswordResetPages.tsx`, `MerchantPortalContext.tsx`.

## 33. Pricing / Subscription

Platform UI/API manage plans, base price, onboarding fee, trial days, included stores/registers/users, overage/add-on prices, commercial capabilities, billing unit, pricing versions, scheduled effective time, cancellation, merchant assignment and subscription actions. Entitlement services enforce capabilities/quantities; register overage is per-store aware. Merchant billing shows current subscription, usage preview, invoices and downloadable PDFs.

Platform invoices can be generated, sent, voided, marked paid by manual payment recording, and exported as PDF. Billing settings exist. No payment gateway, automatic charge collection, tax remittance, or immediate proration exists; UI explicitly says plan changes apply next billing period.

**Status: IMPLEMENTED administration/manual billing; PARTIALLY_IMPLEMENTED automated billing.** Evidence: `platform/billing/*`, `PlatformBillingPages.tsx`, `MerchantBillingPage.tsx`, migrations V75, V77, V81–V87.

## 34. Platform / Super Admin

Dashboard, merchants/onboarding/detail/lifecycle, geography validation, capability preview/update, owners, email delivery, user unlock/reset, plans/versions, subscriptions, invoices/payments/PDF/email, platform admins/invitations/status, audit event search, settings and test email are implemented. Search/filter/pagination exists on high-volume merchant/invoice/audit lists.

**Status: IMPLEMENTED.** Evidence: `platform/admin/*`, `platform/billing/*`, `PlatformPages.tsx`, `PlatformBillingPages.tsx`, `PlatformAdminsPage.tsx`.

## 35. Imports / Exports

- Initial inventory XLSX template/download, upload, row validation, preview, confirm/post and audit: **IMPLEMENTED**.
- Inventory update/count-like XLSX upload, validation, preview, confirm batch: **IMPLEMENTED**.
- EOD CSV/PDF/print HTML: **IMPLEMENTED**.
- Invoice PDF and receipt/kitchen printable documents: **IMPLEMENTED**.
- General product/customer/supplier CSV import/export: **not found**.

Evidence: `initialinventory/*`, `inventoryimport/*`, `EndOfDayReportController.java`, `PlatformInvoicePdfService.java`.

## 36. Search / Filter / Pagination

Server paging/filtering is used for merchants, stores, registers, users, products, suppliers, sales, returns/refunds, cash movements, sessions, BusinessDays, EOD reports, invoices and audit. Catalogue/tax reference screens support search/status filters. POS search is debounced and barcode-aware. Dashboard/report scopes remember selected store locally in several pages. Inventory offers distinct condition routes. Not every small reference screen persists filters.

**Status: IMPLEMENTED.** Evidence: controllers using `PageResponse`, page components, `client.ts` query builders.

## 37. Error & Empty States

Specific handled states include portal missing/inactive, unauthorized, no assigned store, no menu, sold out, unknown barcode, inventory warning, register/device/user already active, wrong register type, BusinessDay absent/closed/previous open, restore/settlement required, PIN locked/invalid, missing reconciliation/count, variance explanation, stale version, print failure/retry, email failure/retry and no activity. Global exceptions include correlation IDs and field errors.

Some screens still fall back to generic API messages; hardware/email/payment-provider behavior needs environment verification. **Status: IMPLEMENTED with minor inconsistency.** Evidence: `GlobalExceptionHandler.java`, `client.ts`, page Alerts, error-handling tests.

## 38. Capability / Permission Gates

`RETAIL`, `FOOD_SERVICE`, and `LOTTERY` are effective only when subscription entitlement and Store capability both allow them. Register type additionally gates the active POS route. Feature definitions also support deployment-, tenant/store-, and register-level resolution via `/features`, independent of commercial capabilities. Navigation hides by roles/permissions/capability; backend repeats authority checks. POS is desktop-only by policy; mobile management exposes an allowlisted subset.

**Status: IMPLEMENTED / CONFIGURED_OR_HIDDEN.** Evidence: `features/*`, `StoreCapabilityService.java`, `RegisterCapabilityService.java`, `App.tsx`, mobile access policy tests.

## 39. Backend / Domain Features Not Yet Exposed in UI

- Lottery operators, payout policies, approvals/referrals, commission rules, settlement calculate/approve/post/reopen.
- Device administration and explicit user-register assignment management.
- Sale line price override and tax-override approval APIs/permissions are richer than visible retail actions.
- Completed-sale void permission/domain behavior lacks an obvious UI action.
- Inventory transfer transaction vocabulary and permissions lack a workflow.
- Support-access request/approval permissions and configuration lack a visible platform workflow.
- Raw audit API beyond the platform audit screen includes more entity/action detail.

## 40. Frontend Features With Missing/Incomplete Backend Support

No clearly mocked major screen was found. The main incomplete surfaces are operational integration boundaries: printer settings depend on browser/QZ runtime; debit/credit/gift/store-credit choices merely record manual tenders; billing “payments” are manual records. Public site is intentionally a Coming Soon page, not a backend-backed marketing CMS.

## 41. Partial Features

- Dedicated Kitchen/KDS experience: queue operations exist inside Food POS only.
- Automated subscription billing: invoice lifecycle exists, processor/autopay and immediate proration do not.
- Product bundle/digital/gift-card/store-credit types: enum/catalog/payment vocabulary exists without complete specialized lifecycle UIs.
- Receiving and inter-store transfer: transaction/permission foundations exist, dedicated workflows absent.
- Advanced promotion campaign/scheduling/coupon workflow: saved/shared/multi-buy engine exists, broader merchandising UI absent.

## 42. Legacy / Unused Features

- `/sales/drafts` is retained but deliberately returns `DRAFT_SALES_DISABLED`; local cart + checkout supersedes incremental server drafts.
- `DRAFT` Sale status remains for compatibility/domain transitions but normal UI creation does not use the disabled endpoint.
- Compatibility role names (`OWNER` vs `TENANT_OWNER`, `MANAGER` vs `STORE_MANAGER`) remain active, not safe to remove but reflect schema evolution.
- StockCount `IN_REVIEW` remains in the enum although migrations clean/simplify old records and current UI favors draft/saved/posted behavior.
- Full lottery settlement code is active backend code, not proven dead, but is unreachable from normal merchant navigation.

## 43. TODO / Planned Features

The product-relevant scan found one explicit limitation: immediate plan proration is not implemented. The public root experience is intentionally Coming Soon. No other production TODO/FIXME/stub markers described significant product features. Generic input `placeholder` strings and test mocks were excluded.

## 44. UX-Relevant Business Rules

1. One current session per physical Register and one active session per user; device uniqueness/enforcement is configurable.
2. Register type must match the POS mode and an effectively enabled Store capability.
3. An open Store BusinessDay is required before a RegisterSession can open.
4. Business date is derived using Store timezone, not browser timezone.
5. A previous open BusinessDay blocks a new day; today’s closed day must be reopened, not duplicated.
6. Subsequent shifts on a register inherit physical cash; restoration or excess settlement blocks reopen until resolved.
7. Every closing session requires expected/count reconciliation; non-zero variance requires explanation.
8. EOD cannot normally close with open sessions, missing counts/reconciliation, unfinished sales, or applicable lottery blockers.
9. Force close/reopen require stronger permission, reason, version/idempotency controls, and leave audit/report evidence.
10. POS cart pricing is provisional until authoritative checkout; payment cannot complete an underpaid sale.
11. Sale completion and refund posting are idempotent.
12. Inventory is variant + store scoped; negative stock depends on Store configuration.
13. Deposits and lottery are excluded from ordinary discount/tax behavior according to backend classification.
14. Printing is downstream of financial posting; print failure never reverses a completed sale.
15. Phone orders are unpaid operational orders until claimed/paid; held sales are a different state/workflow.

## 45. Complete Screen / Route Inventory

| Route(s) | Screen | Audience/gate | Main feature | Status |
|---|---|---|---|---|
| `/login`, `/platform/login` | Login | Public portal-specific | Authentication | IMPLEMENTED |
| `/forgot-password`, `/reset-password`, `/first-login/change-password`, `/activate-platform-admin` | Credential flows | Public/token | Reset/activation | IMPLEMENTED |
| `/` | Owner dashboard/home redirect | Non-cashier; role redirect | Operating snapshot | IMPLEMENTED |
| `/store-menu`, `/select-store` | Store operation launcher/selection | Store user | Store context/actions | IMPLEMENTED |
| `/pos` | Retail POS | RETAIL + POS_ACCESS + desktop | Retail checkout | IMPLEMENTED |
| `/pos/food` | Restaurant/Kitchen POS | FOOD_SERVICE + FOOD_POS_ACCESS + desktop | Food/order/kitchen | IMPLEMENTED |
| `/pos/held-sales` | Held sales | Register user | Resume/cancel held sales | IMPLEMENTED |
| `/food-menu`, `/discounts` | Food menu/discount management | Permission/capability | Catalogue promotions | IMPLEMENTED |
| `/stores`, `/stores/new`, `/stores/:id` | Stores | STORE permissions | Store CRUD/status | IMPLEMENTED |
| `/registers`, `/registers/new`, `/registers/:id` | Registers | REGISTER permissions | Register CRUD/status | IMPLEMENTED |
| `/register/open`, `/current`, `/close`, `/cash-movements`, `/history` | Register operations | Session/cash permissions | Shift and cash lifecycle | IMPLEMENTED |
| `/returns`, `/returns/new`, `/returns/:id` | Returns | RETURN permissions | Return/refund initiation | IMPLEMENTED |
| `/users`, `/users/new`, `/users/:id`, `/users/:id/edit`, `/users/:id/store-assignments`, `/roles` | People/access | User/role permissions | Employee administration | IMPLEMENTED |
| `/products`, `/products/new`, `/products/:id` | Products | Product permissions | Catalogue/variants/barcodes | IMPLEMENTED |
| `/categories`, `/brands`, `/settings/units` | References | Product permissions | Catalogue references | IMPLEMENTED |
| `/suppliers`, `/suppliers/new`, `/suppliers/:id` | Suppliers | Product/supplier permissions | Supplier/product links | IMPLEMENTED |
| `/inventory`, `/inventory/history`, `/low-stock`, `/negative-stock`, `/adjustment-report`, `/damaged`, `/expired` | Inventory/report views | Inventory permissions | Stock visibility | IMPLEMENTED |
| `/inventory/initial-setup` | Initial inventory import | INVENTORY_MANAGE + PRODUCT_CREATE | XLSX workflow | IMPLEMENTED |
| `/inventory/adjustments`, `/adjustments/new` | Adjustments | Inventory permission | Stock corrections | IMPLEMENTED |
| `/inventory/counts`, `/counts/new`, `/counts/:id` | Stock counts | Count permissions | Count/post | IMPLEMENTED |
| `/reports/sales`, `/reports/registers`, `/reports/lottery` | Reports | REPORT_VIEW; LOTTERY gate | Operational reports | IMPLEMENTED |
| `/business-day`, `/close`, `/history` | Business Day | Business-day permissions | Day lifecycle | IMPLEMENTED |
| `/end-of-day-reports`, `/:id` | EOD reports | EOD view/export/print | Snapshot reports | IMPLEMENTED |
| `/settings/features`, `/pos-quick-keys` | Feature/quick-key setup | Feature/product permissions | POS configuration | IMPLEMENTED |
| `/settings/hardware/printers`, `/scanner-test` | Hardware setup/test | Register user | Local hardware | IMPLEMENTED/runtime dependent |
| `/tax/*` (rules, categories, groups, group-components, assignments, types, components, rates, countries, administrative-areas, jurisdictions) | Tax admin | TAX_VIEW/MANAGE | Tax configuration | IMPLEMENTED |
| `/settings/taxes/test` | Tax simulator | Dev + tax role | Calculation test | CONFIGURED_OR_HIDDEN |
| `/billing` | Merchant billing | Owner | Subscription/invoices | IMPLEMENTED |
| `/platform` | Platform dashboard | Platform role | SaaS overview | IMPLEMENTED |
| `/platform/merchants`, `/new`, `/:tenantId` | Merchant admin | Platform permissions | Onboarding/lifecycle | IMPLEMENTED |
| `/platform/audit`, `/settings`, `/admins` | Platform operations | Platform permission | Audit/settings/admins | IMPLEMENTED |
| `/platform/billing`, `/plans`, `/subscriptions`, `/invoices`, `/settings` | Platform billing | Billing permissions | Plans/subscriptions/invoices | IMPLEMENTED |
| `/unauthorized` | Access denied | Authenticated | Actionable denial | IMPLEMENTED |
| `*` | Redirect home | Any | Fallback | IMPLEMENTED |

Routes such as inventory condition views, tax subpages, register close, and business-day close are not all top-level navigation items but are reachable through workflow/deep links.

## 46. Complete API Feature Inventory

Product-oriented groups (minor reference CRUD is consolidated):

- **Auth/portal:** `/auth/register|login|refresh|logout|me|password-*`, `/portal/{slug}` — sessions, reset and host resolution.
- **Platform:** `/platform/auth/login`, `/platform/dashboard|settings|tenants|users|admins|audit-events` — onboarding, lifecycle, administrators and support operations.
- **Billing:** `/platform/billing/plans|pricing-versions|subscriptions|invoices|settings`; `/billing/subscription|preview|invoices` — pricing, entitlements, manual invoicing/payment/PDF.
- **Stores/registers/devices:** CRUD/status/search, capability effective/preview/update, device association — physical operating configuration.
- **Register sessions:** `/open|current|availability|restore-till`, `/{id}/secure|unlock|start-closing|cancel-closing|settlement-preview|close|force-close|transfer|override|release`, search — full shift/till lifecycle.
- **Cash:** `/cash-movements` search/create and `/{id}/reversal` — paid-in/out and payout ledger activity.
- **Business day/EOD:** open/current/state/latest/search/detail/validation/preview/reminder/start-close/close/force/reopen; report detail/print/CSV/PDF.
- **Products:** CRUD/status/delete, barcode POS lookup/ownership/alias management, Store availability; quick-key CRUD/configuration.
- **Catalogue/suppliers:** category/brand/unit and supplier/product-supplier CRUD/status/search.
- **Inventory:** balance/history/posting, stock adjustments/counts, initial-inventory and update-workbook validate/preview/confirm, inventory reports.
- **Sales:** checkout/search/get, line mutation/override/discount, hold/resume/cancel/recalculate, payment/complete, phone confirm/claim/cancel/history/pickup, kitchen status, force close. `/drafts` is disabled.
- **Returns/refunds:** return create/search/detail and idempotent refund posting/search/detail.
- **Receipts/kitchen:** receipt lookup/sale receipt/reprint; ticket/reprint; kitchen job list/claim/print-now/acknowledge.
- **Food menu:** store categories/items/availability/choice groups and add-to-sale; food-service print configuration.
- **Discounts:** definition CRUD/status and active Store resolution.
- **Lottery:** simple POS activity; operator, payout policy, sale, payout/approval/reversal, commission rule, settlement APIs; lottery reports.
- **Tax:** geography/reference CRUD/status, groups/components/categories/assignments/rules, evaluate and calculate.
- **Features:** definitions, resolution, deployment/store/register overrides.
- **Reports/audit/email:** sales/register/inventory/lottery aggregates; audit search; email status/test/retry/history.

Evidence: all `*Controller.java` files and `frontend/src/api/client.ts`.

## 47. Feature Status Matrix

| Feature | Area | Frontend | Backend | DB | Tests | Status | Notes |
|---|---|---:|---:|---:|---:|---|---|
| Merchant onboarding/lifecycle | Platform | Yes | Yes | Yes | Yes | IMPLEMENTED | Includes empty deletion/status history |
| Store management | Setup | Yes | Yes | Yes | Yes | IMPLEMENTED | Geography/capabilities/float |
| Register management | Register | Yes | Yes | Yes | Yes | IMPLEMENTED | Retail/Food types |
| Device enforcement | Register | Partial | Yes | Yes | Yes | CONFIGURED_OR_HIDDEN | Defaults off |
| Session lifecycle | Register | Yes | Yes | Yes | Yes | IMPLEMENTED | Open/transfer/override/release/close |
| Secure Till | POS | Yes | Yes | Yes | Yes | IMPLEMENTED | PIN/password lock screen |
| Physical till state | Cash | Yes | Yes | Yes | Yes | IMPLEMENTED | No standalone Till entity |
| Cash movements | Cash | Yes | Yes | Yes | Yes | IMPLEMENTED | Approval/reversal |
| Business Day | EOD | Yes | Yes | Yes | Yes | IMPLEMENTED | Store/timezone scoped |
| EOD reports | Reports | Yes | Yes | Yes | Yes | IMPLEMENTED | Immutable revisions; PDF/CSV/print |
| Retail POS | POS | Yes | Yes | Yes | Yes | IMPLEMENTED | Authoritative checkout |
| Restaurant POS | Food | Yes | Yes | Yes | Yes | IMPLEMENTED | Made-to-order |
| Dedicated KDS | Kitchen | Partial | Yes | Yes | Yes | PARTIALLY_IMPLEMENTED | Embedded in Food POS |
| Phone/pickup orders | Food | Yes | Yes | Yes | Yes | IMPLEMENTED | Scheduled/asap, cross-register claim |
| Kitchen print queue | Printing | Yes | Yes | Yes | Yes | IMPLEMENTED | Claim/ack/retry/schedule |
| Products/variants | Catalogue | Yes | Yes | Yes | Yes | IMPLEMENTED | Merchant/store availability |
| Barcode aliases | Catalogue | Yes | Yes | Yes | Yes | IMPLEMENTED | Ownership/transfer |
| Categories/brands/units | Catalogue | Yes | Yes | Yes | Yes | IMPLEMENTED | Status rather than delete |
| Suppliers | Catalogue | Yes | Yes | Yes | Yes | IMPLEMENTED | Product links |
| Inventory balances/ledger | Inventory | Yes | Yes | Yes | Yes | IMPLEMENTED | Variant + Store |
| Initial inventory XLSX | Inventory | Yes | Yes | Yes | Yes | IMPLEMENTED | Validate/preview/confirm |
| Inventory update XLSX | Inventory | Yes | Yes | Yes | Yes | IMPLEMENTED | Batch workflow |
| Transfers/receiving | Inventory | No | Partial | Partial | Partial | PARTIALLY_IMPLEMENTED | Vocabulary only/no workflow |
| Simple lottery POS | Lottery | Yes | Yes | Yes | Yes | IMPLEMENTED | Sold/win/product scan |
| Lottery settlement domain | Lottery | No | Yes | Yes | Yes | BACKEND_ONLY | Operators/policies/commission |
| Tax engine/admin | Tax | Yes | Yes | Yes | Yes | IMPLEMENTED | Canadian/vape rules |
| Discounts/multi-buy | Pricing | Yes | Yes | Yes | Yes | IMPLEMENTED | Saved/shared definitions |
| Container deposits/payout | POS | Yes | Yes | Yes | Yes | IMPLEMENTED | Snapshot/report/receipt |
| Split/partial payments | Payments | Yes | Yes | Yes | Yes | IMPLEMENTED | Manual tenders |
| External payment processing | Payments | No | No | No | No | PLANNED_NOT_IMPLEMENTED | Not found |
| Held sales | Sales | Yes | Yes | Yes | Yes | IMPLEMENTED | Server persisted |
| Returns/refunds | Sales | Yes | Yes | Yes | Yes | IMPLEMENTED | Idempotent refund/inventory restore |
| Completed sale void UI | Sales | No | Partial | Yes | Yes | BACKEND_ONLY | Permission/domain evidence |
| Receipts/reprints | Printing | Yes | Yes | Yes | Yes | IMPLEMENTED | Human-readable number |
| QZ/browser printing | Printing | Yes | Partial | N/A | Yes | UNCLEAR | Runtime hardware verification needed |
| Dashboard | Analytics | Yes | Yes | Yes | Yes | IMPLEMENTED | Sales/lottery/inventory/registers |
| Operational reports | Reports | Yes | Yes | Yes | Yes | IMPLEMENTED | Dedicated exports mostly EOD only |
| Users/roles/assignments | Security | Yes | Yes | Yes | Yes | IMPLEMENTED | Granular permissions |
| Auth/reset/lockout | Security | Yes | Yes | Yes | Yes | IMPLEMENTED | JWT + refresh |
| Feature/capability gates | Entitlement | Yes | Yes | Yes | Yes | IMPLEMENTED | Commercial + deployment flags |
| Pricing plans/versions | Billing | Yes | Yes | Yes | Yes | IMPLEMENTED | Scheduled versions |
| Invoice/manual payment | Billing | Yes | Yes | Yes | Yes | IMPLEMENTED | PDF/email/void |
| Automated billing/proration | Billing | Partial | Partial | Partial | Yes | PARTIALLY_IMPLEMENTED | No gateway; no immediate proration |
| Email delivery/retry | Notifications | Yes | Yes | Yes | Yes | IMPLEMENTED | Console or Resend |
| Audit/idempotency | Platform | Yes | Yes | Yes | Yes | IMPLEMENTED | Product-relevant actions |
| Public marketing site | Public | Yes | No | N/A | Partial | PLANNED_NOT_IMPLEMENTED | Coming Soon only |

## 48. Potential Feature Gaps / Inconsistencies

| Area | Observed behavior / evidence | Why inconsistent or UX-relevant |
|---|---|---|
| Sales drafts | `SaleController.createDraft` always returns `DRAFT_SALES_DISABLED`, while draft status/endpoints remain | API vocabulary can mislead integrators; product docs should describe local cart + checkout |
| Kitchen | KITCHEN role exists, but no `/kitchen`; queue is in `FoodPosPage.tsx` | Role-based journey does not map to a dedicated screen |
| Permissions vs navigation | Several pages are route-declared with coarse shell role checks; backend uses granular permission checks | Deep links may render before producing unauthorized errors; design should account for denial states |
| Device architecture | Device model and enforcement exist, default false; open API explicitly says no MAC verification | “Register bound to device” must not be documented as universally enforced |
| Payments | UI names debit/credit/gift/store credit but backend only records tenders | Could be mistaken for integrated authorization/balance management |
| Billing | Subscriptions/invoices are sophisticated, but payments are manually recorded and immediate proration absent | Avoid implying automated collection |
| Inventory | Transfer/receive permissions/types exist without screens/controllers | Do not list as usable inventory workflows |
| Lottery | Full legacy/richer settlement domain is live backend, while UI uses simplified sold/win model | Product/design docs need to distinguish reachable simple flow from backend-only administration |
| Register/EOD cash | Session opening cash exists for every shift, but EOD physical cash uses first/terminal per register | Any external report summing every session opening would double count; built-in EOD avoids it |
| Role names | Owner/Tenant Owner and Manager/Store Manager coexist | UI/security documentation should group aliases but preserve backend names |
| Stock count status | `IN_REVIEW` remains while migrations simplified old review records | Some status vocabulary may be stale relative to current UX |
| Mobile | POS routes are explicitly desktop guarded; management navigation is allowlisted on mobile | Responsive design must not imply mobile POS support |

## 49. Files Inspected

Representative significant files (vendor/build output excluded):

- **Routes/navigation/session:** `frontend/src/app/App.tsx`, `session.tsx`, `MerchantPortalContext.tsx`, `MobileAccessGuards.tsx`, `mobileAccessPolicy.ts`, `deviceEnvironment.ts`.
- **Frontend pages:** all production files under `frontend/src/features/{auth,billing,catalogue,dashboard,discounts,eod,foodmenu,inventory,platform,pos,products,registers,registersessions,reports,returns,settings,stores,suppliers,tax,users}`.
- **Frontend clients/hardware:** `frontend/src/api/client.ts`, `types.ts`, `features/hardware/barcodeScanner.ts`, `features/pos/{receiptPrinter,foodOrderPrinter,posPrintQueue,multiBuyPricing}.ts`.
- **Controllers:** all classes matched by `@RestController`, including Auth, Platform Administration/Billing, Store, Register, RegisterSession, CashMovement, Sale, Return, Refund, Product, Inventory, BusinessDay/EOD, Reports, Tax, Lottery, FoodMenu, Receipt/Kitchen, Features, Users/Roles and Audit.
- **Services:** corresponding services in `auth`, `platform`, `store`, `register`, `registersession`, `cash`, `sales`, `returns`, `refunds`, `product`, `inventory`, `initialinventory`, `inventoryimport`, `eod`, `reports`, `lottery`, `tax`, `foodmenu`, `receipts`, `email`, `security`, and `features`.
- **Entities/repositories:** all `@Entity` matches, especially Register, RegisterSession, RegisterBusinessDayCashState, CashLedgerEntry, BusinessDay/EOD summaries, Sale/Item/Payment, Refund/Return, Product/Variant/Barcode/StoreProduct, Inventory balances/transactions/counts, food menu graph, lottery graph, User/Role/assignments, audit/idempotency, plans/subscriptions/invoices.
- **Migrations:** filenames and feature evolution across V1–V152; detailed inspection focused on V23–V48, V50–V65, V69–V100, V102–V152.
- **Security/configuration:** `PermissionCode.java`, `RoleName.java`, `AuthorizationService.java`, `SecurityConfig.java`, `application.yml`, environment profiles.
- **Tests:** backend and frontend test inventories; focused evidence from auth, register/session/till, cash concurrency, BusinessDay/EOD, sale completion, discounts, inventory concurrency/import, product/barcodes, food menu/orders, lottery, refunds, billing, tax and authorization tests.

## Analysis Confidence and Limits

Confidence is high for code structure, route/API availability, permission gates, state machines, financial calculations, schema history and tested invariants. Runtime verification was not performed against a live database, browser, printer/QZ Tray, Resend account, or production deployment. Consequently external-device success, actual email deliverability, CSS behavior on every physical 1024×768 device, and production configuration flags remain `UNCLEAR — requires runtime verification` where noted.
