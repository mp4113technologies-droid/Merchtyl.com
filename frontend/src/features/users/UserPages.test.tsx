import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  Register,
  RegisterListResponse,
  RoleAdmin,
  AssignedStore,
  Store,
  UserAdmin,
  UserRole
} from '../../api/types';

function authResponse(roles: UserRole[] = ['TENANT_OWNER']): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'owner@example.local',
    displayName: 'Owner User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['TENANT_OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'owner@example.local',
    displayName: 'Owner User',
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
    address: '100 Market Street',
    phone: '+1-555-0100',
    email: 'main@example.test',
    currencyCode: 'USD',
    locale: 'en-US',
    timezone: 'America/New_York',
    pricesIncludeTax: true,
    negativeStockAllowed: false,
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function register(overrides: Partial<Register> = {}): Register {
  return {
    id: '00000000-0000-0000-0000-000000000902',
    storeId: '00000000-0000-0000-0000-000000000901',
    code: 'FRONT',
    name: 'Front Register',
    locationDescription: 'Front counter',
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function assignableStore(overrides: Partial<AssignedStore> = {}): AssignedStore {
  return {
    storeId: '00000000-0000-0000-0000-000000000901',
    storeCode: 'MAIN',
    storeName: 'Main Store',
    city: null,
    administrativeDivisionCode: 'CA',
    assignmentRole: 'MANAGER',
    ...overrides
  };
}

function adminUser(overrides: Partial<UserAdmin> = {}): UserAdmin {
  return {
    id: '00000000-0000-0000-0000-000000000701',
    email: 'cashier@example.local',
    displayName: 'Cashier User',
    enabled: true,
    locked: false,
    roles: ['CASHIER'],
    storeIds: ['00000000-0000-0000-0000-000000000901'],
    registerIds: ['00000000-0000-0000-0000-000000000902'],
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function roles(): RoleAdmin[] {
  return [
    {
      id: '00000000-0000-0000-0000-000000000101',
      name: 'OWNER',
      description: 'Store owner',
      systemRole: true,
      permissions: ['USER_VIEW', 'USER_MANAGE', 'ROLE_VIEW'],
      version: 0
    },
    {
      id: '00000000-0000-0000-0000-000000000102',
      name: 'STORE_MANAGER',
      description: 'Store manager',
      systemRole: true,
      permissions: ['USER_VIEW', 'ROLE_VIEW'],
      version: 0
    },
    {
      id: '00000000-0000-0000-0000-000000000103',
      name: 'CASHIER',
      description: 'Cashier',
      systemRole: true,
      permissions: ['SALE_CREATE'],
      version: 0
    },
    {
      id: '00000000-0000-0000-0000-000000000104',
      name: 'KITCHEN',
      description: 'Kitchen employee',
      systemRole: true,
      permissions: ['FOOD_ORDER_VIEW'],
      version: 0
    }
  ];
}

function page<T>(content: T[], size = 20) {
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

function apiError(message: string, status = 500, code = 'unexpected') {
  return jsonResponse({
    code,
    message,
    status,
    path: '/api/v1/users',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['TENANT_OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('User administration pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders users and applies role filtering', async () => {
    storeSession(['TENANT_OWNER']);
    const mainStore = store();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['TENANT_OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/users/assignable-stores')) {
        return jsonResponse([assignableStore({
          storeId: mainStore.id,
          storeCode: mainStore.code,
          storeName: mainStore.name,
          administrativeDivisionCode: mainStore.administrativeAreaCode
        })]);
      }
      if (url.pathname.endsWith('/api/v1/users')) {
        return jsonResponse(page<UserAdmin>([adminUser()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/users']} />);

    expect(await screen.findByRole('heading', { name: 'Employees' })).toBeInTheDocument();
    expect(await screen.findByText('Cashier User')).toBeInTheDocument();
    const initialListCall = fetchMock.mock.calls.find(([input]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname === '/api/v1/users' && url.searchParams.get('page') === '0' && url.searchParams.get('size') === '10';
    });
    expect(new Headers(initialListCall?.[1]?.headers).get('Authorization')).toBe('Bearer access-token');
    await userEvent.click(screen.getByLabelText('Role'));
    await userEvent.click(await screen.findByRole('option', { name: 'CASHIER' }));
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/users') && url.searchParams.get('role') === 'CASHIER';
      })).toBe(true);
    });
  });

  it('hides mutating user actions from manager users', async () => {
    storeSession(['STORE_MANAGER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['STORE_MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/users')) {
        return jsonResponse(page<UserAdmin>([adminUser()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/users']} />);

    expect(await screen.findByText('Cashier User')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New user' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Deactivate Cashier User/i })).not.toBeInTheDocument();
  });

  it('renders an inactive employee with no store assignments', async () => {
    storeSession(['TENANT_OWNER']);
    const invited = adminUser({
      displayName: 'Pending Cashier',
      enabled: false,
      storeIds: [],
      registerIds: [],
      storeAssignments: [],
      status: 'DISABLED'
    });
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['TENANT_OWNER']));
      if (url.pathname.endsWith('/api/v1/users/assignable-stores')) return jsonResponse([]);
      if (url.pathname.endsWith('/api/v1/users')) return jsonResponse(page<UserAdmin>([invited]));
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/users']} />);

    expect(await screen.findByText('Pending Cashier')).toBeInTheDocument();
    expect(screen.getByText('No active stores')).toBeInTheDocument();
  });

  it.each(['STORE_MANAGER', 'CASHIER'] as const)('creates a %s, invalidates the list, and renders it immediately', async (createdRole) => {
    storeSession(['TENANT_OWNER']);
    const mainStore = store();
    const frontRegister = register();
    const created = adminUser({
      id: '00000000-0000-0000-0000-000000000702',
      email: 'new.user@example.local',
      displayName: 'New User',
      roles: [createdRole]
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['TENANT_OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/users/assignable-stores')) {
        return jsonResponse([assignableStore({
          storeId: mainStore.id,
          storeCode: mainStore.code,
          storeName: mainStore.name,
          administrativeDivisionCode: mainStore.administrativeAreaCode
        })]);
      }
      if (url.pathname.endsWith('/api/v1/registers')) {
        return jsonResponse(page<Register>([frontRegister], 100) as RegisterListResponse);
      }
      if (url.pathname.endsWith('/api/v1/roles')) {
        return jsonResponse(roles());
      }
      if (url.pathname.endsWith('/api/v1/users') && init?.method === 'POST') {
        return jsonResponse(created, 201);
      }
      if (url.pathname.endsWith('/api/v1/users')) {
        return jsonResponse(page<UserAdmin>([created]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/users/new']} />);

    expect(await screen.findByRole('heading', { name: 'New user' })).toBeInTheDocument();
    await userEvent.type(await screen.findByLabelText('Email'), 'new.user@example.local');
    await userEvent.type(screen.getByLabelText('Display name'), 'New User');
    await userEvent.type(screen.getByLabelText('Initial password'), 'NewUser!2026');
    await userEvent.click(screen.getByLabelText(createdRole));
    await userEvent.click(screen.getByLabelText('Main Store (MAIN)'));
    await userEvent.click(screen.getByLabelText('Front Register (FRONT), Main Store'));
    await userEvent.click(screen.getByRole('button', { name: 'Create user' }));

    expect(await screen.findByRole('heading', { name: 'Employees' })).toBeInTheDocument();
    expect(await screen.findByText('New User')).toBeInTheDocument();
    await waitFor(() => {
      const createCall = fetchMock.mock.calls.find(([input, init]) => String(input).endsWith('/api/v1/users') && init?.method === 'POST');
      expect(createCall).toBeTruthy();
      expect(String(createCall?.[1]?.body)).toContain(`"roles":["${createdRole}"]`);
      expect(String(createCall?.[1]?.body)).toContain(mainStore.id);
      expect(String(createCall?.[1]?.body)).toContain(frontRegister.id);
      expect(fetchMock.mock.calls.some(([input, init]) => String(input).includes('/api/v1/users?') && !init?.method)).toBe(true);
    });
  });

  it('does not navigate or render a fake user when creation fails', async () => {
    storeSession(['TENANT_OWNER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['TENANT_OWNER']));
      if (url.pathname.endsWith('/api/v1/users/assignable-stores')) return jsonResponse([assignableStore({ capabilities: ['FOOD_SERVICE'] })]);
      if (url.pathname.endsWith('/api/v1/registers')) return jsonResponse(page<Register>([register()], 100));
      if (url.pathname.endsWith('/api/v1/roles')) return jsonResponse(roles());
      if (url.pathname.endsWith('/api/v1/users') && init?.method === 'POST') return apiError('Creation failed', 409, 'duplicate_email');
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/users/new']} />);
    await userEvent.type(await screen.findByLabelText('Email'), 'duplicate@example.local');
    await userEvent.type(screen.getByLabelText('Display name'), 'Duplicate User');
    await userEvent.type(screen.getByLabelText('Initial password'), 'Duplicate!2026');
    await userEvent.click(screen.getByLabelText('CASHIER'));
    await userEvent.click(screen.getByLabelText('Main Store (MAIN)'));
    await userEvent.click(screen.getByRole('button', { name: 'Create user' }));

    expect(await screen.findByText('Creation failed')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'New user' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Employees' })).not.toBeInTheDocument();
  });

  it('loads a kitchen user and sends profile, role, and existing assignment updates', async () => {
    storeSession(['TENANT_OWNER']);
    const existing = adminUser({ roles: ['KITCHEN'], displayName: 'Kitchen User' });
    const updated = { ...existing, displayName: 'Kitchen Lead', version: 1 };
    const finalUser = { ...updated, roles: ['STORE_MANAGER'] as UserRole[], version: 2 };
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['TENANT_OWNER']));
      if (url.pathname.endsWith('/api/v1/users/assignable-stores')) return jsonResponse([assignableStore({ capabilities: ['FOOD_SERVICE'] })]);
      if (url.pathname.endsWith('/api/v1/registers')) return jsonResponse(page<Register>([register()], 100));
      if (url.pathname.endsWith('/api/v1/roles')) return jsonResponse(roles());
      if (url.pathname.endsWith(`/api/v1/users/${existing.id}/roles`) && init?.method === 'PUT') return jsonResponse(finalUser);
      if (url.pathname.endsWith(`/api/v1/users/${existing.id}`) && init?.method === 'PUT') return jsonResponse(updated);
      if (url.pathname.endsWith(`/api/v1/users/${existing.id}`)) return jsonResponse(existing);
      if (url.pathname.endsWith('/api/v1/users')) return jsonResponse(page<UserAdmin>([finalUser]));
      return apiError('Unexpected request');
    });

    render(<App initialEntries={[`/users/${existing.id}`]} />);

    expect(await screen.findByLabelText('Display name')).toHaveValue('Kitchen User');
    expect(screen.getByLabelText('Email')).toHaveValue(existing.email);
    expect(screen.getByLabelText('KITCHEN')).toBeChecked();
    expect(screen.getByLabelText('Main Store (MAIN)')).toBeChecked();
    expect(screen.getByLabelText('Front Register (FRONT), Main Store')).toBeChecked();
    await userEvent.clear(screen.getByLabelText('Display name'));
    await userEvent.type(screen.getByLabelText('Display name'), 'Kitchen Lead');
    await userEvent.click(screen.getByLabelText('STORE_MANAGER'));
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('User updated successfully')).toBeInTheDocument();
    const updateCall = fetchMock.mock.calls.find(([input, init]) => String(input).endsWith(`/api/v1/users/${existing.id}`) && init?.method === 'PUT');
    expect(JSON.parse(String(updateCall?.[1]?.body))).toEqual({
      email: existing.email,
      displayName: 'Kitchen Lead',
      locked: false,
      storeIds: existing.storeIds,
      registerIds: existing.registerIds,
      version: 0
    });
    const rolesCall = fetchMock.mock.calls.find(([input, init]) => String(input).endsWith(`/api/v1/users/${existing.id}/roles`) && init?.method === 'PUT');
    expect(JSON.parse(String(rolesCall?.[1]?.body))).toMatchObject({
      roles: ['STORE_MANAGER'],
      storeIds: existing.storeIds,
      registerIds: existing.registerIds,
      version: 1
    });
  });

  it('shows validation errors instead of silently ignoring save', async () => {
    storeSession(['TENANT_OWNER']);
    const existing = adminUser();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['TENANT_OWNER']));
      if (url.pathname.endsWith('/api/v1/users/assignable-stores')) return jsonResponse([assignableStore()]);
      if (url.pathname.endsWith('/api/v1/registers')) return jsonResponse(page<Register>([register()], 100));
      if (url.pathname.endsWith('/api/v1/roles')) return jsonResponse(roles());
      if (url.pathname.endsWith(`/api/v1/users/${existing.id}`)) return jsonResponse(existing);
      return apiError('Unexpected request');
    });
    render(<App initialEntries={[`/users/${existing.id}`]} />);
    await screen.findByLabelText('Display name');
    await userEvent.click(screen.getByLabelText('Main Store (MAIN)'));
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Please correct the highlighted user details before saving.')).toBeInTheDocument();
    expect(screen.getByText('Select at least one assigned store')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => String(input).endsWith(`/api/v1/users/${existing.id}`) && init?.method === 'PUT')).toBe(false);
  });

  it('shows saving state, prevents duplicate submission, and surfaces API failure', async () => {
    storeSession(['TENANT_OWNER']);
    const existing = adminUser();
    let rejectUpdate: ((reason?: unknown) => void) | undefined;
    const pendingUpdate = new Promise<Response>((_resolve, reject) => { rejectUpdate = reject; });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['TENANT_OWNER']));
      if (url.pathname.endsWith('/api/v1/users/assignable-stores')) return jsonResponse([assignableStore()]);
      if (url.pathname.endsWith('/api/v1/registers')) return jsonResponse(page<Register>([register()], 100));
      if (url.pathname.endsWith('/api/v1/roles')) return jsonResponse(roles());
      if (url.pathname.endsWith(`/api/v1/users/${existing.id}`) && init?.method === 'PUT') return pendingUpdate;
      if (url.pathname.endsWith(`/api/v1/users/${existing.id}`)) return jsonResponse(existing);
      return apiError('Unexpected request');
    });
    render(<App initialEntries={[`/users/${existing.id}`]} />);
    await userEvent.clear(await screen.findByLabelText('Display name'));
    await userEvent.type(screen.getByLabelText('Display name'), 'Updated Name');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('button', { name: 'Saving…' })).toBeDisabled();
    expect(fetchMock.mock.calls.filter(([input, init]) => String(input).endsWith(`/api/v1/users/${existing.id}`) && init?.method === 'PUT')).toHaveLength(1);
    rejectUpdate?.(new Error('Unable to update user.'));
    expect(await screen.findByText('Unable to update user.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeEnabled();
  });

  it('shows role permissions on the roles page', async () => {
    storeSession(['STORE_MANAGER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['STORE_MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/roles')) {
        return jsonResponse(roles());
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/roles']} />);

    expect(await screen.findByRole('heading', { name: 'Roles' })).toBeInTheDocument();
    const ownerPanel = (await screen.findByText('OWNER')).closest('.MuiPaper-root');
    expect(ownerPanel).not.toBeNull();
    expect(within(ownerPanel as HTMLElement).getByText('USER_MANAGE')).toBeInTheDocument();
  });
});
