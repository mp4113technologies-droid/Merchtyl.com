export type UserRole = 'PLATFORM_SUPER_ADMIN' | 'PLATFORM_SUPPORT_ADMIN' | 'TENANT_OWNER' | 'STORE_MANAGER' | 'OWNER' | 'MANAGER' | 'CASHIER' | 'KITCHEN';

export type BillingPage<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type PricingPlan = {
  id: string; code: string; name: string; description: string | null; status: 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
  billingInterval: 'MONTHLY' | 'YEARLY'; basePrice: number; oneTimeOnboardingFee: number; currency: string; trialDays: number;
  includedStores: number | null; includedRegisters: number | null; includedUsers: number | null;
  additionalStorePrice: number | null; additionalRegisterPrice: number | null; additionalUserPrice: number | null;
  capabilityPrices: CapabilityPrice[];
  taxBehavior: 'EXCLUSIVE' | 'INCLUSIVE' | 'EXEMPT'; effectiveFrom: string; effectiveTo: string | null;
  activeMerchants: number; createdAt: string; updatedAt: string; version: number;
};
export type CommercialCapability = 'RETAIL_POS' | 'INVENTORY' | 'REGISTER_MANAGEMENT' | 'RETURNS' | 'REPORTING' | 'ADVANCED_REPORTING' | 'EMPLOYEE_MANAGEMENT' | 'FOOD_SERVICE' | 'LOTTERY';
export type CapabilityInclusionType = 'INCLUDED' | 'PAID_ADD_ON' | 'NOT_AVAILABLE';
export type CapabilityBillingUnit = 'PER_MERCHANT' | 'PER_STORE' | 'PER_USER' | 'PER_REGISTER';
export type CapabilityPrice = { capability: CommercialCapability; inclusionType: CapabilityInclusionType; billingUnit: CapabilityBillingUnit | null; monthlyPricePerStore: number | null };
export type CapabilityCharge = { capability: CommercialCapability; description: string; billingUnit: CapabilityBillingUnit; storeCount: number; monthlyPricePerStore: number; monthlyTotal: number };
export type CapabilityDefinition = { capability: CommercialCapability; displayName: string; supportedBillingUnits: CapabilityBillingUnit[] };
export type PricingVersion = { id: string; pricingPlanId: string; versionNumber: number; status: 'ACTIVE' | 'SCHEDULED' | 'SUPERSEDED' | 'CANCELLED'; effectiveFrom: string; effectiveTo: string | null; subscriberPolicy: 'NEW_SUBSCRIPTIONS_ONLY' | 'APPLY_NEXT_BILLING_CYCLE'; pricing: Omit<PricingPlan, 'id' | 'activeMerchants' | 'createdAt' | 'updatedAt' | 'version'>; usedForBilling: boolean; createdAt: string; version: number };
export type PricingPreview = { currency: string; baseSubscription: number; storeCount: number; includedStores: number; additionalStoreCount: number; additionalStoreMonthlyPrice: number; includedRegistersPerStore:number|null; additionalRegisterMonthlyPrice:number|null; activeRegisterCount:number; additionalRegisterCount:number; registerUsage:Array<{storeId:string;storeName:string;activeRegisters:number;includedRegisters:number;additionalRegisters:number}>; additionalRegisterMonthlyTotal:number; capabilityCharges: CapabilityCharge[]; estimatedMonthlySubscription: number };
export type BillingSubscription = {
  id: string; tenantId: string; merchantName: string; pricingPlanId: string; planCode: string; planName: string;
  status: 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'PAUSED' | 'CANCELLED' | 'EXPIRED'; billingInterval: 'MONTHLY' | 'YEARLY';
  subscriptionStartDate: string; currentPeriodStart: string; currentPeriodEnd: string; nextBillingDate: string;
  trialEndDate: string | null; cancelAtPeriodEnd: boolean; cancelledAt: string | null; cancellationReason: string | null;
  standardBasePrice: number; merchantBasePrice: number; currency: string; includedStoresSnapshot: number | null;
  additionalStorePriceSnapshot: number | null; onboardingFeeSnapshot: number | null; onboardingFeeInvoicedAt: string | null;
  currentBillableStores: number; additionalBillableStores: number; includedRegistersPerStoreSnapshot: number | null;
  additionalRegisterPriceSnapshot: number | null; currentBillableRegisters: number; additionalBillableRegisters: number;
  registerUsage: Array<{storeId:string;storeName:string;activeRegisters:number;includedRegisters:number;additionalRegisters:number}>;
  estimatedAdditionalRegisterCharge:number; estimatedMonthlyPrice: number;
  capabilityCharges: CapabilityCharge[];
  customAdditionalStorePrice: number | null;
  customAdditionalRegisterPrice: number | null; customAdditionalUserPrice: number | null; discountName: string | null;
  discountType: 'PERCENTAGE' | 'FIXED_AMOUNT' | null; discountValue: number | null; pricingNotes: string | null;
  paymentTermsDays: number | null; version: number;
};
export type PlatformInvoiceLine = { id: string; lineType: string; description: string; quantity: number; unitPrice: number; discount: number; taxAmount: number; lineSubtotal: number; lineTotal: number; capability: CommercialCapability | null; billingUnit: CapabilityBillingUnit | null };
export type PlatformInvoice = {
  id: string; invoiceNumber: string; tenantId: string; merchantName: string; subscriptionId: string; pricingPlanId: string;
  planCode: string; billingPeriodStart: string; billingPeriodEnd: string; issueDate: string; dueDate: string; currency: string;
  subtotal: number; discountTotal: number; taxTotal: number; total: number; amountPaid: number; amountOutstanding: number;
  status: string; billingEmail: string; billingAddress: string | null; taxLabel: string | null; taxRate: number | null;
  notes: string | null; issuedAt: string | null; sentAt: string | null; paidAt: string | null; voidedAt: string | null;
  lines: PlatformInvoiceLine[];
};
export type BillingOverview = { activeSubscriptions: number; trialSubscriptions: number; monthlyRecurringRevenue: number; invoicesThisMonth: number; outstandingBalance: number; pastDueInvoices: number; paidThisMonth: number; subscriptionsCancelling: number; currency: string };
export type PlatformBillingSettings = { id: string; legalName: string | null; billingAddress: string | null; supportEmail: string | null; invoiceSenderEmail: string | null; defaultCurrency: string; defaultPaymentTermsDays: number; invoicePrefix: string; taxRegistrationNumber: string | null; defaultTaxRuleId: string | null; invoiceFooter: string | null; paymentInstructions: string | null; billingEnforcementEnabled: boolean; version: number };

export type TenantStatus = 'PENDING_ONBOARDING' | 'PENDING_OWNER_ACTIVATION' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED' | 'REJECTED';
export type OnboardingStage = 'MERCHANT_DETAILS' | 'OWNER_ACCOUNT' | 'OWNER_INVITATION' | 'OWNER_ACTIVATION' | 'ORGANIZATION_SETUP' | 'FIRST_STORE_SETUP' | 'COMPLETED';

export type TenantSummary = {
  id: string;
  tenantCode: string;
  legalName: string;
  displayName: string;
  status: TenantStatus;
  countryCode: string;
  administrativeDivisionCode: string | null;
  defaultCurrencyCode: string;
  primaryTimezone: string;
  defaultTaxRegionCode: string | null;
  primaryOwnerEmail: string | null;
  subscriptionPlan: string | null;
  onboardingStage: OnboardingStage;
  storeCount: number;
  userCount: number;
  createdAt: string;
  activatedAt: string | null;
  suspendedAt: string | null;
  suspendedByPlatformUserId: string | null;
  suspensionReason: string | null;
  closedAt: string | null;
  closedByPlatformUserId: string | null;
  closureReason: string | null;
  reactivatedAt: string | null;
  reactivatedByPlatformUserId: string | null;
  version: number;
};

export type TenantSummaryListResponse = PageResponse<TenantSummary>;

export type MerchantProfile = {
  id: string;
  tenantId: string;
  legalBusinessName: string;
  operatingName: string;
  businessNumber: string | null;
  contactName: string;
  contactEmail: string;
  contactPhone: string | null;
  billingAddress: string | null;
  countryCode: string;
  administrativeDivisionCode: string | null;
  defaultCurrencyCode: string | null;
  primaryTimezone: string | null;
  defaultTaxRegionCode: string | null;
  postalCode: string | null;
  industryType: string | null;
  estimatedStoreCount: number | null;
  notes: string | null;
  version: number;
};

export type TenantSubscription = {
  id: string;
  tenantId: string;
  planCode: string;
  status: 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED';
  startsAt: string;
  trialEndsAt: string | null;
  renewsAt: string | null;
  cancelledAt: string | null;
  maximumStores: number | null;
  maximumUsers: number | null;
  features: Record<string, boolean>;
  version: number;
};

export type TenantOnboarding = {
  tenantId: string;
  currentStage: OnboardingStage;
  completedAt: string | null;
  stages: { stage: OnboardingStage; completedAt: string | null }[];
};

export type TenantDetail = {
  tenant: TenantSummary;
  merchantProfile: MerchantProfile;
  subscription: TenantSubscription;
  onboarding: TenantOnboarding;
};

export type PlatformDashboard = {
  totalActiveMerchants: number;
  pendingOnboardings: number;
  suspendedMerchants: number;
  closedMerchants: number;
  merchantsRequiringAttention: number;
  activeStores: number;
  activeMerchantUsers: number;
  trialSubscriptions: number;
  recentOnboardingActivity: TenantSummary[];
  recentLifecycleActivity: TenantStatusHistory[];
  failedInvitations: number;
  supportAccessEnabled: boolean;
  supportAccessDefaultMinutes: number;
};

export type TenantStatusHistory = {
  id: string;
  tenantId: string | null;
  tenantCodeSnapshot: string | null;
  previousStatus: TenantStatus | null;
  newStatus: TenantStatus;
  reason: string | null;
  notes: string | null;
  changedByPlatformUserId: string | null;
  changedAt: string;
  correlationId: string | null;
};

export type TenantDeletionBlocker = {
  type: string;
  count: number;
  message: string;
};

export type TenantDeletionEligibility = {
  eligible: boolean;
  merchantStatus: TenantStatus;
  blockers: TenantDeletionBlocker[];
  recommendedAction: 'DELETE' | 'CLOSE' | 'SUSPEND_OR_CLOSE' | string;
};

export type EmailDeliveryStatus = 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'RETRY_SCHEDULED' | 'CANCELLED';

export type EmailProvider = 'CONSOLE' | 'RESEND';

export type EmailDelivery = {
  id: string;
  tenantId: string | null;
  invitationId: string | null;
  recipient: string;
  templateCode: string;
  provider: EmailProvider;
  providerMessageId: string | null;
  status: EmailDeliveryStatus;
  attemptCount: number;
  lastAttemptAt: string | null;
  sentAt: string | null;
  failedAt: string | null;
  nextRetryAt: string | null;
  failureCode: string | null;
  failureMessageSanitized: string | null;
  correlationId: string | null;
  requestedByPlatformUserId: string | null;
  requestedReason: string | null;
  requestedNotes: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type EmailProviderStatus = {
  provider: EmailProvider;
  configured: boolean;
  enabled: boolean;
  fromAddressConfigured: boolean;
};

export type PlatformUser = {
  id: string;
  email: string;
  displayName: string;
  role: 'PLATFORM_SUPER_ADMIN' | 'PLATFORM_SUPPORT_ADMIN';
  enabled: boolean;
  locked: boolean;
  passwordChangeRequired: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type PlatformAdmin = {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role: 'PLATFORM_SUPER_ADMIN' | 'PLATFORM_SUPPORT_ADMIN';
  status: 'PENDING_ACTIVATION' | 'ACTIVE' | 'DEACTIVATED';
  locked: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  createdBy: { id: string; name: string } | null;
  version: number;
};

export type PlatformAdminPage = { content: PlatformAdmin[]; page: number; size: number; totalElements: number; totalPages: number };

export type PlatformSettings = {
  bootstrapEnabled: boolean;
  ownerInvitationExpiryHours: number;
  supportAccessEnabled: boolean;
  supportAccessDefaultMinutes: number;
  tenantStatuses: TenantStatus[];
  onboardingStages: OnboardingStage[];
  subscriptionStatuses: string[];
  serverDate: string;
};

export type OwnerActivationStatus = {
  tenantId: string;
  ownerId: string;
  ownerName: string;
  ownerEmail: string;
  ownerAccountStatus: string;
  invitationStatus: 'PENDING' | 'SENT' | 'EXPIRED' | 'USED' | 'INVALIDATED' | 'CANCELLED';
  invitationId: string | null;
  invitationCreatedAt: string | null;
  invitationExpiresAt: string | null;
  emailProvider: EmailProvider;
  latestEmailDeliveryStatus: EmailDeliveryStatus | null;
  latestAttemptAt: string | null;
  emailSentAt: string | null;
  attemptCount: number;
  sanitizedFailureMessage: string | null;
  activationCompletedAt: string | null;
  temporaryCredentialsIssuedAt: string | null;
  temporaryCredentialsExpiresAt: string | null;
  credentialsDeliveryStatus: EmailDeliveryStatus | null;
  firstLoginAt: string | null;
  passwordChangedAt: string | null;
  failedLoginAttempts?: number;
  lastFailedLoginAt?: string | null;
  lockedAt?: string | null;
  lockReason?: 'FAILED_LOGIN_ATTEMPTS' | 'ADMINISTRATIVE_LOCK' | 'SECURITY_REVIEW' | null;
  temporaryCredentialsExpired: boolean;
  activationUrl: string | null;
  canResend: boolean;
  canRetry: boolean;
  retryDeliveryId: string | null;
  canCopyActivationLink: boolean;
  canResendTemporaryCredentials: boolean;
};

export type OwnerInvitationResendResponse = {
  tenantId: string;
  ownerId: string;
  ownerEmail: string;
  invitationStatus: OwnerActivationStatus['invitationStatus'];
  invitationExpiresAt: string;
  delivery: {
    deliveryId: string;
    provider: EmailProvider;
    providerMessageId: string | null;
    status: EmailDeliveryStatus;
    attemptCount: number;
    lastAttemptAt: string | null;
  } | null;
};

export type AuditEvent = {
  id: string;
  actorUserId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  storeId: string | null;
  registerId: string | null;
  beforeSnapshot: string | null;
  afterSnapshot: string | null;
  reason: string | null;
  correlationId: string | null;
  createdAt: string;
};

export type AuditEventListResponse = PageResponse<AuditEvent>;

export type AuthResponse = {
  authenticationStatus?: 'AUTHENTICATED' | 'PASSWORD_CHANGE_REQUIRED';
  accessToken: string | null;
  refreshToken: string | null;
  tokenType: 'Bearer';
  accessTokenExpiresAt: string | null;
  refreshTokenExpiresAt: string | null;
  userId: string;
  email: string;
  displayName: string;
  roles: UserRole[];
  passwordChangeToken?: string | null;
  passwordChangeTokenExpiresAt?: string | null;
  expiresIn?: number | null;
};

export type CurrentUserResponse = {
  userId: string;
  email: string;
  displayName: string;
  roles: UserRole[];
  permissions?: string[];
};

export type HealthResponse = {
  status: string;
  service: string;
  checkedAt: string;
};

export type SellableType =
  | 'STANDARD_PRODUCT'
  | 'WEIGHTED_PRODUCT'
  | 'SERVICE'
  | 'FOOD_ITEM'
  | 'LOTTERY_PRODUCT'
  | 'GIFT_CARD'
  | 'STORE_CREDIT'
  | 'BUNDLE'
  | 'DIGITAL_PRODUCT';

export type ProductCapability =
  | 'RETAIL'
  | 'FOOD_SERVICE'
  | 'TRACK_INVENTORY'
  | 'ALLOW_DECIMAL_QUANTITY'
  | 'ALLOW_DISCOUNT'
  | 'ALLOW_RETURN'
  | 'ALLOW_REFUND'
  | 'ALLOW_PRICE_OVERRIDE'
  | 'REQUIRE_AGE_VERIFICATION'
  | 'REQUIRE_SERIAL_NUMBER'
  | 'REQUIRE_EXTERNAL_REFERENCE'
  | 'REQUIRE_CUSTOMER'
  | 'SEND_TO_KITCHEN'
  | 'EXCLUDE_FROM_LOYALTY'
  | 'RESTRICT_PAYMENT_METHOD'
  | 'NON_REFUNDABLE';

export type FoodMenuCategory = { id: string; storeId: string; name: string; displayOrder: number; active: boolean; imageUrl: string | null; version: number };
export type FoodMenuItem = { id: string; storeId: string; categoryId: string; categoryName: string; productId: string; productName: string; displayName: string; price: number; displayOrder: number; available: boolean; imageUrl: string | null; version: number };
export type FoodMenuCategoryPayload = { name: string; displayOrder: number; active: boolean; imageUrl?: string };
export type FoodMenuItemPayload = { productId: string; categoryId: string; displayName: string; price: number; displayOrder: number; available: boolean; imageUrl?: string };

export type ProductVariant = {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  cost: number;
  price: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ProductBarcode = {
  id: string;
  barcode: string;
  variantId?: string | null;
  variantSku: string | null;
  primaryBarcode: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type Product = {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  sellableType: SellableType;
  unitOfMeasureId: string | null;
  cost: number;
  price: number;
  categoryId: string | null;
  brandId: string | null;
  active: boolean;
  inventoryTrackingEnabled: boolean;
  decimalQuantityAllowed: boolean;
  imageUrl: string | null;
  taxCategoryId: string | null;
  variants: ProductVariant[];
  barcodes: ProductBarcode[];
  capabilities: ProductCapability[];
  minimumAge?: number | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type BusinessDayStatus = 'OPEN' | 'CLOSING' | 'CLOSED' | 'REOPENED';

export type BusinessDay = {
  id: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  businessDate: string;
  timezone: string;
  status: BusinessDayStatus;
  openedAt: string;
  openedBy: string;
  openedByName: string;
  closingStartedAt: string | null;
  closingStartedBy: string | null;
  closingStartedByName: string | null;
  closedAt: string | null;
  closedBy: string | null;
  closedByName: string | null;
  reopenedAt: string | null;
  reopenedBy: string | null;
  reopenedByName: string | null;
  reopenReason: string | null;
  forceCloseReason: string | null;
  version: number;
};

export type BusinessDayListResponse = PageResponse<BusinessDay>;

export type ClosingBlocker = {
  code: string;
  message: string;
  relatedId: string | null;
};

export type ClosingValidation = {
  businessDayId: string;
  closable: boolean;
  blockers: ClosingBlocker[];
};

export type EndOfDayRegisterSummary = {
  registerSessionId: string | null;
  registerId: string;
  registerCode: string;
  registerName: string;
  openingFloat: number;
  cashReceipts: number;
  changeGiven: number;
  cashRefunds: number;
  lotteryCashSales: number;
  lotteryPayouts: number;
  lotteryPayoutReversals: number;
  lotterySaleCancellations: number;
  cashIn: number;
  cashOut: number;
  safeDrops: number;
  floatAdditions: number;
  floatRemovals: number;
  expenses: number;
  closingAdjustments: number;
  expectedCash: number;
  countedCash: number;
  variance: number;
  openedBy: string;
  openedByName: string;
  closedBy: string | null;
  closedByName: string | null;
  openedAt: string;
  closedAt: string | null;
  forceClosed: boolean;
  forceCloseReason: string | null;
};

export type EndOfDayPaymentSummary = {
  paymentMethod: PaymentMethod;
  collected: number;
  refunded: number;
  net: number;
  cashTendered: number;
  changeGiven: number;
  transactionCount: number;
  splitPaymentCount: number;
};

export type EndOfDayTaxSummary = {
  componentCode: string;
  componentName: string;
  taxableSales: number;
  exemptSales: number;
  zeroRatedSales: number;
  outOfScopeSales: number;
  taxCollected: number;
  taxRefunded: number;
  roundingAdjustment: number;
  netTaxCollected: number;
};

export type EndOfDayLotterySummary = {
  enabled: boolean;
  lotterySales: number;
  lotteryPayouts: number;
  saleCancellations: number;
  payoutReversals: number;
  cashLotteryActivity: number;
  nonCashLotteryActivity: number;
  commissionEarned: number;
  settlementAmount: number;
  operatorReferrals: number;
  pendingReferrals: number;
  approvalCount: number;
  rejectedPayouts: number;
  operatorTotals: string;
  registerTotals: string;
  cashierTotals: string;
};

export type EndOfDayInventorySummary = {
  deductedBySales: number;
  restoredByReturns: number;
  manualIncreases: number;
  manualDecreases: number;
  damagedQuantity: number;
  expiredQuantity: number;
  transferIn: number;
  transferOut: number;
  stockCountVariances: number;
  lowStockProducts: number;
  negativeStockProducts: number;
  inventoryValueMovement: number;
};

export type EndOfDayCashierSummary = {
  cashierId: string;
  cashierName: string;
  transactionCount: number;
  grossSales: number;
  netSales: number;
  refundTotal: number;
  voidCount: number;
  discountTotal: number;
  priceOverrideCount: number;
  cashHandled: number;
  lotterySales: number;
  lotteryPayouts: number;
  averageTransactionValue: number;
  firstActivityAt: string | null;
  lastActivityAt: string | null;
  registersUsed: string;
};

export type EndOfDayExceptionSummary = {
  exceptionType: string;
  count: number;
  totalAmount: number;
  details: string | null;
};

export type EndOfDaySignOff = {
  managerUserId: string;
  managerName: string;
  signedAt: string;
  notes: string | null;
  varianceExplanation: string | null;
  confirmationAccepted: boolean;
};

export type EndOfDayReport = {
  id: string;
  businessDayId: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  businessDate: string;
  businessDayStatus: BusinessDayStatus;
  businessDayVersion: number;
  reportNumber: string;
  revision: number;
  generatedAt: string;
  generatedBy: string;
  generatedByName: string;
  grossSales: number;
  netSales: number;
  discountTotal: number;
  refundTotal: number;
  voidTotal: number;
  taxTotal: number;
  transactionCount: number;
  averageTransactionValue: number;
  highestTransactionValue: number;
  lowestTransactionValue: number;
  itemsSold: number;
  averageBasketSize: number;
  expectedCash: number;
  countedCash: number;
  cashVariance: number;
  currencyCode: string;
  registers: EndOfDayRegisterSummary[];
  payments: EndOfDayPaymentSummary[];
  taxes: EndOfDayTaxSummary[];
  lottery: EndOfDayLotterySummary | null;
  inventory: EndOfDayInventorySummary | null;
  cashiers: EndOfDayCashierSummary[];
  exceptions: EndOfDayExceptionSummary[];
  signOff: EndOfDaySignOff | null;
  reportSnapshot: string;
  version: number;
};

export type EndOfDayClosingPreview = Omit<
  EndOfDayReport,
  'id' | 'reportNumber' | 'revision' | 'generatedAt' | 'generatedBy' | 'generatedByName' | 'signOff' | 'reportSnapshot' | 'version'
> & {
  cashVarianceExplanationThreshold: number;
  varianceExplanationRequired: boolean;
  managerSignOffRequired: boolean;
};

export type EndOfDayReportListResponse = PageResponse<EndOfDayReport>;

export type ProductListResponse = PageResponse<Product>;

export type Store = {
  id: string;
  code: string;
  name: string;
  legalName: string | null;
  countryCode: string;
  countryId?: string | null;
  administrativeAreaCode: string | null;
  administrativeDivisionCode?: string | null;
  administrativeDivisionId?: string | null;
  address: string;
  phone: string | null;
  email: string | null;
  currencyCode: string;
  currencyId?: string | null;
  locale: string;
  timezone: string;
  timezoneId?: string | null;
  timezoneName?: string | null;
  taxRegionId?: string | null;
  taxRegionCode?: string | null;
  pricesIncludeTax: boolean;
  negativeStockAllowed: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
  capabilities?: StoreCapability[];
  foodServiceEnabled?: boolean;
  kitchenDisplayName?: string | null;
  kitchenUsersCount?: number;
};

export type StoreCapability = 'RETAIL' | 'FOOD_SERVICE';

export type StoreListResponse = PageResponse<Store>;

export type StoreDefaults = {
  countryCode: string | null;
  administrativeDivisionCode: string | null;
  currencyCode: string | null;
  locale: string | null;
  timezone: string | null;
  taxRegionCode: string | null;
  capabilities?: StoreCapability[];
  kitchenDisplayName: string | null;
};

export type AssignmentRole = 'MANAGER' | 'CASHIER' | 'KITCHEN';
export type AssignmentStatus = 'ACTIVE' | 'INACTIVE' | 'REVOKED' | 'PENDING';

export type AssignedStore = {
  storeId: string;
  storeCode: string;
  storeName: string;
  city: string | null;
  administrativeDivisionCode: string | null;
  assignmentRole: AssignmentRole;
  capabilities?: StoreCapability[];
};

export type CountryReference = {
  id: string;
  alpha2Code: string;
  alpha3Code: string;
  name: string;
  defaultCurrencyCode: string | null;
  defaultLanguageCode: string;
  active: boolean;
  displayOrder: number;
};

export type AdministrativeDivisionReference = {
  id: string;
  countryCode: string;
  code: string;
  name: string;
  divisionType: 'PROVINCE' | 'TERRITORY' | 'STATE' | 'DISTRICT' | 'REGION' | string;
  defaultTimezone: string | null;
  defaultTaxRegionCode: string | null;
  active: boolean;
  displayOrder: number;
};

export type CurrencyReference = {
  id: string;
  code: string;
  name: string;
  symbol: string;
  decimalPlaces: number;
  active: boolean;
};

export type TimezoneReference = {
  id: string;
  ianaName: string;
  displayName: string;
  countryCode: string | null;
  active: boolean;
  displayOrder: number;
  defaultForDivision: boolean;
};

export type TaxRegionReference = {
  id: string;
  countryCode: string;
  administrativeDivisionId: string | null;
  administrativeDivisionCode: string | null;
  code: string;
  name: string;
  active: boolean;
  defaultForDivision: boolean;
  taxJurisdictionId: string | null;
};

export type InventoryTransactionType =
  | 'OPENING_STOCK'
  | 'PURCHASE'
  | 'SALE'
  | 'RETURN'
  | 'ADJUSTMENT_INCREASE'
  | 'ADJUSTMENT_DECREASE'
  | 'STOCK_COUNT_INCREASE'
  | 'STOCK_COUNT_DECREASE'
  | 'DAMAGED'
  | 'EXPIRED'
  | 'TRANSFER_IN'
  | 'TRANSFER_OUT'
  | 'VOID_REVERSAL';

export type InventoryBalance = {
  id: string | null;
  storeId: string;
  productId: string;
  quantityOnHand: number;
  lastTransactionAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  version: number | null;
};

export type InventoryTransaction = {
  id: string;
  balanceId: string;
  storeId: string;
  productId: string;
  transactionType: InventoryTransactionType;
  quantityDelta: number;
  resultingQuantity: number;
  referenceType: string | null;
  referenceId: string | null;
  reason: string | null;
  actorUserId: string | null;
  occurredAt: string;
  createdAt: string;
  version: number;
};

export type InventoryBalanceListResponse = PageResponse<InventoryBalance>;

export type InventoryTransactionListResponse = PageResponse<InventoryTransaction>;

export type InventoryStockReportRow = {
  storeId: string;
  storeCode: string;
  storeName: string;
  productId: string;
  productSku: string;
  productName: string;
  categoryId: string | null;
  cost: number;
  quantityOnHand: number;
  inventoryValue: number;
  lastTransactionAt: string | null;
};

export type InventoryActivityReportRow = {
  id: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  productId: string;
  productSku: string;
  productName: string;
  categoryId: string | null;
  transactionType: InventoryTransactionType;
  quantityDelta: number;
  quantity: number;
  inventoryValue: number;
  referenceType: string | null;
  referenceId: string | null;
  reason: string | null;
  actorUserId: string | null;
  occurredAt: string;
};

export type InventoryReport = {
  storeId: string | null;
  categoryId: string | null;
  productId: string | null;
  dateFrom: string | null;
  dateTo: string | null;
  lowStockThreshold: number;
  currentStock: number;
  inventoryValue: number;
  stockItemCount: number;
  lowStockCount: number;
  negativeStockCount: number;
  adjustmentCount: number;
  damagedCount: number;
  expiredCount: number;
  adjustmentQuantity: number;
  damagedQuantity: number;
  expiredQuantity: number;
  adjustmentValue: number;
  damagedValue: number;
  expiredValue: number;
  stockRows: InventoryStockReportRow[];
  lowStockRows: InventoryStockReportRow[];
  negativeStockRows: InventoryStockReportRow[];
  adjustmentRows: InventoryActivityReportRow[];
  damagedRows: InventoryActivityReportRow[];
  expiredRows: InventoryActivityReportRow[];
  generatedAt: string;
};

export type StockAdjustmentType =
  | 'INCREASE'
  | 'DECREASE'
  | 'DAMAGED'
  | 'EXPIRED';

export type StockAdjustmentApprovalStatus = 'POSTED';

export type StockAdjustmentLine = {
  id: string;
  productId: string;
  adjustmentType: StockAdjustmentType;
  quantity: number;
  quantityDelta: number;
  resultingQuantity: number;
  inventoryTransactionId: string;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type StockAdjustment = {
  id: string;
  storeId: string;
  reason: string;
  notes: string | null;
  approvalStatus: StockAdjustmentApprovalStatus;
  approvedByUserId: string | null;
  approvedAt: string;
  approvalNotes: string | null;
  lines: StockAdjustmentLine[];
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type StockAdjustmentListResponse = PageResponse<StockAdjustment>;

export type StockCountStatus = 'DRAFT' | 'IN_REVIEW' | 'POSTED' | 'SAVED';

export type StockCountLine = {
  id: string;
  productId: string;
  expectedQuantity: number;
  countedQuantity: number | null;
  varianceQuantity: number | null;
  balanceVersion: number | null;
  resultingQuantity: number | null;
  inventoryTransactionId: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type StockCount = {
  id: string;
  storeId: string;
  reference: string;
  notes: string | null;
  status: StockCountStatus;
  createdByUserId: string | null;
  reviewedByUserId: string | null;
  reviewedAt: string | null;
  reviewNotes: string | null;
  postedByUserId: string | null;
  postedAt: string | null;
  postNotes: string | null;
  lines: StockCountLine[];
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type StockCountListResponse = PageResponse<StockCount>;

export type Register = {
  id: string;
  storeId: string;
  code: string;
  name: string;
  locationDescription: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type RegisterListResponse = PageResponse<Register>;

export type FeatureCode =
  | 'LOTTERY_SALES'
  | 'FOOD_SALES'
  | 'KITCHEN_DISPLAY'
  | 'AGE_VERIFICATION'
  | 'GIFT_CARDS'
  | 'LOYALTY'
  | 'PURCHASE_ORDERS'
  | 'WAREHOUSE_TRANSFERS';

export type FeatureResolutionSource = 'DEFAULT' | 'TENANT' | 'STORE' | 'REGISTER';

export type FeatureDefinition = {
  id: string;
  code: FeatureCode;
  name: string;
  description: string;
  defaultEnabled: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type FeatureOverride = {
  id: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type FeatureResolution = {
  definition: FeatureDefinition;
  enabled: boolean;
  source: FeatureResolutionSource;
  storeId: string | null;
  registerId: string | null;
  tenantOverride: FeatureOverride | null;
  storeOverride: FeatureOverride | null;
  registerOverride: FeatureOverride | null;
};

export type SettlementFrequency = 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';

export type LotteryOperator = {
  id: string;
  code: string;
  name: string;
  jurisdictionId: string;
  jurisdictionCode: string;
  jurisdictionName: string;
  supportContact: string | null;
  settlementFrequency: SettlementFrequency;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type LotteryOperatorListResponse = PageResponse<LotteryOperator>;

export type LotteryPayoutPolicyStatus = 'DRAFT' | 'SCHEDULED' | 'ACTIVE' | 'RETIRED';

export type LotteryPayoutPolicy = {
  id: string;
  operatorId: string;
  operatorCode: string;
  operatorName: string;
  jurisdictionId: string;
  jurisdictionCode: string;
  jurisdictionName: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  maximumCashPayout: number;
  cashierApprovalLimit: number;
  managerApprovalThreshold: number;
  operatorReferralThreshold: number;
  protectedRegisterFloat: number;
  allowCashPayout: boolean;
  allowStoreCredit: boolean;
  requireTicketValidation: boolean;
  requireAgeVerification: boolean;
  requireCustomerIdentification: boolean;
  allowAlternateRegister: boolean;
  effectiveFrom: string;
  effectiveTo: string | null;
  status: LotteryPayoutPolicyStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type LotteryPayoutPolicyListResponse = PageResponse<LotteryPayoutPolicy>;

export type LotteryCommissionRuleType =
  | 'PERCENT_OF_SALES'
  | 'PERCENT_OF_PAYOUT'
  | 'FIXED_PER_TRANSACTION'
  | 'FIXED_PER_PERIOD'
  | 'MANUAL';

export type LotteryCommissionRuleStatus = 'DRAFT' | 'ACTIVE' | 'RETIRED';

export type LotteryCommissionPeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY';

export type LotteryCommissionRule = {
  id: string;
  name: string;
  operatorId: string;
  operatorCode: string;
  operatorName: string;
  jurisdictionId: string;
  jurisdictionCode: string;
  jurisdictionName: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  ruleType: LotteryCommissionRuleType;
  commissionRatePercent: number | null;
  fixedAmount: number | null;
  currencyCode: string | null;
  fixedPeriod: LotteryCommissionPeriod | null;
  effectiveFrom: string;
  effectiveTo: string | null;
  status: LotteryCommissionRuleStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type LotteryCommissionRuleListResponse = PageResponse<LotteryCommissionRule>;

export type LotterySettlementStatus =
  | 'DRAFT'
  | 'CALCULATED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'POSTED'
  | 'REOPENED';

export type LotterySettlement = {
  id: string;
  operatorId: string;
  operatorCode: string;
  operatorName: string;
  jurisdictionId: string;
  jurisdictionCode: string;
  jurisdictionName: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  periodStart: string;
  periodEnd: string;
  grossSales: number;
  totalPayouts: number;
  cancellations: number;
  adjustments: number;
  commission: number;
  expectedSettlement: number;
  currencyCode: string;
  calculatedAt: string;
  status: LotterySettlementStatus;
  approvedBy: string | null;
  approvedByEmail: string | null;
  approvedByDisplayName: string | null;
  approvedAt: string | null;
  postedBy: string | null;
  postedByEmail: string | null;
  postedByDisplayName: string | null;
  postedAt: string | null;
  reopenedBy: string | null;
  reopenedByEmail: string | null;
  reopenedByDisplayName: string | null;
  reopenedAt: string | null;
  reopenReason: string | null;
  lifecycleNotes: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type LotterySettlementListResponse = PageResponse<LotterySettlement>;

export type LotteryGameType =
  | 'DRAW_TICKET'
  | 'INSTANT_TICKET'
  | 'SPORTS_WAGER'
  | 'BREAKOPEN'
  | 'ONLINE_CREDIT'
  | 'OTHER';

export type LotterySaleStatus = 'RECORDED' | 'CANCELLED';

export type LotteryPayoutMethod =
  | 'CASH'
  | 'STORE_CREDIT'
  | 'OPERATOR_VOUCHER'
  | 'CHEQUE_REFERRAL'
  | 'OPERATOR_CLAIM_REFERRAL'
  | 'OTHER';

export type LotteryPayoutStatus =
  | 'DRAFT'
  | 'VALIDATED'
  | 'AUTHORIZED'
  | 'PAID'
  | 'REFERRED_TO_OPERATOR'
  | 'REJECTED'
  | 'REVERSED';

export type LotteryVerificationState = 'NOT_REQUIRED' | 'PENDING' | 'VERIFIED' | 'FAILED';

export type LotteryPayoutApprovalType = 'CASHIER_LIMIT' | 'MANAGER_APPROVAL' | 'OPERATOR_REFERRAL';

export type LotteryPayoutApproval = {
  id: string;
  approvalType: LotteryPayoutApprovalType;
  approvedBy: string;
  approvedByEmail: string;
  approvedByDisplayName: string;
  approvedAt: string;
  payoutAmount: number;
  thresholdAmount: number;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type LotteryPayout = {
  id: string;
  operatorId: string;
  operatorCode: string;
  operatorName: string;
  policyId: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  registerId: string;
  registerCode: string;
  registerName: string;
  deviceId: string;
  deviceIdentifier: string;
  deviceDisplayName: string;
  cashierId: string;
  cashierEmail: string;
  cashierDisplayName: string;
  registerSessionId: string | null;
  ticketNumber: string;
  validationReference: string | null;
  amount: number;
  currencyCode: string;
  payoutMethod: LotteryPayoutMethod;
  status: LotteryPayoutStatus;
  ticketValidationState: LotteryVerificationState;
  ageVerificationState: LotteryVerificationState;
  identificationVerificationState: LotteryVerificationState;
  cashierApprovalLimit: number;
  managerApprovalThreshold: number;
  operatorReferralThreshold: number;
  maximumCashPayout: number;
  ticketValidationRequired: boolean;
  ageVerificationRequired: boolean;
  identificationRequired: boolean;
  alternateRegisterAllowed: boolean;
  businessDate: string;
  occurredAt: string;
  validatedBy: string | null;
  validatedAt: string | null;
  authorizedBy: string | null;
  authorizedAt: string | null;
  paidBy: string | null;
  paidAt: string | null;
  rejectedBy: string | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
  notes: string | null;
  approvals: LotteryPayoutApproval[];
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type LotteryPayoutCashAvailability = {
  registerSessionId: string;
  policyId: string;
  expectedDrawerCash: number;
  protectedRegisterFloat: number;
  reservedObligations: number;
  availablePayoutCash: number;
  currencyCode: string;
};

export type LotteryPayoutListResponse = PageResponse<LotteryPayout>;

export type LotterySaleCancellation = {
  id: string;
  originalSaleId: string;
  cancelledBy: string;
  cancelledByEmail: string;
  cancelledByDisplayName: string;
  amount: number;
  currencyCode: string;
  cashReturned: boolean;
  operationId: string;
  cancelledAt: string;
  reason: string;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type LotteryPayoutReversal = {
  id: string;
  originalPayoutId: string;
  reversedBy: string;
  reversedByEmail: string;
  reversedByDisplayName: string;
  amount: number;
  currencyCode: string;
  operationId: string;
  reversedAt: string;
  reason: string;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type LotterySale = {
  id: string;
  operatorId: string;
  operatorCode: string;
  operatorName: string;
  operatorReference: string | null;
  ticketReference: string | null;
  gameType: LotteryGameType;
  amount: number;
  currencyCode: string;
  paymentMethod: PaymentMethod;
  storeId: string;
  storeCode: string;
  storeName: string;
  registerId: string;
  registerCode: string;
  registerName: string;
  deviceId: string;
  deviceIdentifier: string;
  deviceDisplayName: string;
  cashierId: string;
  cashierEmail: string;
  cashierDisplayName: string;
  registerSessionId: string | null;
  status: LotterySaleStatus;
  operationId: string;
  occurredAt: string;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type LotterySaleListResponse = PageResponse<LotterySale>;

export type LotteryReportApprovalRow = {
  id: string;
  payoutId: string;
  ticketNumber: string;
  operatorId: string;
  operatorCode: string;
  operatorName: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  registerId: string;
  registerCode: string;
  registerName: string;
  cashierId: string;
  cashierEmail: string;
  cashierDisplayName: string;
  approvalType: LotteryPayoutApprovalType;
  approvedBy: string;
  approvedByEmail: string;
  approvedByDisplayName: string;
  approvedAt: string;
  payoutAmount: number;
  thresholdAmount: number;
  notes: string | null;
};

export type LotteryReportCommissionRow = {
  settlementId: string;
  operatorId: string;
  operatorCode: string;
  operatorName: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  periodStart: string;
  periodEnd: string;
  grossSales: number;
  totalPayouts: number;
  commission: number;
  expectedSettlement: number;
  status: LotterySettlementStatus;
};

export type LotteryReportChartPoint = {
  date: string;
  sales: number;
  payouts: number;
  reversals: number;
  referrals: number;
  settlement: number;
};

export type LotteryReport = {
  operatorId: string | null;
  storeId: string | null;
  registerId: string | null;
  cashierId: string | null;
  dateFrom: string | null;
  dateTo: string | null;
  sales: number;
  saleCount: number;
  payouts: number;
  payoutCount: number;
  approvals: number;
  approvalCount: number;
  reversals: number;
  reversalCount: number;
  referrals: number;
  referralCount: number;
  cancellations: number;
  cancellationCount: number;
  commission: number;
  calculatedSettlement: number;
  settlement: number;
  variance: number;
  saleRows: LotterySale[];
  payoutRows: LotteryPayout[];
  approvalRows: LotteryReportApprovalRow[];
  reversalRows: LotteryPayoutReversal[];
  referralRows: LotteryPayout[];
  cancellationRows: LotterySaleCancellation[];
  commissionRows: LotteryReportCommissionRow[];
  settlementRows: LotterySettlement[];
  chartRows: LotteryReportChartPoint[];
  generatedAt: string;
};

export type SalesReportPaymentBreakdown = {
  method: PaymentMethod;
  collected: number;
  refunded: number;
  net: number;
};

export type SalesReport = {
  storeId: string | null;
  registerId: string | null;
  cashierId: string | null;
  categoryId: string | null;
  productId: string | null;
  dateFrom: string | null;
  dateTo: string | null;
  grossSales: number;
  netSales: number;
  discounts: number;
  refunds: number;
  taxes: number;
  payments: number;
  saleCount: number;
  refundCount: number;
  paymentBreakdown: SalesReportPaymentBreakdown[];
  generatedAt: string;
};

export type Device = {
  id: string;
  storeId: string;
  registerId: string;
  deviceIdentifier: string;
  displayName: string;
  deviceType: string;
  registeredAt: string;
  lastSeenAt: string;
  active: boolean;
  version: number;
};

export type DeviceListResponse = PageResponse<Device>;

export type RegisterSessionStatus = 'OPEN' | 'CLOSING' | 'CLOSED' | 'FORCE_CLOSED';

export type RegisterSession = {
  id: string;
  storeId: string;
  registerId: string;
  deviceId: string | null;
  deviceName?: string | null;
  assignedCashierId: string;
  assignedCashierEmail: string;
  assignedCashierDisplayName: string;
  openedByUserId?: string | null;
  openedByDisplayName?: string | null;
  status: RegisterSessionStatus;
  openingCash: number;
  expectedCash: number;
  countedCash: number | null;
  expectedCashAtClose: number | null;
  differenceCash: number | null;
  closedByUserId: string | null;
  closedByEmail: string | null;
  closedByDisplayName: string | null;
  closedAt: string | null;
  forceCloseReason: string | null;
  reconciliation: CashLedgerBreakdown | null;
  openedAt: string;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type RegisterSessionListResponse = PageResponse<RegisterSession>;

export type CashLedgerDirection = 'IN' | 'OUT';

export type CashLedgerSourceType =
  | 'SESSION_OPENING_FLOAT'
  | 'SALE_CASH_RECEIPT'
  | 'SALE_CHANGE_GIVEN'
  | 'LOTTERY_SALE_CASH'
  | 'LOTTERY_PAYOUT_CASH'
  | 'LOTTERY_PAYOUT_REVERSAL'
  | 'LOTTERY_SALE_CANCELLATION_CASH'
  | 'CASH_REFUND'
  | 'CASH_MOVEMENT'
  | 'SESSION_CLOSE_ADJUSTMENT';

export type CashLedgerSourceBreakdown = {
  sourceType: CashLedgerSourceType;
  direction: CashLedgerDirection;
  amount: number;
};

export type CashLedgerBreakdown = {
  openingCash: number;
  retailCashReceived: number;
  retailChange: number;
  retailRefunds: number;
  lotteryCashSales: number;
  lotteryPayouts: number;
  payoutReversals: number;
  lotterySaleCancellations: number;
  otherCashIn: number;
  otherCashOut: number;
  totalIn: number;
  totalOut: number;
  expectedCash: number;
  sourceBreakdown: CashLedgerSourceBreakdown[];
};

export type CashMovementType =
  | 'CASH_IN'
  | 'CASH_OUT'
  | 'SAFE_DROP'
  | 'FLOAT_ADD'
  | 'FLOAT_REMOVE'
  | 'EXPENSE'
  | 'BANK_DEPOSIT'
  | 'CORRECTION';

export type CashMovement = {
  id: string;
  storeId: string;
  registerId: string;
  registerSessionId: string;
  type: CashMovementType;
  direction: CashLedgerDirection;
  amount: number;
  currencyCode: string;
  reason: string;
  notes: string | null;
  createdBy: string;
  occurredAt: string;
  approvedBy: string | null;
  approvedAt: string | null;
  approvalNotes: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type CashMovementListResponse = PageResponse<CashMovement>;

export type RegisterReportRow = {
  registerSessionId: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  registerId: string;
  registerCode: string;
  registerName: string;
  cashierId: string;
  cashierEmail: string;
  cashierDisplayName: string;
  status: RegisterSessionStatus;
  currencyCode: string;
  openingCash: number;
  retailCash: number;
  retailCashReceived: number;
  retailChange: number;
  lotteryCash: number;
  lotteryCashSales: number;
  lotteryPayouts: number;
  payoutReversals: number;
  lotterySaleCancellations: number;
  refunds: number;
  cashMovements: number;
  cashMovementIn: number;
  cashMovementOut: number;
  expectedCash: number;
  countedCash: number | null;
  variance: number | null;
  openedAt: string;
  closedAt: string | null;
};

export type RegisterReport = {
  storeId: string | null;
  registerId: string | null;
  cashierId: string | null;
  status: RegisterSessionStatus | null;
  dateFrom: string | null;
  dateTo: string | null;
  openingCash: number;
  retailCash: number;
  retailCashReceived: number;
  retailChange: number;
  lotteryCash: number;
  lotteryCashSales: number;
  lotteryPayouts: number;
  payoutReversals: number;
  lotterySaleCancellations: number;
  refunds: number;
  cashMovements: number;
  cashMovementIn: number;
  cashMovementOut: number;
  expectedCash: number;
  countedCash: number;
  variance: number;
  sessionCount: number;
  closedSessionCount: number;
  rows: RegisterReportRow[];
  generatedAt: string;
};

export type SaleStatus =
  | 'DRAFT'
  | 'HELD'
  | 'COMPLETED'
  | 'VOIDED'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED'
  | 'CANCELLED';

export type PaymentMethod =
  | 'CASH'
  | 'DEBIT'
  | 'CREDIT'
  | 'GIFT_CARD'
  | 'STORE_CREDIT'
  | 'OTHER';

export type Payment = {
  id: string;
  method: PaymentMethod;
  amount: number;
  currencyCode: string;
  cashTendered: number | null;
  changeDue: number;
  reference: string | null;
  notes: string | null;
  createdBy: string;
  completedAt: string;
  createdAt: string;
  version: number;
};

export type SaleItem = {
  id: string;
  productId: string;
  variantId?: string | null;
  lineNumber: number;
  productSku: string;
  productName: string;
  variantSku?: string | null;
  variantName?: string | null;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  completedProductCost: number | null;
  completedProductPrice: number | null;
  completedProductCapabilities: string | null;
  priceOverride: boolean;
  ageVerified: boolean;
  serialNumber: string | null;
  externalReference: string | null;
  customerId: string | null;
  paymentMethodCode: string | null;
  lineSubtotal: number;
  estimatedTaxAmount: number;
  lineTotal: number;
  version: number;
};

export type PosBarcodeLookup = {
  productId: string;
  variantId: string | null;
  productName: string;
  variantName: string | null;
  barcode: string;
  sku: string;
  unitOfMeasureId: string | null;
  price: number;
  taxCategoryId: string | null;
  taxCategoryName: string | null;
  availableQuantity: number;
  active: boolean;
  ageRestricted?: boolean;
  minimumAge?: number | null;
};

export type Sale = {
  id: string;
  storeId: string;
  registerId: string;
  registerSessionId: string;
  createdBy: string;
  customerId: string | null;
  status: SaleStatus;
  businessDate: string;
  saleChannel: string | null;
  currencyCode: string;
  pricesIncludeTax: boolean;
  subtotalAmount: number;
  discountAmount: number;
  estimatedTaxAmount: number;
  totalAmount: number;
  heldAt: string | null;
  cancelledAt: string | null;
  completedBy: string | null;
  completedAt: string | null;
  items: SaleItem[];
  payments: Payment[];
  paidAmount: number;
  balanceDue: number;
  changeDue: number;
  paymentComplete: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type SaleListResponse = PageResponse<Sale>;

export type ReturnItem = {
  id: string;
  originalSaleItemId: string;
  productId: string;
  lineNumber: number;
  productSku: string;
  productName: string;
  quantity: number;
  reason: string;
  originalQuantity: number;
  originalUnitPrice: number;
  originalDiscountAmount: number;
  originalLineSubtotal: number;
  originalTaxAmount: number;
  originalLineTotal: number;
  originalProductCost: number | null;
  originalProductPrice: number | null;
  originalProductCapabilities: string | null;
  originalProductTaxCategoryId: string | null;
  returnSubtotalAmount: number;
  returnTaxAmount: number;
  returnTotalAmount: number;
  version: number;
};

export type Return = {
  id: string;
  originalSaleId: string;
  storeId: string;
  registerId: string;
  registerSessionId: string;
  createdBy: string;
  businessDate: string;
  occurredAt: string;
  currencyCode: string;
  reason: string;
  totalQuantity: number;
  subtotalAmount: number;
  taxAmount: number;
  totalAmount: number;
  fullReturn: boolean;
  items: ReturnItem[];
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ReturnListResponse = PageResponse<Return>;

export type RefundPayment = {
  id: string;
  originalPaymentId: string | null;
  lineNumber: number;
  method: PaymentMethod;
  amount: number;
  currencyCode: string;
  reference: string | null;
  notes: string | null;
  version: number;
};

export type RefundItemTax = {
  id: string;
  returnItemId: string;
  originalSaleItemId: string;
  lineNumber: number;
  productTaxCategoryId: string | null;
  taxComponentCode: string;
  taxComponentName: string;
  taxableAmount: number;
  taxAmount: number;
  currencyCode: string;
  version: number;
};

export type Refund = {
  id: string;
  returnId: string;
  originalSaleId: string;
  storeId: string;
  registerId: string;
  registerSessionId: string;
  createdBy: string;
  businessDate: string;
  occurredAt: string;
  currencyCode: string;
  reason: string;
  subtotalAmount: number;
  taxAmount: number;
  totalAmount: number;
  approvedBy: string | null;
  approvedAt: string | null;
  approvalNotes: string | null;
  payments: RefundPayment[];
  itemTaxes: RefundItemTax[];
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type RefundListResponse = PageResponse<Refund>;

export type ReceiptStore = {
  id: string;
  code: string;
  name: string;
  legalName: string | null;
  address: string;
  phone: string | null;
  email: string | null;
};

export type ReceiptRegister = {
  id: string;
  code: string;
  name: string;
};

export type ReceiptCashier = {
  id: string;
  displayName: string;
  email: string;
};

export type ReceiptItem = {
  id: string;
  productId: string;
  lineNumber: number;
  productSku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  completedProductCost: number | null;
  completedProductPrice: number | null;
  completedProductCapabilities: string | null;
  discountAmount: number;
  lineSubtotal: number;
  taxAmount: number;
  lineTotal: number;
};

export type ReceiptTaxSummary = {
  componentCode: string;
  componentName: string;
  taxableAmount: number;
  taxAmount: number;
};

export type ReceiptPayment = {
  id: string;
  method: PaymentMethod;
  amount: number;
  cashTendered: number | null;
  changeDue: number;
  reference: string | null;
  completedAt: string;
};

export type ReceiptDocument = {
  brandName: string;
  brandTagline: string;
  store: ReceiptStore;
  register: ReceiptRegister;
  cashier: ReceiptCashier;
  receiptNumber: string;
  saleId: string;
  saleNumber: string;
  businessDate: string;
  completedAt: string;
  currencyCode: string;
  items: ReceiptItem[];
  subtotalAmount: number;
  discountAmount: number;
  taxSummaries: ReceiptTaxSummary[];
  taxAmount: number;
  totalAmount: number;
  payments: ReceiptPayment[];
  cashTendered: number;
  changeDue: number;
};

export type Receipt = {
  id: string;
  saleId: string;
  receiptNumber: string;
  generatedAt: string;
  reprintCount: number;
  lastReprintedAt: string | null;
  document: ReceiptDocument;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type UserAdmin = {
  id: string;
  email: string;
  displayName: string;
  enabled: boolean;
  locked: boolean;
  roles: UserRole[];
  storeIds: string[];
  registerIds: string[];
  status?: 'INVITED' | 'ACTIVE' | 'DISABLED' | 'LOCKED' | 'ARCHIVED' | string;
  storeAssignments?: UserStoreAssignment[];
  createdByUserId?: string | null;
  createdByRole?: 'TENANT_OWNER' | 'STORE_MANAGER' | null;
  updatedByUserId?: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type UserAdminListResponse = PageResponse<UserAdmin>;

export type UserStoreAssignment = {
  id: string;
  tenantId: string;
  userId: string;
  storeId: string;
  storeCode: string;
  storeName: string;
  assignmentRole: AssignmentRole;
  status: AssignmentStatus;
  active: boolean;
  assignedBy: string | null;
  assignedAt: string;
  removedBy: string | null;
  removedAt: string | null;
  removalReason: string | null;
  version: number;
};

export type RoleAdmin = {
  id: string;
  name: UserRole;
  description: string | null;
  systemRole: boolean;
  permissions: string[];
  version: number;
};

export type CatalogueReference = {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type CatalogueReferenceListResponse = PageResponse<CatalogueReference>;

export type AdministrativeAreaType =
  | 'PROVINCE'
  | 'TERRITORY'
  | 'STATE'
  | 'REGION'
  | 'COUNTY'
  | 'MUNICIPAL'
  | 'LOCAL'
  | 'SPECIAL';

export type TaxJurisdictionType =
  | 'NATIONAL'
  | 'PROVINCIAL'
  | 'TERRITORIAL'
  | 'STATE'
  | 'REGIONAL'
  | 'COUNTY'
  | 'MUNICIPAL'
  | 'LOCAL'
  | 'SPECIAL';

export type TaxRateStatus = 'DRAFT' | 'SCHEDULED' | 'ACTIVE' | 'RETIRED';

export type TaxTreatment =
  | 'STANDARD'
  | 'REDUCED'
  | 'ZERO_RATED'
  | 'EXEMPT'
  | 'OUT_OF_SCOPE'
  | 'SPECIAL';

export type TaxRuleConditionType =
  | 'STORE_JURISDICTION'
  | 'SUPPLY_JURISDICTION'
  | 'PRODUCT_TAX_CATEGORY'
  | 'PRODUCT'
  | 'CUSTOMER_EXEMPTION'
  | 'TRANSACTION_DATE'
  | 'SALE_CHANNEL';

export type TaxRuleConditionOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'IN'
  | 'IS_TRUE'
  | 'IS_FALSE'
  | 'ON_OR_AFTER'
  | 'ON_OR_BEFORE'
  | 'BETWEEN';

export type TaxRuleActionType =
  | 'APPLY_TAX_GROUP'
  | 'APPLY_TAX_COMPONENT'
  | 'EXCLUDE_COMPONENT'
  | 'ZERO_RATE'
  | 'EXEMPT'
  | 'OUT_OF_SCOPE'
  | 'INCLUDED_PRICE_BEHAVIOR'
  | 'ROUNDING_STRATEGY';

export type IncludedPriceBehavior = 'USE_RATE_SETTING' | 'FORCE_INCLUDED' | 'FORCE_ADDED';

export type TaxRoundingStrategy = 'HALF_UP' | 'HALF_EVEN' | 'DOWN' | 'UP';

export type Country = {
  id: string;
  code: string;
  name: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type AdministrativeArea = {
  id: string;
  countryId: string;
  code: string;
  name: string;
  type: AdministrativeAreaType;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type TaxJurisdiction = {
  id: string;
  countryId: string;
  administrativeAreaId: string | null;
  code: string;
  name: string;
  type: TaxJurisdictionType;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type TaxType = {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type TaxComponent = {
  id: string;
  taxTypeId: string;
  taxJurisdictionId: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type TaxRate = {
  id: string;
  taxComponentId: string;
  percentageRate: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  includedInPrice: boolean;
  compoundOnPreviousTax: boolean;
  calculationOrder: number;
  status: TaxRateStatus;
  source: string | null;
  sourceReference: string | null;
  verifiedBy: string | null;
  verifiedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type TaxGroup = {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type TaxGroupComponent = {
  id: string;
  taxGroupId: string;
  taxComponentId: string;
  calculationOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type TaxCategory = {
  id: string;
  taxGroupId: string | null;
  code: string;
  name: string;
  treatment: TaxTreatment;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ProductTaxCategoryAssignment = {
  id: string;
  productId: string;
  taxCategoryId: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type TaxRuleCondition = {
  id: string;
  conditionType: TaxRuleConditionType;
  operator: TaxRuleConditionOperator;
  value: string | null;
  secondValue: string | null;
};

export type TaxRuleAction = {
  id: string;
  actionType: TaxRuleActionType;
  taxGroupId: string | null;
  taxComponentId: string | null;
  value: string | null;
};

export type TaxRule = {
  id: string;
  code: string;
  name: string;
  description: string | null;
  priority: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  active: boolean;
  conditions: TaxRuleCondition[];
  actions: TaxRuleAction[];
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type TaxRuleConditionEvaluation = {
  conditionId: string;
  conditionType: TaxRuleConditionType;
  operator: TaxRuleConditionOperator;
  expected: string | null;
  actual: string | null;
  matched: boolean;
  explanation: string;
};

export type TaxRuleActionEvaluation = {
  actionId: string;
  actionType: TaxRuleActionType;
  taxGroupId: string | null;
  taxComponentId: string | null;
  value: string | null;
};

export type TaxRuleMatch = {
  ruleId: string;
  code: string;
  name: string;
  priority: number;
  matched: boolean;
  conditions: TaxRuleConditionEvaluation[];
  actions: TaxRuleActionEvaluation[];
  explanation: string;
};

export type TaxRuleEvaluation = {
  appliedTaxGroupIds: string[];
  appliedTaxComponentIds: string[];
  excludedTaxComponentIds: string[];
  zeroRated: boolean;
  exempt: boolean;
  outOfScope: boolean;
  includedPriceBehavior: IncludedPriceBehavior;
  roundingStrategy: TaxRoundingStrategy;
  ruleMatches: TaxRuleMatch[];
};

export type TaxComponentCalculation = {
  taxComponentId: string | null;
  taxComponentCode: string;
  taxComponentName: string;
  taxRateId: string | null;
  percentageRate: number;
  taxableAmount: number;
  taxAmount: number;
  includedInPrice: boolean;
  compoundOnPreviousTax: boolean;
  calculationOrder: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  explanation: string;
};

export type TaxCalculation = {
  storeId: string | null;
  storeJurisdictionId: string | null;
  supplyJurisdictionId: string | null;
  productId: string | null;
  productTaxCategoryId: string | null;
  transactionDate: string;
  saleChannel: string | null;
  currencyCode: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  pricesIncludeTax: boolean;
  netAmount: number;
  taxAmount: number;
  grossAmount: number;
  zeroRated: boolean;
  exempt: boolean;
  outOfScope: boolean;
  includedPriceBehavior: IncludedPriceBehavior;
  roundingStrategy: TaxRoundingStrategy;
  components: TaxComponentCalculation[];
  explanations: string[];
  ruleEvaluation: TaxRuleEvaluation;
};

export type CountryListResponse = PageResponse<Country>;
export type AdministrativeAreaListResponse = PageResponse<AdministrativeArea>;
export type TaxJurisdictionListResponse = PageResponse<TaxJurisdiction>;
export type TaxTypeListResponse = PageResponse<TaxType>;
export type TaxComponentListResponse = PageResponse<TaxComponent>;
export type TaxRateListResponse = PageResponse<TaxRate>;
export type TaxGroupListResponse = PageResponse<TaxGroup>;
export type TaxGroupComponentListResponse = PageResponse<TaxGroupComponent>;
export type TaxCategoryListResponse = PageResponse<TaxCategory>;
export type ProductTaxCategoryAssignmentListResponse = PageResponse<ProductTaxCategoryAssignment>;
export type TaxRuleListResponse = PageResponse<TaxRule>;

export type Supplier = {
  id: string;
  code: string;
  name: string;
  contactName: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type SupplierListResponse = PageResponse<Supplier>;

export type ProductSupplier = {
  id: string;
  productId: string;
  supplierId: string;
  supplierSku: string | null;
  preferred: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type ProductSupplierListResponse = PageResponse<ProductSupplier>;

export type ApiError = {
  code: string;
  message: string;
  status: number;
  path: string;
  method: string;
  correlationId: string | null;
  violations: Array<{ field: string; code: string; message: string }>;
  timestamp: string;
};
