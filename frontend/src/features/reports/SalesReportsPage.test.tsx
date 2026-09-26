import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CatalogueReference,
  CatalogueReferenceListResponse,
  CurrentUserResponse,
  Product,
  ProductListResponse,
  Register,
  RegisterListResponse,
  SalesReport,
  Store,
  StoreListResponse,
  UserAdmin,
  UserAdminListResponse,
  UserRole
} from '../../api/types';

const storeId = '00000000-0000-0000-0000-000000000101';
const registerId = '00000000-0000-0000-0000-000000000102';
const cashierId = '00000000-0000-0000-0000-000000000103';
const categoryId = '00000000-0000-0000-0000-000000000104';
const productId = '00000000-0000-0000-0000-000000000105';

function authResponse(roles: UserRole[] = ['MANAGER']): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: cashierId,
    email: 'manager@example.local',
    displayName: 'Manager One',
    roles
  };
}

function currentUser(roles: UserRole[] = ['MANAGER']): CurrentUserResponse {
  return {
    userId: cashierId,
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
    createdAt: '2026-07-29T12:00:00Z',
    updatedAt: '2026-07-29T12:00:00Z',
    version: 0
  };
}

function register(): Register {
  return {
    id: registerId,
    storeId,
    code: 'FRONT',
    name: 'Front Register',
    locationDescription: null,
    active: true,
    createdAt: '2026-07-29T12:00:00Z',
    updatedAt: '2026-07-29T12:00:00Z',
    version: 0
  };
}

function cashier(): UserAdmin {
  return {
    id: cashierId,
    email: 'cashier@example.local',
    displayName: 'Cashier One',
    enabled: true,
    locked: false,
    roles: ['CASHIER'],
    storeIds: [storeId],
    registerIds: [registerId],
    createdAt: '2026-07-29T12:00:00Z',
    updatedAt: '2026-07-29T12:00:00Z',
    version: 0
  };
}

function category(): CatalogueReference {
  return {
    id: categoryId,
    code: 'DRINKS',
    name: 'Drinks',
    description: null,
    active: true,
    createdAt: '2026-07-29T12:00:00Z',
    updatedAt: '2026-07-29T12:00:00Z',
    version: 0
  };
}

function product(): Product {
  return {
    id: productId,
    sku: 'COFFEE-12OZ',
    name: 'House Coffee',
    description: null,
    sellableType: 'STANDARD_PRODUCT',
    unitOfMeasureId: null,
    cost: 1.25,
    price: 4,
    categoryId,
    brandId: null,
    active: true,
    inventoryTrackingEnabled: true,
    decimalQuantityAllowed: false,
    imageUrl: null,
    taxCategoryId: null,
    variants: [],
    barcodes: [],
    capabilities: [],
    createdAt: '2026-07-29T12:00:00Z',
    updatedAt: '2026-07-29T12:00:00Z',
    version: 0
  };
}

function report(): SalesReport {
  return {
    storeId,
    registerId,
    cashierId,
    categoryId,
    productId,
    dateFrom: '2026-07-01',
    dateTo: '2026-07-31',
    grossSales: 2285.97,
    netSales: 2285.97,
    discounts: 0,
    refunds: 0,
    taxes: 290.10,
    taxableSales: 1933.97,
    nonTaxableSales: 352,
    taxCollected: 290.10,
    merchandiseNetSales: 2285.97,
    payments: 3114.87,
    containerDeposits: 0,
    depositPayouts: 0,
    netDeposits: 0,
    refundedContainerDeposits: 0,
    saleCount: 1,
    refundCount: 1,
    paymentBreakdown: [{
      method: 'CASH',
      collected: 3114.87,
      refunded: 0,
      net: 3114.87
    }],
    generatedAt: '2026-07-29T12:00:00Z'
  };
}

function pageResponse<T>(content: T[], size = 100) {
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

function storeSession(roles: UserRole[] = ['MANAGER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Sales reports page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:sales-report');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  });

  it('renders sales totals, applies filters, and exports CSV', async () => {
    storeSession(['MANAGER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(pageResponse<Store>([store()]) satisfies StoreListResponse);
      }
      if (url.pathname.endsWith('/api/v1/registers')) {
        return jsonResponse(pageResponse<Register>([register()]) satisfies RegisterListResponse);
      }
      if (url.pathname.endsWith('/api/v1/users')) {
        return jsonResponse(pageResponse<UserAdmin>([cashier()]) satisfies UserAdminListResponse);
      }
      if (url.pathname.endsWith('/api/v1/categories')) {
        return jsonResponse(pageResponse<CatalogueReference>([category()]) satisfies CatalogueReferenceListResponse);
      }
      if (url.pathname.endsWith('/api/v1/products')) {
        return jsonResponse(pageResponse<Product>([product()]) satisfies ProductListResponse);
      }
      if (url.pathname.endsWith('/api/v1/reports/sales')) {
        return jsonResponse(report());
      }
      return jsonResponse({ message: `Unexpected request: ${url.pathname}` }, 500);
    });

    render(<App initialEntries={['/reports/sales']} />);

    expect(await screen.findByRole('heading', { name: 'Sales reports' })).toBeInTheDocument();
    expect(await screen.findByText('Gross sales')).toBeInTheDocument();
    expect(await screen.findAllByText('$2,285.97')).not.toHaveLength(0);
    expect(screen.getByText('Net sales')).toBeInTheDocument();
    expect(screen.getByText('$1,933.97')).toBeInTheDocument();
    expect(screen.getByText('$352.00')).toBeInTheDocument();
    expect(screen.getByText('$290.10')).toBeInTheDocument();
    expect(screen.getAllByText('$3,114.87')).not.toHaveLength(0);
    const summaryGrid = screen.getByTestId('sales-summary-grid');
    expect(summaryGrid.querySelectorAll(':scope > [data-summary-card]')).toHaveLength(13);
    for (const key of ['taxable-sales', 'non-taxable-sales', 'tax-collected', 'merchandise-net-sales']) {
      const item = summaryGrid.querySelector(`[data-summary-card="${key}"]`);
      expect(item?.parentElement).toBe(summaryGrid);
      expect(item).toHaveClass('MuiGrid-grid-xs-12', 'MuiGrid-grid-sm-6', 'MuiGrid-grid-lg-3');
    }
    expect(screen.getByRole('table', { name: 'Payment breakdown' })).toBeInTheDocument();
    expect(screen.getByText('Cash')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Store'));
    await userEvent.click(await screen.findByRole('option', { name: 'Main Store' }));
    await userEvent.click(screen.getByLabelText('Register'));
    await userEvent.click(await screen.findByRole('option', { name: 'Front Register' }));
    await userEvent.click(screen.getByLabelText('Cashier'));
    await userEvent.click(await screen.findByRole('option', { name: 'Cashier One' }));
    await userEvent.click(screen.getByLabelText('Category'));
    await userEvent.click(await screen.findByRole('option', { name: 'Drinks' }));
    await userEvent.click(screen.getByLabelText('Product'));
    await userEvent.click(await screen.findByRole('option', { name: 'House Coffee' }));
    await userEvent.clear(screen.getByLabelText('From'));
    await userEvent.type(screen.getByLabelText('From'), '2026-07-01');
    await userEvent.clear(screen.getByLabelText('To'));
    await userEvent.type(screen.getByLabelText('To'), '2026-07-31');
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }));

    await waitFor(() => {
      const filteredCall = fetchMock.mock.calls
        .map(([input]) => new URL(String(input), window.location.origin))
        .find((url) => url.pathname.endsWith('/api/v1/reports/sales') && url.searchParams.get('storeId') === storeId);
      expect(filteredCall).toBeTruthy();
      expect(filteredCall?.searchParams.get('registerId')).toBe(registerId);
      expect(filteredCall?.searchParams.get('cashierId')).toBe(cashierId);
      expect(filteredCall?.searchParams.get('categoryId')).toBe(categoryId);
      expect(filteredCall?.searchParams.get('productId')).toBe(productId);
      expect(filteredCall?.searchParams.get('dateFrom')).toBe('2026-07-01');
      expect(filteredCall?.searchParams.get('dateTo')).toBe('2026-07-31');
    });
    expect(screen.getByTestId('sales-summary-grid')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Export CSV' }));
    expect(URL.createObjectURL).toHaveBeenCalled();
  });

  it('keeps the responsive summary grid stable while loading', async () => {
    storeSession(['MANAGER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['MANAGER']));
      if (url.pathname.endsWith('/api/v1/reports/sales')) return new Promise(() => undefined);
      return jsonResponse(pageResponse([]));
    });
    render(<App initialEntries={['/reports/sales']} />);
    expect(await screen.findByRole('status', { name: 'Loading sales report' })).toBeInTheDocument();
    expect(screen.getByTestId('sales-summary-grid').querySelectorAll(':scope > [data-summary-card]')).toHaveLength(13);
  });

  it('shows a report error without rendering failed metrics as zero', async () => {
    storeSession(['MANAGER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['MANAGER']));
      if (url.pathname.endsWith('/api/v1/reports/sales')) return jsonResponse({ message: 'Report unavailable' }, 500);
      return jsonResponse(pageResponse([]));
    });
    render(<App initialEntries={['/reports/sales']} />);
    const unavailableValues = await screen.findAllByText('Unavailable', {}, { timeout: 5_000 });
    const errorGrid = screen.getByTestId('sales-summary-grid');
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(errorGrid.querySelectorAll(':scope > [data-summary-card]')).toHaveLength(13);
    expect(unavailableValues).toHaveLength(13);
    expect(screen.queryByText('$0.00')).not.toBeInTheDocument();
  });
});
