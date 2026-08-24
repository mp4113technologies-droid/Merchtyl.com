import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  FeatureResolution,
  LotteryOperator,
  LotteryOperatorListResponse,
  TaxJurisdiction,
  TaxJurisdictionListResponse,
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
    displayName: 'Lottery User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'user@example.local',
    displayName: 'Lottery User',
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
    storeId: null,
    registerId: null,
    tenantOverride: {
      id: '00000000-0000-0000-0000-00000000f201',
      enabled,
      createdAt: '2026-07-28T12:00:00Z',
      updatedAt: '2026-07-28T12:00:00Z',
      version: 0
    },
    storeOverride: null,
    registerOverride: null
  }];
}

function jurisdiction(overrides: Partial<TaxJurisdiction> = {}): TaxJurisdiction {
  return {
    id: '00000000-0000-0000-0000-00000000a101',
    countryId: '00000000-0000-0000-0000-00000000c101',
    administrativeAreaId: null,
    code: 'CA',
    name: 'California',
    type: 'STATE',
    active: true,
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function operator(overrides: Partial<LotteryOperator> = {}): LotteryOperator {
  return {
    id: '00000000-0000-0000-0000-00000000b101',
    code: 'CLOTTO',
    name: 'California Lottery',
    jurisdictionId: '00000000-0000-0000-0000-00000000a101',
    jurisdictionCode: 'CA',
    jurisdictionName: 'California',
    supportContact: 'support@lottery.example',
    settlementFrequency: 'WEEKLY',
    active: true,
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function lotteryPageResponse(content: LotteryOperator[], overrides: Partial<LotteryOperatorListResponse> = {}): LotteryOperatorListResponse {
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

function jurisdictionPageResponse(content: TaxJurisdiction[]): TaxJurisdictionListResponse {
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

function apiError(message: string, status = 500, code = 'unexpected') {
  return jsonResponse({
    code,
    message,
    status,
    path: '/api/v1/lottery/operators',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Lottery operator pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders operators and applies search filters', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/features/resolution')) {
        return jsonResponse(featureResolution(true));
      }
      if (url.pathname.endsWith('/api/v1/tax/jurisdictions')) {
        return jsonResponse(jurisdictionPageResponse([jurisdiction()]));
      }
      if (url.pathname.endsWith('/api/v1/lottery/operators')) {
        if (url.searchParams.get('name') === 'california') {
          return jsonResponse(lotteryPageResponse([operator()], { totalElements: 1 }));
        }
        return jsonResponse(lotteryPageResponse([
          operator(),
          operator({
            id: '00000000-0000-0000-0000-00000000b102',
            code: 'NLOTTO',
            name: 'Nevada Lottery',
            jurisdictionName: 'Nevada',
            jurisdictionCode: 'NV',
            active: false
          })
        ], { totalElements: 2 }));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/lottery/operators']} />);

    expect(await screen.findByRole('heading', { name: 'Lottery operators' })).toBeInTheDocument();
    expect(await screen.findByText('California Lottery')).toBeInTheDocument();
    expect(screen.getByText('Nevada Lottery')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Name'), 'california');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/lottery/operators')
          && url.searchParams.get('name') === 'california';
      })).toBe(true);
    });
  });

  it('shows disabled feature state and suppresses mutating actions', async () => {
    storeSession(['MANAGER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/features/resolution')) {
        return jsonResponse(featureResolution(false));
      }
      if (url.pathname.endsWith('/api/v1/tax/jurisdictions')) {
        return jsonResponse(jurisdictionPageResponse([jurisdiction()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/lottery/operators']} />);

    expect(await screen.findByText(/Lottery sales is disabled/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New operator' })).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes('/api/v1/lottery/operators'))).toBe(false);
  });

  it('creates an operator and opens the detail page', async () => {
    storeSession(['OWNER']);
    const created = operator({
      id: '00000000-0000-0000-0000-00000000b103',
      code: 'NYLOTTO',
      name: 'New York Lottery',
      jurisdictionId: '00000000-0000-0000-0000-00000000a102',
      jurisdictionCode: 'NY',
      jurisdictionName: 'New York',
      settlementFrequency: 'DAILY'
    });
    const jurisdictions = [
      jurisdiction(),
      jurisdiction({
        id: '00000000-0000-0000-0000-00000000a102',
        code: 'NY',
        name: 'New York'
      })
    ];
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/features/resolution')) {
        return jsonResponse(featureResolution(true));
      }
      if (url.pathname.endsWith('/api/v1/tax/jurisdictions')) {
        return jsonResponse(jurisdictionPageResponse(jurisdictions));
      }
      if (url.pathname.endsWith('/api/v1/lottery/operators') && init?.method === 'POST') {
        return jsonResponse(created, 201);
      }
      if (url.pathname.endsWith(`/api/v1/lottery/operators/${created.id}`)) {
        return jsonResponse(created);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/lottery/operators/new']} />);

    expect(await screen.findByRole('heading', { name: 'New lottery operator' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Create operator' })).toBeEnabled());
    await userEvent.type(screen.getByLabelText('Code'), 'nylotto');
    await userEvent.type(screen.getByLabelText('Name'), 'New York Lottery');
    await userEvent.click(screen.getByLabelText('Jurisdiction'));
    await userEvent.click(await screen.findByRole('option', { name: 'New York (NY)' }));
    await userEvent.click(screen.getByLabelText('Settlement frequency'));
    await userEvent.click(await screen.findByRole('option', { name: 'Daily' }));
    await userEvent.type(screen.getByLabelText('Support contact'), 'support@nylottery.example');
    await userEvent.click(screen.getByRole('button', { name: 'Create operator' }));

    expect(await screen.findByRole('heading', { name: 'New York Lottery' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith('/api/v1/lottery/operators') && init?.method === 'POST';
    })).toBe(true);
  });
});
