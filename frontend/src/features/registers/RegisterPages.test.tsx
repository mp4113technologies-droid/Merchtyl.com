import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  Register,
  RegisterListResponse,
  Store,
  StoreListResponse,
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
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'user@example.local',
    displayName: 'Register User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'user@example.local',
    displayName: 'Register User',
    roles
  };
}

function store(overrides: Partial<Store> = {}): Store {
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
    version: 0,
    ...overrides
  };
}

function register(overrides: Partial<Register> = {}): Register {
  return {
    id: '00000000-0000-0000-0000-000000000902',
    storeId: '00000000-0000-0000-0000-000000000901',
    code: 'FRONT-1',
    name: 'Front Register',
    locationDescription: 'Front counter',
    active: true,
    createdAt: '2026-07-21T12:00:00Z',
    updatedAt: '2026-07-21T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function storePage(content: Store[]): StoreListResponse {
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

function registerPage(content: Register[], overrides: Partial<RegisterListResponse> = {}): RegisterListResponse {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    first: true,
    last: true,
    ...overrides
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
    path: '/api/v1/registers',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Register pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders registers and filters by store', async () => {
    storeSession(['OWNER']);
    const mainStore = store();
    const secondStore = store({
      id: '00000000-0000-0000-0000-000000000903',
      code: 'SECOND',
      name: 'Second Store'
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(storePage([mainStore, secondStore]));
      }
      if (url.pathname.endsWith('/api/v1/registers')) {
        return jsonResponse(registerPage([
          register(),
          register({
            id: '00000000-0000-0000-0000-000000000904',
            storeId: secondStore.id,
            code: 'BACK-1',
            name: 'Back Register',
            active: false
          })
        ], { totalElements: 2 }));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={[`/registers?storeId=${mainStore.id}`]} />);

    expect(await screen.findByRole('heading', { name: 'Registers' })).toBeInTheDocument();
    expect(await screen.findByText('Front Register')).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/registers') && url.searchParams.get('storeId') === mainStore.id;
      })).toBe(true);
    });
  });

  it('hides mutating actions from cashier users', async () => {
    storeSession(['CASHIER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(storePage([store()]));
      }
      if (url.pathname.endsWith('/api/v1/registers')) {
        return jsonResponse(registerPage([register()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/registers']} />);

    expect(await screen.findByText('Front Register')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New register' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Deactivate Front Register/i })).not.toBeInTheDocument();
  });

  it('creates a register and opens the edit page', async () => {
    storeSession(['OWNER']);
    const mainStore = store();
    const created = register({
      id: '00000000-0000-0000-0000-000000000905',
      code: 'LANE-2',
      name: 'Lane Two',
      locationDescription: 'Checkout lane two'
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(storePage([mainStore]));
      }
      if (url.pathname.endsWith('/api/v1/registers') && init?.method === 'POST') {
        return jsonResponse(created, 201);
      }
      if (url.pathname.endsWith(`/api/v1/registers/${created.id}`)) {
        return jsonResponse(created);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/registers/new']} />);

    expect(await screen.findByRole('heading', { name: 'New register' })).toBeInTheDocument();
    await userEvent.type(await screen.findByLabelText('Code'), 'lane-2');
    await userEvent.type(screen.getByLabelText('Name'), 'Lane Two');
    await userEvent.type(screen.getByLabelText('Location description'), 'Checkout lane two');
    await userEvent.click(screen.getByRole('button', { name: 'Create register' }));

    expect(await screen.findByRole('heading', { name: 'Lane Two' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith('/api/v1/registers') && init?.method === 'POST';
    })).toBe(true);
  });

  it('validates, edits, and deactivates a register', async () => {
    storeSession(['MANAGER']);
    const mainStore = store();
    let current = register();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(storePage([mainStore]));
      }
      if (url.pathname.endsWith(`/api/v1/registers/${current.id}`) && init?.method === 'PUT') {
        const body = JSON.parse(String(init.body));
        current = register({ ...current, ...body, version: 1 });
        return jsonResponse(current);
      }
      if (url.pathname.endsWith(`/api/v1/registers/${current.id}/status`) && init?.method === 'PATCH') {
        current = register({ ...current, active: false, version: 2 });
        return jsonResponse(current);
      }
      if (url.pathname.endsWith(`/api/v1/registers/${current.id}`)) {
        return jsonResponse(current);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={[`/registers/${current.id}`]} />);

    expect(await screen.findByRole('heading', { name: 'Front Register' })).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Name'));
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Name is required')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Name'), 'Updated Register');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Register saved.')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Updated Register' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Deactivate' }));
    expect(await screen.findByText('Inactive')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => String(input).endsWith('/status') && init?.method === 'PATCH')).toBe(true);
  });
});
