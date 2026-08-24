import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import { applicationDeviceIdentifierKey } from '../../app/deviceIdentity';
import type {
  AuthResponse,
  CurrentUserResponse,
  LotteryPayout,
  LotteryPayoutListResponse,
  LotterySale,
  LotterySaleListResponse,
  UserRole
} from '../../api/types';

const cashierId = '00000000-0000-0000-0000-000000000806';
const saleId = '00000000-0000-0000-0000-000000000900';
const payoutId = '00000000-0000-0000-0000-000000000901';

function authResponse(roles: UserRole[] = ['CASHIER']): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: cashierId,
    email: 'cashier@example.local',
    displayName: 'Cashier One',
    roles
  };
}

function currentUser(roles: UserRole[] = ['CASHIER']): CurrentUserResponse {
  return {
    userId: cashierId,
    email: 'cashier@example.local',
    displayName: 'Cashier One',
    roles
  };
}

function sale(): LotterySale {
  return {
    id: saleId,
    operatorId: '00000000-0000-0000-0000-000000000801',
    operatorCode: 'STATE',
    operatorName: 'State Lottery',
    operatorReference: 'TERM-14',
    ticketReference: 'TICKET-99',
    gameType: 'DRAW_TICKET',
    amount: 25,
    currencyCode: 'USD',
    paymentMethod: 'CASH',
    storeId: '00000000-0000-0000-0000-000000000802',
    storeCode: 'MAIN',
    storeName: 'Main Store',
    registerId: '00000000-0000-0000-0000-000000000803',
    registerCode: 'FRONT',
    registerName: 'Front Register',
    deviceId: '00000000-0000-0000-0000-000000000804',
    deviceIdentifier: 'browser:test-device',
    deviceDisplayName: 'Front Browser',
    cashierId,
    cashierEmail: 'cashier@example.local',
    cashierDisplayName: 'Cashier One',
    registerSessionId: '00000000-0000-0000-0000-000000000805',
    status: 'RECORDED',
    operationId: '00000000-0000-0000-0000-000000000910',
    occurredAt: '2026-07-28T12:00:00Z',
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0
  };
}

function payout(): LotteryPayout {
  return {
    id: payoutId,
    operatorId: '00000000-0000-0000-0000-000000000801',
    operatorCode: 'STATE',
    operatorName: 'State Lottery',
    policyId: '00000000-0000-0000-0000-000000000811',
    storeId: '00000000-0000-0000-0000-000000000802',
    storeCode: 'MAIN',
    storeName: 'Main Store',
    registerId: '00000000-0000-0000-0000-000000000803',
    registerCode: 'FRONT',
    registerName: 'Front Register',
    deviceId: '00000000-0000-0000-0000-000000000804',
    deviceIdentifier: 'browser:test-device',
    deviceDisplayName: 'Front Browser',
    cashierId,
    cashierEmail: 'cashier@example.local',
    cashierDisplayName: 'Cashier One',
    registerSessionId: '00000000-0000-0000-0000-000000000805',
    ticketNumber: 'PAY-99',
    validationReference: 'VALID-99',
    amount: 75,
    currencyCode: 'USD',
    payoutMethod: 'CASH',
    status: 'PAID',
    ticketValidationState: 'VERIFIED',
    ageVerificationState: 'VERIFIED',
    identificationVerificationState: 'VERIFIED',
    cashierApprovalLimit: 100,
    managerApprovalThreshold: 250,
    operatorReferralThreshold: 500,
    maximumCashPayout: 400,
    ticketValidationRequired: true,
    ageVerificationRequired: true,
    identificationRequired: true,
    alternateRegisterAllowed: false,
    businessDate: '2026-07-28',
    occurredAt: '2026-07-28T12:00:00Z',
    validatedBy: cashierId,
    validatedAt: '2026-07-28T12:01:00Z',
    authorizedBy: cashierId,
    authorizedAt: '2026-07-28T12:02:00Z',
    paidBy: cashierId,
    paidAt: '2026-07-28T12:03:00Z',
    rejectedBy: null,
    rejectedAt: null,
    rejectionReason: null,
    notes: null,
    approvals: [],
    createdAt: '2026-07-28T12:00:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 0
  };
}

function pageResponse<T>(content: T[]) {
  return {
    content,
    page: 0,
    size: 25,
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

function storeSession(roles: UserRole[] = ['CASHIER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
  window.localStorage.setItem(applicationDeviceIdentifierKey, 'browser:test-device');
}

describe('Lottery management page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('cancels a recorded lottery sale with a reason and idempotency key', async () => {
    storeSession(['CASHIER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      if (url.pathname.endsWith('/api/v1/lottery/sales') && init?.method !== 'POST') {
        return jsonResponse(pageResponse<LotterySale>([sale()]) satisfies LotterySaleListResponse);
      }
      if (url.pathname.endsWith('/api/v1/lottery/payouts')) {
        return jsonResponse(pageResponse<LotteryPayout>([payout()]) satisfies LotteryPayoutListResponse);
      }
      if (url.pathname.endsWith(`/api/v1/lottery/sales/${saleId}/cancel`) && init?.method === 'POST') {
        return jsonResponse({
          id: '00000000-0000-0000-0000-000000000920',
          originalSaleId: saleId,
          reason: 'Customer request',
          cashReturned: true
        });
      }
      return jsonResponse({ message: `Unexpected request: ${url.pathname}` }, 500);
    });

    render(<App initialEntries={['/lottery/management']} />);

    expect(await screen.findByRole('heading', { name: 'Lottery management' })).toBeInTheDocument();
    await userEvent.selectOptions(await screen.findByLabelText('Original sale'), saleId);
    await userEvent.type(screen.getByLabelText('Cancellation reason'), 'Customer request');
    await userEvent.click(screen.getByRole('button', { name: 'Cancel sale' }));

    await screen.findByText('Lottery sale cancelled: Customer request');
    const cancelCall = fetchMock.mock.calls.find(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith(`/api/v1/lottery/sales/${saleId}/cancel`) && init?.method === 'POST';
    });
    expect(cancelCall).toBeTruthy();
    expect(new Headers(cancelCall![1]?.headers).get('Idempotency-Key')).toBeTruthy();
    expect(JSON.parse(String(cancelCall![1]?.body))).toEqual({ reason: 'Customer request' });
  });

  it('does not allow cashiers to submit payout reversals', async () => {
    storeSession(['CASHIER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      if (url.pathname.endsWith('/api/v1/lottery/sales')) {
        return jsonResponse(pageResponse<LotterySale>([sale()]) satisfies LotterySaleListResponse);
      }
      if (url.pathname.endsWith('/api/v1/lottery/payouts')) {
        return jsonResponse(pageResponse<LotteryPayout>([payout()]) satisfies LotteryPayoutListResponse);
      }
      return jsonResponse({ message: `Unexpected request: ${url.pathname}` }, 500);
    });

    render(<App initialEntries={['/lottery/management']} />);

    expect(await screen.findByText('Payout reversal requires manager approval permission.')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Reverse payout' })).toBeDisabled());
  });
});
