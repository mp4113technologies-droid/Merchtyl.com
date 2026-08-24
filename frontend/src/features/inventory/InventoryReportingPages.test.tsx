import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CatalogueReference,
  CatalogueReferenceListResponse,
  CurrentUserResponse,
  InventoryActivityReportRow,
  InventoryReport,
  InventoryStockReportRow,
  Product,
  ProductListResponse,
  Store,
  StoreListResponse,
  UserRole
} from '../../api/types';

const STORE_ID = '00000000-0000-0000-0000-000000000901';
const SECOND_STORE_ID = '00000000-0000-0000-0000-000000000902';
const CATEGORY_ID = '00000000-0000-0000-0000-000000000906';
const COFFEE_ID = '00000000-0000-0000-0000-000000000903';
const SODA_ID = '00000000-0000-0000-0000-000000000904';
const CHIPS_ID = '00000000-0000-0000-0000-000000000905';

function authResponse(roles: UserRole[] = ['OWNER']): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'inventory@example.local',
    displayName: 'Inventory User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'inventory@example.local',
    displayName: 'Inventory User',
    roles
  };
}

function store(id = STORE_ID, code = 'MAIN', name = 'Main Store'): Store {
  return {
    id,
    code,
    name,
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
    createdAt: '2026-07-27T12:00:00Z',
    updatedAt: '2026-07-27T12:00:00Z',
    version: 0
  };
}

function category(): CatalogueReference {
  return {
    id: CATEGORY_ID,
    code: 'BEV',
    name: 'Beverages',
    description: null,
    active: true,
    createdAt: '2026-07-27T12:00:00Z',
    updatedAt: '2026-07-27T12:00:00Z',
    version: 0
  };
}

function product(id: string, sku: string, name: string, cost: number): Product {
  return {
    id,
    sku,
    name,
    description: null,
    sellableType: 'STANDARD_PRODUCT',
    unitOfMeasureId: null,
    cost,
    price: cost * 2,
    categoryId: CATEGORY_ID,
    brandId: null,
    active: true,
    inventoryTrackingEnabled: true,
    decimalQuantityAllowed: false,
    imageUrl: null,
    taxCategoryId: null,
    variants: [],
    barcodes: [],
    capabilities: ['TRACK_INVENTORY'],
    createdAt: '2026-07-27T12:00:00Z',
    updatedAt: '2026-07-27T12:00:00Z',
    version: 0
  };
}

function stockRow(
  productId: string,
  productSku: string,
  productName: string,
  quantityOnHand: number,
  cost: number,
  storeId = STORE_ID,
  storeCode = 'MAIN',
  storeName = 'Main Store'
): InventoryStockReportRow {
  return {
    storeId,
    storeCode,
    storeName,
    productId,
    productSku,
    productName,
    categoryId: CATEGORY_ID,
    cost,
    quantityOnHand,
    inventoryValue: quantityOnHand * cost,
    lastTransactionAt: '2026-07-27T12:00:00Z'
  };
}

function activityRow(
  id: string,
  productId: string,
  productSku: string,
  productName: string,
  transactionType: InventoryActivityReportRow['transactionType'],
  quantityDelta: number,
  value: number
): InventoryActivityReportRow {
  return {
    id,
    storeId: STORE_ID,
    storeCode: 'MAIN',
    storeName: 'Main Store',
    productId,
    productSku,
    productName,
    categoryId: CATEGORY_ID,
    transactionType,
    quantityDelta,
    quantity: Math.abs(quantityDelta),
    inventoryValue: value,
    referenceType: 'STOCK_ADJUSTMENT',
    referenceId: '00000000-0000-0000-0000-000000000908',
    reason: 'Cycle count A',
    actorUserId: null,
    occurredAt: '2026-07-27T12:00:00Z'
  };
}

function inventoryReport(): InventoryReport {
  const stockRows = [
    stockRow(COFFEE_ID, 'COFFEE-12OZ', 'House Coffee', 4, 1.25),
    stockRow(SODA_ID, 'SODA-CAN', 'Cola Can', -2, 0.8),
    stockRow(CHIPS_ID, 'CHIPS-BBQ', 'BBQ Chips', 12, 1.5, SECOND_STORE_ID, 'WEST', 'West Store')
  ];
  const adjustmentRows = [
    activityRow('adjustment-row', COFFEE_ID, 'COFFEE-12OZ', 'House Coffee', 'ADJUSTMENT_INCREASE', 3, 3.75)
  ];
  const damagedRows = [
    activityRow('damaged-row', SODA_ID, 'SODA-CAN', 'Cola Can', 'DAMAGED', -2, 1.6)
  ];
  const expiredRows = [
    activityRow('expired-row', CHIPS_ID, 'CHIPS-BBQ', 'BBQ Chips', 'EXPIRED', -1, 1.5)
  ];
  return {
    storeId: null,
    categoryId: null,
    productId: null,
    dateFrom: '2026-07-01',
    dateTo: '2026-07-31',
    lowStockThreshold: 5,
    currentStock: 14,
    inventoryValue: 21.4,
    stockItemCount: 3,
    lowStockCount: 1,
    negativeStockCount: 1,
    adjustmentCount: 1,
    damagedCount: 1,
    expiredCount: 1,
    adjustmentQuantity: 3,
    damagedQuantity: 2,
    expiredQuantity: 1,
    adjustmentValue: 3.75,
    damagedValue: 1.6,
    expiredValue: 1.5,
    stockRows,
    lowStockRows: [stockRows[0]],
    negativeStockRows: [stockRows[1]],
    adjustmentRows,
    damagedRows,
    expiredRows,
    generatedAt: '2026-07-29T12:00:00Z'
  };
}

function page<T>(content: T[], size = 100) {
  return {
    content,
    page: 0,
    size,
    totalElements: content.length,
    totalPages: 1,
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
    path: '/api/v1/reports/inventory',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

function mockInventoryReportApi() {
  const stores = [store(), store(SECOND_STORE_ID, 'WEST', 'West Store')];
  const categories = [category()];
  const products = [
    product(COFFEE_ID, 'COFFEE-12OZ', 'House Coffee', 1.25),
    product(SODA_ID, 'SODA-CAN', 'Cola Can', 0.8),
    product(CHIPS_ID, 'CHIPS-BBQ', 'BBQ Chips', 1.5)
  ];
  return vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = new URL(String(input), window.location.origin);
    if (url.pathname.endsWith('/api/v1/auth/me')) {
      return jsonResponse(currentUser(['OWNER']));
    }
    if (url.pathname.endsWith('/api/v1/stores')) {
      return jsonResponse(page<Store>(stores) satisfies StoreListResponse);
    }
    if (url.pathname.endsWith('/api/v1/categories')) {
      return jsonResponse(page<CatalogueReference>(categories) satisfies CatalogueReferenceListResponse);
    }
    if (url.pathname.endsWith('/api/v1/products')) {
      return jsonResponse(page<Product>(products) satisfies ProductListResponse);
    }
    if (url.pathname.endsWith('/api/v1/reports/inventory')) {
      return jsonResponse(inventoryReport());
    }
    return apiError('Unexpected request');
  });
}

describe('Inventory reporting pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:inventory-report');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  });

  it('renders current inventory totals, applies filters, and exports CSV', async () => {
    storeSession(['OWNER']);
    const fetchMock = mockInventoryReportApi();

    render(<App initialEntries={['/inventory']} />);

    expect(await screen.findByRole('heading', { name: 'Inventory' })).toBeInTheDocument();
    expect(await screen.findByText('House Coffee')).toBeInTheDocument();
    expect(screen.getByText('COFFEE-12OZ')).toBeInTheDocument();
    expect(screen.getByText('$21.40')).toBeInTheDocument();
    expect(screen.getByText('$5.00')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Store'));
    await userEvent.click(await screen.findByRole('option', { name: 'Main Store (MAIN)' }));
    await userEvent.click(screen.getByLabelText('Category'));
    await userEvent.click(await screen.findByRole('option', { name: 'Beverages (BEV)' }));
    await userEvent.click(screen.getByLabelText('Product'));
    await userEvent.click(await screen.findByRole('option', { name: 'House Coffee (COFFEE-12OZ)' }));
    await userEvent.clear(screen.getByLabelText('Date from'));
    await userEvent.type(screen.getByLabelText('Date from'), '2026-07-01');
    await userEvent.clear(screen.getByLabelText('Date to'));
    await userEvent.type(screen.getByLabelText('Date to'), '2026-07-31');
    await userEvent.clear(screen.getByLabelText('Low stock threshold'));
    await userEvent.type(screen.getByLabelText('Low stock threshold'), '8');

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/reports/inventory')
          && url.searchParams.get('storeId') === STORE_ID
          && url.searchParams.get('categoryId') === CATEGORY_ID
          && url.searchParams.get('productId') === COFFEE_ID
          && url.searchParams.get('dateFrom') === '2026-07-01'
          && url.searchParams.get('dateTo') === '2026-07-31'
          && url.searchParams.get('lowStockThreshold') === '8';
      })).toBe(true);
    });

    await userEvent.click(screen.getByRole('button', { name: 'Export CSV' }));
    expect(URL.createObjectURL).toHaveBeenCalled();
  });

  it('renders activity history from report rows', async () => {
    storeSession(['OWNER']);
    mockInventoryReportApi();

    render(<App initialEntries={['/inventory/history']} />);

    expect(await screen.findByRole('heading', { name: 'Inventory history' })).toBeInTheDocument();
    expect(await screen.findByText('House Coffee')).toBeInTheDocument();
    expect(screen.getByText('Adjustment Increase')).toBeInTheDocument();
    expect(screen.getByText('+3')).toBeInTheDocument();
    expect(screen.getAllByText('STOCK_ADJUSTMENT')[0]).toBeInTheDocument();
  });

  it('renders the low-stock list from the report threshold', async () => {
    storeSession(['MANAGER']);
    mockInventoryReportApi();

    render(<App initialEntries={['/inventory/low-stock']} />);

    expect(await screen.findByRole('heading', { name: 'Low stock' })).toBeInTheDocument();
    expect(await screen.findByText('House Coffee')).toBeInTheDocument();
    expect(screen.queryByText('BBQ Chips')).not.toBeInTheDocument();
    expect(screen.queryByText('Cola Can')).not.toBeInTheDocument();
  });

  it('renders the negative-stock list', async () => {
    storeSession(['MANAGER']);
    mockInventoryReportApi();

    render(<App initialEntries={['/inventory/negative-stock']} />);

    expect(await screen.findByRole('heading', { name: 'Negative stock' })).toBeInTheDocument();
    expect(await screen.findByText('Cola Can')).toBeInTheDocument();
    expect(screen.getByText('-2')).toBeInTheDocument();
    expect(screen.queryByText('House Coffee')).not.toBeInTheDocument();
  });

  it('renders damaged and expired inventory report pages', async () => {
    storeSession(['OWNER']);
    mockInventoryReportApi();

    const { unmount } = render(<App initialEntries={['/inventory/damaged']} />);

    expect(await screen.findByRole('heading', { name: 'Damaged inventory' })).toBeInTheDocument();
    expect(await screen.findByText('Cola Can')).toBeInTheDocument();
    expect(screen.getAllByText('Damaged')[0]).toBeInTheDocument();

    unmount();
    render(<App initialEntries={['/inventory/expired']} />);

    expect(await screen.findByRole('heading', { name: 'Expired inventory' })).toBeInTheDocument();
    expect(await screen.findByText('BBQ Chips')).toBeInTheDocument();
    expect(screen.getAllByText('Expired')[0]).toBeInTheDocument();
  });
});
