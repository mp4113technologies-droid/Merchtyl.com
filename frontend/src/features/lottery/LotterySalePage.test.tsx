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
  LotterySale,
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
const cashierId = '00000000-0000-0000-0000-000000000806';

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
    expectedCash: 100,
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

function lotterySale(): LotterySale {
  return {
    id: '00000000-0000-0000-0000-000000000900',
    operatorId,
    operatorCode: 'STATE',
    operatorName: 'State Lottery',
    operatorReference: 'TERM-14',
    ticketReference: 'TICKET-99',
    gameType: 'DRAW_TICKET',
    amount: 25,
    currencyCode: 'USD',
    paymentMethod: 'CASH',
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
    status: 'RECORDED',
    operationId: '00000000-0000-0000-0000-000000000901',
    occurredAt: '2026-07-28T12:05:00Z',
    createdAt: '2026-07-28T12:05:00Z',
    updatedAt: '2026-07-28T12:05:00Z',
    version: 0
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
    path: '/api/v1/lottery/sales',
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

describe('Lottery sale page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('records a cash lottery sale with register context and idempotency key', async () => {
    storeSession(['CASHIER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
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
        expect(url.searchParams.get('storeId')).toBe(storeId);
        expect(url.searchParams.get('registerId')).toBe(registerId);
        return jsonResponse(featureResolution(true));
      }
      if (url.pathname.endsWith('/api/v1/lottery/operators')) {
        return jsonResponse(pageResponse<LotteryOperator>([operator()]) satisfies LotteryOperatorListResponse);
      }
      if (url.pathname.endsWith('/api/v1/lottery/sales') && init?.method === 'POST') {
        return jsonResponse(lotterySale(), 201);
      }
      return apiError(`Unexpected request: ${url.pathname}`);
    });

    render(<App initialEntries={['/lottery/sale']} />);

    expect(await screen.findByRole('heading', { name: 'Lottery sale' })).toBeInTheDocument();
    const operatorSelect = await screen.findByRole('combobox', { name: 'Operator' });
    await waitFor(() => expect(operatorSelect).not.toHaveAttribute('aria-disabled', 'true'));
    fireEvent.mouseDown(operatorSelect);
    await userEvent.click(await screen.findByText('State Lottery (STATE)'));
    const amount = screen.getByLabelText('Amount');
    await userEvent.clear(amount);
    await userEvent.type(amount, '25.00');
    await userEvent.type(screen.getByLabelText('Ticket reference'), 'TICKET-99');
    await userEvent.type(screen.getByLabelText('Operator reference'), 'TERM-14');
    await userEvent.click(screen.getByRole('button', { name: 'Record lottery sale' }));

    await screen.findByText('Lottery sale recorded');
    const postCall = fetchMock.mock.calls.find(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith('/api/v1/lottery/sales') && init?.method === 'POST';
    });
    expect(postCall).toBeTruthy();
    const [, init] = postCall!;
    expect(new Headers(init?.headers).get('Idempotency-Key')).toBeTruthy();
    expect(JSON.parse(String(init?.body))).toMatchObject({
      operatorId,
      ticketReference: 'TICKET-99',
      operatorReference: 'TERM-14',
      gameType: 'DRAW_TICKET',
      amount: 25,
      paymentMethod: 'CASH',
      storeId,
      registerId,
      deviceId,
      registerSessionId: sessionId
    });
  });

  it('shows scoped disabled feature state without loading operators', async () => {
    storeSession(['CASHIER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      if (url.pathname.endsWith('/api/v1/register-sessions/current')) {
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
        return jsonResponse(featureResolution(false));
      }
      return apiError(`Unexpected request: ${url.pathname}`);
    });

    render(<App initialEntries={['/lottery/sale']} />);

    expect(await screen.findByText('Lottery sales is disabled for this store/register.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Record lottery sale' })).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith('/api/v1/lottery/operators');
    })).toBe(false);
  });
});
