import type {
  ApiError,
  AdministrativeArea,
  AdministrativeAreaListResponse,
  AdministrativeAreaType,
  AuthResponse,
  BusinessDay,
  BusinessDayListResponse,
  BusinessDayStatus,
  CatalogueReference,
  CatalogueReferenceListResponse,
  CashLedgerDirection,
  CashMovement,
  CashMovementListResponse,
  CashMovementType,
  ClosingValidation,
  Country,
  CountryListResponse,
  CountryReference,
  CurrentUserResponse,
  Device,
  DeviceListResponse,
  EndOfDayClosingPreview,
  EndOfDayReport,
  EndOfDayReportListResponse,
  FeatureDefinition,
  FeatureResolution,
  FeatureCode,
  HealthResponse,
  InventoryReport,
  InventoryBalance,
  InventoryBalanceListResponse,
  InventoryTransaction,
  InventoryTransactionListResponse,
  InventoryTransactionType,
  LotteryCommissionPeriod,
  LotteryCommissionRule,
  LotteryCommissionRuleListResponse,
  LotteryCommissionRuleStatus,
  LotteryCommissionRuleType,
  LotterySettlement,
  LotterySettlementListResponse,
  LotterySettlementStatus,
  LotteryOperator,
  LotteryOperatorListResponse,
  LotteryGameType,
  LotteryPayout,
  LotteryPayoutCashAvailability,
  LotteryPayoutListResponse,
  LotteryPayoutMethod,
  LotteryPayoutReversal,
  LotteryReport,
  LotteryPayoutPolicy,
  LotteryPayoutPolicyListResponse,
  LotteryPayoutPolicyStatus,
  LotteryVerificationState,
  LotterySale,
  LotterySaleListResponse,
  LotterySaleCancellation,
  LotterySaleStatus,
  Product,
  ProductCapability,
  ProductListResponse,
  PosBarcodeLookup,
  ProductSupplier,
  ProductSupplierListResponse,
  ProductTaxCategoryAssignment,
  ProductTaxCategoryAssignmentListResponse,
  Register,
  RegisterReport,
  RegisterListResponse,
  RegisterSession,
  RegisterSessionListResponse,
  RegisterSessionStatus,
  Store,
  StoreListResponse,
  AdministrativeDivisionReference,
  CurrencyReference,
  TaxRegionReference,
  TimezoneReference,
  StockAdjustment,
  StockAdjustmentApprovalStatus,
  StockAdjustmentListResponse,
  StockAdjustmentType,
  StockCount,
  StockCountListResponse,
  StockCountStatus,
  TaxCategory,
  TaxCategoryListResponse,
  Supplier,
  SupplierListResponse,
  TaxGroup,
  TaxGroupComponent,
  TaxGroupComponentListResponse,
  TaxGroupListResponse,
  TaxJurisdiction,
  TaxJurisdictionListResponse,
  TaxJurisdictionType,
  TaxComponent,
  TaxComponentListResponse,
  TaxCalculation,
  TaxRate,
  TaxRateListResponse,
  TaxRateStatus,
  TaxRule,
  TaxRuleActionType,
  TaxRuleConditionOperator,
  TaxRuleConditionType,
  TaxRuleListResponse,
  TaxTreatment,
  TaxType,
  TaxTypeListResponse,
  RoleAdmin,
  Receipt,
  Refund,
  RefundListResponse,
  Return,
  ReturnListResponse,
  Sale,
  SaleListResponse,
  SalesReport,
  SaleStatus,
  PaymentMethod,
  SettlementFrequency,
  PlatformDashboard,
  PlatformSettings,
  PlatformUser,
  PlatformAdmin,
  PlatformAdminPage,
  AuditEventListResponse,
  EmailDelivery,
  EmailProviderStatus,
  OwnerActivationStatus,
  OwnerInvitationResendResponse,
  TenantDeletionEligibility,
  TenantStatusHistory,
  AssignedStore,
  AssignmentRole,
  StoreDefaults,
  TenantDetail,
  TenantSubscription,
  TenantSummaryListResponse,
  UserAdmin,
  UserAdminListResponse,
  UserStoreAssignment,
  UserRole
} from './types';

type AuthRegisterPayload = {
  email: string;
  password: string;
  displayName: string;
};

type LoginPayload = {
  email: string;
  password: string;
};

export type FirstLoginPasswordChangePayload = {
  passwordChangeToken: string;
  newPassword: string;
  confirmPassword: string;
};

export type MerchantOnboardingPayload = {
  tenantCode?: string;
  legalBusinessName: string;
  operatingName: string;
  countryCode: string;
  administrativeDivisionCode?: string;
  primaryTimezone: string;
  defaultCurrencyCode: string;
  defaultTaxRegionCode?: string;
  currencyOverrideReason?: string;
  businessNumber?: string;
  industryType?: string;
  estimatedStoreCount?: number;
  notes?: string;
  pricingPlanId: string;
  maximumUsers?: number;
  features: Record<string, boolean>;
  ownerFirstName: string;
  ownerLastName: string;
  ownerEmail: string;
  ownerPhone?: string;
  storeCapabilities: Array<'RETAIL' | 'FOOD_SERVICE'>;
  kitchenDisplayName?: string;
};

export type MerchantGeographyValidationPayload = {
  countryCode: string;
  administrativeDivisionCode: string;
  currencyCode: string;
  timezone: string;
  taxRegionCode: string;
  currencyOverrideReason?: string;
};

export type MerchantGeographyValidationResult = {
  valid: boolean;
  country: { code: string; name: string };
  administrativeDivision: { code: string; name: string };
  currency: { code: string; name: string };
  timezone: string;
  taxRegion: { code: string; name: string };
  warnings: string[];
};

export type TenantLifecyclePayload = {
  reason: string;
  notes?: string | null;
  confirmation?: string | null;
  version?: number;
};

export type TenantVersionPayload = {
  version: number;
};

export type TenantDeletePayload = {
  confirmation: string;
  reason?: string | null;
  version?: number;
};

export type OwnerInvitationResendPayload = {
  reason: string;
  notes?: string;
};

export type TenantSubscriptionPayload = {
  planCode: string;
  status: TenantSubscription['status'];
  startsAt: string;
  trialEndsAt?: string | null;
  renewsAt?: string | null;
  cancelledAt?: string | null;
  maximumStores?: number | null;
  maximumUsers?: number | null;
  features: Record<string, boolean>;
  version: number;
};

export type PlatformUserPayload = {
  email: string;
  displayName: string;
  password: string;
  role: PlatformUser['role'];
  enabled?: boolean;
};

export type PlatformUserUpdatePayload = {
  email: string;
  displayName: string;
  role: PlatformUser['role'];
  locked: boolean;
  version: number;
};

type RefreshPayload = {
  refreshToken: string;
};

export type ProductVariantPayload = {
  id?: string;
  sku: string;
  name: string;
  description?: string;
  cost: number;
  price: number;
  active: boolean;
};

export type ProductBarcodePayload = {
  id?: string;
  barcode: string;
  variantId?: string;
  variantSku?: string;
  primaryBarcode: boolean;
  active: boolean;
};

export type ProductPayload = {
  sku: string;
  name: string;
  description?: string;
  sellableType: string;
  unitOfMeasureId?: string;
  cost: number;
  price: number;
  categoryId?: string;
  brandId?: string;
  active: boolean;
  inventoryTrackingEnabled: boolean;
  decimalQuantityAllowed: boolean;
  imageUrl?: string;
  taxCategoryId?: string;
  variants: ProductVariantPayload[];
  barcodes: ProductBarcodePayload[];
  capabilities: ProductCapability[];
  minimumAge?: number;
  storeIds?: string[];
};

export type ProductUpdatePayload = ProductPayload & {
  version: number;
};

export type ProductStatusPayload = {
  active: boolean;
  version: number;
};

export type ProductSearchParams = {
  name?: string;
  sku?: string;
  barcode?: string;
  sellableType?: string;
  categoryId?: string;
  brandId?: string;
  unitOfMeasureId?: string;
  active?: boolean | '';
  storeId?: string;
  page?: number;
  size?: number;
};

export type StorePayload = {
  code: string;
  name: string;
  legalName?: string;
  countryCode: string;
  administrativeDivisionCode?: string;
  administrativeAreaCode?: string;
  address: string;
  phone?: string;
  email?: string;
  currencyCode: string;
  locale: string;
  timezone: string;
  taxRegionCode?: string;
  pricesIncludeTax: boolean;
  negativeStockAllowed: boolean;
  active: boolean;
  capabilities: Array<'RETAIL' | 'FOOD_SERVICE' | 'LOTTERY'>;
  kitchenDisplayName?: string;
};

export type StoreUpdatePayload = StorePayload & {
  version: number;
};

export type StoreStatusPayload = {
  active: boolean;
  version: number;
};

export type StoreSearchParams = {
  code?: string;
  name?: string;
  countryCode?: string;
  administrativeAreaCode?: string;
  currencyCode?: string;
  active?: boolean | '';
  pricesIncludeTax?: boolean | '';
  negativeStockAllowed?: boolean | '';
  page?: number;
  size?: number;
};

export type BusinessDayOpenPayload = {
  storeId: string;
  businessDate?: string;
  overrideOpenPrevious?: boolean;
  overrideReason?: string;
};

export type BusinessDaySearchParams = {
  storeId?: string;
  dateFrom?: string;
  dateTo?: string;
  status?: BusinessDayStatus | '';
  page?: number;
  size?: number;
};

export type BusinessDayClosePayload = {
  version: number;
  managerNotes?: string;
  varianceExplanation?: string;
  confirmationAccepted: boolean;
};

export type BusinessDayForceClosePayload = BusinessDayClosePayload & {
  reason: string;
};

export type BusinessDayReopenPayload = {
  version: number;
  reason: string;
};

export type EndOfDayReportSearchParams = {
  storeId?: string;
  dateFrom?: string;
  dateTo?: string;
  status?: BusinessDayStatus | '';
  closedBy?: string;
  reportNumber?: string;
  page?: number;
  size?: number;
};

export type InventoryStockChangePayload = {
  storeId: string;
  productId: string;
  transactionType: InventoryTransactionType;
  quantityDelta: number;
  referenceType?: string;
  referenceId?: string;
  reason?: string;
  occurredAt?: string;
  balanceVersion?: number;
};

export type InventoryBalanceSearchParams = {
  storeId?: string;
  productId?: string;
  page?: number;
  size?: number;
};

export type InventoryTransactionSearchParams = {
  storeId?: string;
  productId?: string;
  transactionType?: InventoryTransactionType;
  referenceId?: string;
  occurredFrom?: string;
  occurredTo?: string;
  page?: number;
  size?: number;
};

export type InventoryReportParams = {
  storeId?: string;
  categoryId?: string;
  productId?: string;
  dateFrom?: string;
  dateTo?: string;
  lowStockThreshold?: number;
};

export type StockAdjustmentLinePayload = {
  productId: string;
  adjustmentType: StockAdjustmentType;
  quantity: number;
  balanceVersion?: number;
};

export type StockAdjustmentPayload = {
  storeId: string;
  reason: string;
  notes?: string;
  approvalNotes?: string;
  lines: StockAdjustmentLinePayload[];
};

export type StockAdjustmentSearchParams = {
  storeId?: string;
  approvalStatus?: StockAdjustmentApprovalStatus;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
};

export type StockCountLineCreatePayload = {
  productId: string;
  countedQuantity?: number;
};

export type StockCountPayload = {
  storeId: string;
  reference: string;
  notes?: string;
  lines: StockCountLineCreatePayload[];
};

export type StockCountLineCountPayload = {
  lineId: string;
  countedQuantity: number;
};

export type StockCountUpdateLinesPayload = {
  lines: StockCountLineCountPayload[];
};

export type StockCountReviewPayload = {
  reviewNotes?: string;
};

export type StockCountPostPayload = {
  postNotes?: string;
};

export type StockCountSearchParams = {
  storeId?: string;
  status?: StockCountStatus;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
};

export type RegisterPayload = {
  storeId: string;
  code: string;
  name: string;
  locationDescription?: string;
  active: boolean;
  type: 'RETAIL' | 'FOOD_SERVICE';
};

export type RegisterUpdatePayload = RegisterPayload & {
  version: number;
};

export type RegisterStatusPayload = {
  active: boolean;
  version: number;
};

export type RegisterSearchParams = {
  storeId?: string;
  code?: string;
  name?: string;
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type FeatureResolutionParams = {
  storeId?: string;
  registerId?: string;
};

export type FeatureOverridePayload = {
  enabled: boolean | null;
  version?: number;
};

export type LotteryOperatorPayload = {
  code: string;
  name: string;
  jurisdictionId: string;
  supportContact?: string;
  settlementFrequency: SettlementFrequency;
  active: boolean;
};

export type LotteryOperatorUpdatePayload = LotteryOperatorPayload & {
  version: number;
};

export type LotteryOperatorStatusPayload = {
  active: boolean;
  version: number;
};

export type LotteryOperatorSearchParams = {
  code?: string;
  name?: string;
  jurisdictionId?: string;
  settlementFrequency?: SettlementFrequency | '';
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type LotteryPayoutPolicyPayload = {
  operatorId: string;
  jurisdictionId: string;
  storeId: string;
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
  effectiveTo?: string;
  status: LotteryPayoutPolicyStatus;
};

export type LotteryPayoutPolicyUpdatePayload = LotteryPayoutPolicyPayload & {
  version: number;
};

export type LotteryPayoutPolicyStatusPayload = {
  status: LotteryPayoutPolicyStatus;
  version: number;
};

export type LotteryPayoutPolicySearchParams = {
  operatorId?: string;
  jurisdictionId?: string;
  storeId?: string;
  status?: LotteryPayoutPolicyStatus | '';
  page?: number;
  size?: number;
};

export type LotteryCommissionRulePayload = {
  name: string;
  operatorId: string;
  jurisdictionId: string;
  storeId: string;
  ruleType: LotteryCommissionRuleType;
  commissionRatePercent?: number;
  fixedAmount?: number;
  currencyCode?: string;
  fixedPeriod?: LotteryCommissionPeriod;
  effectiveFrom: string;
  effectiveTo?: string;
  status: LotteryCommissionRuleStatus;
  notes?: string;
};

export type LotteryCommissionRuleUpdatePayload = LotteryCommissionRulePayload & {
  version: number;
};

export type LotteryCommissionRuleSearchParams = {
  operatorId?: string;
  jurisdictionId?: string;
  storeId?: string;
  ruleType?: LotteryCommissionRuleType | '';
  status?: LotteryCommissionRuleStatus | '';
  page?: number;
  size?: number;
};

export type LotterySettlementCalculationPayload = {
  operatorId: string;
  storeId: string;
  periodStart: string;
  periodEnd: string;
};

export type LotterySettlementLifecyclePayload = {
  version: number;
  reason?: string;
  notes?: string;
};

export type LotterySettlementSearchParams = {
  operatorId?: string;
  storeId?: string;
  status?: LotterySettlementStatus | '';
  periodStart?: string;
  periodEnd?: string;
  page?: number;
  size?: number;
};

export type LotterySalePayload = {
  operatorId: string;
  operatorReference?: string;
  ticketReference?: string;
  gameType: LotteryGameType;
  amount: number;
  paymentMethod: PaymentMethod;
  storeId: string;
  registerId: string;
  deviceId: string;
  registerSessionId?: string;
  occurredAt?: string;
};

export type LotteryPayoutCreatePayload = {
  operatorId: string;
  storeId: string;
  registerId: string;
  deviceId: string;
  registerSessionId?: string;
  ticketNumber: string;
  amount: number;
  payoutMethod: LotteryPayoutMethod;
  businessDate?: string;
  occurredAt?: string;
  notes?: string;
};

export type LotteryPayoutValidationPayload = {
  version: number;
  ticketValidationState?: LotteryVerificationState;
  ageVerificationState?: LotteryVerificationState;
  identificationVerificationState?: LotteryVerificationState;
  validationReference?: string;
};

export type LotteryPayoutAuthorizationPayload = {
  version: number;
  approvalNotes?: string;
};

export type LotterySaleSearchParams = {
  search?: string;
  operatorId?: string;
  storeId?: string;
  registerId?: string;
  cashierId?: string;
  registerSessionId?: string;
  gameType?: LotteryGameType | '';
  status?: LotterySaleStatus | '';
  paymentMethod?: PaymentMethod | '';
  occurredFrom?: string;
  occurredTo?: string;
  page?: number;
  size?: number;
};

export type LotteryPayoutSearchParams = {
  operatorId?: string;
  storeId?: string;
  registerId?: string;
  registerSessionId?: string;
  status?: string;
  page?: number;
  size?: number;
};

export type LotteryAdjustmentPayload = {
  reason: string;
};

export type LotteryReportParams = {
  operatorId?: string;
  storeId?: string;
  registerId?: string;
  cashierId?: string;
  dateFrom?: string;
  dateTo?: string;
};

export type DevicePayload = {
  storeId: string;
  registerId: string;
  deviceIdentifier: string;
  displayName: string;
  deviceType: string;
  active: boolean;
};

export type DeviceRegisterPayload = Omit<DevicePayload, 'active'>;

export type DeviceUpdatePayload = DevicePayload & {
  version: number;
};

export type DeviceStatusPayload = {
  active: boolean;
  version: number;
};

export type DeviceSearchParams = {
  storeId?: string;
  registerId?: string;
  deviceIdentifier?: string;
  displayName?: string;
  deviceType?: string;
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type RegisterSessionOpenPayload = {
  storeId: string;
  registerId: string;
  deviceId?: string | null;
  openingCash: number;
};

export type RegisterSessionClosePayload = {
  countedCash: number;
  version: number;
};

export type RegisterSessionForceClosePayload = RegisterSessionClosePayload & {
  reason: string;
};

export type RegisterSessionTransferPayload = {
  newOperatorUserId: string;
  reason: string;
  version: number;
};

export type RegisterSessionOverridePayload = {
  reason: string;
  version: number;
};

export type RegisterSessionReleasePayload = {
  cashierUserId: string;
  reason: string;
  version: number;
};

export type RegisterSessionSearchParams = {
  storeId?: string;
  registerId?: string;
  deviceId?: string;
  assignedCashierId?: string;
  status?: string;
  openedFrom?: string;
  openedTo?: string;
  page?: number;
  size?: number;
};

export type RegisterReportParams = {
  storeId?: string;
  registerId?: string;
  cashierId?: string;
  status?: RegisterSessionStatus | '';
  dateFrom?: string;
  dateTo?: string;
};

export type CashMovementPayload = {
  registerSessionId: string;
  type: CashMovementType;
  direction?: CashLedgerDirection;
  amount: number;
  reason: string;
  notes?: string;
  occurredAt: string;
  approvalNotes?: string;
};

export type CashMovementSearchParams = {
  storeId?: string;
  registerId?: string;
  registerSessionId?: string;
  type?: CashMovementType | '';
  occurredFrom?: string;
  occurredTo?: string;
  page?: number;
  size?: number;
};

export type SaleCreateDraftPayload = {
  registerSessionId: string;
  customerId?: string;
  saleChannel?: string;
};

export type SaleAddItemPayload = {
  productId: string;
  variantId?: string;
  quantity: number;
  unitPrice?: number;
  discountAmount?: number;
  priceOverride?: boolean;
  ageVerified?: boolean;
  serialNumber?: string;
  externalReference?: string;
  customerId?: string;
  paymentMethodCode?: string;
};

export type SaleUpdateQuantityPayload = {
  quantity: number;
};

export type SalePaymentPayload = {
  method: PaymentMethod;
  amount: number;
  cashTendered?: number;
  reference?: string;
  notes?: string;
};

export type SaleSearchParams = {
  storeId?: string;
  registerId?: string;
  registerSessionId?: string;
  createdBy?: string;
  status?: SaleStatus | '';
  page?: number;
  size?: number;
};

export type SalesReportParams = {
  storeId?: string;
  registerId?: string;
  cashierId?: string;
  categoryId?: string;
  productId?: string;
  dateFrom?: string;
  dateTo?: string;
};

export type ReturnItemPayload = {
  originalSaleItemId: string;
  quantity: number;
  reason?: string;
};

export type ReturnCreatePayload = {
  originalSaleId: string;
  reason?: string;
  items: ReturnItemPayload[];
};

export type ReturnSearchParams = {
  originalSaleId?: string;
  storeId?: string;
  page?: number;
  size?: number;
};

export type RefundPaymentPayload = {
  method: PaymentMethod;
  amount: number;
  originalPaymentId?: string;
  reference?: string;
  notes?: string;
};

export type RefundCreatePayload = {
  returnId: string;
  reason: string;
  payments: RefundPaymentPayload[];
  approvalNotes?: string;
};

export type RefundSearchParams = {
  originalSaleId?: string;
  returnId?: string;
  storeId?: string;
  registerSessionId?: string;
  page?: number;
  size?: number;
};

export type UserAdminPayload = {
  email: string;
  displayName: string;
  password?: string;
  roles: UserRole[];
  storeIds: string[];
  registerIds: string[];
  enabled?: boolean;
  locked: boolean;
};

export type UserAdminCreatePayload = UserAdminPayload & {
  password: string;
};

export type UserAdminUpdatePayload = Omit<UserAdminPayload, 'password' | 'roles' | 'enabled'> & {
  version: number;
};

export type UserAdminStatusPayload = {
  enabled: boolean;
  version: number;
};

export type UserAdminPasswordResetPayload = {
  newPassword: string;
  version: number;
};

export type UserAdminRolesPayload = {
  roles: UserRole[];
  storeIds: string[];
  registerIds: string[];
  version: number;
};

export type UserAdminSearchParams = {
  email?: string;
  displayName?: string;
  search?: string;
  role?: UserRole | '';
  storeId?: string;
  registerId?: string;
  status?: string;
  enabled?: boolean | '';
  locked?: boolean | '';
  page?: number;
  size?: number;
};

export type UserStoreAssignmentPayload = {
  storeIds: string[];
  assignmentRole: AssignmentRole;
  removalReason?: string;
};

export type CatalogueReferencePayload = {
  code: string;
  name: string;
  description?: string;
  active: boolean;
};

export type CatalogueReferenceUpdatePayload = CatalogueReferencePayload & {
  version: number;
};

export type CatalogueReferenceStatusPayload = {
  active: boolean;
  version: number;
};

export type CatalogueReferenceSearchParams = {
  code?: string;
  name?: string;
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type CountryPayload = {
  code: string;
  name: string;
  active: boolean;
};

export type CountryUpdatePayload = CountryPayload & {
  version: number;
};

export type CountryStatusPayload = {
  active: boolean;
  version: number;
};

export type CountrySearchParams = {
  code?: string;
  name?: string;
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type AdministrativeAreaPayload = {
  countryId: string;
  code: string;
  name: string;
  type: AdministrativeAreaType;
  active: boolean;
};

export type AdministrativeAreaUpdatePayload = AdministrativeAreaPayload & {
  version: number;
};

export type AdministrativeAreaStatusPayload = {
  active: boolean;
  version: number;
};

export type AdministrativeAreaSearchParams = {
  countryId?: string;
  code?: string;
  name?: string;
  type?: AdministrativeAreaType | '';
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type TaxJurisdictionPayload = {
  countryId: string;
  administrativeAreaId?: string;
  code: string;
  name: string;
  type: TaxJurisdictionType;
  active: boolean;
};

export type TaxJurisdictionUpdatePayload = TaxJurisdictionPayload & {
  version: number;
};

export type TaxJurisdictionStatusPayload = {
  active: boolean;
  version: number;
};

export type TaxJurisdictionSearchParams = {
  countryId?: string;
  administrativeAreaId?: string;
  code?: string;
  name?: string;
  type?: TaxJurisdictionType | '';
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type TaxTypePayload = {
  code: string;
  name: string;
  description?: string;
  active: boolean;
};

export type TaxTypeUpdatePayload = TaxTypePayload & {
  version: number;
};

export type TaxTypeStatusPayload = {
  active: boolean;
  version: number;
};

export type TaxTypeSearchParams = {
  code?: string;
  name?: string;
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type TaxComponentPayload = {
  taxTypeId: string;
  taxJurisdictionId: string;
  code: string;
  name: string;
  description?: string;
  active: boolean;
};

export type TaxComponentUpdatePayload = TaxComponentPayload & {
  version: number;
};

export type TaxComponentStatusPayload = {
  active: boolean;
  version: number;
};

export type TaxComponentSearchParams = {
  taxTypeId?: string;
  taxJurisdictionId?: string;
  code?: string;
  name?: string;
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type TaxRatePayload = {
  taxComponentId: string;
  percentageRate: number;
  effectiveFrom: string;
  effectiveTo?: string;
  includedInPrice: boolean;
  compoundOnPreviousTax: boolean;
  calculationOrder: number;
  status: TaxRateStatus;
  source?: string;
  sourceReference?: string;
  verifiedBy?: string;
  verifiedAt?: string;
};

export type TaxRateUpdatePayload = TaxRatePayload & {
  version: number;
};

export type TaxRateStatusPayload = {
  status: TaxRateStatus;
  version: number;
};

export type TaxRateSearchParams = {
  taxComponentId?: string;
  status?: TaxRateStatus | '';
  includedInPrice?: boolean | '';
  compoundOnPreviousTax?: boolean | '';
  calculationOrder?: number | '';
  page?: number;
  size?: number;
};

export type TaxGroupPayload = {
  code: string;
  name: string;
  description?: string;
  active: boolean;
};

export type TaxGroupUpdatePayload = TaxGroupPayload & {
  version: number;
};

export type TaxGroupStatusPayload = {
  active: boolean;
  version: number;
};

export type TaxGroupSearchParams = {
  code?: string;
  name?: string;
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type TaxGroupComponentPayload = {
  taxGroupId: string;
  taxComponentId: string;
  calculationOrder: number;
  active: boolean;
};

export type TaxGroupComponentUpdatePayload = TaxGroupComponentPayload & {
  version: number;
};

export type TaxGroupComponentStatusPayload = {
  active: boolean;
  version: number;
};

export type TaxGroupComponentSearchParams = {
  taxGroupId?: string;
  taxComponentId?: string;
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type TaxCategoryPayload = {
  taxGroupId?: string;
  code: string;
  name: string;
  treatment: TaxTreatment;
  description?: string;
  active: boolean;
};

export type TaxCategoryUpdatePayload = TaxCategoryPayload & {
  version: number;
};

export type TaxCategoryStatusPayload = {
  active: boolean;
  version: number;
};

export type TaxCategorySearchParams = {
  taxGroupId?: string;
  code?: string;
  name?: string;
  treatment?: TaxTreatment | '';
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type ProductTaxCategoryAssignmentPayload = {
  productId: string;
  taxCategoryId: string;
  active: boolean;
};

export type ProductTaxCategoryAssignmentUpdatePayload = ProductTaxCategoryAssignmentPayload & {
  version: number;
};

export type ProductTaxCategoryAssignmentStatusPayload = {
  active: boolean;
  version: number;
};

export type ProductTaxCategoryAssignmentSearchParams = {
  productId?: string;
  taxCategoryId?: string;
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type TaxRuleConditionPayload = {
  conditionType: TaxRuleConditionType;
  operator: TaxRuleConditionOperator;
  value?: string;
  secondValue?: string;
};

export type TaxRuleActionPayload = {
  actionType: TaxRuleActionType;
  taxGroupId?: string;
  taxComponentId?: string;
  value?: string;
};

export type TaxRulePayload = {
  code: string;
  name: string;
  description?: string;
  priority: number;
  effectiveFrom: string;
  effectiveTo?: string;
  active: boolean;
  conditions: TaxRuleConditionPayload[];
  actions: TaxRuleActionPayload[];
};

export type TaxRuleUpdatePayload = TaxRulePayload & {
  version: number;
};

export type TaxRuleStatusPayload = {
  active: boolean;
  version: number;
};

export type TaxRuleSearchParams = {
  code?: string;
  name?: string;
  active?: boolean | '';
  effectiveOn?: string;
  page?: number;
  size?: number;
};

export type TaxCalculationPayload = {
  storeId?: string;
  storeJurisdictionId?: string;
  supplyJurisdictionId?: string;
  productId?: string;
  productTaxCategoryId?: string;
  customerExempt: boolean;
  transactionDate: string;
  saleChannel?: string;
  unitPrice: number;
  quantity: number;
  discountAmount?: number;
  pricesIncludeTax?: boolean;
  currencyCode?: string;
};

export type SupplierPayload = {
  code: string;
  name: string;
  contactName?: string;
  phone?: string;
  email?: string;
  address?: string;
  notes?: string;
  active: boolean;
};

export type SupplierUpdatePayload = SupplierPayload & {
  version: number;
};

export type SupplierStatusPayload = {
  active: boolean;
  version: number;
};

export type SupplierSearchParams = {
  code?: string;
  name?: string;
  contactName?: string;
  email?: string;
  active?: boolean | '';
  page?: number;
  size?: number;
};

export type ProductSupplierPayload = {
  productId: string;
  supplierId: string;
  supplierSku?: string;
  preferred: boolean;
  active: boolean;
};

export type ProductSupplierUpdatePayload = ProductSupplierPayload & {
  version: number;
};

export type ProductSupplierStatusPayload = {
  active: boolean;
  version: number;
};

export type ProductSupplierSearchParams = {
  productId?: string;
  supplierId?: string;
  supplierSku?: string;
  preferred?: boolean | '';
  active?: boolean | '';
  page?: number;
  size?: number;
};

export class ApiClientError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly correlationId?: string | null;
  readonly violations: ApiError['violations'];

  constructor(message: string, status: number, code?: string, correlationId?: string | null, violations: ApiError['violations'] = []) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
    this.violations = violations;
  }
}

export function getApiFieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiClientError)) return {};
  return Object.fromEntries(error.violations.map((violation) => [violation.field, violation.message]));
}

export function getApiErrorMessage(error: unknown, fallback = 'The request could not be completed.'): string {
  if (!(error instanceof ApiClientError)) return error instanceof Error ? error.message : fallback;
  if (error.status >= 500) {
    return error.correlationId ? `${fallback} Reference: ${error.correlationId}` : fallback;
  }
  return error.message || fallback;
}

const API_BASE_URL = `${(import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')}/api/v1`;

async function request<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers
  });

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    let code: string | undefined;
    let correlationId: string | null | undefined;
    let violations: ApiError['violations'] = [];
    try {
      const body = await response.json() as ApiError;
      message = body.message;
      code = body.code;
      correlationId = body.correlationId;
      violations = body.violations ?? [];
    } catch {
      // Keep the status-derived fallback message.
    }
    throw new ApiClientError(message, response.status, code, correlationId, violations);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

async function requestText(path: string, init: RequestInit = {}, token?: string): Promise<string> {
  const headers = new Headers(init.headers);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  if (!response.ok) {
    throw new ApiClientError(`Request failed with status ${response.status}`, response.status);
  }
  return response.text();
}

async function requestBlob(path: string, init: RequestInit = {}, token?: string): Promise<Blob> {
  const headers = new Headers(init.headers);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  if (!response.ok) {
    throw new ApiClientError(`Request failed with status ${response.status}`, response.status);
  }
  return response.blob();
}

export function getHealth() {
  return request<HealthResponse>('/health');
}

export function register(payload: AuthRegisterPayload) {
  return request<AuthResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function login(payload: LoginPayload) {
  return request<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function forgotPassword(email: string) {
  return request<{ message: string }>('/auth/forgot-password', {
    method: 'POST',
    body: JSON.stringify({ email })
  });
}

export function resetPassword(payload: { token: string; newPassword: string; confirmPassword: string }) {
  return request<void>('/auth/reset-password', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export type PasswordPolicy = {
  minimumLength: number;
  maximumLength: number;
  requiresUppercase: boolean;
  requiresLowercase: boolean;
  requiresNumber: boolean;
  requiresSpecialCharacter: boolean;
  allowedSpecialCharacters: string;
};

export function getPasswordPolicy() {
  return request<PasswordPolicy>('/auth/password-policy');
}

export function sendPlatformUserPasswordReset(token: string, tenantId: string, userId: string, reason: string) {
  return request<EmailDelivery>(`/platform/tenants/${tenantId}/users/${userId}/send-password-reset`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  }, token);
}

export function unlockPlatformUser(token: string, tenantId: string, userId: string, reason: string) {
  return request<void>(`/platform/tenants/${tenantId}/users/${userId}/unlock`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  }, token);
}

export function firstLoginChangePassword(payload: FirstLoginPasswordChangePayload) {
  return request<void>('/auth/first-login/change-password', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function platformLogin(payload: LoginPayload) {
  return request<AuthResponse>('/platform/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function activateOwnerInvitation(payload: { token: string; password: string }) {
  return request<void>('/platform/owner-invitations/activate', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function refreshSession(payload: RefreshPayload) {
  return request<AuthResponse>('/auth/refresh', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function logout(refreshToken: string) {
  return request<void>('/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken })
  });
}

export function getCurrentUser(token: string) {
  return request<CurrentUserResponse>('/auth/me', undefined, token);
}

export function getPlatformDashboard(token: string) {
  return request<PlatformDashboard>('/platform/dashboard', undefined, token);
}

export function getPlatformSettings(token: string) {
  return request<PlatformSettings>('/platform/settings', undefined, token);
}

export type PlatformTenantListParams = {
  page?: number;
  size?: number;
  search?: string;
  status?: string;
  country?: string;
  province?: string;
  createdFrom?: string;
  createdTo?: string;
  subscriptionStatus?: string;
  pricingPlan?: string;
  sort?: 'createdAt,desc' | 'createdAt,asc' | 'merchantName,asc' | 'merchantName,desc' | 'status,asc' | 'status,desc';
};

export function listPlatformTenants(token: string, params: PlatformTenantListParams = {}) {
  return request<TenantSummaryListResponse>(`/platform/tenants${queryString(params)}`, undefined, token);
}

export function getPlatformTenant(token: string, tenantId: string) {
  return request<TenantDetail>(`/platform/tenants/${tenantId}`, undefined, token);
}

export function listPlatformTenantStores(token: string, tenantId: string) {
  return request<import('./types').MerchantStoreCapability[]>(`/platform/tenants/${tenantId}/stores`, undefined, token);
}

export function previewPlatformStoreCapabilities(token: string, tenantId: string, storeId: string,
  payload: import('./types').StoreCapabilityUpdatePayload) {
  return request<import('./types').StoreCapabilityChangePreview>(`/platform/tenants/${tenantId}/stores/${storeId}/capabilities/preview`, {
    method: 'POST', body: JSON.stringify(payload)
  }, token);
}

export function updatePlatformStoreCapabilities(token: string, tenantId: string, storeId: string,
  payload: import('./types').StoreCapabilityUpdatePayload) {
  return request<import('./types').MerchantStoreCapability>(`/platform/tenants/${tenantId}/stores/${storeId}/capabilities`, {
    method: 'PUT', body: JSON.stringify(payload)
  }, token);
}

export function getOwnerActivationStatus(token: string, tenantId: string) {
  return request<OwnerActivationStatus>(`/platform/tenants/${tenantId}/owner-invitation`, undefined, token);
}

export function createPlatformTenant(token: string, payload: MerchantOnboardingPayload) {
  return request<TenantDetail>('/platform/tenants', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function validatePlatformTenantGeography(token: string, payload: MerchantGeographyValidationPayload) {
  return request<MerchantGeographyValidationResult>('/platform/tenants/validate-geography', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function suspendPlatformTenant(token: string, tenantId: string, payload: TenantLifecyclePayload) {
  return request<TenantDetail>(`/platform/tenants/${tenantId}/suspend`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function reactivatePlatformTenant(token: string, tenantId: string, payload: TenantLifecyclePayload) {
  return request<TenantDetail>(`/platform/tenants/${tenantId}/reactivate`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function closePlatformTenant(token: string, tenantId: string, payload: TenantLifecyclePayload) {
  return request<TenantDetail>(`/platform/tenants/${tenantId}/close`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function reopenPlatformTenant(token: string, tenantId: string, payload: TenantLifecyclePayload) {
  return request<TenantDetail>(`/platform/tenants/${tenantId}/reopen`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function getTenantDeletionEligibility(token: string, tenantId: string) {
  return request<TenantDeletionEligibility>(`/platform/tenants/${tenantId}/deletion-eligibility`, undefined, token);
}

export function deleteEmptyPlatformTenant(token: string, tenantId: string, payload: TenantDeletePayload) {
  return request<void>(`/platform/tenants/${tenantId}`, {
    method: 'DELETE',
    body: JSON.stringify(payload)
  }, token);
}

export function listTenantStatusHistory(token: string, tenantId: string) {
  return request<TenantStatusHistory[]>(`/platform/tenants/${tenantId}/status-history`, undefined, token);
}

export function resendOwnerInvitation(token: string, tenantId: string, payload: OwnerInvitationResendPayload) {
  return request<OwnerInvitationResendResponse>(`/platform/tenants/${tenantId}/owners/resend-invitation`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function resendTemporaryCredentials(token: string, tenantId: string, ownerId: string, payload: OwnerInvitationResendPayload) {
  return request<OwnerActivationStatus>(`/platform/tenants/${tenantId}/owners/${ownerId}/resend-temporary-credentials`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function listTenantEmailDeliveries(token: string, tenantId: string) {
  return request<EmailDelivery[]>(`/platform/tenants/${tenantId}/email-deliveries`, undefined, token);
}

export function retryEmailDelivery(token: string, deliveryId: string) {
  return request<EmailDelivery>(`/platform/email-deliveries/${deliveryId}/retry`, {
    method: 'POST'
  }, token);
}

export function getEmailProviderStatus(token: string) {
  return request<EmailProviderStatus>('/platform/email/status', undefined, token);
}

export function sendPlatformTestEmail(token: string, recipient: string) {
  return request<EmailDelivery>('/platform/email/test', {
    method: 'POST',
    body: JSON.stringify({ recipient })
  }, token);
}

export function updatePlatformTenantSubscription(token: string, tenantId: string, payload: TenantSubscriptionPayload) {
  return request<TenantSubscription>(`/platform/tenants/${tenantId}/subscription`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function listPlatformUsers(token: string) {
  return request<PlatformUser[]>('/platform/users', undefined, token);
}

export function createPlatformUser(token: string, payload: PlatformUserPayload) {
  return request<PlatformUser>('/platform/users', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updatePlatformUser(token: string, platformUserId: string, payload: PlatformUserUpdatePayload) {
  return request<PlatformUser>(`/platform/users/${platformUserId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function disablePlatformUser(token: string, platformUserId: string, payload: { enabled: boolean; version: number }) {
  return request<PlatformUser>(`/platform/users/${platformUserId}/disable`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function listPlatformAdmins(token: string) {
  return request<PlatformAdminPage>('/platform/admins?page=0&size=100', undefined, token);
}

export function invitePlatformAdmin(token: string, payload: { firstName: string; lastName: string; email: string; role: PlatformAdmin['role'] }) {
  return request<PlatformAdmin>('/platform/admins', { method: 'POST', body: JSON.stringify(payload) }, token);
}

export function resendPlatformAdminInvitation(token: string, id: string) {
  return request<PlatformAdmin>(`/platform/admins/${id}/resend-invitation`, { method: 'POST' }, token);
}

export function updatePlatformAdminStatus(token: string, id: string, enabled: boolean, version: number) {
  return request<PlatformAdmin>(`/platform/admins/${id}/status`, { method: 'POST', body: JSON.stringify({ enabled, version }) }, token);
}

export function activatePlatformAdmin(payload: { token: string; password: string }) {
  return request<void>('/platform/admins/activate', { method: 'POST', body: JSON.stringify(payload) });
}

export function listPlatformAuditEvents(token: string, params: { page?: number; size?: number; action?: string; entityType?: string } = {}) {
  return request<AuditEventListResponse>(`/platform/audit-events${queryString(params)}`, undefined, token);
}

export function listProducts(token: string, params: ProductSearchParams = {}) {
  return request<ProductListResponse>(`/products${queryString(params)}`, undefined, token);
}

export function lookupPosBarcode(token: string, barcode: string, storeId: string) {
  return request<PosBarcodeLookup>(`/products/barcodes/${encodeURIComponent(barcode)}${queryString({ storeId })}`, undefined, token);
}

export function getProduct(token: string, id: string) {
  return request<Product>(`/products/${id}`, undefined, token);
}

export function createProduct(token: string, payload: ProductPayload) {
  return request<Product>('/products', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateProduct(token: string, id: string, payload: ProductUpdatePayload) {
  return request<Product>(`/products/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateProductStatus(token: string, id: string, payload: ProductStatusPayload) {
  return request<Product>(`/products/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

function queryString(params: Record<string, string | number | boolean | undefined | null>) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return;
    }
    searchParams.set(key, String(value));
  });
  const query = searchParams.toString();
  return query ? `?${query}` : '';
}

export function listStores(token: string, params: StoreSearchParams = {}) {
  return request<StoreListResponse>(`/stores${queryString(params)}`, undefined, token);
}

export function getStore(token: string, id: string) {
  return request<Store>(`/stores/${id}`, undefined, token);
}

export function getFoodServiceConfiguration(token: string, storeId: string) {
  return request<{ storeId: string; restaurantPosEnabled: boolean; kitchenDisplayName: string }>(`/stores/${storeId}/food-service/configuration`, undefined, token);
}

export function listFoodMenuCategories(token: string, storeId: string) { return request<import('./types').FoodMenuCategory[]>(`/stores/${storeId}/food-menu/categories`, undefined, token); }
export function createFoodMenuCategory(token: string, storeId: string, payload: import('./types').FoodMenuCategoryPayload) { return request<import('./types').FoodMenuCategory>(`/stores/${storeId}/food-menu/categories`, { method: 'POST', body: JSON.stringify(payload) }, token); }
export function updateFoodMenuCategory(token: string, storeId: string, id: string, payload: import('./types').FoodMenuCategoryPayload) { return request<import('./types').FoodMenuCategory>(`/stores/${storeId}/food-menu/categories/${id}`, { method: 'PUT', body: JSON.stringify(payload) }, token); }
export function deleteFoodMenuCategory(token: string, storeId: string, id: string) { return request<void>(`/stores/${storeId}/food-menu/categories/${id}`, { method: 'DELETE' }, token); }
export function listFoodMenuItems(token: string, storeId: string) { return request<import('./types').FoodMenuItem[]>(`/stores/${storeId}/food-menu/items`, undefined, token); }
export function createFoodMenuItem(token: string, storeId: string, payload: import('./types').FoodMenuItemPayload) { return request<import('./types').FoodMenuItem>(`/stores/${storeId}/food-menu/items`, { method: 'POST', body: JSON.stringify(payload) }, token); }
export function updateFoodMenuItem(token: string, storeId: string, id: string, payload: import('./types').FoodMenuItemPayload) { return request<import('./types').FoodMenuItem>(`/stores/${storeId}/food-menu/items/${id}`, { method: 'PUT', body: JSON.stringify(payload) }, token); }
export function updateFoodMenuItemAvailability(token: string, storeId: string, id: string, available: boolean) { return request<import('./types').FoodMenuItem>(`/stores/${storeId}/food-menu/items/${id}/availability`, { method: 'PATCH', body: JSON.stringify({ available }) }, token); }
export function deleteFoodMenuItem(token: string, storeId: string, id: string) { return request<void>(`/stores/${storeId}/food-menu/items/${id}`, { method: 'DELETE' }, token); }
export function addFoodMenuItemToSale(token: string, storeId: string, itemId: string, saleId: string, quantity: number) { return request<Sale>(`/stores/${storeId}/food-menu/items/${itemId}/sales/${saleId}`, { method: 'POST', body: JSON.stringify({ quantity }) }, token); }

export function createStore(token: string, payload: StorePayload) {
  return request<Store>('/stores', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateStore(token: string, id: string, payload: StoreUpdatePayload) {
  return request<Store>(`/stores/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateStoreStatus(token: string, id: string, payload: StoreStatusPayload) {
  return request<Store>(`/stores/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function getStoreDefaults(token: string) {
  return request<StoreDefaults>('/stores/defaults', undefined, token);
}

export function listReferenceCountries(token: string) {
  return request<CountryReference[]>('/reference/countries?active=true', undefined, token);
}

export function listReferenceAdministrativeDivisions(token: string, countryCode: string) {
  return request<AdministrativeDivisionReference[]>(
    `/reference/countries/${encodeURIComponent(countryCode)}/administrative-divisions?active=true`,
    undefined,
    token
  );
}

export function listReferenceCountryCurrencies(token: string, countryCode: string) {
  return request<CurrencyReference[]>(`/reference/countries/${encodeURIComponent(countryCode)}/currencies`, undefined, token);
}

export function listReferenceDivisionTimezones(token: string, divisionId: string) {
  return request<TimezoneReference[]>(`/reference/administrative-divisions/${encodeURIComponent(divisionId)}/timezones`, undefined, token);
}

export function listReferenceDivisionTaxRegions(token: string, divisionId: string) {
  return request<TaxRegionReference[]>(`/reference/administrative-divisions/${encodeURIComponent(divisionId)}/tax-regions`, undefined, token);
}

export function openBusinessDay(token: string, payload: BusinessDayOpenPayload) {
  return request<BusinessDay>('/business-days/open', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function getCurrentBusinessDay(token: string, storeId: string) {
  return request<BusinessDay | undefined>(`/business-days/current${queryString({ storeId })}`, undefined, token);
}

export function getLatestBusinessDay(token: string, storeId: string) {
  return request<BusinessDay | undefined>(`/business-days/latest${queryString({ storeId })}`, undefined, token);
}

export function listBusinessDays(token: string, params: BusinessDaySearchParams = {}) {
  return request<BusinessDayListResponse>(`/business-days${queryString(params)}`, undefined, token);
}

export function getBusinessDay(token: string, id: string) {
  return request<BusinessDay>(`/business-days/${id}`, undefined, token);
}

export function getBusinessDayClosingValidation(token: string, id: string) {
  return request<ClosingValidation>(`/business-days/${id}/validation`, undefined, token);
}

export function getBusinessDayClosingPreview(token: string, id: string) {
  return request<EndOfDayClosingPreview>(`/business-days/${id}/preview`, undefined, token);
}

export function startBusinessDayClosing(token: string, id: string, idempotencyKey: string) {
  return request<BusinessDay>(`/business-days/${id}/start-closing`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey }
  }, token);
}

export function closeBusinessDay(token: string, id: string, payload: BusinessDayClosePayload, idempotencyKey: string) {
  return request<EndOfDayReport>(`/business-days/${id}/close`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(payload)
  }, token);
}

export function forceCloseBusinessDay(token: string, id: string, payload: BusinessDayForceClosePayload, idempotencyKey: string) {
  return request<EndOfDayReport>(`/business-days/${id}/force-close`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(payload)
  }, token);
}

export function reopenBusinessDay(token: string, id: string, payload: BusinessDayReopenPayload, idempotencyKey: string) {
  return request<BusinessDay>(`/business-days/${id}/reopen`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(payload)
  }, token);
}

export function listEndOfDayReports(token: string, params: EndOfDayReportSearchParams = {}) {
  return request<EndOfDayReportListResponse>(`/end-of-day-reports${queryString(params)}`, undefined, token);
}

export function getEndOfDayReport(token: string, id: string) {
  return request<EndOfDayReport>(`/end-of-day-reports/${id}`, undefined, token);
}

export function getEndOfDayReportPrintHtml(token: string, id: string) {
  return requestText(`/end-of-day-reports/${id}/print`, undefined, token);
}

export function exportEndOfDayReportCsv(token: string, id: string) {
  return requestText(`/end-of-day-reports/${id}/export/csv`, undefined, token);
}

export function exportEndOfDayReportPdf(token: string, id: string) {
  return requestBlob(`/end-of-day-reports/${id}/export/pdf`, undefined, token);
}

export function recordInventoryStockChange(token: string, payload: InventoryStockChangePayload) {
  return request<InventoryTransaction>('/inventory/transactions', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function getCurrentInventoryStock(token: string, storeId: string, productId: string) {
  return request<InventoryBalance>(
    `/inventory/balances/current${queryString({ storeId, productId })}`,
    undefined,
    token
  );
}

export function listInventoryBalances(token: string, params: InventoryBalanceSearchParams = {}) {
  return request<InventoryBalanceListResponse>(`/inventory/balances${queryString(params)}`, undefined, token);
}

export function listInventoryTransactions(token: string, params: InventoryTransactionSearchParams = {}) {
  return request<InventoryTransactionListResponse>(`/inventory/transactions${queryString(params)}`, undefined, token);
}

export function listStockAdjustments(token: string, params: StockAdjustmentSearchParams = {}) {
  return request<StockAdjustmentListResponse>(`/inventory/adjustments${queryString(params)}`, undefined, token);
}

export function createStockAdjustment(token: string, payload: StockAdjustmentPayload) {
  return request<StockAdjustment>('/inventory/adjustments', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function listStockCounts(token: string, params: StockCountSearchParams = {}) {
  return request<StockCountListResponse>(`/inventory/counts${queryString(params)}`, undefined, token);
}

export function getStockCount(token: string, id: string) {
  return request<StockCount>(`/inventory/counts/${id}`, undefined, token);
}

export function createStockCount(token: string, payload: StockCountPayload) {
  return request<StockCount>('/inventory/counts', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateStockCountLines(token: string, id: string, payload: StockCountUpdateLinesPayload) {
  return request<StockCount>(`/inventory/counts/${id}/lines`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function reviewStockCount(token: string, id: string, payload: StockCountReviewPayload) {
  return request<StockCount>(`/inventory/counts/${id}/review`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function postStockCount(token: string, id: string, payload: StockCountPostPayload, idempotencyKey: string) {
  return request<StockCount>(`/inventory/counts/${id}/post`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(payload)
  }, token);
}

export function listRegisters(token: string, params: RegisterSearchParams = {}) {
  return request<RegisterListResponse>(`/registers${queryString(params)}`, undefined, token);
}

export function getRegister(token: string, id: string) {
  return request<Register>(`/registers/${id}`, undefined, token);
}

export function createRegister(token: string, payload: RegisterPayload) {
  return request<Register>('/registers', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateRegister(token: string, id: string, payload: RegisterUpdatePayload) {
  return request<Register>(`/registers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateRegisterStatus(token: string, id: string, payload: RegisterStatusPayload) {
  return request<Register>(`/registers/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listFeatureDefinitions(token: string) {
  return request<FeatureDefinition[]>('/features/definitions', undefined, token);
}

export function getFeatureResolution(token: string, params: FeatureResolutionParams = {}) {
  return request<FeatureResolution[]>(`/features/resolution${queryString(params)}`, undefined, token);
}

export function updateDeploymentFeature(token: string, featureCode: FeatureCode, payload: FeatureOverridePayload) {
  return request<FeatureResolution>(`/features/${featureCode}/deployment`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateStoreFeature(token: string, featureCode: FeatureCode, storeId: string, payload: FeatureOverridePayload) {
  return request<FeatureResolution>(`/features/${featureCode}/stores/${storeId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateRegisterFeature(token: string, featureCode: FeatureCode, registerId: string, payload: FeatureOverridePayload) {
  return request<FeatureResolution>(`/features/${featureCode}/registers/${registerId}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function listLotteryOperators(token: string, params: LotteryOperatorSearchParams = {}) {
  return request<LotteryOperatorListResponse>(`/lottery/operators${queryString(params)}`, undefined, token);
}

export function getLotteryOperator(token: string, id: string) {
  return request<LotteryOperator>(`/lottery/operators/${id}`, undefined, token);
}

export function createLotteryOperator(token: string, payload: LotteryOperatorPayload) {
  return request<LotteryOperator>('/lottery/operators', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateLotteryOperator(token: string, id: string, payload: LotteryOperatorUpdatePayload) {
  return request<LotteryOperator>(`/lottery/operators/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateLotteryOperatorStatus(token: string, id: string, payload: LotteryOperatorStatusPayload) {
  return request<LotteryOperator>(`/lottery/operators/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listLotteryPayoutPolicies(token: string, params: LotteryPayoutPolicySearchParams = {}) {
  return request<LotteryPayoutPolicyListResponse>(`/lottery/payout-policies${queryString(params)}`, undefined, token);
}

export function getLotteryPayoutPolicy(token: string, id: string) {
  return request<LotteryPayoutPolicy>(`/lottery/payout-policies/${id}`, undefined, token);
}

export function createLotteryPayoutPolicy(token: string, payload: LotteryPayoutPolicyPayload) {
  return request<LotteryPayoutPolicy>('/lottery/payout-policies', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateLotteryPayoutPolicy(token: string, id: string, payload: LotteryPayoutPolicyUpdatePayload) {
  return request<LotteryPayoutPolicy>(`/lottery/payout-policies/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateLotteryPayoutPolicyStatus(token: string, id: string, payload: LotteryPayoutPolicyStatusPayload) {
  return request<LotteryPayoutPolicy>(`/lottery/payout-policies/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listLotteryCommissionRules(token: string, params: LotteryCommissionRuleSearchParams = {}) {
  return request<LotteryCommissionRuleListResponse>(`/lottery/commission-rules${queryString(params)}`, undefined, token);
}

export function getLotteryCommissionRule(token: string, id: string) {
  return request<LotteryCommissionRule>(`/lottery/commission-rules/${id}`, undefined, token);
}

export function createLotteryCommissionRule(token: string, payload: LotteryCommissionRulePayload) {
  return request<LotteryCommissionRule>('/lottery/commission-rules', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateLotteryCommissionRule(token: string, id: string, payload: LotteryCommissionRuleUpdatePayload) {
  return request<LotteryCommissionRule>(`/lottery/commission-rules/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function deleteLotteryCommissionRule(token: string, id: string, version: number) {
  return request<void>(`/lottery/commission-rules/${id}${queryString({ version })}`, {
    method: 'DELETE'
  }, token);
}

export function listLotterySettlements(token: string, params: LotterySettlementSearchParams = {}) {
  return request<LotterySettlementListResponse>(`/lottery/settlements${queryString(params)}`, undefined, token);
}

export function getLotterySettlement(token: string, id: string) {
  return request<LotterySettlement>(`/lottery/settlements/${id}`, undefined, token);
}

export function calculateLotterySettlement(token: string, payload: LotterySettlementCalculationPayload) {
  return request<LotterySettlement>('/lottery/settlements/calculate', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function approveLotterySettlement(token: string, id: string, payload: LotterySettlementLifecyclePayload) {
  return request<LotterySettlement>(`/lottery/settlements/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function reopenLotterySettlement(token: string, id: string, payload: LotterySettlementLifecyclePayload) {
  return request<LotterySettlement>(`/lottery/settlements/${id}/reopen`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function postLotterySettlement(token: string, id: string, payload: LotterySettlementLifecyclePayload) {
  return request<LotterySettlement>(`/lottery/settlements/${id}/post`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function recordLotterySale(token: string, payload: LotterySalePayload, idempotencyKey: string) {
  return request<LotterySale>('/lottery/sales', {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey
    },
    body: JSON.stringify(payload)
  }, token);
}

export function listLotterySales(token: string, params: LotterySaleSearchParams = {}) {
  return request<LotterySaleListResponse>(`/lottery/sales${queryString(params)}`, undefined, token);
}

export function cancelLotterySale(token: string, id: string, payload: LotteryAdjustmentPayload, idempotencyKey: string) {
  return request<LotterySaleCancellation>(`/lottery/sales/${id}/cancel`, {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey
    },
    body: JSON.stringify(payload)
  }, token);
}

export function getLotteryPayoutAvailableCash(
  token: string,
  params: { registerSessionId: string; operatorId: string }
) {
  return request<LotteryPayoutCashAvailability>(
    `/lottery/payouts/available-cash${queryString(params)}`,
    undefined,
    token
  );
}

export function createLotteryPayout(token: string, payload: LotteryPayoutCreatePayload) {
  return request<LotteryPayout>('/lottery/payouts', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function listLotteryPayouts(token: string, params: LotteryPayoutSearchParams = {}) {
  return request<LotteryPayoutListResponse>(`/lottery/payouts${queryString(params)}`, undefined, token);
}

export function validateLotteryPayout(token: string, id: string, payload: LotteryPayoutValidationPayload) {
  return request<LotteryPayout>(`/lottery/payouts/${id}/validate`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function authorizeLotteryPayout(token: string, id: string, payload: LotteryPayoutAuthorizationPayload) {
  return request<LotteryPayout>(`/lottery/payouts/${id}/authorize`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function completeLotteryCashPayout(token: string, id: string, idempotencyKey: string) {
  return request<LotteryPayout>(`/lottery/payouts/${id}/complete-cash`, {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey
    }
  }, token);
}

export function reverseLotteryPayout(token: string, id: string, payload: LotteryAdjustmentPayload, idempotencyKey: string) {
  return request<LotteryPayoutReversal>(`/lottery/payouts/${id}/reverse`, {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey
    },
    body: JSON.stringify(payload)
  }, token);
}

export function registerDevice(token: string, payload: DeviceRegisterPayload) {
  return request<Device>('/devices/register', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function listDevices(token: string, params: DeviceSearchParams = {}) {
  return request<DeviceListResponse>(`/devices${queryString(params)}`, undefined, token);
}

export function getDevice(token: string, id: string) {
  return request<Device>(`/devices/${id}`, undefined, token);
}

export function updateDevice(token: string, id: string, payload: DeviceUpdatePayload) {
  return request<Device>(`/devices/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateDeviceStatus(token: string, id: string, payload: DeviceStatusPayload) {
  return request<Device>(`/devices/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function heartbeatDevice(token: string, id: string) {
  return request<Device>(`/devices/${id}/heartbeat`, {
    method: 'POST'
  }, token);
}

export function openRegisterSession(token: string, payload: RegisterSessionOpenPayload) {
  return request<RegisterSession>('/register-sessions/open', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export async function getCurrentRegisterSession(token: string, params: { deviceId?: string; deviceIdentifier?: string } = {}) {
  return (await request<RegisterSession | undefined>(
    `/register-sessions/current${queryString(params)}`,
    undefined,
    token
  )) ?? null;
}

export function listRegisterSessions(token: string, params: RegisterSessionSearchParams = {}) {
  return request<RegisterSessionListResponse>(`/register-sessions${queryString(params)}`, undefined, token);
}

export function closeRegisterSession(token: string, id: string, payload: RegisterSessionClosePayload) {
  return request<RegisterSession>(`/register-sessions/${id}/close`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function forceCloseRegisterSession(token: string, id: string, payload: RegisterSessionForceClosePayload) {
  return request<RegisterSession>(`/register-sessions/${id}/force-close`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function transferRegisterSession(token: string, id: string, payload: RegisterSessionTransferPayload) {
  return request<RegisterSession>(`/register-sessions/${id}/transfer`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function overrideRegisterSession(token: string, id: string, payload: RegisterSessionOverridePayload) {
  return request<RegisterSession>(`/register-sessions/${id}/override`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function releaseRegisterSession(token: string, id: string, payload: RegisterSessionReleasePayload) {
  return request<RegisterSession>(`/register-sessions/${id}/release`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function createCashMovement(token: string, payload: CashMovementPayload) {
  return request<CashMovement>('/cash-movements', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function listCashMovements(token: string, params: CashMovementSearchParams = {}) {
  return request<CashMovementListResponse>(`/cash-movements${queryString(params)}`, undefined, token);
}

export function listSales(token: string, params: SaleSearchParams = {}) {
  return request<SaleListResponse>(`/sales${queryString(params)}`, undefined, token);
}

export function getSalesReport(token: string, params: SalesReportParams = {}) {
  return request<SalesReport>(`/reports/sales${queryString(params)}`, undefined, token);
}

export function getInventoryReport(token: string, params: InventoryReportParams = {}) {
  return request<InventoryReport>(`/reports/inventory${queryString(params)}`, undefined, token);
}

export function getLotteryReport(token: string, params: LotteryReportParams = {}) {
  return request<LotteryReport>(`/reports/lottery${queryString(params)}`, undefined, token);
}

export function getRegisterReport(token: string, params: RegisterReportParams = {}) {
  return request<RegisterReport>(`/reports/registers${queryString(params)}`, undefined, token);
}

export function getSale(token: string, id: string) {
  return request<Sale>(`/sales/${id}`, undefined, token);
}

export function createSaleDraft(token: string, payload: SaleCreateDraftPayload) {
  return request<Sale>('/sales/drafts', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function addSaleItem(token: string, id: string, payload: SaleAddItemPayload) {
  return request<Sale>(`/sales/${id}/items`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateSaleItemQuantity(token: string, id: string, itemId: string, payload: SaleUpdateQuantityPayload) {
  return request<Sale>(`/sales/${id}/items/${itemId}/quantity`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function removeSaleItem(token: string, id: string, itemId: string) {
  return request<Sale>(`/sales/${id}/items/${itemId}`, {
    method: 'DELETE'
  }, token);
}

export function holdSale(token: string, id: string) {
  return request<Sale>(`/sales/${id}/hold`, {
    method: 'POST'
  }, token);
}

export function resumeSale(token: string, id: string) {
  return request<Sale>(`/sales/${id}/resume`, {
    method: 'POST'
  }, token);
}

export function cancelSale(token: string, id: string) {
  return request<Sale>(`/sales/${id}/cancel`, {
    method: 'POST'
  }, token);
}

export function recalculateSale(token: string, id: string) {
  return request<Sale>(`/sales/${id}/recalculate`, {
    method: 'POST'
  }, token);
}

export function recordSalePayment(token: string, id: string, payload: SalePaymentPayload) {
  return request<Sale>(`/sales/${id}/payments`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function completeSale(token: string, id: string, idempotencyKey: string) {
  return request<Sale>(`/sales/${id}/complete`, {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey
    }
  }, token);
}

export function getSaleReceipt(token: string, id: string) {
  return request<Receipt>(`/sales/${id}/receipt`, undefined, token);
}

export function reprintSaleReceipt(token: string, id: string) {
  return request<Receipt>(`/sales/${id}/receipt/reprint`, {
    method: 'POST'
  }, token);
}

export function getPlatformBillingOverview(token: string) {
  return request<import('./types').BillingOverview>('/platform/billing/overview', undefined, token);
}

export function listPlatformPricingPlans(token: string, page = 0, size = 100) {
  return request<import('./types').BillingPage<import('./types').PricingPlan>>(`/platform/billing/plans?page=${page}&size=${size}`, undefined, token);
}

export function listActivePlatformPricingPlans(token: string) {
  return request<import('./types').PricingPlan[]>('/platform/billing/plans/options', undefined, token);
}

export function createPlatformPricingPlan(token: string, payload: Record<string, unknown>) {
  return request<import('./types').PricingPlan>('/platform/billing/plans', { method: 'POST', body: JSON.stringify(payload) }, token);
}

export function updatePlatformPricingPlan(token: string, id: string, payload: Record<string, unknown>) {
  return request<import('./types').PricingPlan>(`/platform/billing/plans/${id}`, { method: 'PUT', body: JSON.stringify(payload) }, token);
}

export function listPlatformBillingCapabilities(token: string) {
  return request<import('./types').CapabilityDefinition[]>('/platform/billing/capabilities', undefined, token);
}

export function listPlatformPricingHistory(token: string, id: string) {
  return request<import('./types').PricingVersion[]>(`/platform/billing/plans/${id}/pricing-history`, undefined, token);
}

export function schedulePlatformPricingVersion(token: string, id: string, payload: Record<string, unknown>) {
  return request<import('./types').PricingVersion>(`/platform/billing/plans/${id}/pricing-versions`, { method: 'POST', body: JSON.stringify(payload) }, token);
}

export function cancelPlatformPricingVersion(token: string, planId: string, versionId: string) {
  return request<void>(`/platform/billing/plans/${planId}/pricing-versions/${versionId}/cancel`, { method: 'POST' }, token);
}
export function getPlatformPricingPreview(token:string,planId:string,storeCount:number,foodServiceStoreCount:number){return request<import('./types').PricingPreview>(`/platform/billing/plans/${planId}/preview?storeCount=${storeCount}&foodServiceStoreCount=${foodServiceStoreCount}`,undefined,token);}
export function getMerchantStorePricingPreview(token:string,foodService:boolean){return request<import('./types').PricingPreview>(`/stores/pricing-preview?foodService=${foodService}`,undefined,token);}

export function getPlatformBillingSubscription(token: string, tenantId: string) {
  return request<import('./types').BillingSubscription>(`/platform/billing/subscriptions/${tenantId}`, undefined, token);
}

export function assignPlatformBillingSubscription(token: string, tenantId: string, payload: Record<string, unknown>) {
  return request<import('./types').BillingSubscription>(`/platform/billing/subscriptions/${tenantId}`, { method: 'POST', body: JSON.stringify(payload) }, token);
}

export function platformBillingSubscriptionAction(token: string, tenantId: string, payload: Record<string, unknown>) {
  return request<import('./types').BillingSubscription>(`/platform/billing/subscriptions/${tenantId}/actions`, { method: 'POST', body: JSON.stringify(payload) }, token);
}

export function listPlatformInvoices(token: string, query = '') {
  return request<import('./types').BillingPage<import('./types').PlatformInvoice>>(`/platform/billing/invoices${query ? `?${query}` : ''}`, undefined, token);
}

export function getPlatformInvoice(token: string, id: string) {
  return request<import('./types').PlatformInvoice>(`/platform/billing/invoices/${id}`, undefined, token);
}

export function generatePlatformInvoice(token: string, tenantId: string, payload: Record<string, unknown> = {}) {
  return request<import('./types').PlatformInvoice>(`/platform/billing/subscriptions/${tenantId}/invoices`, { method: 'POST', body: JSON.stringify(payload) }, token);
}

export function sendPlatformInvoice(token: string, id: string) {
  return request<import('./types').PlatformInvoice>(`/platform/billing/invoices/${id}/send`, { method: 'POST' }, token);
}

export function recordPlatformInvoicePayment(token: string, id: string, payload: Record<string, unknown>) {
  return request<import('./types').PlatformInvoice>(`/platform/billing/invoices/${id}/payments`, { method: 'POST', body: JSON.stringify(payload) }, token);
}

export function voidPlatformInvoice(token: string, id: string, reason: string) {
  return request<import('./types').PlatformInvoice>(`/platform/billing/invoices/${id}/void?reason=${encodeURIComponent(reason)}`, { method: 'POST' }, token);
}

export function getPlatformBillingSettings(token: string) {
  return request<import('./types').PlatformBillingSettings>('/platform/billing/settings', undefined, token);
}

export function updatePlatformBillingSettings(token: string, payload: Record<string, unknown>) {
  return request<import('./types').PlatformBillingSettings>('/platform/billing/settings', { method: 'PUT', body: JSON.stringify(payload) }, token);
}

export function getMerchantBillingSubscription(token: string) {
  return request<import('./types').BillingSubscription>('/billing/subscription', undefined, token);
}

export function downloadPlatformInvoicePdf(token: string, id: string) {
  return requestBlob(`/platform/billing/invoices/${id}/pdf`, undefined, token);
}

export function downloadMerchantInvoicePdf(token: string, id: string) {
  return requestBlob(`/billing/invoices/${id}/pdf`, undefined, token);
}

export function listMerchantBillingInvoices(token: string) {
  return request<import('./types').BillingPage<import('./types').PlatformInvoice>>('/billing/invoices', undefined, token);
}

export function listReturns(token: string, params: ReturnSearchParams = {}) {
  return request<ReturnListResponse>(`/returns${queryString(params)}`, undefined, token);
}

export function getReturn(token: string, id: string) {
  return request<Return>(`/returns/${id}`, undefined, token);
}

export function createReturn(token: string, payload: ReturnCreatePayload) {
  return request<Return>('/returns', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function listRefunds(token: string, params: RefundSearchParams = {}) {
  return request<RefundListResponse>(`/refunds${queryString(params)}`, undefined, token);
}

export function getRefund(token: string, id: string) {
  return request<Refund>(`/refunds/${id}`, undefined, token);
}

export function createRefund(token: string, payload: RefundCreatePayload, idempotencyKey: string) {
  return request<Refund>('/refunds', {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey
    },
    body: JSON.stringify(payload)
  }, token);
}

export function listUsers(token: string, params: UserAdminSearchParams = {}) {
  return request<UserAdminListResponse>(`/users${queryString(params)}`, undefined, token);
}

export function listAssignableStores(token: string) {
  return request<AssignedStore[]>('/users/assignable-stores', undefined, token);
}

export function getUser(token: string, id: string) {
  return request<UserAdmin>(`/users/${id}`, undefined, token);
}

export function createUser(token: string, payload: UserAdminCreatePayload) {
  return request<UserAdmin>('/users', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateUser(token: string, id: string, payload: UserAdminUpdatePayload) {
  return request<UserAdmin>(`/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateUserStatus(token: string, id: string, payload: UserAdminStatusPayload) {
  return request<UserAdmin>(`/users/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function resetUserPassword(token: string, id: string, payload: UserAdminPasswordResetPayload) {
  return request<UserAdmin>(`/users/${id}/reset-password`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateUserRoles(token: string, id: string, payload: UserAdminRolesPayload) {
  return request<UserAdmin>(`/users/${id}/roles`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function disableUser(token: string, id: string, version: number) {
  return request<UserAdmin>(`/users/${id}/disable`, {
    method: 'POST',
    body: JSON.stringify({ enabled: false, version })
  }, token);
}

export function reactivateUser(token: string, id: string, version: number) {
  return request<UserAdmin>(`/users/${id}/reactivate`, {
    method: 'POST',
    body: JSON.stringify({ enabled: true, version })
  }, token);
}

export function listUserStoreAssignments(token: string, id: string) {
  return request<UserStoreAssignment[]>(`/users/${id}/store-assignments`, undefined, token);
}

export function addUserStoreAssignments(token: string, id: string, payload: UserStoreAssignmentPayload) {
  return request<UserStoreAssignment[]>(`/users/${id}/store-assignments`, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function replaceUserStoreAssignments(token: string, id: string, payload: UserStoreAssignmentPayload) {
  return request<UserStoreAssignment[]>(`/users/${id}/store-assignments`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function removeUserStoreAssignment(token: string, id: string, storeId: string) {
  return request<void>(`/users/${id}/store-assignments/${storeId}`, {
    method: 'DELETE'
  }, token);
}

export function listAssignedStores(token: string) {
  return request<AssignedStore[]>('/store-access/assigned-stores', undefined, token);
}

export function validateStoreAccess(token: string, storeId: string) {
  return request<AssignedStore>(`/store-access/stores/${storeId}/validate`, undefined, token);
}

export function listRoles(token: string) {
  return request<RoleAdmin[]>('/roles', undefined, token);
}

function listCatalogueReferences(token: string, path: string, params: CatalogueReferenceSearchParams = {}) {
  return request<CatalogueReferenceListResponse>(`${path}${queryString(params)}`, undefined, token);
}

function getCatalogueReference(token: string, path: string, id: string) {
  return request<CatalogueReference>(`${path}/${id}`, undefined, token);
}

function createCatalogueReference(token: string, path: string, payload: CatalogueReferencePayload) {
  return request<CatalogueReference>(path, {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

function updateCatalogueReference(token: string, path: string, id: string, payload: CatalogueReferenceUpdatePayload) {
  return request<CatalogueReference>(`${path}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

function updateCatalogueReferenceStatus(token: string, path: string, id: string, payload: CatalogueReferenceStatusPayload) {
  return request<CatalogueReference>(`${path}/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export const catalogueReferenceApi = {
  categories: {
    list: (token: string, params?: CatalogueReferenceSearchParams) => listCatalogueReferences(token, '/categories', params),
    get: (token: string, id: string) => getCatalogueReference(token, '/categories', id),
    create: (token: string, payload: CatalogueReferencePayload) => createCatalogueReference(token, '/categories', payload),
    update: (token: string, id: string, payload: CatalogueReferenceUpdatePayload) => updateCatalogueReference(token, '/categories', id, payload),
    updateStatus: (token: string, id: string, payload: CatalogueReferenceStatusPayload) => updateCatalogueReferenceStatus(token, '/categories', id, payload)
  },
  brands: {
    list: (token: string, params?: CatalogueReferenceSearchParams) => listCatalogueReferences(token, '/brands', params),
    get: (token: string, id: string) => getCatalogueReference(token, '/brands', id),
    create: (token: string, payload: CatalogueReferencePayload) => createCatalogueReference(token, '/brands', payload),
    update: (token: string, id: string, payload: CatalogueReferenceUpdatePayload) => updateCatalogueReference(token, '/brands', id, payload),
    updateStatus: (token: string, id: string, payload: CatalogueReferenceStatusPayload) => updateCatalogueReferenceStatus(token, '/brands', id, payload)
  },
  units: {
    list: (token: string, params?: CatalogueReferenceSearchParams) => listCatalogueReferences(token, '/units', params),
    get: (token: string, id: string) => getCatalogueReference(token, '/units', id),
    create: (token: string, payload: CatalogueReferencePayload) => createCatalogueReference(token, '/units', payload),
    update: (token: string, id: string, payload: CatalogueReferenceUpdatePayload) => updateCatalogueReference(token, '/units', id, payload),
    updateStatus: (token: string, id: string, payload: CatalogueReferenceStatusPayload) => updateCatalogueReferenceStatus(token, '/units', id, payload)
  }
};

export function listCountries(token: string, params: CountrySearchParams = {}) {
  return request<CountryListResponse>(`/tax/countries${queryString(params)}`, undefined, token);
}

export function getCountry(token: string, id: string) {
  return request<Country>(`/tax/countries/${id}`, undefined, token);
}

export function createCountry(token: string, payload: CountryPayload) {
  return request<Country>('/tax/countries', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateCountry(token: string, id: string, payload: CountryUpdatePayload) {
  return request<Country>(`/tax/countries/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateCountryStatus(token: string, id: string, payload: CountryStatusPayload) {
  return request<Country>(`/tax/countries/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listAdministrativeAreas(token: string, params: AdministrativeAreaSearchParams = {}) {
  return request<AdministrativeAreaListResponse>(`/tax/administrative-areas${queryString(params)}`, undefined, token);
}

export function getAdministrativeArea(token: string, id: string) {
  return request<AdministrativeArea>(`/tax/administrative-areas/${id}`, undefined, token);
}

export function createAdministrativeArea(token: string, payload: AdministrativeAreaPayload) {
  return request<AdministrativeArea>('/tax/administrative-areas', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateAdministrativeArea(token: string, id: string, payload: AdministrativeAreaUpdatePayload) {
  return request<AdministrativeArea>(`/tax/administrative-areas/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateAdministrativeAreaStatus(token: string, id: string, payload: AdministrativeAreaStatusPayload) {
  return request<AdministrativeArea>(`/tax/administrative-areas/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listTaxJurisdictions(token: string, params: TaxJurisdictionSearchParams = {}) {
  return request<TaxJurisdictionListResponse>(`/tax/jurisdictions${queryString(params)}`, undefined, token);
}

export function getTaxJurisdiction(token: string, id: string) {
  return request<TaxJurisdiction>(`/tax/jurisdictions/${id}`, undefined, token);
}

export function createTaxJurisdiction(token: string, payload: TaxJurisdictionPayload) {
  return request<TaxJurisdiction>('/tax/jurisdictions', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxJurisdiction(token: string, id: string, payload: TaxJurisdictionUpdatePayload) {
  return request<TaxJurisdiction>(`/tax/jurisdictions/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxJurisdictionStatus(token: string, id: string, payload: TaxJurisdictionStatusPayload) {
  return request<TaxJurisdiction>(`/tax/jurisdictions/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listTaxTypes(token: string, params: TaxTypeSearchParams = {}) {
  return request<TaxTypeListResponse>(`/tax/types${queryString(params)}`, undefined, token);
}

export function getTaxType(token: string, id: string) {
  return request<TaxType>(`/tax/types/${id}`, undefined, token);
}

export function createTaxType(token: string, payload: TaxTypePayload) {
  return request<TaxType>('/tax/types', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxType(token: string, id: string, payload: TaxTypeUpdatePayload) {
  return request<TaxType>(`/tax/types/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxTypeStatus(token: string, id: string, payload: TaxTypeStatusPayload) {
  return request<TaxType>(`/tax/types/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listTaxComponents(token: string, params: TaxComponentSearchParams = {}) {
  return request<TaxComponentListResponse>(`/tax/components${queryString(params)}`, undefined, token);
}

export function getTaxComponent(token: string, id: string) {
  return request<TaxComponent>(`/tax/components/${id}`, undefined, token);
}

export function createTaxComponent(token: string, payload: TaxComponentPayload) {
  return request<TaxComponent>('/tax/components', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxComponent(token: string, id: string, payload: TaxComponentUpdatePayload) {
  return request<TaxComponent>(`/tax/components/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxComponentStatus(token: string, id: string, payload: TaxComponentStatusPayload) {
  return request<TaxComponent>(`/tax/components/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listTaxRates(token: string, params: TaxRateSearchParams = {}) {
  return request<TaxRateListResponse>(`/tax/rates${queryString(params)}`, undefined, token);
}

export function getTaxRate(token: string, id: string) {
  return request<TaxRate>(`/tax/rates/${id}`, undefined, token);
}

export function createTaxRate(token: string, payload: TaxRatePayload) {
  return request<TaxRate>('/tax/rates', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxRate(token: string, id: string, payload: TaxRateUpdatePayload) {
  return request<TaxRate>(`/tax/rates/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxRateStatus(token: string, id: string, payload: TaxRateStatusPayload) {
  return request<TaxRate>(`/tax/rates/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listTaxGroups(token: string, params: TaxGroupSearchParams = {}) {
  return request<TaxGroupListResponse>(`/tax/groups${queryString(params)}`, undefined, token);
}

export function getTaxGroup(token: string, id: string) {
  return request<TaxGroup>(`/tax/groups/${id}`, undefined, token);
}

export function createTaxGroup(token: string, payload: TaxGroupPayload) {
  return request<TaxGroup>('/tax/groups', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxGroup(token: string, id: string, payload: TaxGroupUpdatePayload) {
  return request<TaxGroup>(`/tax/groups/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxGroupStatus(token: string, id: string, payload: TaxGroupStatusPayload) {
  return request<TaxGroup>(`/tax/groups/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listTaxGroupComponents(token: string, params: TaxGroupComponentSearchParams = {}) {
  return request<TaxGroupComponentListResponse>(`/tax/group-components${queryString(params)}`, undefined, token);
}

export function getTaxGroupComponent(token: string, id: string) {
  return request<TaxGroupComponent>(`/tax/group-components/${id}`, undefined, token);
}

export function createTaxGroupComponent(token: string, payload: TaxGroupComponentPayload) {
  return request<TaxGroupComponent>('/tax/group-components', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxGroupComponent(token: string, id: string, payload: TaxGroupComponentUpdatePayload) {
  return request<TaxGroupComponent>(`/tax/group-components/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxGroupComponentStatus(token: string, id: string, payload: TaxGroupComponentStatusPayload) {
  return request<TaxGroupComponent>(`/tax/group-components/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listTaxCategories(token: string, params: TaxCategorySearchParams = {}) {
  return request<TaxCategoryListResponse>(`/tax/categories${queryString(params)}`, undefined, token);
}

export function getTaxCategory(token: string, id: string) {
  return request<TaxCategory>(`/tax/categories/${id}`, undefined, token);
}

export function createTaxCategory(token: string, payload: TaxCategoryPayload) {
  return request<TaxCategory>('/tax/categories', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxCategory(token: string, id: string, payload: TaxCategoryUpdatePayload) {
  return request<TaxCategory>(`/tax/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxCategoryStatus(token: string, id: string, payload: TaxCategoryStatusPayload) {
  return request<TaxCategory>(`/tax/categories/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listProductTaxCategoryAssignments(token: string, params: ProductTaxCategoryAssignmentSearchParams = {}) {
  return request<ProductTaxCategoryAssignmentListResponse>(`/tax/product-category-assignments${queryString(params)}`, undefined, token);
}

export function getProductTaxCategoryAssignment(token: string, id: string) {
  return request<ProductTaxCategoryAssignment>(`/tax/product-category-assignments/${id}`, undefined, token);
}

export function createProductTaxCategoryAssignment(token: string, payload: ProductTaxCategoryAssignmentPayload) {
  return request<ProductTaxCategoryAssignment>('/tax/product-category-assignments', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateProductTaxCategoryAssignment(token: string, id: string, payload: ProductTaxCategoryAssignmentUpdatePayload) {
  return request<ProductTaxCategoryAssignment>(`/tax/product-category-assignments/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateProductTaxCategoryAssignmentStatus(token: string, id: string, payload: ProductTaxCategoryAssignmentStatusPayload) {
  return request<ProductTaxCategoryAssignment>(`/tax/product-category-assignments/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listTaxRules(token: string, params: TaxRuleSearchParams = {}) {
  return request<TaxRuleListResponse>(`/tax/rules${queryString(params)}`, undefined, token);
}

export function getTaxRule(token: string, id: string) {
  return request<TaxRule>(`/tax/rules/${id}`, undefined, token);
}

export function createTaxRule(token: string, payload: TaxRulePayload) {
  return request<TaxRule>('/tax/rules', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxRule(token: string, id: string, payload: TaxRuleUpdatePayload) {
  return request<TaxRule>(`/tax/rules/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateTaxRuleStatus(token: string, id: string, payload: TaxRuleStatusPayload) {
  return request<TaxRule>(`/tax/rules/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function calculateTax(token: string, payload: TaxCalculationPayload) {
  return request<TaxCalculation>('/tax/calculate', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function listSuppliers(token: string, params: SupplierSearchParams = {}) {
  return request<SupplierListResponse>(`/suppliers${queryString(params)}`, undefined, token);
}

export function getSupplier(token: string, id: string) {
  return request<Supplier>(`/suppliers/${id}`, undefined, token);
}

export function createSupplier(token: string, payload: SupplierPayload) {
  return request<Supplier>('/suppliers', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateSupplier(token: string, id: string, payload: SupplierUpdatePayload) {
  return request<Supplier>(`/suppliers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateSupplierStatus(token: string, id: string, payload: SupplierStatusPayload) {
  return request<Supplier>(`/suppliers/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}

export function listProductSuppliers(token: string, params: ProductSupplierSearchParams = {}) {
  return request<ProductSupplierListResponse>(`/product-suppliers${queryString(params)}`, undefined, token);
}

export function getProductSupplier(token: string, id: string) {
  return request<ProductSupplier>(`/product-suppliers/${id}`, undefined, token);
}

export function createProductSupplier(token: string, payload: ProductSupplierPayload) {
  return request<ProductSupplier>('/product-suppliers', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, token);
}

export function updateProductSupplier(token: string, id: string, payload: ProductSupplierUpdatePayload) {
  return request<ProductSupplier>(`/product-suppliers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, token);
}

export function updateProductSupplierStatus(token: string, id: string, payload: ProductSupplierStatusPayload) {
  return request<ProductSupplier>(`/product-suppliers/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, token);
}
