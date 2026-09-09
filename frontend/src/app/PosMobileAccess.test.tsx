import { render, screen, waitFor, within } from '@testing-library/react';
import type { AuthResponse, CurrentUserResponse } from '../api/types';
import type { UserRole } from '../api/types';
import { App } from './App';

const originalNavigator = {
  userAgent: Object.getOwnPropertyDescriptor(window.navigator, 'userAgent'),
  platform: Object.getOwnPropertyDescriptor(window.navigator, 'platform'),
  maxTouchPoints: Object.getOwnPropertyDescriptor(window.navigator, 'maxTouchPoints')
};

function setMobileDevice() {
  Object.defineProperties(window.navigator, {
    userAgent: { configurable: true, value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Mobile/15E148' },
    platform: { configurable: true, value: 'iPhone' },
    maxTouchPoints: { configurable: true, value: 5 }
  });
}

function session(roles: UserRole[] = ['OWNER']): AuthResponse {
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(Date.now() + 24 * 60 * 60_000).toISOString(),
    userId: 'user-id',
    email: 'owner@example.local',
    displayName: 'Owner',
    roles
  };
}

function user(permissions: string[], roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return { userId: 'user-id', email: 'owner@example.local', displayName: 'Owner', roles, permissions };
}

function json(body: unknown) {
  return Promise.resolve(new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

function emptyPage() {
  return { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, first: true, last: true };
}

beforeEach(() => {
  setMobileDevice();
  window.localStorage.clear();
  window.localStorage.setItem('merchtyl.session', JSON.stringify(session()));
});

afterEach(() => {
  vi.restoreAllMocks();
  for (const [key, descriptor] of Object.entries(originalNavigator)) {
    if (descriptor) Object.defineProperty(window.navigator, key, descriptor);
    else Reflect.deleteProperty(window.navigator, key);
  }
});

describe('mobile POS integration', () => {
  it.each(['/pos', '/pos/food'])('protects the direct operational route %s before POS APIs initialize', async (path) => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (String(input).endsWith('/api/v1/auth/me')) return json(user(['POS_ACCESS', 'FOOD_POS_ACCESS']));
      throw new Error(`POS API should not initialize: ${String(input)}`);
    });

    render(<App initialEntries={[path]} />);

    expect(await screen.findByRole('heading', { name: 'POS unavailable on mobile' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes('/register-sessions/current'))).toBe(false);
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes('/registers'))).toBe(false);
  });

  it('shows entitled POS capabilities as disabled and leaves unentitled capabilities filtered out', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = new URL(String(input), window.location.origin).pathname;
      if (path.endsWith('/api/v1/auth/me')) return json(user(['POS_ACCESS']));
      if (path.endsWith('/api/v1/register-sessions/current')) return Promise.resolve(new Response(null, { status: 204 }));
      if (path.endsWith('/api/v1/registers')) return json(emptyPage());
      if (path.endsWith('/api/v1/stores')) return json({ ...emptyPage(), content: [{ id: 'store-id', code: 'MAIN', name: 'Main', capabilities: ['RETAIL', 'FOOD_SERVICE'] }] });
      throw new Error(`Unexpected request: ${path}`);
    });

    render(<App initialEntries={['/store-menu']} />);

    const workspace = await screen.findByRole('main', { name: 'Workspace content' });
    const retail = await within(workspace).findByText('Retail POS');
    expect(retail.closest('a')).toHaveAttribute('aria-disabled', 'true');
    expect(within(workspace).getAllByText('Desktop register required').length).toBeGreaterThanOrEqual(1);
    expect(within(workspace).queryByText('Restaurant / Kitchen POS')).not.toBeInTheDocument();
  });

  it('removes POS and live operational actions from mobile management navigation', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = new URL(String(input), window.location.origin).pathname;
      if (path.endsWith('/api/v1/auth/me')) return json(user(['POS_ACCESS', 'FOOD_POS_ACCESS']));
      if (path.endsWith('/api/v1/register-sessions/current')) return Promise.resolve(new Response(null, { status: 204 }));
      if (path.endsWith('/api/v1/stores')) return json({ ...emptyPage(), content: [{ id: 'store-id', code: 'MAIN', name: 'Main', capabilities: ['RETAIL', 'FOOD_SERVICE'] }] });
      if (path.endsWith('/api/v1/registers')) return json(emptyPage());
      throw new Error(`Unexpected request: ${path}`);
    });

    render(<App initialEntries={['/store-menu']} />);

    const navigation = await screen.findByRole('navigation', { name: 'Primary navigation' });
    await waitFor(() => expect(within(navigation).getByRole('link', { name: 'Users' })).toBeInTheDocument());
    expect(within(navigation).queryByRole('link', { name: 'Retail POS' })).not.toBeInTheDocument();
    expect(within(navigation).queryByRole('link', { name: 'Restaurant POS' })).not.toBeInTheDocument();
    expect(within(navigation).queryByRole('link', { name: 'Cash Movements' })).not.toBeInTheDocument();
    expect(within(navigation).queryByRole('link', { name: 'Returns' })).not.toBeInTheDocument();
    expect(within(navigation).queryByRole('link', { name: 'Lottery Sale' })).not.toBeInTheDocument();
    expect(within(navigation).queryByRole('link', { name: 'Lottery Payout' })).not.toBeInTheDocument();
    expect(within(navigation).queryByText('Sales')).not.toBeInTheDocument();
  });

  it.each(['OWNER', 'MANAGER'] as const)('keeps approved management navigation for mobile %s', async (role) => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(session([role])));
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = new URL(String(input), window.location.origin).pathname;
      if (path.endsWith('/api/v1/auth/me')) return json(user([], [role]));
      if (path.endsWith('/api/v1/register-sessions/current')) return Promise.resolve(new Response(null, { status: 204 }));
      if (path.endsWith('/api/v1/stores')) return json(emptyPage());
      throw new Error(`Unexpected request: ${path}`);
    });

    render(<App initialEntries={['/unauthorized']} />);

    const navigation = await screen.findByRole('navigation', { name: 'Primary navigation' });
    expect(within(navigation).getByRole('link', { name: 'Dashboard' })).toBeInTheDocument();
    expect(within(navigation).getByRole('link', { name: 'Sales Reports' })).toBeInTheDocument();
    expect(within(navigation).getByRole('link', { name: 'Register Reports' })).toBeInTheDocument();
    expect(within(navigation).getByRole('link', { name: 'EOD Reports' })).toBeInTheDocument();
    expect(within(navigation).getByRole('link', { name: 'Users' })).toBeInTheDocument();
    expect(within(navigation).getByRole('link', { name: 'Stores' })).toBeInTheDocument();
    expect(within(navigation).queryByRole('link', { name: 'Subscription & Billing' })).not.toBeInTheDocument();
  });

  it.each(['/returns', '/register/cash-movements', '/lottery/sale', '/lottery/payout'])('blocks direct mobile operational route %s', async (path) => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const requestPath = new URL(String(input), window.location.origin).pathname;
      if (requestPath.endsWith('/api/v1/auth/me')) return json(user([]));
      if (requestPath.endsWith('/api/v1/register-sessions/current')) return Promise.resolve(new Response(null, { status: 204 }));
      if (requestPath.endsWith('/api/v1/stores')) return json(emptyPage());
      throw new Error(`Operational page should not initialize: ${requestPath}`);
    });

    render(<App initialEntries={[path]} />);

    expect(await screen.findByRole('heading', { name: 'Operation unavailable on mobile' })).toBeInTheDocument();
  });

  it('does not block merchant management pages on mobile', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = new URL(String(input), window.location.origin).pathname;
      if (path.endsWith('/api/v1/auth/me')) return json(user([]));
      if (path.endsWith('/api/v1/register-sessions/current')) return Promise.resolve(new Response(null, { status: 204 }));
      if (path.endsWith('/api/v1/stores')) return json(emptyPage());
      throw new Error(`Unexpected request: ${path}`);
    });

    render(<App initialEntries={['/stores']} />);

    expect(await screen.findByRole('heading', { name: 'Stores' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'POS unavailable on mobile' })).not.toBeInTheDocument();
  });

  it.each(['CASHIER', 'KITCHEN'] as const)('blocks a mobile %s before the merchant shell mounts', async (role) => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(session([role])));
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      if (String(input).endsWith('/api/v1/auth/me')) return json(user([], [role]));
      throw new Error(`Merchant shell should not initialize: ${String(input)}`);
    });

    render(<App initialEntries={['/store-menu']} />);

    expect(await screen.findByRole('heading', { name: 'Mobile access unavailable' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign Out' })).toBeInTheDocument();
    expect(screen.queryByText('Store Menu')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes('/register-sessions/current'))).toBe(false);
  });

  it('allows a platform super admin to use the mobile platform dashboard', async () => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(session(['PLATFORM_SUPER_ADMIN'])));
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const path = new URL(String(input), window.location.origin).pathname;
      if (path.endsWith('/api/v1/platform/dashboard')) return json({
        totalActiveMerchants: 1,
        pendingOnboardings: 0,
        suspendedMerchants: 0,
        activeStores: 1,
        activeMerchantUsers: 1,
        trialSubscriptions: 0,
        recentOnboardingActivity: [],
        recentLifecycleActivity: [],
        failedInvitations: 0,
        supportAccessEnabled: false,
        supportAccessDefaultMinutes: 30
      });
      throw new Error(`Unexpected request: ${path}`);
    });

    render(<App initialEntries={['/platform']} hostname="platform.merchtyl.com" />);

    expect(await screen.findByRole('heading', { name: 'Platform' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'New merchant' })).toHaveAttribute('href', '/platform/merchants/new');
  });
});
