import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CatalogueReference,
  CurrentUserResponse,
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
    email: 'catalogue@example.local',
    displayName: 'Catalogue User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'catalogue@example.local',
    displayName: 'Catalogue User',
    roles
  };
}

function reference(overrides: Partial<CatalogueReference> = {}): CatalogueReference {
  return {
    id: '00000000-0000-0000-0000-000000000801',
    code: 'GROCERY',
    name: 'Grocery',
    description: 'General grocery items',
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function page(content: CatalogueReference[]) {
  return {
    content,
    page: 0,
    size: 10,
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
    path: '/api/v1/categories',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Catalogue reference pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('searches, creates, and deactivates categories', async () => {
    storeSession(['OWNER']);
    let current = reference();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/categories') && init?.method === 'POST') {
        current = reference({
          id: '00000000-0000-0000-0000-000000000802',
          code: 'DAIRY',
          name: 'Dairy',
          description: 'Cold dairy items'
        });
        return jsonResponse(current, 201);
      }
      if (url.pathname.endsWith(`/api/v1/categories/${current.id}/status`) && init?.method === 'PATCH') {
        current = reference({ ...current, active: false, version: 1 });
        return jsonResponse(current);
      }
      if (url.pathname.endsWith('/api/v1/categories')) {
        return jsonResponse(page([current]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/categories']} />);

    expect(await screen.findByRole('heading', { name: 'Categories' })).toBeInTheDocument();
    expect(await screen.findByText('Grocery')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Name'), 'dai');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/categories') && url.searchParams.get('name') === 'dai';
      })).toBe(true);
    });

    await userEvent.click(screen.getByRole('button', { name: 'New category' }));
    const dialog = await screen.findByRole('dialog', { name: 'New category' });
    await userEvent.type(within(dialog).getByLabelText('Code'), 'dairy');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Dairy');
    await userEvent.type(within(dialog).getByLabelText('Description'), 'Cold dairy items');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    expect(await screen.findByText('DAIRY')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'New category' })).not.toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole('button', { name: 'Deactivate Dairy' }));
    expect(await screen.findByText('Inactive')).toBeInTheDocument();
  });

  it('hides mutating actions from cashier users', async () => {
    storeSession(['CASHIER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      if (url.pathname.endsWith('/api/v1/categories')) {
        return jsonResponse(page([reference()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/categories']} />);

    expect(await screen.findByText('Grocery')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New category' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Deactivate Grocery' })).not.toBeInTheDocument();
  });

  it('renders brands and units against their endpoints', async () => {
    storeSession(['MANAGER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/brands')) {
        return jsonResponse(page([reference({ code: 'ACME', name: 'Acme' })]));
      }
      if (url.pathname.endsWith('/api/v1/units')) {
        return jsonResponse(page([reference({ code: 'EA', name: 'Each' })]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/brands']} />);
    expect(await screen.findByRole('heading', { name: 'Brands' })).toBeInTheDocument();
    expect(await screen.findByText('Acme')).toBeInTheDocument();

    render(<App initialEntries={['/settings/units']} />);
    expect(await screen.findByRole('heading', { name: 'Units of measure' })).toBeInTheDocument();
    expect(await screen.findByText('Each')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes('/api/v1/brands'))).toBe(true);
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes('/api/v1/units'))).toBe(true);
  });
});
