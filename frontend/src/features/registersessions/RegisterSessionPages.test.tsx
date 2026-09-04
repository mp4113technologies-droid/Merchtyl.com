import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import { applicationDeviceIdentifierKey } from '../../app/deviceIdentity';
import type {
  AuthResponse,
  CashMovement,
  CashMovementListResponse,
  CurrentUserResponse,
  Device,
  DeviceListResponse,
  Register,
  RegisterListResponse,
  RegisterSession,
  Store,
  StoreListResponse,
  UserRole
} from '../../api/types';

function authResponse(roles: UserRole[] = ['CASHIER']): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: '00000000-0000-0000-0000-000000000904',
    email: 'cashier@example.local',
    displayName: 'Cashier One',
    roles
  };
}

function currentUser(roles: UserRole[] = ['CASHIER'], permissions?: string[]): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000904',
    email: 'cashier@example.local',
    displayName: 'Cashier One',
    roles,
    permissions
  };
}

function foodStore(): Store {
  return { ...store(), capabilities: ['FOOD_SERVICE'] };
}

function foodRegister(): Register {
  return { ...register(), type: 'FOOD_SERVICE' };
}

function store(): Store {
  return {
    id: '00000000-0000-0000-0000-000000000901',
    code: 'MAIN',
    name: 'Main Store',
    legalName: 'Main Store LLC',
    countryCode: 'US',
    administrativeAreaCode: 'CA',
    address: '100 Market Street, San Francisco, CA',
    phone: '+1-555-0100',
    email: 'main@example.test',
    currencyCode: 'USD',
    locale: 'en-US',
    timezone: 'America/Los_Angeles',
    pricesIncludeTax: true,
    negativeStockAllowed: false,
    active: true,
    createdAt: '2026-07-21T12:00:00Z',
    updatedAt: '2026-07-21T12:00:00Z',
    version: 0
  };
}

function register(): Register {
  return {
    id: '00000000-0000-0000-0000-000000000902',
    storeId: '00000000-0000-0000-0000-000000000901',
    code: 'FRONT-1',
    name: 'Front Register',
    locationDescription: 'Front counter',
    active: true,
    createdAt: '2026-07-21T12:00:00Z',
    updatedAt: '2026-07-21T12:00:00Z',
    version: 0
  };
}

function device(): Device {
  return {
    id: '00000000-0000-0000-0000-000000000903',
    storeId: '00000000-0000-0000-0000-000000000901',
    registerId: '00000000-0000-0000-0000-000000000902',
    deviceIdentifier: 'browser:test-device',
    displayName: 'Front Browser',
    deviceType: 'BROWSER_POS',
    registeredAt: '2026-07-21T12:00:00Z',
    lastSeenAt: '2026-07-21T12:00:00Z',
    active: true,
    version: 0
  };
}

function registerSession(): RegisterSession {
  return {
    id: '00000000-0000-0000-0000-000000000900',
    storeId: '00000000-0000-0000-0000-000000000901',
    registerId: '00000000-0000-0000-0000-000000000902',
    deviceId: '00000000-0000-0000-0000-000000000903',
    assignedCashierId: '00000000-0000-0000-0000-000000000904',
    assignedCashierEmail: 'cashier@example.local',
    assignedCashierDisplayName: 'Cashier One',
    openedByUserId: '00000000-0000-0000-0000-000000000904',
    openedByDisplayName: 'Cashier One',
    status: 'OPEN',
    openingCash: 125.5,
    expectedCash: 125.5,
    countedCash: null,
    expectedCashAtClose: null,
    differenceCash: null,
    closedByUserId: null,
    closedByEmail: null,
    closedByDisplayName: null,
    closedAt: null,
    forceCloseReason: null,
    reconciliation: {
      openingCash: 125.5,
      retailCashReceived: 0,
      retailChange: 0,
      retailRefunds: 0,
      lotteryCashSales: 0,
      lotteryPayouts: 0,
      payoutReversals: 0,
      lotterySaleCancellations: 0,
      otherCashIn: 0,
      otherCashOut: 0,
      totalIn: 0,
      totalOut: 0,
      expectedCash: 125.5,
      sourceBreakdown: []
    },
    openedAt: '2026-07-21T12:00:00Z',
    createdAt: '2026-07-21T12:00:00Z',
    updatedAt: '2026-07-21T12:00:00Z',
    version: 0
  };
}

function closedRegisterSession(): RegisterSession {
  return {
    ...registerSession(),
    status: 'CLOSED',
    expectedCash: 115.5,
    countedCash: 115,
    expectedCashAtClose: 115.5,
    differenceCash: -0.5,
    closedByUserId: '00000000-0000-0000-0000-000000000904',
    closedByEmail: 'cashier@example.local',
    closedByDisplayName: 'Cashier One',
    closedAt: '2026-07-21T13:00:00Z',
    reconciliation: {
      openingCash: 125.5,
      retailCashReceived: 0,
      retailChange: 0,
      retailRefunds: 0,
      lotteryCashSales: 0,
      lotteryPayouts: 0,
      payoutReversals: 0,
      lotterySaleCancellations: 0,
      otherCashIn: 0,
      otherCashOut: 10,
      totalIn: 0,
      totalOut: 10,
      expectedCash: 115.5,
      sourceBreakdown: [
        { sourceType: 'CASH_MOVEMENT', direction: 'OUT', amount: 10 }
      ]
    },
    version: 1
  };
}

function lotteryReconciledRegisterSession(): RegisterSession {
  return {
    ...registerSession(),
    expectedCash: 136,
    reconciliation: {
      openingCash: 100,
      retailCashReceived: 50,
      retailChange: 5,
      retailRefunds: 7,
      lotteryCashSales: 20,
      lotteryPayouts: 30,
      payoutReversals: 10,
      lotterySaleCancellations: 8,
      otherCashIn: 12,
      otherCashOut: 6,
      totalIn: 92,
      totalOut: 56,
      expectedCash: 136,
      sourceBreakdown: [
        { sourceType: 'LOTTERY_SALE_CASH', direction: 'IN', amount: 20 },
        { sourceType: 'LOTTERY_PAYOUT_CASH', direction: 'OUT', amount: 30 },
        { sourceType: 'LOTTERY_PAYOUT_REVERSAL', direction: 'IN', amount: 10 },
        { sourceType: 'LOTTERY_SALE_CANCELLATION_CASH', direction: 'OUT', amount: 8 }
      ]
    }
  };
}

function cashMovement(): CashMovement {
  return {
    id: '00000000-0000-0000-0000-000000000905',
    storeId: '00000000-0000-0000-0000-000000000901',
    registerId: '00000000-0000-0000-0000-000000000902',
    registerSessionId: '00000000-0000-0000-0000-000000000900',
    type: 'CASH_OUT',
    direction: 'OUT',
    amount: 10,
    currencyCode: 'USD',
    reason: 'Petty cash',
    notes: null,
    createdBy: '00000000-0000-0000-0000-000000000904',
    occurredAt: '2026-07-21T12:05:00Z',
    approvedBy: null,
    approvedAt: null,
    approvalNotes: null,
    createdAt: '2026-07-21T12:05:00Z',
    updatedAt: '2026-07-21T12:05:00Z',
    version: 0
  };
}

function page<T>(content: T[]) {
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

function noContentResponse() {
  return Promise.resolve(new Response(null, { status: 204 }));
}

function apiError(message: string, status = 500, code = 'unexpected') {
  return jsonResponse({
    code,
    message,
    status,
    path: '/api/v1/register-sessions',
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

describe('Register session pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
    vi.unstubAllEnvs();
  });

  it('shows when no current register session is open for this device', async () => {
    storeSession(['CASHIER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      if (url.pathname.endsWith('/api/v1/register-sessions/current')) {
        return noContentResponse();
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/register/current']} />);

    expect(await screen.findByRole('heading', { name: 'Current register' })).toBeInTheDocument();
    expect(await screen.findByText('No register session is open for this user.')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /Open register|Open/i }).length).toBeGreaterThan(0);
  });

  it('confirms starting closing and restores the closing screen after navigation', async () => {
    storeSession(['CASHIER']);
    let current = registerSession();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['CASHIER']));
      if (url.pathname.endsWith('/api/v1/register-sessions/current')) return jsonResponse(current);
      if (url.pathname.endsWith('/api/v1/stores')) return jsonResponse(page<Store>([store()]));
      if (url.pathname.endsWith('/api/v1/registers')) return jsonResponse(page<Register>([register()]));
      if (url.pathname.endsWith('/api/v1/devices')) return jsonResponse(page<Device>([device()]));
      if (url.pathname.endsWith(`/api/v1/register-sessions/${current.id}/start-closing`) && init?.method === 'POST') {
        current = { ...current, status: 'CLOSING', version: 1 };
        return jsonResponse(current);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/register/current']} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Start Closing' }));
    expect(screen.getByRole('heading', { name: 'Start register closing?' })).toBeInTheDocument();
    expect(screen.getByText('This will begin the cash reconciliation process. You can cancel before the register is finalized.')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'Start Closing' }).at(-1)!);

    expect(await screen.findByRole('heading', { name: 'Close register' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel Closing' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Complete Closing' })).toBeEnabled();
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes('/start-closing'))).toBe(true);
  });

  it('opens a food register without a device and routes to Food POS', async () => {
    storeSession(['KITCHEN']);
    const mainStore = foodStore();
    const frontRegister = foodRegister();
    const opened = { ...registerSession(), registerType: 'FOOD_SERVICE' as const };
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['KITCHEN'], ['REGISTER_SESSION_OPEN', 'REGISTER_SESSION_VIEW', 'REGISTER_SESSION_OPERATE', 'FOOD_POS_ACCESS']));
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(page<Store>([mainStore]) satisfies StoreListResponse);
      }
      if (url.pathname.endsWith('/api/v1/registers')) {
        return jsonResponse(page<Register>([frontRegister]) satisfies RegisterListResponse);
      }
      if (url.pathname.endsWith('/api/v1/register-sessions') && url.searchParams.get('status') === 'OPEN') {
        return jsonResponse(page<RegisterSession>([]));
      }
      if (url.pathname.endsWith('/api/v1/devices')) return apiError('Device API must not be called');
      if (url.pathname.endsWith('/api/v1/register-sessions/open') && init?.method === 'POST') {
        return jsonResponse(opened, 201);
      }
      if (url.pathname.endsWith('/api/v1/register-sessions/current')) {
        return jsonResponse(opened);
      }
      if (url.pathname.endsWith('/api/v1/food-service/configuration')) return jsonResponse({ enabled: true, kitchenDisplayName: 'Kitchen POS' });
      if (url.pathname.endsWith('/api/v1/food-menu/categories')) return jsonResponse([]);
      if (url.pathname.endsWith('/api/v1/food-menu/items')) return jsonResponse([]);
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/register/open']} />);

    expect(await screen.findByRole('heading', { name: 'Open register' })).toBeInTheDocument();
    expect(await screen.findByText('Not required for current browser deployment')).toBeInTheDocument();
    await screen.findByRole('button', { name: 'Open register' });
    const openingCash = screen.getByLabelText('Opening cash');
    fireEvent.change(openingCash, { target: { value: '125.50' } });
    await userEvent.click(await screen.findByRole('button', { name: 'Open register' }));

    await waitFor(() => {
      const openCall = fetchMock.mock.calls.find(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/register-sessions/open') && init?.method === 'POST';
      });
      expect(openCall).toBeTruthy();
      expect(JSON.parse(String(openCall?.[1]?.body))).toEqual({
        storeId: mainStore.id,
        registerId: frontRegister.id,
        openingCash: 125.5
      });
      const openIndex = fetchMock.mock.calls.indexOf(openCall!);
      expect(fetchMock.mock.calls.slice(0, openIndex).some(([input]) => new URL(String(input), window.location.origin).pathname.endsWith('/api/v1/devices'))).toBe(false);
    });
    expect(await screen.findByRole('heading', { name: 'Restaurant / Kitchen POS' })).toBeInTheDocument();
    expect(screen.queryByText('Unauthorized')).not.toBeInTheDocument();
  });

  it('starts a missing business day from register open and enables register operation immediately', async () => {
    storeSession(['CASHIER']);
    let businessDayOpen = false;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER'], ['REGISTER_SESSION_OPEN', 'BUSINESS_DAY_VIEW', 'BUSINESS_DAY_OPEN']));
      }
      if (url.pathname.endsWith('/api/v1/stores')) return jsonResponse(page<Store>([store()]));
      if (url.pathname.endsWith('/api/v1/registers')) return jsonResponse(page<Register>([register()]));
      if (url.pathname.endsWith('/api/v1/register-sessions') && url.searchParams.get('status') === 'OPEN') {
        return jsonResponse(page<RegisterSession>([]));
      }
      if (url.pathname.endsWith('/api/v1/business-days/open') && init?.method === 'POST') {
        businessDayOpen = true;
        return jsonResponse({ id: 'day-id', storeId: store().id, businessDate: '2026-09-03', status: 'OPEN' });
      }
      if (url.pathname.endsWith('/api/v1/business-days/operational-state')) return jsonResponse({
        storeId: store().id,
        currentBusinessDate: '2026-09-03',
        currentBusinessDay: businessDayOpen ? { id: 'day-id', storeId: store().id, businessDate: '2026-09-03', status: 'OPEN' } : null,
        previousBusinessDay: null,
        state: businessDayOpen ? 'OPEN' : 'NO_BUSINESS_DAY_TODAY',
        availableAction: businessDayOpen ? 'NONE' : 'OPEN'
      });
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/register/open']} />);

    const openRegister = await screen.findByRole('button', { name: 'Open register' });
    expect(openRegister).toBeDisabled();
    await userEvent.click(await screen.findByRole('button', { name: 'Start Business Day' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Open register' })).toBeEnabled());
    expect(fetchMock.mock.calls.filter(([input, init]) => String(input).includes('/business-days/open') && init?.method === 'POST')).toHaveLength(1);
  });

  it('requires explicit owner confirmation before overriding an active cashier session', async () => {
    storeSession(['TENANT_OWNER']);
    const activeSession = registerSession();
    const overridden = { ...activeSession, assignedCashierDisplayName: 'Owner One' };
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['TENANT_OWNER']));
      if (url.pathname.endsWith('/api/v1/stores')) return jsonResponse(page<Store>([store()]));
      if (url.pathname.endsWith('/api/v1/registers')) return jsonResponse(page<Register>([register()]));
      if (url.pathname.endsWith('/api/v1/register-sessions/open') && init?.method === 'POST') {
        return apiError('Register already has an open session', 409, 'conflict');
      }
      if (url.pathname.endsWith('/api/v1/register-sessions') && url.searchParams.get('status') === 'OPEN') {
        return jsonResponse(page<RegisterSession>([activeSession]));
      }
      if (url.pathname.endsWith(`/api/v1/register-sessions/${activeSession.id}/override`) && init?.method === 'POST') {
        return jsonResponse(overridden);
      }
      if (url.pathname.endsWith('/api/v1/register-sessions/current')) return jsonResponse(overridden);
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/register/open']} />);
    await screen.findByRole('heading', { name: 'Open register' });

    expect(await screen.findByRole('heading', { name: 'Register already in use' })).toBeInTheDocument();
    expect(screen.getByText(/currently operated by Cashier One/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Opening cash')).not.toBeInTheDocument();
    expect(screen.getByText(/Opening cash:/)).toHaveTextContent('$125.50');
    await userEvent.type(await screen.findByRole('textbox', { name: 'Reason' }), 'Manager required access');
    await userEvent.click(screen.getByRole('button', { name: 'Override Session' }));

    await waitFor(() => {
      const overrideCall = fetchMock.mock.calls.find(([input, init]) =>
        new URL(String(input), window.location.origin).pathname.endsWith(`/${activeSession.id}/override`)
        && init?.method === 'POST');
      expect(overrideCall).toBeTruthy();
      expect(JSON.parse(String(overrideCall?.[1]?.body))).toEqual({
        reason: 'Manager required access',
        version: activeSession.version
      });
    });
  });

  it('loads and requires active devices when enforcement is enabled', async () => {
    vi.stubEnv('VITE_REGISTER_DEVICE_ENFORCEMENT_ENABLED', 'true');
    storeSession(['CASHIER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['CASHIER']));
      if (url.pathname.endsWith('/api/v1/stores')) return jsonResponse(page<Store>([store()]));
      if (url.pathname.endsWith('/api/v1/registers')) return jsonResponse(page<Register>([register()]));
      if (url.pathname.endsWith('/api/v1/register-sessions') && url.searchParams.get('status') === 'OPEN') {
        return jsonResponse(page<RegisterSession>([]));
      }
      if (url.pathname.endsWith('/api/v1/devices')) return jsonResponse(page<Device>([device()]));
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/register/open']} />);

    expect(await screen.findByText('Front Browser (browser:test-device)')).toBeInTheDocument();
    expect(screen.getByLabelText(/Device/)).toBeRequired();
    expect(fetchMock.mock.calls.some(([input]) => new URL(String(input), window.location.origin).pathname.endsWith('/api/v1/devices'))).toBe(true);
  });

  it('records cash movement and shows movement history', async () => {
    storeSession(['MANAGER']);
    const opened = registerSession();
    const movement = cashMovement();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/register-sessions/current')) {
        return jsonResponse(opened);
      }
      if (url.pathname.endsWith('/api/v1/cash-movements') && init?.method === 'POST') {
        return jsonResponse(movement, 201);
      }
      if (url.pathname.endsWith('/api/v1/cash-movements')) {
        return jsonResponse(page<CashMovement>([movement]) satisfies CashMovementListResponse);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/register/cash-movements']} />);

    expect(await screen.findByRole('heading', { name: 'Cash movements' })).toBeInTheDocument();
    expect(await screen.findByText('Petty cash')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Type'));
    await userEvent.click(screen.getByRole('option', { name: 'Cash Out' }));
    await userEvent.clear(screen.getByLabelText('Amount'));
    await userEvent.type(screen.getByLabelText('Amount'), '10.00');
    await userEvent.type(screen.getByLabelText('Reason'), 'Petty cash');
    await userEvent.click(screen.getByRole('button', { name: 'Record movement' }));

    await waitFor(() => {
      const movementCall = fetchMock.mock.calls.find(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/cash-movements') && init?.method === 'POST';
      });
      expect(movementCall).toBeTruthy();
      expect(JSON.parse(String(movementCall?.[1]?.body))).toMatchObject({
        registerSessionId: opened.id,
        type: 'CASH_OUT',
        amount: 10,
        reason: 'Petty cash'
      });
    });
  });

  it('closes the current register with counted cash and version', async () => {
    storeSession(['CASHIER']);
    const opened = { ...registerSession(), status: 'CLOSING' as const };
    const closed = closedRegisterSession();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      if (url.pathname.endsWith('/api/v1/register-sessions/current')) {
        return jsonResponse(opened);
      }
      if (url.pathname.endsWith(`/api/v1/register-sessions/${opened.id}/close`) && init?.method === 'POST') {
        return jsonResponse(closed);
      }
      if (url.pathname.endsWith('/api/v1/register-sessions')) {
        return jsonResponse(page<RegisterSession>([closed]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/register/close']} />);

    expect(await screen.findByRole('heading', { name: 'Close register' })).toBeInTheDocument();
    expect(await screen.findByText('Expected cash')).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Counted cash'));
    await userEvent.type(screen.getByLabelText('Counted cash'), '115.00');
    await userEvent.click(screen.getByRole('button', { name: 'Complete Closing' }));

    await waitFor(() => {
      const closeCall = fetchMock.mock.calls.find(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith(`/api/v1/register-sessions/${opened.id}/close`) && init?.method === 'POST';
      });
      expect(closeCall).toBeTruthy();
      expect(JSON.parse(String(closeCall?.[1]?.body))).toEqual({
        countedCash: 115,
        version: 0
      });
    });
  });

  it('cancels closing and returns the same session to the current register', async () => {
    storeSession(['CASHIER']);
    const sessionId = registerSession().id;
    let current: RegisterSession = { ...registerSession(), status: 'CLOSING' };
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['CASHIER']));
      if (url.pathname.endsWith('/api/v1/register-sessions/current')) return jsonResponse(current);
      if (url.pathname.endsWith('/api/v1/stores')) return jsonResponse(page<Store>([store()]));
      if (url.pathname.endsWith('/api/v1/registers')) return jsonResponse(page<Register>([register()]));
      if (url.pathname.endsWith('/api/v1/devices')) return jsonResponse(page<Device>([device()]));
      if (url.pathname.endsWith(`/api/v1/register-sessions/${sessionId}/cancel-closing`) && init?.method === 'POST') {
        current = { ...current, status: 'OPEN', version: 1 };
        return jsonResponse(current);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/register/close']} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel Closing' }));
    expect(await screen.findByRole('heading', { name: 'Current register' })).toBeInTheDocument();
    expect(current.id).toBe(sessionId);
    expect(current.openingCash).toBe(125.5);
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes('/cancel-closing'))).toBe(true);
  });

  it('shows every lottery reconciliation category on the current session screen', async () => {
    storeSession(['CASHIER']);
    const opened = lotteryReconciledRegisterSession();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      if (url.pathname.endsWith('/api/v1/register-sessions/current')) {
        return jsonResponse(opened);
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(page<Store>([store()]) satisfies StoreListResponse);
      }
      if (url.pathname.endsWith('/api/v1/registers')) {
        return jsonResponse(page<Register>([register()]) satisfies RegisterListResponse);
      }
      if (url.pathname.endsWith('/api/v1/devices')) {
        return jsonResponse(page<Device>([device()]) satisfies DeviceListResponse);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/register/current']} />);

    expect(await screen.findByRole('heading', { name: 'Current register' })).toBeInTheDocument();
    expect(await screen.findByText('Retail cash received')).toBeInTheDocument();
    expect(screen.getByText('Retail change')).toBeInTheDocument();
    expect(screen.getByText('Retail refunds')).toBeInTheDocument();
    expect(screen.getByText('Lottery cash sales')).toBeInTheDocument();
    expect(screen.getByText('Lottery payouts')).toBeInTheDocument();
    expect(screen.getByText('Payout reversals')).toBeInTheDocument();
    expect(screen.getByText('Lottery sale cancellations')).toBeInTheDocument();
    expect(screen.getByText('Other cash in')).toBeInTheDocument();
    expect(screen.getByText('Other cash out')).toBeInTheDocument();
    expect(screen.getByText('Expected closing cash')).toBeInTheDocument();
    expect(screen.getAllByText('$136.00').length).toBeGreaterThan(0);
  });

  it('shows register history reconciliation breakdown', async () => {
    storeSession(['MANAGER']);
    const closed = closedRegisterSession();
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/register-sessions')) {
        return jsonResponse(page<RegisterSession>([closed]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/register/history']} />);

    expect(await screen.findByRole('heading', { name: 'Register history' })).toBeInTheDocument();
    expect(await screen.findByText('Cashier One')).toBeInTheDocument();
    expect(await screen.findByText('Cash Movement')).toBeInTheDocument();
    expect(await screen.findByText('Other cash out')).toBeInTheDocument();
    expect((await screen.findAllByText(/\$10\.00/)).length).toBeGreaterThanOrEqual(2);
  });
});
