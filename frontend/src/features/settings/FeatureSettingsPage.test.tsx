import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  FeatureOverride,
  FeatureResolution,
  Store,
  Register
} from '../../api/types';

const userId = '00000000-0000-0000-0000-000000000901';
const storeId = '00000000-0000-0000-0000-000000000902';
const registerId = '00000000-0000-0000-0000-000000000903';
const tenantOverrideId = '00000000-0000-0000-0000-000000000904';

function authResponse(): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId,
    email: 'owner@example.local',
    displayName: 'Owner One',
    roles: ['OWNER']
  };
}

function currentUser(): CurrentUserResponse {
  return {
    userId,
    email: 'owner@example.local',
    displayName: 'Owner One',
    roles: ['OWNER']
  };
}

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  }));
}

function page<T>(content: T[]) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true
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
    code: 'FRONT-1',
    name: 'Front Register',
    locationDescription: 'Front counter',
    active: true,
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0
  };
}

function override(enabled: boolean, version = 0): FeatureOverride {
  return {
    id: tenantOverrideId,
    enabled,
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version
  };
}

function resolutions(tenantOverride: FeatureOverride | null = null): FeatureResolution[] {
  return [
    {
      definition: {
        id: '00000000-0000-0000-0000-000000000f04',
        code: 'AGE_VERIFICATION',
        name: 'Age verification',
        description: 'Enable age-verification prompts and enforcement for restricted products.',
        defaultEnabled: true,
        createdAt: '2026-07-28T12:00:00Z',
        updatedAt: '2026-07-28T12:00:00Z',
        version: 0
      },
      enabled: tenantOverride?.enabled ?? true,
      source: tenantOverride ? 'TENANT' : 'DEFAULT',
      storeId: null,
      registerId: null,
      tenantOverride,
      storeOverride: null,
      registerOverride: null
    },
    {
      definition: {
        id: '00000000-0000-0000-0000-000000000f05',
        code: 'GIFT_CARDS',
        name: 'Gift cards',
        description: 'Enable gift card sale, redemption, and balance workflows.',
        defaultEnabled: false,
        createdAt: '2026-07-28T12:00:00Z',
        updatedAt: '2026-07-28T12:00:00Z',
        version: 0
      },
      enabled: false,
      source: 'DEFAULT',
      storeId: null,
      registerId: null,
      tenantOverride: null,
      storeOverride: null,
      registerOverride: null
    }
  ];
}

describe('FeatureSettingsPage', () => {
  beforeEach(() => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse()));
    vi.restoreAllMocks();
  });

  it('renders resolved feature flags and updates a deployment override', async () => {
    let tenantOverride: FeatureOverride | null = null;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser());
      }
      if (url.includes('/api/v1/stores')) {
        return jsonResponse(page([store()]));
      }
      if (url.includes('/api/v1/registers')) {
        return jsonResponse(page([register()]));
      }
      if (url.includes('/api/v1/features/resolution')) {
        return jsonResponse(resolutions(tenantOverride));
      }
      if (url.endsWith('/api/v1/features/AGE_VERIFICATION/deployment') && init?.method === 'PUT') {
        const body = JSON.parse(String(init.body)) as { enabled: boolean };
        tenantOverride = override(body.enabled, tenantOverride ? tenantOverride.version + 1 : 0);
        return jsonResponse(resolutions(tenantOverride)[0]);
      }
      return jsonResponse({ message: 'Unexpected request' }, 500);
    });

    render(<App initialEntries={['/settings/features']} />);

    expect(await screen.findByRole('heading', { name: 'Feature flags' })).toBeInTheDocument();
    expect(await screen.findByText('Age verification')).toBeInTheDocument();
    expect(screen.getAllByText('DEFAULT').length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('combobox', { name: 'Age verification deployment override' }));
    await userEvent.click(within(screen.getByRole('listbox')).getByText('Disabled'));

    expect(await screen.findByText('AGE VERIFICATION deployment override saved.')).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/features/AGE_VERIFICATION/deployment'),
        expect.objectContaining({
          method: 'PUT',
          body: expect.stringContaining('"enabled":false')
        })
      );
    });
    expect(await screen.findByText('TENANT')).toBeInTheDocument();
  });

  it('resolves feature flags for selected store and register scopes', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser());
      }
      if (url.includes('/api/v1/stores')) {
        return jsonResponse(page([store()]));
      }
      if (url.includes('/api/v1/registers')) {
        return jsonResponse(page([register()]));
      }
      if (url.includes('/api/v1/features/resolution')) {
        return jsonResponse(resolutions());
      }
      return jsonResponse({ message: 'Unexpected request' }, 500);
    });

    render(<App initialEntries={['/settings/features']} />);

    expect(await screen.findByText('Gift cards')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('combobox', { name: 'Store scope' }));
    await userEvent.click(within(screen.getByRole('listbox')).getByText('Main Store'));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining(`storeId=${storeId}`), expect.anything());
    });

    await userEvent.click(screen.getByRole('combobox', { name: 'Register scope' }));
    await userEvent.click(within(screen.getByRole('listbox')).getByText('Front Register'));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining(`registerId=${registerId}`), expect.anything());
    });
  });
});
