import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  Register,
  RegisterListResponse,
  RegisterReport,
  Store,
  StoreListResponse,
  UserAdmin,
  UserAdminListResponse,
  UserRole
} from '../../api/types';

const STORE_ID = '00000000-0000-0000-0000-000000000701';
const REGISTER_ID = '00000000-0000-0000-0000-000000000702';
const CASHIER_ID = '00000000-0000-0000-0000-000000000703';
const SESSION_ID = '00000000-0000-0000-0000-000000000704';

function authResponse(roles: UserRole[] = ['OWNER']): AuthResponse {
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

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: CASHIER_ID,
    email: 'manager@example.local',
    displayName: 'Manager User',
    roles
  };
}

function store(): Store {
  return {
    id: STORE_ID,
    code: 'MAIN',
    name: 'Main Store',
    legalName: null,
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
    locationDescription: 'Front counter',
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

function registerReport(): RegisterReport {
  return {
    storeId: null,
    registerId: null,
    cashierId: null,
    status: null,
    dateFrom: '2026-07-01',
    dateTo: '2026-07-31',
    openingCash: 100,
    retailCash: 220,
    retailCashReceived: 250,
    retailChange: 30,
    lotteryCash: 50,
    lotteryCashSales: 80,
    lotteryPayouts: 25,
    payoutReversals: 5,
    lotterySaleCancellations: 10,
    refunds: 12,
    cashMovements: 25,
    cashMovementIn: 40,
    cashMovementOut: 15,
    expectedCash: 383,
    countedCash: 380,
    variance: -3,
    sessionCount: 1,
    closedSessionCount: 1,
    rows: [{
      registerSessionId: SESSION_ID,
      storeId: STORE_ID,
      storeCode: 'MAIN',
      storeName: 'Main Store',
      registerId: REGISTER_ID,
      registerCode: 'R1',
      registerName: 'Front Register',
      cashierId: CASHIER_ID,
      cashierEmail: 'cashier@example.local',
      cashierDisplayName: 'Ada Cashier',
      status: 'CLOSED',
      currencyCode: 'USD',
      openingCash: 100,
      retailCash: 220,
      retailCashReceived: 250,
      retailChange: 30,
      lotteryCash: 50,
      lotteryCashSales: 80,
      lotteryPayouts: 25,
      payoutReversals: 5,
      lotterySaleCancellations: 10,
      refunds: 12,
      cashMovements: 25,
      cashMovementIn: 40,
      cashMovementOut: 15,
      expectedCash: 383,
      countedCash: 380,
      variance: -3,
      openedAt: '2026-07-29T08:00:00Z',
      closedAt: '2026-07-29T16:00:00Z'
    }],
    generatedAt: '2026-07-29T17:00:00Z'
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
    path: '/api/v1/reports/registers',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

function mockRegisterReportApi() {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = new URL(String(input), window.location.origin);
    if (url.pathname.endsWith('/api/v1/auth/me')) {
      return jsonResponse(currentUser(['OWNER']));
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
    if (url.pathname.endsWith('/api/v1/reports/registers')) {
      return jsonResponse(registerReport());
    }
    return apiError('Unexpected request');
  });
}

describe('Register reports page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:register-report');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  });

  it('renders register totals, charts, filters, and CSV export', async () => {
    storeSession(['OWNER']);
    const fetchMock = mockRegisterReportApi();

    render(<App initialEntries={['/reports/registers']} />);

    expect(await screen.findByRole('heading', { name: 'Register reports' })).toBeInTheDocument();
    expect((await screen.findAllByText('Opening cash'))[0]).toBeInTheDocument();
    expect(screen.getAllByText('$100.00')[0]).toBeInTheDocument();
    expect(screen.getAllByText('$220.00')[0]).toBeInTheDocument();
    expect(screen.getAllByText('$50.00')[0]).toBeInTheDocument();
    expect(screen.getAllByText('$12.00')[0]).toBeInTheDocument();
    expect(screen.getAllByText('$383.00')[0]).toBeInTheDocument();
    expect(screen.getAllByText('-$3.00')[0]).toBeInTheDocument();
    expect(screen.getByText('Cash sources')).toBeInTheDocument();
    expect(screen.getByText('Reconciliation')).toBeInTheDocument();
    expect(screen.getByText('Front Register (R1)')).toBeInTheDocument();
    expect(screen.getByText('Ada Cashier')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Store'));
    await userEvent.click(await screen.findByRole('option', { name: 'Main Store (MAIN)' }));
    await userEvent.click(screen.getByLabelText('Register'));
    await userEvent.click(await screen.findByRole('option', { name: 'Front Register (R1)' }));
    await userEvent.click(screen.getByLabelText('Cashier'));
    await userEvent.click(await screen.findByRole('option', { name: 'Ada Cashier (cashier@example.local)' }));
    await userEvent.click(screen.getByLabelText('Status'));
    await userEvent.click(await screen.findByRole('option', { name: 'Closed' }));
    await userEvent.clear(screen.getByLabelText('Date from'));
    await userEvent.type(screen.getByLabelText('Date from'), '2026-07-01');
    await userEvent.clear(screen.getByLabelText('Date to'));
    await userEvent.type(screen.getByLabelText('Date to'), '2026-07-31');

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/reports/registers')
          && url.searchParams.get('storeId') === STORE_ID
          && url.searchParams.get('registerId') === REGISTER_ID
          && url.searchParams.get('cashierId') === CASHIER_ID
          && url.searchParams.get('status') === 'CLOSED'
          && url.searchParams.get('dateFrom') === '2026-07-01'
          && url.searchParams.get('dateTo') === '2026-07-31';
      })).toBe(true);
    });

    await userEvent.click(screen.getByRole('button', { name: 'Export CSV' }));
    expect(URL.createObjectURL).toHaveBeenCalled();
  });
});
