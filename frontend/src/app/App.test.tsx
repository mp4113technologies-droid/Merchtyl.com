import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App, homeDestination } from './App';
import type { AuthResponse, CurrentUserResponse } from '../api/types';

function authResponse(overrides: Partial<AuthResponse> = {}): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'owner@example.local',
    displayName: 'Development Owner',
    roles: ['OWNER'],
    ...overrides
  };
}

function currentUser(overrides: Partial<CurrentUserResponse> = {}): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'owner@example.local',
    displayName: 'Development Owner',
    roles: ['OWNER'],
    ...overrides
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

function apiError(message: string, status = 401, code = 'bad_credentials') {
  return jsonResponse({
    code,
    message,
    status,
    path: '/api/v1/auth/login',
    method: 'POST',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

describe('App authentication', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('routes retail, kitchen, and combined operators correctly', () => {
    expect(homeDestination(['KITCHEN'])).toBe('/pos/food');
    expect(homeDestination(['CASHIER'])).toBe('/store-menu');
    expect(homeDestination(['CASHIER', 'KITCHEN'])).toBe('/store-menu');
    expect(homeDestination(['OWNER'])).toBeNull();
  });

  it('lets an assigned cashier start the business day from Store Menu', async () => {
    const storeId = '00000000-0000-0000-0000-000000000301';
    let opened = false;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/login')) return jsonResponse(authResponse({
        email: 'test3@adviam.com', displayName: 'Test 3', roles: ['CASHIER']
      }));
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser({
        email: 'test3@adviam.com', displayName: 'Test 3',
        roles: ['CASHIER'],
        permissions: ['BUSINESS_DAY_VIEW', 'BUSINESS_DAY_OPEN', 'BUSINESS_DAY_CLOSE', 'REGISTER_SESSION_OPEN']
      }));
      if (url.pathname.endsWith('/api/v1/register-sessions/current')) return noContentResponse();
      if (url.pathname.endsWith('/api/v1/registers')) return jsonResponse({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, first: true, last: true });
      if (url.pathname.endsWith('/api/v1/stores')) return jsonResponse({
        content: [{ id: storeId, code: 'MAIN', name: 'Main Store', capabilities: ['RETAIL'] }],
        page: 0, size: 100, totalElements: 1, totalPages: 1, first: true, last: true
      });
      if (url.pathname.endsWith('/api/v1/business-days/open') && init?.method === 'POST') {
        opened = true;
        return jsonResponse({ id: 'day-id', storeId, businessDate: '2026-09-03', status: 'OPEN' });
      }
      if (url.pathname.endsWith('/api/v1/business-days/operational-state')) return jsonResponse({
        storeId,
        currentBusinessDate: '2026-09-03',
        currentBusinessDay: opened ? { id: 'day-id', storeId, businessDate: '2026-09-03', status: 'OPEN' } : null,
        previousBusinessDay: null,
        state: opened ? 'OPEN' : 'NO_BUSINESS_DAY_TODAY',
        availableAction: opened ? 'NONE' : 'OPEN'
      });
      return apiError('Unexpected request', 500, 'unexpected');
    });

    render(<App initialEntries={['/login']} />);

    await userEvent.type(screen.getByLabelText('Email'), 'test3@adviam.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Password1!');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Start Business Day' }));
    expect(await screen.findByText('Business Day Open')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Close Business Day' })).toHaveAttribute('href', `/business-day/close?storeId=${storeId}`);
    expect(fetchMock.mock.calls.filter(([input, init]) => String(input).includes('/business-days/open') && init?.method === 'POST')).toHaveLength(1);
  });

  it('renders the login page when no session exists', async () => {
    vi.spyOn(globalThis, 'fetch');

    render(<App initialEntries={['/']} />);

    expect(await screen.findByRole('heading', { name: 'Merchtyl' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('logs in and shows the protected application shell', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/login')) {
        return jsonResponse(authResponse());
      }
      if (url.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser({ permissions: ['BUSINESS_DAY_VIEW', 'BUSINESS_DAY_OPEN'] }));
      }
      return apiError('Unexpected request', 500, 'unexpected');
    });

    render(<App initialEntries={['/login']} />);

    await userEvent.type(screen.getByLabelText('Email'), 'owner@example.local');
    await userEvent.type(screen.getByLabelText('Password'), 'OwnerDev!2026');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Owner dashboard' })).toBeInTheDocument();
    expect(screen.getAllByText('owner@example.local')).not.toHaveLength(0);
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/login', expect.objectContaining({ method: 'POST' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/me', expect.anything());
    expect(window.localStorage.getItem('merchtyl.session')).toContain('refresh-token');
  });

  it('allows a platform administrator to sign in from the main login page', async () => {
    const platformSession = authResponse({
      refreshToken: '',
      refreshTokenExpiresAt: new Date().toISOString(),
      email: 'aditya.admin@test.merchtyl.local',
      displayName: 'Aditya Admin',
      roles: ['PLATFORM_SUPER_ADMIN']
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/login')) {
        return apiError('Invalid email or password');
      }
      if (url.endsWith('/api/v1/platform/auth/login')) {
        return jsonResponse(platformSession);
      }
      if (url.endsWith('/api/v1/platform/dashboard')) {
        return jsonResponse({
          totalActiveMerchants: 0,
          pendingOnboardings: 0,
          suspendedMerchants: 0,
          activeStores: 0,
          activeMerchantUsers: 0,
          trialSubscriptions: 0,
          recentOnboardingActivity: [],
          failedInvitations: 0,
          supportAccessEnabled: false,
          supportAccessDefaultMinutes: 30
        });
      }
      return apiError('Unexpected request', 500, 'unexpected');
    });

    render(<App initialEntries={['/login']} />);

    await userEvent.type(screen.getByLabelText('Email'), 'aditya.admin@test.merchtyl.local');
    await userEvent.type(screen.getByLabelText('Password'), 'Test1234!');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Platform' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/login', expect.objectContaining({ method: 'POST' }));
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/platform/auth/login', expect.objectContaining({ method: 'POST' }));
    expect(window.localStorage.getItem('merchtyl.session')).toContain('PLATFORM_SUPER_ADMIN');
  });

  it('logs out and revokes the refresh token', async () => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse()));
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser());
      }
      if (url.endsWith('/api/v1/auth/logout')) {
        return noContentResponse();
      }
      return apiError('Unexpected request', 500, 'unexpected');
    });

    render(<App initialEntries={['/']} />);

    expect(await screen.findByRole('heading', { name: 'Owner dashboard' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(await screen.findByRole('heading', { name: 'Merchtyl' })).toBeInTheDocument();
    expect(window.localStorage.getItem('merchtyl.session')).toBeNull();
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/logout', expect.objectContaining({ method: 'POST' }));
  });

  it('protects shell routes from anonymous users', async () => {
    vi.spyOn(globalThis, 'fetch');

    render(<App initialEntries={['/unauthorized']} />);

    expect(await screen.findByRole('heading', { name: 'Merchtyl' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: "You don't have access to this feature" })).not.toBeInTheDocument();
  });

  it('renders the unauthorized page for authenticated users', async () => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse()));
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser());
      }
      return apiError('Unexpected request', 500, 'unexpected');
    });

    render(<App initialEntries={['/unauthorized']} />);

    expect(await screen.findByRole('heading', { name: "You don't have access to this feature" })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Return to Store Menu' })).toHaveAttribute('href', '/store-menu');
  });

  it('returns platform administrators to the platform dashboard', async () => {
    const roles = ['PLATFORM_SUPER_ADMIN'] as AuthResponse['roles'];
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse({ roles })));
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser({ roles }));
      return apiError('Unexpected request', 500, 'unexpected');
    });

    render(<App initialEntries={['/unauthorized']} />);

    expect(await screen.findByRole('link', { name: 'Return to Platform Dashboard' })).toHaveAttribute('href', '/platform');
  });

  it('supports keyboard shortcuts and moves focus to the main content region', async () => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse()));
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser());
      }
      return apiError('Unexpected request', 500, 'unexpected');
    });

    render(<App initialEntries={['/unauthorized']} />);

    expect(await screen.findByRole('heading', { name: "You don't have access to this feature" })).toBeInTheDocument();
    const main = screen.getByRole('main', { name: 'Workspace content' });
    await waitFor(() => expect(main).toHaveFocus());

    await userEvent.keyboard('?');
    expect(await screen.findByRole('dialog', { name: 'Keyboard shortcuts' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Keyboard shortcuts' })).not.toBeInTheDocument());

    screen.getByRole('button', { name: 'Sign out' }).focus();
    await userEvent.keyboard('{Alt>}s{/Alt}');
    expect(main).toHaveFocus();
  });

  it('refreshes an expired access token before opening the shell', async () => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse({
      accessToken: 'expired-access-token',
      refreshToken: 'old-refresh-token',
      accessTokenExpiresAt: new Date(Date.now() - 1000).toISOString()
    })));
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/refresh')) {
        return jsonResponse(authResponse({
          accessToken: 'new-access-token',
          refreshToken: 'new-refresh-token'
        }));
      }
      if (url.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser());
      }
      return apiError('Unexpected request', 500, 'unexpected');
    });

    render(<App initialEntries={['/']} />);

    expect(await screen.findByRole('heading', { name: 'Owner dashboard' })).toBeInTheDocument();
    expect(window.localStorage.getItem('merchtyl.session')).toContain('new-refresh-token');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/refresh', expect.objectContaining({ method: 'POST' }));
  });

  it('clears the session and shows an expired-session message when refresh fails', async () => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse({
      accessTokenExpiresAt: new Date(Date.now() - 1000).toISOString()
    })));
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/refresh')) {
        return apiError('Invalid refresh token', 401, 'bad_credentials');
      }
      return apiError('Unexpected request', 500, 'unexpected');
    });

    render(<App initialEntries={['/']} />);

    expect(await screen.findByText('Your session expired. Sign in again to continue.')).toBeInTheDocument();
    expect(window.localStorage.getItem('merchtyl.session')).toBeNull();
  });
});
