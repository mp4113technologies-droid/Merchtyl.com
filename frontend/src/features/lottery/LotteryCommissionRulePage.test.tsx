import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  FeatureResolution,
  LotteryCommissionRule,
  LotteryCommissionRuleListResponse,
  LotteryOperator,
  LotteryOperatorListResponse,
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
    displayName: 'Commission User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'user@example.local',
    displayName: 'Commission User',
    roles
  };
}

function featureResolution(enabled = true): FeatureResolution[] {
  return [{
    definition: {
      id: '00000000-0000-0000-0000-00000000f101',
      code: 'LOTTERY_SALES',
      name: 'Lottery sales',
      description: 'Lottery workflows',
      defaultEnabled: false,
      createdAt: '2026-07-28T12:00:00Z',
      updatedAt: '2026-07-28T12:00:00Z',
      version: 0
    },
    enabled,
    source: 'TENANT',
    storeId: null,
    registerId: null,
    tenantOverride: null,
    storeOverride: null,
    registerOverride: null
  }];
}

function lotteryOperator(): LotteryOperator {
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
    version: 0
  };
}

function store(): Store {
  return {
    id: '00000000-0000-0000-0000-00000000c101',
    code: 'MAIN',
    name: 'Main Store',
    legalName: null,
    countryCode: 'US',
    administrativeAreaCode: 'CA',
    address: '10 Main Street',
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

function rule(overrides: Partial<LotteryCommissionRule> = {}): LotteryCommissionRule {
  return {
    id: '00000000-0000-0000-0000-00000000d101',
    name: 'Sales commission',
    operatorId: '00000000-0000-0000-0000-00000000b101',
    operatorCode: 'CLOTTO',
    operatorName: 'California Lottery',
    jurisdictionId: '00000000-0000-0000-0000-00000000a101',
    jurisdictionCode: 'CA',
    jurisdictionName: 'California',
    storeId: '00000000-0000-0000-0000-00000000c101',
    storeCode: 'MAIN',
    storeName: 'Main Store',
    ruleType: 'PERCENT_OF_SALES',
    commissionRatePercent: 5.25,
    fixedAmount: null,
    currencyCode: null,
    fixedPeriod: null,
    effectiveFrom: '2026-08-01',
    effectiveTo: null,
    status: 'ACTIVE',
    notes: null,
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function pageResponse<T>(content: T[], size: number) {
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

function noContentResponse() {
  return Promise.resolve(new Response(null, { status: 204 }));
}

function apiError(message: string, status = 500, code = 'unexpected') {
  return jsonResponse({
    code,
    message,
    status,
    path: '/api/v1/lottery/commission-rules',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Lottery commission rule page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders existing rules and creates a percent commission rule', async () => {
    storeSession(['OWNER']);
    const created = rule({ id: '00000000-0000-0000-0000-00000000d102', name: 'New sales commission' });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/features/resolution')) {
        return jsonResponse(featureResolution(true));
      }
      if (url.pathname.endsWith('/api/v1/lottery/operators')) {
        return jsonResponse(pageResponse<LotteryOperator>([lotteryOperator()], 100) satisfies LotteryOperatorListResponse);
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(pageResponse<Store>([store()], 100) satisfies StoreListResponse);
      }
      if (url.pathname.endsWith('/api/v1/lottery/commission-rules') && init?.method === 'POST') {
        return jsonResponse(created, 201);
      }
      if (url.pathname.endsWith('/api/v1/lottery/commission-rules')) {
        return jsonResponse(pageResponse<LotteryCommissionRule>([rule()], 10) satisfies LotteryCommissionRuleListResponse);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/lottery/commission-rules']} />);

    expect(await screen.findByRole('heading', { name: 'Lottery commission rules' })).toBeInTheDocument();
    expect(await screen.findByText('Sales commission')).toBeInTheDocument();
    expect(await screen.findByText('5.25%')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Name'), 'New sales commission');
    await userEvent.click(screen.getByLabelText('Operator'));
    await userEvent.click(await screen.findByRole('option', { name: 'California Lottery' }));
    await userEvent.click(screen.getByLabelText('Store'));
    await userEvent.click(await screen.findByRole('option', { name: 'Main Store' }));
    await userEvent.clear(screen.getByLabelText('Rate percent'));
    await userEvent.type(screen.getByLabelText('Rate percent'), '6.5');
    await userEvent.click(screen.getByRole('button', { name: 'Create rule' }));

    await waitFor(() => {
      const createCall = fetchMock.mock.calls.find(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/lottery/commission-rules') && init?.method === 'POST';
      });
      expect(createCall).toBeTruthy();
      expect(JSON.parse(String(createCall?.[1]?.body))).toMatchObject({
        name: 'New sales commission',
        operatorId: '00000000-0000-0000-0000-00000000b101',
        jurisdictionId: '00000000-0000-0000-0000-00000000a101',
        storeId: '00000000-0000-0000-0000-00000000c101',
        ruleType: 'PERCENT_OF_SALES',
        commissionRatePercent: 6.5,
        status: 'DRAFT'
      });
    });
  });

  it('deletes a selected rule with its current version', async () => {
    storeSession(['MANAGER']);
    const existing = rule();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/features/resolution')) {
        return jsonResponse(featureResolution(true));
      }
      if (url.pathname.endsWith('/api/v1/lottery/operators')) {
        return jsonResponse(pageResponse<LotteryOperator>([lotteryOperator()], 100) satisfies LotteryOperatorListResponse);
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(pageResponse<Store>([store()], 100) satisfies StoreListResponse);
      }
      if (url.pathname.endsWith(`/api/v1/lottery/commission-rules/${existing.id}`) && init?.method === 'DELETE') {
        return noContentResponse();
      }
      if (url.pathname.endsWith('/api/v1/lottery/commission-rules')) {
        return jsonResponse(pageResponse<LotteryCommissionRule>([existing], 10) satisfies LotteryCommissionRuleListResponse);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/lottery/commission-rules']} />);

    await userEvent.click(await screen.findByLabelText('Delete Sales commission'));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith(`/api/v1/lottery/commission-rules/${existing.id}`)
          && url.searchParams.get('version') === '0'
          && init?.method === 'DELETE';
      })).toBe(true);
    });
  });
});
