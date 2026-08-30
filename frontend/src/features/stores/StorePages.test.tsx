import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type { AuthResponse, CurrentUserResponse, Store, StoreListResponse, UserRole } from '../../api/types';

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
    displayName: 'Store User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'user@example.local',
    displayName: 'Store User',
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
    countryId: '20000000-0000-0000-0000-000000000001',
    administrativeAreaCode: 'CA',
    administrativeDivisionCode: 'CA',
    administrativeDivisionId: '20000000-0000-0000-0000-000000000105',
    address: '100 Market Street, San Francisco, CA',
    phone: '+1-555-0100',
    email: 'main@example.test',
    currencyCode: 'USD',
    currencyId: '30000000-0000-0000-0000-000000000002',
    locale: 'en-US',
    timezone: 'America/Los_Angeles',
    timezoneId: '30000000-0000-0000-0000-000000000213',
    timezoneName: 'America/Los_Angeles',
    taxRegionId: '40000000-0000-0000-0000-000000000105',
    taxRegionCode: 'US-CA',
    pricesIncludeTax: true,
    negativeStockAllowed: false,
    active: true,
    createdAt: '2026-07-21T12:00:00Z',
    updatedAt: '2026-07-21T12:00:00Z',
    version: 0,
    capabilities: ['RETAIL'],
    foodServiceEnabled: false,
    kitchenDisplayName: null,
    kitchenUsersCount: 0,
    ...overrides
  };
}

function pageResponse(content: Store[], overrides: Partial<StoreListResponse> = {}): StoreListResponse {
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
    path: '/api/v1/stores',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function referenceResponse(url: URL) {
  if (url.pathname.endsWith('/api/v1/reference/countries')) {
    return jsonResponse([
      { id: '10000000-0000-0000-0000-000000000001', alpha2Code: 'CA', alpha3Code: 'CAN', name: 'Canada', defaultCurrencyCode: 'CAD', defaultLanguageCode: 'en', active: true, displayOrder: 10 },
      { id: '20000000-0000-0000-0000-000000000001', alpha2Code: 'US', alpha3Code: 'USA', name: 'United States', defaultCurrencyCode: 'USD', defaultLanguageCode: 'en', active: true, displayOrder: 20 }
    ]);
  }
  if (url.pathname.endsWith('/api/v1/reference/countries/CA/administrative-divisions')) {
    return jsonResponse([
      { id: '10000000-0000-0000-0000-000000000104', countryCode: 'CA', code: 'NB', name: 'New Brunswick', divisionType: 'PROVINCE', defaultTimezone: 'America/Moncton', defaultTaxRegionCode: 'CA-NB', active: true, displayOrder: 40 }
    ]);
  }
  if (url.pathname.endsWith('/api/v1/reference/countries/US/administrative-divisions')) {
    return jsonResponse([
      { id: '20000000-0000-0000-0000-000000000105', countryCode: 'US', code: 'CA', name: 'California', divisionType: 'STATE', defaultTimezone: 'America/Los_Angeles', defaultTaxRegionCode: 'US-CA', active: true, displayOrder: 50 },
      { id: '20000000-0000-0000-0000-000000000119', countryCode: 'US', code: 'ME', name: 'Maine', divisionType: 'STATE', defaultTimezone: 'America/New_York', defaultTaxRegionCode: 'US-ME', active: true, displayOrder: 190 }
    ]);
  }
  if (url.pathname.endsWith('/api/v1/reference/countries/CA/currencies')) {
    return jsonResponse([{ id: '30000000-0000-0000-0000-000000000001', code: 'CAD', name: 'Canadian Dollar', symbol: '$', decimalPlaces: 2, active: true }]);
  }
  if (url.pathname.endsWith('/api/v1/reference/countries/US/currencies')) {
    return jsonResponse([{ id: '30000000-0000-0000-0000-000000000002', code: 'USD', name: 'United States Dollar', symbol: '$', decimalPlaces: 2, active: true }]);
  }
  if (url.pathname.endsWith('/api/v1/reference/administrative-divisions/10000000-0000-0000-0000-000000000104/timezones')) {
    return jsonResponse([{ id: '30000000-0000-0000-0000-000000000203', ianaName: 'America/Moncton', displayName: 'Atlantic Time - Moncton', countryCode: 'CA', active: true, displayOrder: 30, defaultForDivision: true }]);
  }
  if (url.pathname.endsWith('/api/v1/reference/administrative-divisions/20000000-0000-0000-0000-000000000105/timezones')) {
    return jsonResponse([{ id: '30000000-0000-0000-0000-000000000213', ianaName: 'America/Los_Angeles', displayName: 'Pacific Time', countryCode: 'US', active: true, displayOrder: 140, defaultForDivision: true }]);
  }
  if (url.pathname.endsWith('/api/v1/reference/administrative-divisions/10000000-0000-0000-0000-000000000104/tax-regions')) {
    return jsonResponse([{ id: '40000000-0000-0000-0000-000000000104', countryCode: 'CA', administrativeDivisionId: '10000000-0000-0000-0000-000000000104', administrativeDivisionCode: 'NB', code: 'CA-NB', name: 'New Brunswick tax region', active: true, defaultForDivision: true, taxJurisdictionId: null }]);
  }
  if (url.pathname.endsWith('/api/v1/reference/administrative-divisions/20000000-0000-0000-0000-000000000105/tax-regions')) {
    return jsonResponse([{ id: '40000000-0000-0000-0000-000000000105', countryCode: 'US', administrativeDivisionId: '20000000-0000-0000-0000-000000000105', administrativeDivisionCode: 'CA', code: 'US-CA', name: 'California tax region', active: true, defaultForDivision: true, taxJurisdictionId: null }]);
  }
  return undefined;
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Store pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders the store table and applies search filters', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      const reference = referenceResponse(url);
      if (reference) return reference;
      if (url.pathname.endsWith('/api/v1/stores')) {
        if (url.searchParams.get('name') === 'main') {
          return jsonResponse(pageResponse([store()], { totalElements: 1 }));
        }
        return jsonResponse(pageResponse([
          store(),
          store({
            id: '00000000-0000-0000-0000-000000000902',
            code: 'WAREHOUSE',
            name: 'Warehouse',
            active: false
          })
        ], { totalElements: 2 }));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/stores']} />);

    expect(await screen.findByRole('heading', { name: 'Stores' })).toBeInTheDocument();
    expect(await screen.findByText('Main Store')).toBeInTheDocument();
    expect(screen.getByText('Warehouse')).toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Store filters' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Stores' })).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Name'), 'main');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/v1/stores?'), expect.anything());
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/stores') && url.searchParams.get('name') === 'main';
      })).toBe(true);
    });
    expect(await screen.findByText('Main Store')).toBeInTheDocument();
  });

  it('hides mutating actions from cashier users', async () => {
    storeSession(['CASHIER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      const reference = referenceResponse(url);
      if (reference) return reference;
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(pageResponse([store()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/stores']} />);

    expect(await screen.findByText('Main Store')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New store' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Deactivate Main Store/i })).not.toBeInTheDocument();
  });

  it('creates a store and opens the edit page', async () => {
    storeSession(['OWNER']);
    const created = store({
      id: '00000000-0000-0000-0000-000000000903',
      code: 'ANNEX',
      name: 'Annex Store',
      email: 'annex@example.test'
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      const reference = referenceResponse(url);
      if (reference) return reference;
      if (url.pathname.endsWith('/api/v1/stores/defaults')) {
        return jsonResponse({
          countryCode: null,
          administrativeDivisionCode: null,
          currencyCode: null,
          locale: null,
          timezone: null,
          taxRegionCode: null
        });
      }
      if (url.pathname.endsWith('/api/v1/stores') && init?.method === 'POST') {
        return jsonResponse(created, 201);
      }
      if (url.pathname.endsWith(`/api/v1/stores/${created.id}`)) {
        return jsonResponse(created);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/stores/new']} />);

    expect(await screen.findByRole('heading', { name: 'New store' })).toBeInTheDocument();
    await userEvent.type(await screen.findByLabelText('Code'), 'annex');
    await userEvent.type(screen.getByLabelText('Name'), 'Annex Store');
    await userEvent.type(screen.getByLabelText('Legal name'), 'Annex Store LLC');
    await userEvent.type(screen.getByLabelText('Address'), '200 Market Street');
    await userEvent.type(screen.getByLabelText('Email'), 'annex@example.test');

    await userEvent.click(screen.getByLabelText('Country'));
    await userEvent.click(await screen.findByText('Canada (CA)'));
    expect(await screen.findByText('CAD - Canadian Dollar ($)')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Province / Territory'));
    await userEvent.click(await screen.findByText('New Brunswick (NB)'));
    expect(await screen.findByText('America/Moncton - Atlantic Time - Moncton')).toBeInTheDocument();
    expect(await screen.findByText('CA-NB - New Brunswick tax region')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Create store' }));

    expect(await screen.findByRole('heading', { name: 'Annex Store' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      const body = init?.body ? JSON.parse(String(init.body)) : {};
      return url.pathname.endsWith('/api/v1/stores')
        && init?.method === 'POST'
        && body.countryCode === 'CA'
        && body.administrativeDivisionCode === 'NB'
        && body.currencyCode === 'CAD'
        && body.timezone === 'America/Moncton'
        && body.taxRegionCode === 'CA-NB';
    })).toBe(true);
  });

  it('validates, edits, and deactivates a store', async () => {
    storeSession(['MANAGER']);
    let current = store();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      const reference = referenceResponse(url);
      if (reference) return reference;
      if (url.pathname.endsWith(`/api/v1/stores/${current.id}`) && init?.method === 'PUT') {
        const body = JSON.parse(String(init.body));
        current = store({ ...current, ...body, name: body.name, version: 1 });
        return jsonResponse(current);
      }
      if (url.pathname.endsWith(`/api/v1/stores/${current.id}/status`) && init?.method === 'PATCH') {
        current = store({ ...current, active: false, version: 2 });
        return jsonResponse(current);
      }
      if (url.pathname.endsWith(`/api/v1/stores/${current.id}`)) {
        return jsonResponse(current);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={[`/stores/${current.id}`]} />);

    expect(await screen.findByRole('heading', { name: 'Main Store' })).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Name'));
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Name is required')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Name'), 'Updated Store');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Store saved.')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Updated Store' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Deactivate' }));
    const heading = await screen.findByRole('heading', { name: 'Updated Store' });
    expect(within(heading.closest('div')?.parentElement ?? document.body).getByText('Inactive')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => String(input).endsWith('/status') && init?.method === 'PATCH')).toBe(true);
  });
});
