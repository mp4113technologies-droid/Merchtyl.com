import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  FeatureResolution,
  LotteryOperator,
  LotteryOperatorListResponse,
  LotterySettlement,
  LotterySettlementListResponse,
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
    userId: '00000000-0000-0000-0000-000000000301',
    email: 'settlement@example.local',
    displayName: 'Settlement User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000301',
    email: 'settlement@example.local',
    displayName: 'Settlement User',
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
    id: '00000000-0000-0000-0000-00000000b301',
    code: 'CLOTTO',
    name: 'California Lottery',
    jurisdictionId: '00000000-0000-0000-0000-00000000a301',
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
    id: '00000000-0000-0000-0000-00000000c301',
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

function settlement(overrides: Partial<LotterySettlement> = {}): LotterySettlement {
  return {
    id: '00000000-0000-0000-0000-00000000d301',
    operatorId: '00000000-0000-0000-0000-00000000b301',
    operatorCode: 'CLOTTO',
    operatorName: 'California Lottery',
    jurisdictionId: '00000000-0000-0000-0000-00000000a301',
    jurisdictionCode: 'CA',
    jurisdictionName: 'California',
    storeId: '00000000-0000-0000-0000-00000000c301',
    storeCode: 'MAIN',
    storeName: 'Main Store',
    periodStart: '2026-07-01',
    periodEnd: '2026-07-07',
    grossSales: 1200,
    totalPayouts: 325,
    cancellations: 50,
    adjustments: 10,
    commission: 60,
    expectedSettlement: 775,
    currencyCode: 'USD',
    calculatedAt: '2026-07-28T12:00:00Z',
    status: 'CALCULATED',
    approvedBy: null,
    approvedByEmail: null,
    approvedByDisplayName: null,
    approvedAt: null,
    postedBy: null,
    postedByEmail: null,
    postedByDisplayName: null,
    postedAt: null,
    reopenedBy: null,
    reopenedByEmail: null,
    reopenedByDisplayName: null,
    reopenedAt: null,
    reopenReason: null,
    lifecycleNotes: null,
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

function apiError(message: string, status = 500, code = 'unexpected') {
  return jsonResponse({
    code,
    message,
    status,
    path: '/api/v1/lottery/settlements',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Lottery settlement page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('calculates a settlement and renders the reconciliation breakdown', async () => {
    storeSession(['OWNER']);
    const calculated = settlement({ id: '00000000-0000-0000-0000-00000000d302' });
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
      if (url.pathname.endsWith('/api/v1/lottery/settlements/calculate') && init?.method === 'POST') {
        return jsonResponse(calculated, 201);
      }
      if (url.pathname.endsWith('/api/v1/lottery/settlements')) {
        return jsonResponse(pageResponse<LotterySettlement>([settlement()], 10) satisfies LotterySettlementListResponse);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/lottery/settlements']} />);

    expect(await screen.findByRole('heading', { name: 'Lottery settlements' })).toBeInTheDocument();
    expect(await screen.findByText('California Lottery')).toBeInTheDocument();
    expect(await screen.findAllByText('$775.00')).not.toHaveLength(0);

    await userEvent.click(screen.getByLabelText('Operator'));
    await userEvent.click(await screen.findByRole('option', { name: 'California Lottery' }));
    await userEvent.click(screen.getByLabelText('Store'));
    await userEvent.click(await screen.findByRole('option', { name: 'Main Store' }));
    await userEvent.clear(screen.getByLabelText('Period start'));
    await userEvent.type(screen.getByLabelText('Period start'), '2026-07-01');
    await userEvent.clear(screen.getByLabelText('Period end'));
    await userEvent.type(screen.getByLabelText('Period end'), '2026-07-07');
    await userEvent.click(screen.getByRole('button', { name: 'Calculate settlement' }));

    await waitFor(() => {
      const calculateCall = fetchMock.mock.calls.find(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/lottery/settlements/calculate') && init?.method === 'POST';
      });
      expect(calculateCall).toBeTruthy();
      expect(JSON.parse(String(calculateCall?.[1]?.body))).toEqual({
        operatorId: '00000000-0000-0000-0000-00000000b301',
        storeId: '00000000-0000-0000-0000-00000000c301',
        periodStart: '2026-07-01',
        periodEnd: '2026-07-07'
      });
    });

    expect(await screen.findByRole('table', { name: 'Settlement breakdown' })).toBeInTheDocument();
    expect(screen.getByText('Gross sales')).toBeInTheDocument();
    expect(screen.getByText('-$325.00')).toBeInTheDocument();
  });

  it('approves a calculated settlement with its current version', async () => {
    storeSession(['MANAGER']);
    const existing = settlement();
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
      if (url.pathname.endsWith(`/api/v1/lottery/settlements/${existing.id}/approve`) && init?.method === 'POST') {
        return jsonResponse(settlement({ status: 'APPROVED', version: 1 }));
      }
      if (url.pathname.endsWith('/api/v1/lottery/settlements')) {
        return jsonResponse(pageResponse<LotterySettlement>([existing], 10) satisfies LotterySettlementListResponse);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/lottery/settlements']} />);

    await screen.findByRole('heading', { name: 'Lottery settlements' });
    await userEvent.click(await screen.findByLabelText('Approve settlement California Lottery'));
    await userEvent.type(screen.getByLabelText('Notes'), 'Reviewed with operator portal');
    await userEvent.click(screen.getByRole('button', { name: 'Approve' }));

    await waitFor(() => {
      const approveCall = fetchMock.mock.calls.find(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith(`/api/v1/lottery/settlements/${existing.id}/approve`) && init?.method === 'POST';
      });
      expect(approveCall).toBeTruthy();
      expect(JSON.parse(String(approveCall?.[1]?.body))).toEqual({
        version: 0,
        notes: 'Reviewed with operator portal'
      });
    });
  });

  it('shows post as unavailable for managers', async () => {
    storeSession(['MANAGER']);
    const existing = settlement({ status: 'APPROVED' });
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
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
      if (url.pathname.endsWith('/api/v1/lottery/settlements')) {
        return jsonResponse(pageResponse<LotterySettlement>([existing], 10) satisfies LotterySettlementListResponse);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/lottery/settlements']} />);

    const row = (await screen.findByText('California Lottery')).closest('tr');
    expect(row).toBeTruthy();
    expect(within(row as HTMLElement).getByLabelText('Post settlement California Lottery')).toBeDisabled();
  });
});
