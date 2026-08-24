import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import { applicationDeviceIdentifierKey } from '../../app/deviceIdentity';
import type {
  AuthResponse,
  CurrentUserResponse,
  Device,
  DeviceListResponse,
  FeatureResolution,
  LotteryOperator,
  LotteryOperatorListResponse,
  LotteryPayout,
  LotteryPayoutCashAvailability,
  LotteryPayoutPolicy,
  Register,
  RegisterListResponse,
  RegisterSession,
  Store,
  StoreListResponse,
  UserRole
} from '../../api/types';

const storeId = '00000000-0000-0000-0000-000000000802';
const registerId = '00000000-0000-0000-0000-000000000803';
const deviceId = '00000000-0000-0000-0000-000000000804';
const sessionId = '00000000-0000-0000-0000-000000000805';
const operatorId = '00000000-0000-0000-0000-000000000801';
const policyId = '00000000-0000-0000-0000-000000000811';
const cashierId = '00000000-0000-0000-0000-000000000806';
const payoutId = '00000000-0000-0000-0000-000000000900';

function authResponse(roles: UserRole[] = ['CASHIER']): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: cashierId,
    email: 'cashier@example.local',
    displayName: 'Cashier One',
    roles
  };
}

function currentUser(roles: UserRole[] = ['CASHIER']): CurrentUserResponse {
  return {
    userId: cashierId,
    email: 'cashier@example.local',
    displayName: 'Cashier One',
    roles
  };
}

function featureResolution(enabled = true): FeatureResolution[] {
  return [{
    definition: {
      id: '00000000-0000-0000-0000-00000000f101',
      code: 'LOTTERY_SALES',
      name: 'Lottery sales',
      description: 'Lottery sales workflows',
      defaultEnabled: false,
      createdAt: '2026-07-28T12:00:00Z',
      updatedAt: '2026-07-28T12:00:00Z',
      version: 0
    },
    enabled,
    source: 'TENANT',
    storeId,
    registerId,
    tenantOverride: null,
    storeOverride: null,
    registerOverride: null
  }];
}

function store(): Store {
  return {
    id: storeId,
    code: 'MAIN',
    name: 'Main Store',
    legalName: 'Main Store LLC',
    countryCode: 'US',
    administrativeAreaCode: 'CA',
    address: '100 Market Street',
    phone: null,
    email: null,
    currencyCode: 'USD',
    locale: 'en-US',
    timezone: 'America/Los_Angeles',
    pricesIncludeTax: false,
    negativeStockAllowed: false,
    active: true,
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0
  };
}

function register(): Register {
  return {
    id: registerId,
    storeId,
    code: 'FRONT',
    name: 'Front Register',
    locationDescription: null,
    active: true,
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0
  };
}

function device(): Device {
  return {
    id: deviceId,
    storeId,
    registerId,
    deviceIdentifier: 'browser:test-device',
    displayName: 'Front Browser',
    deviceType: 'BROWSER_POS',
    registeredAt: '2026-07-28T12:00:00Z',
    lastSeenAt: '2026-07-28T12:00:00Z',
    active: true,
    version: 0
  };
}

function registerSession(): RegisterSession {
  return {
    id: sessionId,
    storeId,
    registerId,
    deviceId,
    assignedCashierId: cashierId,
    assignedCashierEmail: 'cashier@example.local',
    assignedCashierDisplayName: 'Cashier One',
    status: 'OPEN',
    openingCash: 100,
    expectedCash: 300,
    countedCash: null,
    expectedCashAtClose: null,
    differenceCash: null,
    closedByUserId: null,
    closedByEmail: null,
    closedByDisplayName: null,
    closedAt: null,
    forceCloseReason: null,
    reconciliation: null,
    openedAt: '2026-07-28T12:00:00Z',
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0
  };
}

function operator(): LotteryOperator {
  return {
    id: operatorId,
    code: 'STATE',
    name: 'State Lottery',
    jurisdictionId: '00000000-0000-0000-0000-000000000807',
    jurisdictionCode: 'CA',
    jurisdictionName: 'California',
    supportContact: null,
    settlementFrequency: 'WEEKLY',
    active: true,
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0
  };
}

function policy(): LotteryPayoutPolicy {
  return {
    id: policyId,
    operatorId,
    operatorCode: 'STATE',
    operatorName: 'State Lottery',
    jurisdictionId: '00000000-0000-0000-0000-000000000807',
    jurisdictionCode: 'CA',
    jurisdictionName: 'California',
    storeId,
    storeCode: 'MAIN',
    storeName: 'Main Store',
    maximumCashPayout: 400,
    cashierApprovalLimit: 100,
    managerApprovalThreshold: 250,
    operatorReferralThreshold: 500,
    protectedRegisterFloat: 50,
    allowCashPayout: true,
    allowStoreCredit: true,
    requireTicketValidation: true,
    requireAgeVerification: true,
    requireCustomerIdentification: true,
    allowAlternateRegister: false,
    effectiveFrom: '2026-07-28',
    effectiveTo: null,
    status: 'ACTIVE',
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0
  };
}

function availability(): LotteryPayoutCashAvailability {
  return {
    registerSessionId: sessionId,
    policyId,
    expectedDrawerCash: 300,
    protectedRegisterFloat: 50,
    reservedObligations: 0,
    availablePayoutCash: 250,
    currencyCode: 'USD'
  };
}

function payout(status: LotteryPayout['status'], amount = 75, version = 0): LotteryPayout {
  return {
    id: payoutId,
    operatorId,
    operatorCode: 'STATE',
    operatorName: 'State Lottery',
    policyId,
    storeId,
    storeCode: 'MAIN',
    storeName: 'Main Store',
    registerId,
    registerCode: 'FRONT',
    registerName: 'Front Register',
    deviceId,
    deviceIdentifier: 'browser:test-device',
    deviceDisplayName: 'Front Browser',
    cashierId,
    cashierEmail: 'cashier@example.local',
    cashierDisplayName: 'Cashier One',
    registerSessionId: sessionId,
    ticketNumber: 'PAY-99',
    validationReference: status === 'DRAFT' ? null : 'VALID-99',
    amount,
    currencyCode: 'USD',
    payoutMethod: 'CASH',
    status,
    ticketValidationState: status === 'DRAFT' ? 'PENDING' : 'VERIFIED',
    ageVerificationState: status === 'DRAFT' ? 'PENDING' : 'VERIFIED',
    identificationVerificationState: status === 'DRAFT' ? 'PENDING' : 'VERIFIED',
    cashierApprovalLimit: 100,
    managerApprovalThreshold: 250,
    operatorReferralThreshold: 500,
    maximumCashPayout: 400,
    ticketValidationRequired: true,
    ageVerificationRequired: true,
    identificationRequired: true,
    alternateRegisterAllowed: false,
    businessDate: '2026-07-28',
    occurredAt: '2026-07-28T12:05:00Z',
    validatedBy: status === 'DRAFT' ? null : cashierId,
    validatedAt: status === 'DRAFT' ? null : '2026-07-28T12:06:00Z',
    authorizedBy: status === 'AUTHORIZED' || status === 'PAID' ? cashierId : null,
    authorizedAt: status === 'AUTHORIZED' || status === 'PAID' ? '2026-07-28T12:07:00Z' : null,
    paidBy: status === 'PAID' ? cashierId : null,
    paidAt: status === 'PAID' ? '2026-07-28T12:08:00Z' : null,
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    notes: 'Ticket type: Draw ticket',
    approvals: status === 'AUTHORIZED' || status === 'PAID' ? [{
      id: '00000000-0000-0000-0000-000000000901',
      approvalType: amount > 100 ? 'MANAGER_APPROVAL' : 'CASHIER_LIMIT',
      approvedBy: cashierId,
      approvedByEmail: 'cashier@example.local',
      approvedByDisplayName: 'Cashier One',
      approvedAt: '2026-07-28T12:07:00Z',
      payoutAmount: amount,
      thresholdAmount: amount > 100 ? 250 : 100,
      notes: null,
      createdAt: '2026-07-28T12:07:00Z',
      updatedAt: '2026-07-28T12:07:00Z',
      version: 0
    }] : [],
    createdAt: '2026-07-28T12:05:00Z',
    updatedAt: '2026-07-28T12:05:00Z',
    version
  };
}

function pageResponse<T>(content: T[]) {
  return {
    content,
    page: 0,
    size: 100,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
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

function apiError(message: string, status = 500) {
  return jsonResponse({
    code: 'unexpected_request',
    message,
    status,
    path: '/api/v1/lottery/payouts',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['CASHIER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
  window.localStorage.setItem(applicationDeviceIdentifierKey, 'browser:test-device');
}

function mockContext(fetchMock: ReturnType<typeof vi.spyOn>) {
  fetchMock.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), window.location.origin);
    if (url.pathname.endsWith('/api/v1/auth/me')) {
      return jsonResponse(currentUser(['CASHIER']));
    }
    if (url.pathname.endsWith('/api/v1/register-sessions/current')) {
      expect(url.searchParams.get('deviceIdentifier')).toBe('browser:test-device');
      return jsonResponse(registerSession());
    }
    if (url.pathname.endsWith('/api/v1/stores')) {
      return jsonResponse(pageResponse<Store>([store()]) satisfies StoreListResponse);
    }
    if (url.pathname.endsWith('/api/v1/registers')) {
      return jsonResponse(pageResponse<Register>([register()]) satisfies RegisterListResponse);
    }
    if (url.pathname.endsWith('/api/v1/devices')) {
      return jsonResponse(pageResponse<Device>([device()]) satisfies DeviceListResponse);
    }
    if (url.pathname.endsWith('/api/v1/features/resolution')) {
      return jsonResponse(featureResolution(true));
    }
    if (url.pathname.endsWith('/api/v1/lottery/operators')) {
      return jsonResponse(pageResponse<LotteryOperator>([operator()]) satisfies LotteryOperatorListResponse);
    }
    if (url.pathname.endsWith('/api/v1/lottery/payouts/available-cash')) {
      expect(url.searchParams.get('registerSessionId')).toBe(sessionId);
      expect(url.searchParams.get('operatorId')).toBe(operatorId);
      return jsonResponse(availability());
    }
    if (url.pathname.endsWith(`/api/v1/lottery/payout-policies/${policyId}`)) {
      return jsonResponse(policy());
    }
    if (url.pathname.endsWith('/api/v1/lottery/payouts') && init?.method === 'POST') {
      const body = JSON.parse(String(init.body));
      return jsonResponse(payout('DRAFT', body.amount, 0), 201);
    }
    if (url.pathname.endsWith(`/api/v1/lottery/payouts/${payoutId}/validate`) && init?.method === 'POST') {
      const body = JSON.parse(String(init.body));
      const createCall = fetchMock.mock.calls.find((call: unknown[]) => {
        const [callInput, callInit] = call as [RequestInfo | URL, RequestInit | undefined];
        const callUrl = new URL(String(callInput), window.location.origin);
        return callUrl.pathname.endsWith('/api/v1/lottery/payouts') && callInit?.method === 'POST';
      });
      const createdAmount = body.version === 0
        ? JSON.parse(String((createCall?.[1] as RequestInit | undefined)?.body ?? '{}')).amount ?? 75
        : 75;
      return jsonResponse(payout('VALIDATED', createdAmount, 1));
    }
    if (url.pathname.endsWith(`/api/v1/lottery/payouts/${payoutId}/authorize`) && init?.method === 'POST') {
      return jsonResponse(payout('AUTHORIZED', 75, 2));
    }
    if (url.pathname.endsWith(`/api/v1/lottery/payouts/${payoutId}/complete-cash`) && init?.method === 'POST') {
      return jsonResponse(payout('PAID', 75, 3));
    }
    return apiError(`Unexpected request: ${url.pathname}`);
  });
}

describe('Lottery payout page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('completes a cash payout inside cashier approval limits', async () => {
    storeSession(['CASHIER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    mockContext(fetchMock);

    render(<App initialEntries={['/lottery/payout']} />);

    expect(await screen.findByRole('heading', { name: 'Lottery payout' })).toBeInTheDocument();
    const operatorSelect = await screen.findByRole('combobox', { name: 'Operator' });
    fireEvent.mouseDown(operatorSelect);
    await userEvent.click(await screen.findByText('State Lottery (STATE)'));
    await screen.findByText('Cashier limit approval');

    await userEvent.type(screen.getByLabelText('Payout reference'), 'PAY-99');
    await userEvent.type(screen.getByLabelText('Validation reference'), 'VALID-99');
    const amount = screen.getByLabelText('Prize amount');
    await userEvent.clear(amount);
    await userEvent.type(amount, '75.00');
    await userEvent.type(screen.getByLabelText('Notes'), 'Customer signed ticket');
    await userEvent.click(screen.getByRole('button', { name: 'Submit payout' }));

    await screen.findByText('Lottery payout Paid');
    const createCall = fetchMock.mock.calls.find(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith('/api/v1/lottery/payouts') && init?.method === 'POST';
    });
    expect(createCall).toBeTruthy();
    expect(JSON.parse(String(createCall![1]?.body))).toMatchObject({
      operatorId,
      storeId,
      registerId,
      deviceId,
      registerSessionId: sessionId,
      ticketNumber: 'PAY-99',
      amount: 75,
      payoutMethod: 'CASH'
    });
    const completeCall = fetchMock.mock.calls.find(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith(`/api/v1/lottery/payouts/${payoutId}/complete-cash`) && init?.method === 'POST';
    });
    expect(completeCall).toBeTruthy();
    expect(new Headers(completeCall![1]?.headers).get('Idempotency-Key')).toBeTruthy();
  });

  it('validates but does not approve manager-required payouts for cashiers', async () => {
    storeSession(['CASHIER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    mockContext(fetchMock);

    render(<App initialEntries={['/lottery/payout']} />);

    const operatorSelect = await screen.findByRole('combobox', { name: 'Operator' });
    fireEvent.mouseDown(operatorSelect);
    await userEvent.click(await screen.findByText('State Lottery (STATE)'));

    await userEvent.type(screen.getByLabelText('Payout reference'), 'PAY-99');
    await userEvent.type(screen.getByLabelText('Validation reference'), 'VALID-99');
    const amount = screen.getByLabelText('Prize amount');
    await userEvent.clear(amount);
    await userEvent.type(amount, '150.00');
    await screen.findByText('Manager approval required');
    await userEvent.click(screen.getByRole('button', { name: 'Submit payout' }));

    await screen.findByText('Manager approval required. The payout was validated but not approved or completed.');
    await screen.findByText('Lottery payout Validated');
    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith(`/api/v1/lottery/payouts/${payoutId}/authorize`) && init?.method === 'POST';
      })).toBe(false);
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith(`/api/v1/lottery/payouts/${payoutId}/complete-cash`) && init?.method === 'POST';
      })).toBe(false);
    });
  });
});
