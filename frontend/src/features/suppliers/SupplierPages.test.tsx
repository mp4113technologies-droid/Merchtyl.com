import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type { AuthResponse, CurrentUserResponse, Supplier, SupplierListResponse, UserRole } from '../../api/types';

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
    displayName: 'Supplier User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'user@example.local',
    displayName: 'Supplier User',
    roles
  };
}

function supplier(overrides: Partial<Supplier> = {}): Supplier {
  return {
    id: '00000000-0000-0000-0000-000000001101',
    code: 'ACME',
    name: 'ACME Foods',
    contactName: 'Jane Buyer',
    phone: '555-0100',
    email: 'jane@example.test',
    address: '10 Warehouse Road',
    notes: 'Net 30',
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function pageResponse(content: Supplier[], overrides: Partial<SupplierListResponse> = {}): SupplierListResponse {
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
    path: '/api/v1/suppliers',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Supplier pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders the supplier table and applies search filters', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/suppliers')) {
        if (url.searchParams.get('name') === 'acme') {
          return jsonResponse(pageResponse([supplier()], { totalElements: 1 }));
        }
        return jsonResponse(pageResponse([
          supplier(),
          supplier({
            id: '00000000-0000-0000-0000-000000001102',
            code: 'FRESH',
            name: 'Fresh Goods',
            active: false
          })
        ], { totalElements: 2 }));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/suppliers']} />);

    expect(await screen.findByRole('heading', { name: 'Suppliers' })).toBeInTheDocument();
    expect(await screen.findByText('ACME Foods')).toBeInTheDocument();
    expect(screen.getByText('Fresh Goods')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Name'), 'acme');
    await userEvent.type(screen.getByLabelText('Contact'), 'jane');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/suppliers')
          && url.searchParams.get('name') === 'acme'
          && url.searchParams.get('contactName') === 'jane';
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
      if (url.pathname.endsWith('/api/v1/suppliers')) {
        return jsonResponse(pageResponse([supplier()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/suppliers']} />);

    expect(await screen.findByText('ACME Foods')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New supplier' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Deactivate ACME Foods/i })).not.toBeInTheDocument();
  });

  it('creates a supplier and opens the edit page', async () => {
    storeSession(['OWNER']);
    const created = supplier({
      id: '00000000-0000-0000-0000-000000001103',
      code: 'GLOBAL',
      name: 'Global Supply',
      email: 'orders@global.example'
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/suppliers') && init?.method === 'POST') {
        return jsonResponse(created, 201);
      }
      if (url.pathname.endsWith(`/api/v1/suppliers/${created.id}`)) {
        return jsonResponse(created);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/suppliers/new']} />);

    expect(await screen.findByRole('heading', { name: 'New supplier' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Code'), 'global');
    await userEvent.type(screen.getByLabelText('Name'), 'Global Supply');
    await userEvent.type(screen.getByLabelText('Contact name'), 'Order Desk');
    await userEvent.type(screen.getByLabelText('Email'), 'orders@global.example');
    await userEvent.click(screen.getByRole('button', { name: 'Create supplier' }));

    expect(await screen.findByRole('heading', { name: 'Global Supply' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith('/api/v1/suppliers') && init?.method === 'POST';
    })).toBe(true);
  });

  it('validates, edits, and deactivates a supplier', async () => {
    storeSession(['MANAGER']);
    let current = supplier();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith(`/api/v1/suppliers/${current.id}`) && init?.method === 'PUT') {
        const body = JSON.parse(String(init.body));
        current = supplier({ ...current, ...body, name: body.name, version: 1 });
        return jsonResponse(current);
      }
      if (url.pathname.endsWith(`/api/v1/suppliers/${current.id}/status`) && init?.method === 'PATCH') {
        current = supplier({ ...current, active: false, version: 2 });
        return jsonResponse(current);
      }
      if (url.pathname.endsWith(`/api/v1/suppliers/${current.id}`)) {
        return jsonResponse(current);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={[`/suppliers/${current.id}`]} />);

    expect(await screen.findByRole('heading', { name: 'ACME Foods' })).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Name'));
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Name is required')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Name'), 'Updated Supplier');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Supplier saved.')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Updated Supplier' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Deactivate' }));
    const heading = await screen.findByRole('heading', { name: 'Updated Supplier' });
    expect(within(heading.closest('div')?.parentElement ?? document.body).getByText('Inactive')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => String(input).endsWith('/status') && init?.method === 'PATCH')).toBe(true);
  });
});
