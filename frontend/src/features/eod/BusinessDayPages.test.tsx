import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type { AuthResponse, BusinessDay, ClosingValidation, CurrentUserResponse, EndOfDayClosingPreview, EndOfDayReport, Store, StoreListResponse, UserRole } from '../../api/types';

const storeId = '00000000-0000-0000-0000-000000001201';
const dayId = '00000000-0000-0000-0000-000000001202';
const reportId = '00000000-0000-0000-0000-000000001203';

function authResponse(roles: UserRole[] = ['MANAGER']): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: '00000000-0000-0000-0000-000000001204',
    email: 'manager@example.local',
    displayName: 'Manager One',
    roles
  };
}

function currentUser(roles: UserRole[] = ['MANAGER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000001204',
    email: 'manager@example.local',
    displayName: 'Manager One',
    roles
  };
}

function store(): Store {
  return {
    id: storeId,
    code: 'MAIN',
    name: 'Main Store',
    legalName: null,
    countryCode: 'US',
    administrativeAreaCode: null,
    address: '100 Market Street',
    phone: null,
    email: null,
    currencyCode: 'USD',
    locale: 'en-US',
    timezone: 'America/New_York',
    pricesIncludeTax: false,
    negativeStockAllowed: false,
    active: true,
    createdAt: '2026-07-29T12:00:00Z',
    updatedAt: '2026-07-29T12:00:00Z',
    version: 0
  };
}

function businessDay(overrides: Partial<BusinessDay> = {}): BusinessDay {
  return {
    id: dayId,
    storeId,
    storeCode: 'MAIN',
    storeName: 'Main Store',
    businessDate: '2026-07-29',
    timezone: 'America/New_York',
    status: 'OPEN',
    openedAt: '2026-07-29T08:00:00Z',
    openedBy: '00000000-0000-0000-0000-000000001204',
    openedByName: 'Manager One',
    closingStartedAt: null,
    closingStartedBy: null,
    closingStartedByName: null,
    closedAt: null,
    closedBy: null,
    closedByName: null,
    reopenedAt: null,
    reopenedBy: null,
    reopenedByName: null,
    reopenReason: null,
    forceCloseReason: null,
    version: 0,
    ...overrides
  };
}

function validation(): ClosingValidation {
  return {
    businessDayId: dayId,
    closable: false,
    blockers: [{ code: 'OPEN_REGISTER_SESSION', message: 'Register session remains open: FRONT', relatedId: '00000000-0000-0000-0000-000000001205' }]
  };
}

function report(): EndOfDayReport {
  return {
    id: reportId,
    businessDayId: dayId,
    storeId,
    storeCode: 'MAIN',
    storeName: 'Main Store',
    businessDate: '2026-07-29',
    businessDayStatus: 'CLOSED',
    businessDayVersion: 0,
    reportNumber: 'MAIN-2026-07-29-R1',
    revision: 1,
    generatedAt: '2026-07-29T23:05:00Z',
    generatedBy: '00000000-0000-0000-0000-000000001204',
    generatedByName: 'Manager One',
    grossSales: 100,
    netSales: 80,
    discountTotal: 5,
    refundTotal: 10,
    voidTotal: 0,
    taxTotal: 6,
    transactionCount: 2,
    averageTransactionValue: 40,
    highestTransactionValue: 60,
    lowestTransactionValue: 20,
    itemsSold: 4,
    averageBasketSize: 2,
    expectedCash: 125,
    countedCash: 124,
    cashVariance: -1,
    currencyCode: 'USD',
    registers: [{
      registerSessionId: '00000000-0000-0000-0000-000000001205',
      registerId: '00000000-0000-0000-0000-000000001206',
      registerCode: 'FRONT',
      registerName: 'Front Register',
      openingFloat: 50,
      cashReceipts: 100,
      changeGiven: 20,
      cashRefunds: 5,
      lotteryCashSales: 0,
      lotteryPayouts: 0,
      lotteryPayoutReversals: 0,
      lotterySaleCancellations: 0,
      cashIn: 0,
      cashOut: 0,
      safeDrops: 0,
      floatAdditions: 0,
      floatRemovals: 0,
      expenses: 0,
      closingAdjustments: 0,
      expectedCash: 125,
      countedCash: 124,
      variance: -1,
      openedBy: '00000000-0000-0000-0000-000000001204',
      openedByName: 'Manager One',
      closedBy: '00000000-0000-0000-0000-000000001204',
      closedByName: 'Manager One',
      openedAt: '2026-07-29T08:00:00Z',
      closedAt: '2026-07-29T23:00:00Z',
      forceClosed: false,
      forceCloseReason: null
    }],
    payments: [{ paymentMethod: 'CASH', collected: 100, refunded: 10, net: 90, cashTendered: 120, changeGiven: 20, transactionCount: 2, splitPaymentCount: 0 }],
    taxes: [{ componentCode: 'SALES_TAX', componentName: 'Sales tax', taxableSales: 95, exemptSales: 0, zeroRatedSales: 0, outOfScopeSales: 0, taxCollected: 6, taxRefunded: 0, roundingAdjustment: 0, netTaxCollected: 6 }],
    lottery: null,
    inventory: null,
    cashiers: [{ cashierId: '00000000-0000-0000-0000-000000001204', cashierName: 'Manager One', transactionCount: 2, grossSales: 100, netSales: 80, refundTotal: 10, voidCount: 0, discountTotal: 5, priceOverrideCount: 0, cashHandled: 90, lotterySales: 0, lotteryPayouts: 0, averageTransactionValue: 40, firstActivityAt: '2026-07-29T10:00:00Z', lastActivityAt: '2026-07-29T20:00:00Z', registersUsed: 'FRONT' }],
    exceptions: [{ exceptionType: 'CASH_VARIANCES', count: 1, totalAmount: 1, details: null }],
    signOff: { managerUserId: '00000000-0000-0000-0000-000000001204', managerName: 'Manager One', signedAt: '2026-07-29T23:05:00Z', notes: null, varianceExplanation: 'Cash drawer was short by one dollar.', confirmationAccepted: true },
    reportSnapshot: '{}',
    version: 0
  };
}

function closingPreview(): EndOfDayClosingPreview {
  const finalReport = report();
  return {
    businessDayId: finalReport.businessDayId,
    storeId: finalReport.storeId,
    storeCode: finalReport.storeCode,
    storeName: finalReport.storeName,
    businessDate: finalReport.businessDate,
    businessDayStatus: 'OPEN',
    businessDayVersion: 0,
    grossSales: finalReport.grossSales,
    netSales: finalReport.netSales,
    discountTotal: finalReport.discountTotal,
    refundTotal: finalReport.refundTotal,
    voidTotal: finalReport.voidTotal,
    taxTotal: finalReport.taxTotal,
    transactionCount: finalReport.transactionCount,
    averageTransactionValue: finalReport.averageTransactionValue,
    highestTransactionValue: finalReport.highestTransactionValue,
    lowestTransactionValue: finalReport.lowestTransactionValue,
    itemsSold: finalReport.itemsSold,
    averageBasketSize: finalReport.averageBasketSize,
    expectedCash: finalReport.expectedCash,
    countedCash: finalReport.countedCash,
    cashVariance: finalReport.cashVariance,
    cashVarianceExplanationThreshold: 0.5,
    varianceExplanationRequired: true,
    managerSignOffRequired: true,
    currencyCode: finalReport.currencyCode,
    registers: finalReport.registers,
    payments: finalReport.payments,
    taxes: finalReport.taxes,
    lottery: finalReport.lottery,
    inventory: finalReport.inventory,
    cashiers: finalReport.cashiers,
    exceptions: finalReport.exceptions
  };
}

function pageResponse<T>(content: T[]) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1, first: true, last: true };
}

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

function textResponse(body: string) {
  return Promise.resolve(new Response(body, { status: 200, headers: { 'Content-Type': 'text/csv' } }));
}

function storeSession(roles: UserRole[] = ['MANAGER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Business day pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:eod-report');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  });

  it('shows the current business day and every closing blocker', async () => {
    storeSession(['MANAGER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['MANAGER']));
      if (url.pathname.endsWith('/api/v1/stores')) return jsonResponse(pageResponse<Store>([store()]) satisfies StoreListResponse);
      if (url.pathname.endsWith('/api/v1/business-days/current')) return jsonResponse(businessDay());
      if (url.pathname.endsWith('/api/v1/business-days/latest')) return jsonResponse(businessDay());
      if (url.pathname.endsWith(`/api/v1/business-days/${dayId}/validation`)) return jsonResponse(validation());
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/business-day']} />);

    expect(await screen.findByRole('heading', { name: 'Business day' })).toBeInTheDocument();
    expect(await screen.findByText('2026-07-29')).toBeInTheDocument();
    expect(await screen.findByText('Register session remains open: FRONT')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Close' })).toBeInTheDocument();
  });

  it('switches Store business-day state and reopens the selected closed day', async () => {
    storeSession(['MANAGER']);
    const airportStoreId = '00000000-0000-0000-0000-000000001211';
    const airportDayId = '00000000-0000-0000-0000-000000001212';
    const airportStore = { ...store(), id: airportStoreId, code: 'AIR', name: 'Airport Store' };
    const closedAirportDay = businessDay({
      id: airportDayId,
      storeId: airportStoreId,
      storeCode: 'AIR',
      storeName: 'Airport Store',
      status: 'CLOSED',
      closedAt: '2026-07-30T02:00:00Z',
      closedBy: '00000000-0000-0000-0000-000000001204',
      closedByName: 'Manager One',
      version: 3
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['MANAGER']));
      if (url.pathname.endsWith('/api/v1/stores')) return jsonResponse(pageResponse<Store>([store(), airportStore]) satisfies StoreListResponse);
      if (url.pathname.endsWith('/api/v1/business-days/current')) {
        return url.searchParams.get('storeId') === airportStoreId
          ? Promise.resolve(new Response(null, { status: 204 }))
          : jsonResponse(businessDay());
      }
      if (url.pathname.endsWith('/api/v1/business-days/latest')) {
        return jsonResponse(url.searchParams.get('storeId') === airportStoreId ? closedAirportDay : businessDay());
      }
      if (url.pathname.endsWith(`/api/v1/business-days/${dayId}/validation`)) return jsonResponse(validation());
      if (url.pathname.endsWith(`/api/v1/business-days/${airportDayId}/reopen`) && init?.method === 'POST') {
        return jsonResponse({ ...closedAirportDay, status: 'REOPENED', reopenedAt: '2026-07-30T02:10:00Z', reopenedBy: closedAirportDay.closedBy, reopenedByName: 'Manager One', version: 4 });
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/business-day']} />);

    expect(await screen.findByText('OPEN')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('combobox', { name: 'Store' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Airport Store (AIR)' }));
    await waitFor(() => expect(screen.queryByRole('option', { name: 'Airport Store (AIR)' })).not.toBeInTheDocument());
    await waitFor(() => expect(document.body.style.overflow).not.toBe('hidden'));
    expect(await screen.findByText('CLOSED')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reopen business day' }));
    const reopenDialog = await screen.findByRole('dialog');
    await userEvent.type(within(reopenDialog).getByRole('textbox', { name: 'Reason for reopening' }), 'Store reopened for additional evening sales');
    await userEvent.click(within(reopenDialog).getByRole('button', { name: 'Reopen' }));

    await waitFor(() => expect(fetchMock.mock.calls.some(([input, init]) => {
      if (!String(input).includes(`/business-days/${airportDayId}/reopen`) || init?.method !== 'POST') return false;
      const body = JSON.parse(String(init.body));
      return body.version === 3 && body.reason === 'Store reopened for additional evening sales';
    })).toBe(true));
  });

  it('renders a report and exports CSV from the immutable report endpoint', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['OWNER']));
      if (url.pathname.endsWith(`/api/v1/end-of-day-reports/${reportId}`)) return jsonResponse(report());
      if (url.pathname.endsWith(`/api/v1/end-of-day-reports/${reportId}/export/csv`)) return textResponse('# summary\nmetric,value\n');
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={[`/end-of-day-reports/${reportId}`]} />);

    expect(await screen.findByRole('heading', { name: 'Merchtyl End-of-Day Report' })).toBeInTheDocument();
    expect(await screen.findByText('MAIN-2026-07-29-R1 - Main Store - 2026-07-29')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'CSV' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining(`/api/v1/end-of-day-reports/${reportId}/export/csv`), expect.any(Object)));
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();
  });

  it('renders the calculated closing preview before sign-off', async () => {
    storeSession(['MANAGER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['MANAGER']));
      if (url.pathname.endsWith('/api/v1/stores')) return jsonResponse(pageResponse<Store>([store()]) satisfies StoreListResponse);
      if (url.pathname.endsWith('/api/v1/business-days/current')) return jsonResponse(businessDay());
      if (url.pathname.endsWith('/api/v1/business-days/latest')) return jsonResponse(businessDay());
      if (url.pathname.endsWith(`/api/v1/business-days/${dayId}/validation`)) return jsonResponse({ ...validation(), closable: true, blockers: [] });
      if (url.pathname.endsWith(`/api/v1/business-days/${dayId}/preview`)) return jsonResponse(closingPreview());
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/business-day/close']} />);

    expect(await screen.findByRole('heading', { name: 'Close business day' })).toBeInTheDocument();
    expect((await screen.findAllByText('$100.00')).length).toBeGreaterThan(0);
    expect(await screen.findByText('Payment preview')).toBeInTheDocument();
    expect(await screen.findByText('Register reconciliation preview')).toBeInTheDocument();
  });
});
