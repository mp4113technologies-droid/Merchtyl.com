import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  FeatureResolution,
  LotteryOperator,
  LotteryOperatorListResponse,
  LotterySale,
  LotterySaleListResponse,
  Register,
  RegisterListResponse,
  Store,
  StoreListResponse,
  UserAdmin,
  UserAdminListResponse,
  UserRole
} from '../../api/types';

const operatorId = '00000000-0000-0000-0000-000000000801';
const storeId = '00000000-0000-0000-0000-000000000802';
const registerId = '00000000-0000-0000-0000-000000000803';
const cashierId = '00000000-0000-0000-0000-000000000806';

function authResponse(roles: UserRole[] = ['MANAGER']): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: cashierId,
    email: 'manager@example.local',
    displayName: 'Manager One',
    roles
  };
}

function currentUser(roles: UserRole[] = ['MANAGER']): CurrentUserResponse {
  return {
    userId: cashierId,
    email: 'manager@example.local',
    displayName: 'Manager One',
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

function cashier(): UserAdmin {
  return {
    id: cashierId,
    email: 'cashier@example.local',
    displayName: 'Cashier One',
    enabled: true,
    locked: false,
    roles: ['CASHIER'],
    storeIds: [storeId],
    registerIds: [registerId],
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0
  };
}

function sale(): LotterySale {
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
    deviceId: '00000000-0000-0000-0000-000000000804',
    deviceIdentifier: 'browser:test-device',
    deviceDisplayName: 'Front Browser',
    cashierId,
    cashierEmail: 'cashier@example.local',
    cashierDisplayName: 'Cashier One',
    registerSessionId: '00000000-0000-0000-0000-000000000805',
    status: 'RECORDED',
    operationId: '00000000-0000-0000-0000-000000000910',
    occurredAt: '2026-07-28T12:00:00Z',
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0
  };
}

function pageResponse<T>(content: T[], size = 100) {
  return {
    content,
    page: 0,
    size,
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

function storeSession(roles: UserRole[] = ['MANAGER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Lottery history page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders lottery sales and applies history filters', async () => {
    storeSession(['MANAGER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/features/resolution')) {
        return jsonResponse(featureResolution(true));
      }
      if (url.pathname.endsWith('/api/v1/lottery/operators')) {
        return jsonResponse(pageResponse<LotteryOperator>([operator()]) satisfies LotteryOperatorListResponse);
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(pageResponse<Store>([store()]) satisfies StoreListResponse);
      }
      if (url.pathname.endsWith('/api/v1/registers')) {
        return jsonResponse(pageResponse<Register>([register()]) satisfies RegisterListResponse);
      }
      if (url.pathname.endsWith('/api/v1/users')) {
        return jsonResponse(pageResponse<UserAdmin>([cashier()]) satisfies UserAdminListResponse);
      }
      if (url.pathname.endsWith('/api/v1/lottery/sales')) {
        return jsonResponse(pageResponse<LotterySale>([sale()], 10) satisfies LotterySaleListResponse);
      }
      return jsonResponse({ message: `Unexpected request: ${url.pathname}` }, 500);
    });

    render(<App initialEntries={['/lottery/history']} />);

    expect(await screen.findByRole('heading', { name: 'Lottery history' })).toBeInTheDocument();
    expect(await screen.findByText('TICKET-99')).toBeInTheDocument();
    expect(await screen.findByText('Draw Ticket')).toBeInTheDocument();
    expect(await screen.findByText('Cashier One')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Search'), 'TICKET-99');
    await userEvent.click(screen.getByLabelText('Operator'));
    await userEvent.click(await screen.findByRole('option', { name: 'State Lottery' }));
    await userEvent.click(screen.getByLabelText('Cashier'));
    await userEvent.click(await screen.findByRole('option', { name: 'Cashier One' }));
    await userEvent.click(screen.getByLabelText('Store'));
    await userEvent.click(await screen.findByRole('option', { name: 'Main Store' }));
    await userEvent.click(screen.getByLabelText('Register'));
    await userEvent.click(await screen.findByRole('option', { name: 'Front Register' }));
    await userEvent.click(screen.getByLabelText('Game type'));
    await userEvent.click(await screen.findByRole('option', { name: 'Draw Ticket' }));
    await userEvent.click(screen.getByLabelText('Status'));
    await userEvent.click(await screen.findByRole('option', { name: 'Recorded' }));
    await userEvent.click(screen.getByLabelText('Payment'));
    await userEvent.click(await screen.findByRole('option', { name: 'Cash' }));
    await userEvent.type(screen.getByLabelText('From'), '2026-07-28');
    await userEvent.type(screen.getByLabelText('To'), '2026-07-28');
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }));

    await waitFor(() => {
      const filteredCall = fetchMock.mock.calls
        .map(([input]) => new URL(String(input), window.location.origin))
        .find((url) => url.pathname.endsWith('/api/v1/lottery/sales') && url.searchParams.get('search') === 'TICKET-99');
      expect(filteredCall).toBeTruthy();
      expect(filteredCall?.searchParams.get('operatorId')).toBe(operatorId);
      expect(filteredCall?.searchParams.get('storeId')).toBe(storeId);
      expect(filteredCall?.searchParams.get('registerId')).toBe(registerId);
      expect(filteredCall?.searchParams.get('cashierId')).toBe(cashierId);
      expect(filteredCall?.searchParams.get('gameType')).toBe('DRAW_TICKET');
      expect(filteredCall?.searchParams.get('status')).toBe('RECORDED');
      expect(filteredCall?.searchParams.get('paymentMethod')).toBe('CASH');
      expect(filteredCall?.searchParams.get('occurredFrom')).toBe('2026-07-28T00:00:00.000Z');
      expect(filteredCall?.searchParams.get('occurredTo')).toBe('2026-07-28T23:59:59.999Z');
    });
  });
});
