# Merchtyl Feature Implementation Matrix

Status is based on backend, frontend and migration evidence at revision `fbebd405a5be4d0effa6ef6e27f4a8b2771060ad`.

| Domain | Feature | Backend | Frontend | Database | Status | Notes |
|---|---|---|---|---|---|---|
| Platform | Super Admin authentication | Yes | Yes | Yes | Complete | Separate PLATFORM JWT scope and platform login UI |
| Platform | Merchant onboarding | Yes | Yes | Yes | Complete | Tenant/profile/owner invitation/onboarding stages |
| Platform | Merchant activation and lifecycle | Yes | Yes | Yes | Complete | Pending, active, suspended, closed, rejected flows |
| Platform | Pricing plans | Yes | Yes | Yes | Complete | Versioned plans, price/capability snapshots and scheduling |
| Platform | Trials and included quantities | Yes | Yes | Yes | Complete | Trial days plus included stores/registers/users |
| Platform | Add-on store/register/user pricing | Yes | Yes | Yes | Complete | Plan and per-subscription overrides; register overage support |
| Platform | Subscription management | Yes | Yes | Yes | Complete | Trial/active/past-due/paused/cancelled/expired/suspended statuses |
| Platform | Invoice generation and email | Yes | Yes | Yes | Complete | PDF, Resend delivery, invoice payments and status lifecycle |
| Platform | External payment gateway | Partial | No | Partial | Partial | Provider/customer/subscription IDs exist; no direct gateway adapter found |
| Tenancy | Tenant user isolation | Partial | N/A | Partial | Partial | Many scoped queries exist, but some generic read paths are unscoped; no RLS |
| Tenancy | Store access assignments | Yes | Yes | Yes | Complete | Owner-wide access; manager/cashier/kitchen assignments |
| Auth | Tenant login and refresh rotation | Yes | Yes | Yes | Complete | Account lockout, refresh rotation and logout revocation |
| Auth | Merchant portal realm binding | Yes | Yes | Yes | Complete | Host/slug frontend resolution and optional header match |
| Auth | Role and permission authorization | Yes | Yes | Yes | Complete | Backend permission authorities; frontend visibility guards |
| Auth | Password reset and first-login change | Yes | Yes | Yes | Complete | One-time hashed tokens and temporary credential expiry |
| Users | User create/update/activation | Yes | Yes | Yes | Complete | Includes role and Store assignments |
| Users | Multiple Store access | Yes | Yes | Yes | Complete | Tenant-scoped user-store assignments |
| Users | Direct register assignment | Yes | Partial | Yes | Partial | Entity/repository exist; operational use is less consistent than Store assignments |
| Stores | Store CRUD and geography | Yes | Yes | Yes | Complete | Currency, locale, timezone and tax region support |
| Stores | Capabilities | Yes | Yes | Yes | Complete | RETAIL, FOOD_SERVICE, LOTTERY |
| Stores | Hierarchical feature flags | Yes | Yes | Yes | Complete | Deployment, tenant, Store and register overrides |
| Registers | Retail/Food register types | Yes | Yes | Yes | Complete | Type constrained and capability-gated |
| Registers | Register session open/close | Yes | Yes | Yes | Complete | OPEN, CLOSING, CLOSED, FORCE_CLOSED |
| Registers | Cancel closing | Yes | Yes | Yes | Complete | CLOSING → OPEN transition |
| Registers | Operator transfer/override/release | Yes | Yes | Yes | Complete | Audit history and token revocation on takeover |
| Registers | Till secure/PIN unlock | Yes | Yes | Yes | Complete | Failed-attempt lockout fields and UI |
| Business Day | Store-local date opening | Yes | Yes | Yes | Complete | Store timezone determines current date |
| Business Day | Previous-day validation | Yes | Yes | Yes | Complete | Configurable blocking with management override |
| Business Day | Close/force close/reopen | Yes | Yes | Yes | Complete | Reopen only today's closed day; revisioned reports |
| Retail | Product creation and variants | Yes | Yes | Yes | Complete | Tenant catalog, variants, capabilities and Store availability |
| Retail | Barcode scan/lookup | Yes | Yes | Yes | Complete | Multiple variant barcodes and hardware scan listener |
| Retail | Tax categories and calculation | Yes | Yes | Yes | Complete | Canadian geography/rules plus sale tax snapshots |
| Retail | POS cart/checkout | Yes | Yes | Yes | Complete | Atomic checkout creates PENDING_PAYMENT Sale |
| Retail | Legacy draft-sale creation | Yes | Disabled | Yes | Unused | Controller deliberately returns DRAFT_SALES_DISABLED |
| Retail | Held/resumed sales | Yes | Yes | Yes | Complete | Explicit HELD lifecycle |
| Payments | Cash/debit/credit | Yes | Yes | Yes | Complete | Also gift card, Store credit and other methods |
| Payments | Partial/split payment | Yes | Yes | Yes | Complete | Multiple Payment rows; amount cannot exceed remaining balance |
| Payments | Cash tender/change/rounding | Yes | Yes | Yes | Complete | Cash snapshots include tender, change and rounding adjustment |
| Payments | Payment allocation to line/domain | No | No | No | Missing | Mixed tender cannot precisely allocate cash to lottery vs merchandise |
| Payments | Core sale payment reversal | Partial | Partial | Partial | Partial | Adjustments/refunds exist; no first-class immutable payment reversal aggregate |
| Inventory | Store inventory balance | Yes | Yes | Yes | Complete | Product + optional variant balance by Store |
| Inventory | Automatic sale decrement | Yes | Yes | Yes | Complete | Posts SALE delta only for tracked catalog items on completion |
| Inventory | Manual adjustments | Yes | Yes | Yes | Complete | Increase/decrease/damaged/expired flows |
| Inventory | Stock counts | Yes | Yes | Yes | Complete | Draft/review/post with idempotent posting and balance versions |
| Inventory | Initial/update imports | Yes | Yes | Yes | Complete | Workbook validation, row audit and confirmation |
| Inventory | Negative stock policy | Yes | Yes | Yes | Partial | Manual negatives can be blocked; sales intentionally bypass the block |
| Restaurant | Food-enabled Store/register | Yes | Yes | Yes | Complete | Capability + subscription entitlement |
| Restaurant | Food menu categories/items | Yes | Yes | Yes | Complete | Store-scoped menu linked to catalog products |
| Restaurant | Variants/modifiers/components | Yes | Yes | Yes | Complete | Reusable choice groups and sale-item snapshots |
| Restaurant | Restaurant order aggregate | Partial | Yes | Partial | Partial | Implemented through Sale; no distinct RestaurantOrder entity |
| Kitchen | Kitchen status workflow | Yes | Yes | Yes | Complete | PENDING, IN_PROGRESS, READY, COMPLETED, CANCELLED |
| Kitchen | Customer receipt | Yes | Yes | Yes | Complete | Persisted receipt and lookup/reprint |
| Kitchen | Kitchen ticket/token | Yes | Yes | Yes | Complete | Food token sequence plus kitchen ticket document |
| Kitchen | Scheduled/ASAP printing | Yes | Yes | Yes | Complete | Persistent jobs with claim/acknowledge and QZ Tray client |
| Lottery | Store capability and feature gate | Yes | Yes | Yes | Complete | Plan, Store, deployment and permission dimensions |
| Lottery | Sales and cancellations | Yes | Partial | Yes | Partial | Backend rich domain; frontend emphasizes POS lines and reports |
| Lottery | Wins/payouts/approvals/reversals | Yes | Partial | Yes | Partial | Backend complete; dedicated operations UI coverage is limited |
| Lottery | Commission rules and settlements | Yes | Partial | Yes | Partial | Full backend lifecycle and reports; limited management UI |
| Reporting | Sales reports | Yes | Yes | Yes | Complete | Store/date filters and frontend pages |
| Reporting | Register reports | Yes | Yes | Yes | Complete | Register/session summaries |
| Reporting | Lottery reports | Yes | Yes | Yes | Complete | Multiple lottery report services and page |
| EOD | Register/cash/payment aggregation | Yes | Yes | Yes | Complete | Opening/count/variance/tender/cash movement summaries |
| EOD | Tax/category/refund aggregation | Yes | Yes | Yes | Complete | Persisted summary rows and exports |
| EOD | Inventory/lottery/cashier exceptions | Yes | Yes | Yes | Complete | Rich summary and exception taxonomy |
| EOD | HTML/CSV/PDF output | Yes | Yes | Yes | Complete | Print, export and persisted revisions |
| Frontend | Platform portal | N/A | Yes | N/A | Complete | Dedicated routes for tenants, admins, billing and audit |
| Frontend | Merchant management portal | N/A | Yes | N/A | Complete | Stores, users, products, inventory, reports and settings |
| Frontend | Retail POS | N/A | Yes | N/A | Complete | Barcode, quick keys, cart, payment and printing |
| Frontend | Food POS | N/A | Yes | N/A | Complete | Menu, modifiers, pickup orders and kitchen printing |
| Deployment | Vercel frontend | N/A | Yes | N/A | Complete | SPA rewrite and production API URL |
| Deployment | Railway backend | Yes | N/A | N/A | Complete | Java 21 JAR, health probe and environment contract |
| Deployment | Neon PostgreSQL | Yes | N/A | Yes | Complete | Repository documents external Neon database |
| Deployment | Resend email | Yes | N/A | Yes | Complete | Production provider plus delivery audit/retry |

