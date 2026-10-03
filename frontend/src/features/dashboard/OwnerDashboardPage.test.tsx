import { render, screen, waitFor } from '@testing-library/react';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  InventoryReport,
  LotterySalesReport,
  RegisterSession,
  RegisterSessionListResponse,
  SalesReport,
  Store,
  UserRole
} from '../../api/types';

function authResponse(roles: UserRole[] = ['OWNER']): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: '00000000-0000-0000-0000-000000000901',
    email: 'owner@example.local',
    displayName: 'Owner User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000901',
    email: 'owner@example.local',
    displayName: 'Owner User',
    roles
  };
}

function salesReport(): SalesReport {
  return {
    storeId: null,
    registerId: null,
    cashierId: null,
    categoryId: null,
    productId: null,
    dateFrom: '2026-07-29',
    dateTo: '2026-07-29',
    grossSales: 1300,
    netSales: 1180,
    discounts: 35,
    refunds: 85,
    taxes: 92,
    payments: 1272,
    saleCount: 24,
    refundCount: 3,
    paymentBreakdown: [{
      method: 'CASH',
      collected: 500,
      refunded: 25,
      net: 475
    }, {
      method: 'CREDIT',
      collected: 772,
      refunded: 60,
      net: 712
    }],
    generatedAt: '2026-07-29T12:00:00Z'
  };
}

function lotteryReport(): LotterySalesReport {
  return {
    storeId: null,
    registerId: null,
    cashierId: null,
    dateFrom: '2026-07-29',
    dateTo: '2026-07-29',
    type: 'ALL',
    source: 'ALL',
    physicalTicketSales: 300,
    manualLotterySold: 120,
    totalLotterySold: 420,
    lotteryWins: 125,
    netLottery: 295,
    actualCashPayouts: 20,
    currencyCode: 'CAD',
    activities: [
      { occurredAt: '2026-07-29T09:10:00Z', register: 'R1', cashier: 'A', type: 'SOLD', source: 'MANUAL', description: 'Lottery Sold', amount: 250, receiptNumber: 'A' },
      { occurredAt: '2026-07-29T09:20:00Z', register: 'R1', cashier: 'A', type: 'WIN', source: 'MANUAL', description: 'Lottery Win', amount: 75, receiptNumber: 'A' },
      { occurredAt: '2026-07-29T11:10:00Z', register: 'R1', cashier: 'B', type: 'SOLD', source: 'PHYSICAL_TICKET', description: 'Ticket', amount: 170, receiptNumber: 'B' },
      { occurredAt: '2026-07-29T11:20:00Z', register: 'R1', cashier: 'B', type: 'WIN', source: 'MANUAL', description: 'Lottery Win', amount: 50, receiptNumber: 'B' }
    ],
    generatedAt: '2026-07-29T12:00:00Z'
  };
}

function activeStore(): Store {
  return {
    id: '00000000-0000-0000-0000-000000000902', code: 'STR001', name: 'Main Store', legalName: 'Main Store',
    countryCode: 'CA', administrativeAreaCode: 'NL', address: '1 Main St', phone: '', email: '',
    currencyCode: 'CAD', locale: 'en-CA', timezone: 'UTC', pricesIncludeTax: true,
    negativeStockAllowed: false, active: true, createdAt: '2026-07-29T00:00:00Z',
    updatedAt: '2026-07-29T00:00:00Z', version: 0
  };
}

function inventoryReport(): InventoryReport {
  return {
    storeId: null,
    categoryId: null,
    productId: null,
    dateFrom: null,
    dateTo: null,
    lowStockThreshold: 5,
    currentStock: 100,
    inventoryValue: 2500,
    stockItemCount: 40,
    lowStockCount: 4,
    negativeStockCount: 1,
    adjustmentCount: 0,
    damagedCount: 0,
    expiredCount: 0,
    adjustmentQuantity: 0,
    damagedQuantity: 0,
    expiredQuantity: 0,
    adjustmentValue: 0,
    damagedValue: 0,
    expiredValue: 0,
    stockRows: [],
    lowStockRows: [],
    negativeStockRows: [],
    adjustmentRows: [],
    damagedRows: [],
    expiredRows: [],
    generatedAt: '2026-07-29T12:00:00Z'
  };
}

function openRegisterSession(id: string): RegisterSession {
  return {
    id,
    storeId: '00000000-0000-0000-0000-000000000902',
    registerId: '00000000-0000-0000-0000-000000000903',
    deviceId: '00000000-0000-0000-0000-000000000904',
    assignedCashierId: '00000000-0000-0000-0000-000000000905',
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
    openedAt: '2026-07-29T09:00:00Z',
    createdAt: '2026-07-29T09:00:00Z',
    updatedAt: '2026-07-29T09:00:00Z',
    version: 0
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
    path: '/api/v1/reports/sales',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
  window.localStorage.setItem('merchtyl.activeStoreId', activeStore().id);
}

function mockDashboardApi(lottery = lotteryReport()) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = new URL(String(input), window.location.origin);
    if (url.pathname.endsWith('/api/v1/auth/me')) {
      return jsonResponse(currentUser(['OWNER']));
    }
    if (url.pathname.endsWith('/api/v1/reports/sales')) {
      return jsonResponse(salesReport());
    }
    if (url.pathname.endsWith('/api/v1/stores')) {
      return jsonResponse(page([activeStore()]));
    }
    if (url.pathname.endsWith('/api/v1/reports/lottery-sales')) {
      return jsonResponse(lottery);
    }
    if (url.pathname.endsWith('/api/v1/reports/inventory')) {
      return jsonResponse(inventoryReport());
    }
    if (url.pathname.endsWith('/api/v1/register-sessions')) {
      return jsonResponse(page<RegisterSession>([
        openRegisterSession('00000000-0000-0000-0000-000000000906'),
        openRegisterSession('00000000-0000-0000-0000-000000000907')
      ]) satisfies RegisterSessionListResponse);
    }
    return apiError('Unexpected request');
  });
}

describe('Owner dashboard page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders owner cards, responsive chart sections, and today filters', async () => {
    storeSession(['OWNER']);
    const fetchMock = mockDashboardApi();
    const expectedToday = new Date().toISOString().slice(0, 10);

    render(<App initialEntries={['/']} />);

    expect(await screen.findByRole('heading', { name: 'Owner dashboard' })).toBeInTheDocument();
    expect(await screen.findByText("Today's sales")).toBeInTheDocument();
    expect(screen.getByText('CA$1,180.00')).toBeInTheDocument();
    expect(screen.getByText("Today's lottery")).toBeInTheDocument();
    expect(screen.getByText('CA$295.00')).toBeInTheDocument();
    expect(screen.getByText(/Sold CA\$420\.00.*Wins CA\$125\.00/)).toBeInTheDocument();
    expect(screen.getByText('Inventory alerts')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('Open registers')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('Refunds')).toBeInTheDocument();
    expect(screen.getByText('CA$85.00')).toBeInTheDocument();
    expect(screen.getByText('Tax collected')).toBeInTheDocument();
    expect(screen.getByText('CA$92.00')).toBeInTheDocument();
    expect(screen.getByText('Payment mix')).toBeInTheDocument();
    expect(screen.getByText('Lottery trend')).toBeInTheDocument();
    expect(screen.queryByText('Settlement')).not.toBeInTheDocument();

    await waitFor(() => {
      const calls = fetchMock.mock.calls.map(([input]) => new URL(String(input), window.location.origin));
      expect(calls.some((url) => url.pathname.endsWith('/api/v1/reports/sales')
        && url.searchParams.get('dateFrom') === expectedToday
        && url.searchParams.get('dateTo') === expectedToday)).toBe(true);
      expect(calls.some((url) => url.pathname.endsWith('/api/v1/reports/lottery-sales')
        && url.searchParams.get('storeId') === activeStore().id
        && url.searchParams.get('dateFrom') === expectedToday
        && url.searchParams.get('dateTo') === expectedToday)).toBe(true);
      expect(calls.some((url) => url.pathname.endsWith('/api/v1/reports/inventory')
        && url.searchParams.get('lowStockThreshold') === '5')).toBe(true);
      expect(calls.some((url) => url.pathname.endsWith('/api/v1/register-sessions')
        && url.searchParams.get('status') === 'OPEN')).toBe(true);
    });
  });

  it('shows an explicit empty state only when sold and wins are both zero', async () => {
    storeSession(['OWNER']);
    mockDashboardApi({
      ...lotteryReport(),
      physicalTicketSales: 0,
      manualLotterySold: 0,
      totalLotterySold: 0,
      lotteryWins: 0,
      netLottery: 0,
      activities: []
    });

    render(<App initialEntries={['/']} />);

    expect(await screen.findByText('No lottery activity today')).toBeInTheDocument();
    expect(screen.queryByText('Settlement')).not.toBeInTheDocument();
  });
});
