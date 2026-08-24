import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  LotteryOperator,
  LotteryOperatorListResponse,
  LotteryPayout,
  LotteryReport,
  Register,
  RegisterListResponse,
  Store,
  StoreListResponse,
  UserAdmin,
  UserAdminListResponse,
  UserRole
} from '../../api/types';

const OPERATOR_ID = '00000000-0000-0000-0000-000000000801';
const STORE_ID = '00000000-0000-0000-0000-000000000802';
const REGISTER_ID = '00000000-0000-0000-0000-000000000803';
const CASHIER_ID = '00000000-0000-0000-0000-000000000804';
const PAYOUT_ID = '00000000-0000-0000-0000-000000000805';

function authResponse(roles: UserRole[] = ['MANAGER']): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: CASHIER_ID,
    email: 'manager@example.local',
    displayName: 'Manager User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['MANAGER']): CurrentUserResponse {
  return {
    userId: CASHIER_ID,
    email: 'manager@example.local',
    displayName: 'Manager User',
    roles
  };
}

function operator(): LotteryOperator {
  return {
    id: OPERATOR_ID,
    code: 'ATL',
    name: 'Atlantic Lottery',
    jurisdictionId: '00000000-0000-0000-0000-000000000806',
    jurisdictionCode: 'NB',
    jurisdictionName: 'New Brunswick',
    supportContact: null,
    settlementFrequency: 'WEEKLY',
    active: true,
    createdAt: '2026-07-27T12:00:00Z',
    updatedAt: '2026-07-27T12:00:00Z',
    version: 0
  };
}

function store(): Store {
  return {
    id: STORE_ID,
    code: 'MAIN',
    name: 'Main Store',
    legalName: null,
    countryCode: 'US',
    administrativeAreaCode: 'ME',
    address: '100 Market Street',
    phone: null,
    email: null,
    currencyCode: 'USD',
    locale: 'en-US',
    timezone: 'America/New_York',
    pricesIncludeTax: false,
    negativeStockAllowed: false,
    active: true,
    createdAt: '2026-07-27T12:00:00Z',
    updatedAt: '2026-07-27T12:00:00Z',
    version: 0
  };
}

function register(): Register {
  return {
    id: REGISTER_ID,
    storeId: STORE_ID,
    code: 'R1',
    name: 'Front Register',
    locationDescription: null,
    active: true,
    createdAt: '2026-07-27T12:00:00Z',
    updatedAt: '2026-07-27T12:00:00Z',
    version: 0
  };
}

function cashier(): UserAdmin {
  return {
    id: CASHIER_ID,
    email: 'cashier@example.local',
    displayName: 'Ada Cashier',
    enabled: true,
    locked: false,
    roles: ['CASHIER'],
    storeIds: [STORE_ID],
    registerIds: [REGISTER_ID],
    createdAt: '2026-07-27T12:00:00Z',
    updatedAt: '2026-07-27T12:00:00Z',
    version: 0
  };
}

function payout(status: LotteryPayout['status'] = 'PAID', amount = 40): LotteryPayout {
  return {
    id: status === 'REFERRED_TO_OPERATOR' ? '00000000-0000-0000-0000-000000000815' : PAYOUT_ID,
    operatorId: OPERATOR_ID,
    operatorCode: 'ATL',
    operatorName: 'Atlantic Lottery',
    policyId: '00000000-0000-0000-0000-000000000807',
    storeId: STORE_ID,
    storeCode: 'MAIN',
    storeName: 'Main Store',
    registerId: REGISTER_ID,
    registerCode: 'R1',
    registerName: 'Front Register',
    deviceId: '00000000-0000-0000-0000-000000000808',
    deviceIdentifier: 'POS-1',
    deviceDisplayName: 'POS 1',
    cashierId: CASHIER_ID,
    cashierEmail: 'cashier@example.local',
    cashierDisplayName: 'Ada Cashier',
    registerSessionId: null,
    ticketNumber: status === 'REFERRED_TO_OPERATOR' ? 'REF-777' : 'WIN-123',
    validationReference: 'VAL-123',
    amount,
    currencyCode: 'USD',
    payoutMethod: 'CASH',
    status,
    ticketValidationState: 'VERIFIED',
    ageVerificationState: 'NOT_REQUIRED',
    identificationVerificationState: 'NOT_REQUIRED',
    cashierApprovalLimit: 50,
    managerApprovalThreshold: 500,
    operatorReferralThreshold: 1000,
    maximumCashPayout: 999,
    ticketValidationRequired: true,
    ageVerificationRequired: false,
    identificationRequired: false,
    alternateRegisterAllowed: true,
    businessDate: '2026-07-27',
    occurredAt: '2026-07-27T11:00:00Z',
    validatedBy: null,
    validatedAt: null,
    authorizedBy: null,
    authorizedAt: null,
    paidBy: null,
    paidAt: null,
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    notes: null,
    approvals: [],
    createdAt: '2026-07-27T11:00:00Z',
    updatedAt: '2026-07-27T11:00:00Z',
    version: 0
  };
}

function lotteryReport(): LotteryReport {
  const paidPayout = payout('PAID', 40);
  const referralPayout = payout('REFERRED_TO_OPERATOR', 75);
  return {
    operatorId: null,
    storeId: null,
    registerId: null,
    cashierId: null,
    dateFrom: '2026-07-01',
    dateTo: '2026-07-31',
    sales: 120,
    saleCount: 1,
    payouts: 40,
    payoutCount: 2,
    approvals: 40,
    approvalCount: 1,
    reversals: 10,
    reversalCount: 1,
    referrals: 75,
    referralCount: 1,
    cancellations: 20,
    cancellationCount: 1,
    commission: 12,
    calculatedSettlement: 58,
    settlement: 58,
    variance: 0,
    saleRows: [{
      id: '00000000-0000-0000-0000-000000000809',
      operatorId: OPERATOR_ID,
      operatorCode: 'ATL',
      operatorName: 'Atlantic Lottery',
      operatorReference: 'SALE-REF',
      ticketReference: 'DRAW-55',
      gameType: 'DRAW_TICKET',
      amount: 120,
      currencyCode: 'USD',
      paymentMethod: 'CASH',
      storeId: STORE_ID,
      storeCode: 'MAIN',
      storeName: 'Main Store',
      registerId: REGISTER_ID,
      registerCode: 'R1',
      registerName: 'Front Register',
      deviceId: '00000000-0000-0000-0000-000000000808',
      deviceIdentifier: 'POS-1',
      deviceDisplayName: 'POS 1',
      cashierId: CASHIER_ID,
      cashierEmail: 'cashier@example.local',
      cashierDisplayName: 'Ada Cashier',
      registerSessionId: null,
      status: 'RECORDED',
      operationId: '00000000-0000-0000-0000-000000000810',
      occurredAt: '2026-07-27T10:00:00Z',
      createdAt: '2026-07-27T10:00:00Z',
      updatedAt: '2026-07-27T10:00:00Z',
      version: 0
    }],
    payoutRows: [paidPayout, referralPayout],
    approvalRows: [{
      id: '00000000-0000-0000-0000-000000000811',
      payoutId: PAYOUT_ID,
      ticketNumber: 'WIN-123',
      operatorId: OPERATOR_ID,
      operatorCode: 'ATL',
      operatorName: 'Atlantic Lottery',
      storeId: STORE_ID,
      storeCode: 'MAIN',
      storeName: 'Main Store',
      registerId: REGISTER_ID,
      registerCode: 'R1',
      registerName: 'Front Register',
      cashierId: CASHIER_ID,
      cashierEmail: 'cashier@example.local',
      cashierDisplayName: 'Ada Cashier',
      approvalType: 'MANAGER_APPROVAL',
      approvedBy: '00000000-0000-0000-0000-000000000812',
      approvedByEmail: 'manager@example.local',
      approvedByDisplayName: 'Manager User',
      approvedAt: '2026-07-27T11:30:00Z',
      payoutAmount: 40,
      thresholdAmount: 50,
      notes: 'Approved'
    }],
    reversalRows: [{
      id: '00000000-0000-0000-0000-000000000813',
      originalPayoutId: PAYOUT_ID,
      reversedBy: CASHIER_ID,
      reversedByEmail: 'cashier@example.local',
      reversedByDisplayName: 'Ada Cashier',
      amount: 10,
      currencyCode: 'USD',
      operationId: '00000000-0000-0000-0000-000000000814',
      reversedAt: '2026-07-27T12:00:00Z',
      reason: 'Ticket voided',
      createdAt: '2026-07-27T12:00:00Z',
      updatedAt: '2026-07-27T12:00:00Z',
      version: 0
    }],
    referralRows: [referralPayout],
    cancellationRows: [],
    commissionRows: [{
      settlementId: '00000000-0000-0000-0000-000000000816',
      operatorId: OPERATOR_ID,
      operatorCode: 'ATL',
      operatorName: 'Atlantic Lottery',
      storeId: STORE_ID,
      storeCode: 'MAIN',
      storeName: 'Main Store',
      periodStart: '2026-07-01',
      periodEnd: '2026-07-31',
      grossSales: 120,
      totalPayouts: 40,
      commission: 12,
      expectedSettlement: 58,
      status: 'CALCULATED'
    }],
    settlementRows: [{
      id: '00000000-0000-0000-0000-000000000816',
      operatorId: OPERATOR_ID,
      operatorCode: 'ATL',
      operatorName: 'Atlantic Lottery',
      jurisdictionId: '00000000-0000-0000-0000-000000000806',
      jurisdictionCode: 'NB',
      jurisdictionName: 'New Brunswick',
      storeId: STORE_ID,
      storeCode: 'MAIN',
      storeName: 'Main Store',
      periodStart: '2026-07-01',
      periodEnd: '2026-07-31',
      grossSales: 120,
      totalPayouts: 40,
      cancellations: 20,
      adjustments: 10,
      commission: 12,
      expectedSettlement: 58,
      currencyCode: 'USD',
      calculatedAt: '2026-07-29T12:00:00Z',
      status: 'CALCULATED',
      approvedBy: null,
      approvedByEmail: null,
      approvedByDisplayName: null,
      approvedAt: null,
      postedBy: null,
      postedByEmail: null,
      postedByDisplayName: null,
      postedAt: null,
      reopenedBy: null,
      reopenedByEmail: null,
      reopenedByDisplayName: null,
      reopenedAt: null,
      reopenReason: null,
      lifecycleNotes: null,
      createdAt: '2026-07-29T12:00:00Z',
      updatedAt: '2026-07-29T12:00:00Z',
      version: 0
    }],
    chartRows: [{
      date: '2026-07-27',
      sales: 120,
      payouts: 40,
      reversals: 10,
      referrals: 75,
      settlement: 0
    }, {
      date: '2026-07-31',
      sales: 0,
      payouts: 0,
      reversals: 0,
      referrals: 0,
      settlement: 58
    }],
    generatedAt: '2026-07-29T12:00:00Z'
  };
}

function page<T>(content: T[]) {
  return {
    content,
    page: 0,
    size: 100,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true
  };
}

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  }));
}

function apiError(message: string, status = 500, code = 'unexpected') {
  return jsonResponse({
    code,
    message,
    status,
    path: '/api/v1/reports/lottery',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['MANAGER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

function mockLotteryReportApi() {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = new URL(String(input), window.location.origin);
    if (url.pathname.endsWith('/api/v1/auth/me')) {
      return jsonResponse(currentUser(['MANAGER']));
    }
    if (url.pathname.endsWith('/api/v1/lottery/operators')) {
      return jsonResponse(page<LotteryOperator>([operator()]) satisfies LotteryOperatorListResponse);
    }
    if (url.pathname.endsWith('/api/v1/stores')) {
      return jsonResponse(page<Store>([store()]) satisfies StoreListResponse);
    }
    if (url.pathname.endsWith('/api/v1/registers')) {
      return jsonResponse(page<Register>([register()]) satisfies RegisterListResponse);
    }
    if (url.pathname.endsWith('/api/v1/users')) {
      return jsonResponse(page<UserAdmin>([cashier()]) satisfies UserAdminListResponse);
    }
    if (url.pathname.endsWith('/api/v1/reports/lottery')) {
      return jsonResponse(lotteryReport());
    }
    return apiError('Unexpected request');
  });
}

describe('Lottery reports page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:lottery-report');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  });

  it('renders lottery reporting sections, applies filters, and exports CSV', async () => {
    storeSession(['MANAGER']);
    const fetchMock = mockLotteryReportApi();

    render(<App initialEntries={['/reports/lottery']} />);

    expect(await screen.findByRole('heading', { name: 'Lottery reports' })).toBeInTheDocument();
    expect((await screen.findAllByText('Sales')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Payouts').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Approvals').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Reversals').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Referrals').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Commission').length).toBeGreaterThan(0);
    expect(screen.getByText('Settlement and variance')).toBeInTheDocument();
    expect(screen.getAllByText('$120.00')[0]).toBeInTheDocument();
    expect(screen.getAllByText('Atlantic Lottery')[0]).toBeInTheDocument();
    expect(screen.getAllByText('WIN-123').length).toBeGreaterThan(0);
    expect(screen.getByText('Manager Approval')).toBeInTheDocument();
    expect(screen.getByText('Ticket voided')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Operator'));
    await userEvent.click(await screen.findByRole('option', { name: 'Atlantic Lottery (ATL)' }));
    await userEvent.click(screen.getByLabelText('Store'));
    await userEvent.click(await screen.findByRole('option', { name: 'Main Store (MAIN)' }));
    await userEvent.click(screen.getByLabelText('Register'));
    await userEvent.click(await screen.findByRole('option', { name: 'Front Register (R1)' }));
    await userEvent.click(screen.getByLabelText('Cashier'));
    await userEvent.click(await screen.findByRole('option', { name: 'Ada Cashier (cashier@example.local)' }));
    await userEvent.clear(screen.getByLabelText('Date from'));
    await userEvent.type(screen.getByLabelText('Date from'), '2026-07-01');
    await userEvent.clear(screen.getByLabelText('Date to'));
    await userEvent.type(screen.getByLabelText('Date to'), '2026-07-31');

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/reports/lottery')
          && url.searchParams.get('operatorId') === OPERATOR_ID
          && url.searchParams.get('storeId') === STORE_ID
          && url.searchParams.get('registerId') === REGISTER_ID
          && url.searchParams.get('cashierId') === CASHIER_ID
          && url.searchParams.get('dateFrom') === '2026-07-01'
          && url.searchParams.get('dateTo') === '2026-07-31';
      })).toBe(true);
    });

    await userEvent.click(screen.getByRole('button', { name: 'Export CSV' }));
    expect(URL.createObjectURL).toHaveBeenCalled();
  });
});
