import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  FeatureResolution,
  LotteryOperator,
  LotteryOperatorListResponse,
  LotteryPayoutPolicy,
  LotteryPayoutPolicyListResponse,
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
    displayName: 'Policy User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'user@example.local',
    displayName: 'Policy User',
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

function lotteryOperator(overrides: Partial<LotteryOperator> = {}): LotteryOperator {
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

function store(overrides: Partial<Store> = {}): Store {
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
    version: 0,
    ...overrides
  };
}

function policy(overrides: Partial<LotteryPayoutPolicy> = {}): LotteryPayoutPolicy {
  return {
    id: '00000000-0000-0000-0000-00000000d101',
    operatorId: '00000000-0000-0000-0000-00000000b101',
    operatorCode: 'CLOTTO',
    operatorName: 'California Lottery',
    jurisdictionId: '00000000-0000-0000-0000-00000000a101',
    jurisdictionCode: 'CA',
    jurisdictionName: 'California',
    storeId: '00000000-0000-0000-0000-00000000c101',
    storeCode: 'MAIN',
    storeName: 'Main Store',
    maximumCashPayout: 2500,
    cashierApprovalLimit: 200,
    managerApprovalThreshold: 500,
    operatorReferralThreshold: 2500,
    protectedRegisterFloat: 150,
    allowCashPayout: true,
    allowStoreCredit: true,
    requireTicketValidation: true,
    requireAgeVerification: true,
    requireCustomerIdentification: true,
    allowAlternateRegister: false,
    effectiveFrom: '2026-08-01',
    effectiveTo: null,
    status: 'ACTIVE',
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function operatorPageResponse(content: LotteryOperator[]): LotteryOperatorListResponse {
  return pageResponse(content, 100);
}

function storePageResponse(content: Store[]): StoreListResponse {
  return pageResponse(content, 100);
}

function policyPageResponse(content: LotteryPayoutPolicy[], overrides: Partial<LotteryPayoutPolicyListResponse> = {}): LotteryPayoutPolicyListResponse {
  return {
    ...pageResponse(content, 10),
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

function apiError(message: string, status = 500, code = 'unexpected') {
  return jsonResponse({
    code,
    message,
    status,
    path: '/api/v1/lottery/payout-policies',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Lottery payout policy pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders policies and applies filters', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/features/resolution')) {
        return jsonResponse(featureResolution(true));
      }
      if (url.pathname.endsWith('/api/v1/lottery/operators')) {
        return jsonResponse(operatorPageResponse([lotteryOperator()]));
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(storePageResponse([store()]));
      }
      if (url.pathname.endsWith('/api/v1/lottery/payout-policies')) {
        return jsonResponse(policyPageResponse([policy()], { totalElements: 1 }));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/lottery/payout-policies']} />);

    expect(await screen.findByRole('heading', { name: 'Lottery payout policies' })).toBeInTheDocument();
    expect(await screen.findByText('California Lottery')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Operator'));
    await userEvent.click(await screen.findByRole('option', { name: 'California Lottery (CLOTTO)' }));
    await userEvent.click(screen.getByLabelText('Status'));
    await userEvent.click(await screen.findByRole('option', { name: 'Active' }));
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/lottery/payout-policies')
          && url.searchParams.get('operatorId') === '00000000-0000-0000-0000-00000000b101'
          && url.searchParams.get('status') === 'ACTIVE';
      })).toBe(true);
    });
  });

  it('shows disabled feature state and avoids policy requests', async () => {
    storeSession(['MANAGER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/features/resolution')) {
        return jsonResponse(featureResolution(false));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/lottery/payout-policies']} />);

    expect(await screen.findByText(/Lottery sales is disabled/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New policy' })).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input]) => String(input).includes('/api/v1/lottery/payout-policies'))).toBe(false);
  });

  it('creates a policy and opens the detail page', async () => {
    storeSession(['OWNER']);
    const created = policy({
      id: '00000000-0000-0000-0000-00000000d103',
      status: 'SCHEDULED',
      maximumCashPayout: 3000
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/features/resolution')) {
        return jsonResponse(featureResolution(true));
      }
      if (url.pathname.endsWith('/api/v1/lottery/operators')) {
        return jsonResponse(operatorPageResponse([lotteryOperator()]));
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(storePageResponse([store()]));
      }
      if (url.pathname.endsWith('/api/v1/lottery/payout-policies') && init?.method === 'POST') {
        return jsonResponse(created, 201);
      }
      if (url.pathname.endsWith(`/api/v1/lottery/payout-policies/${created.id}`)) {
        return jsonResponse(created);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/lottery/payout-policies/new']} />);

    expect(await screen.findByRole('heading', { name: 'New payout policy' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Create policy' })).toBeEnabled());

    await userEvent.click(screen.getByLabelText('Operator'));
    await userEvent.click(await screen.findByRole('option', { name: 'California Lottery (CLOTTO)' }));
    await userEvent.click(screen.getByLabelText('Store'));
    await userEvent.click(await screen.findByRole('option', { name: 'Main Store (MAIN)' }));
    await userEvent.click(screen.getByLabelText('Status'));
    await userEvent.click(await screen.findByRole('option', { name: 'Scheduled' }));
    await userEvent.clear(screen.getByLabelText('Maximum cash payout'));
    await userEvent.type(screen.getByLabelText('Maximum cash payout'), '3000');
    await userEvent.clear(screen.getByLabelText('Cashier approval limit'));
    await userEvent.type(screen.getByLabelText('Cashier approval limit'), '250');
    await userEvent.clear(screen.getByLabelText('Manager approval threshold'));
    await userEvent.type(screen.getByLabelText('Manager approval threshold'), '600');
    await userEvent.clear(screen.getByLabelText('Operator referral threshold'));
    await userEvent.type(screen.getByLabelText('Operator referral threshold'), '3000');
    await userEvent.clear(screen.getByLabelText('Protected register float'));
    await userEvent.type(screen.getByLabelText('Protected register float'), '150');
    await userEvent.click(screen.getByRole('button', { name: 'Create policy' }));

    expect(await screen.findByRole('heading', { name: 'California Lottery payout policy' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith('/api/v1/lottery/payout-policies') && init?.method === 'POST';
    })).toBe(true);
  });
});
