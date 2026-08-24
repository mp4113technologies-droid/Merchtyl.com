import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  Product,
  ProductListResponse,
  StockAdjustment,
  StockAdjustmentListResponse,
  Store,
  StoreListResponse,
  UserRole
} from '../../api/types';

const STORE_ID = '00000000-0000-0000-0000-000000000901';
const PRODUCT_ID = '00000000-0000-0000-0000-000000000902';
const ADJUSTMENT_ID = '00000000-0000-0000-0000-000000000903';

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

function store(): Store {
  return {
    id: STORE_ID,
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
    createdAt: '2026-07-23T12:00:00Z',
    updatedAt: '2026-07-23T12:00:00Z',
    version: 0
  };
}

function product(): Product {
  return {
    id: PRODUCT_ID,
    sku: 'COFFEE-12OZ',
    name: 'House Coffee',
    description: null,
    sellableType: 'STANDARD_PRODUCT',
    unitOfMeasureId: null,
    cost: 1.25,
    price: 3.25,
    categoryId: null,
    brandId: null,
    active: true,
    inventoryTrackingEnabled: true,
    decimalQuantityAllowed: false,
    imageUrl: null,
    taxCategoryId: null,
    variants: [],
    barcodes: [],
    capabilities: ['TRACK_INVENTORY'],
    createdAt: '2026-07-23T12:00:00Z',
    updatedAt: '2026-07-23T12:00:00Z',
    version: 0
  };
}

function adjustment(overrides: Partial<StockAdjustment> = {}): StockAdjustment {
  return {
    id: ADJUSTMENT_ID,
    storeId: STORE_ID,
    reason: 'Cycle count',
    notes: 'Back shelf count',
    approvalStatus: 'POSTED',
    approvedByUserId: '00000000-0000-0000-0000-000000000201',
    approvedAt: '2026-07-23T12:00:00Z',
    approvalNotes: 'Approved by manager',
    lines: [
      {
        id: '00000000-0000-0000-0000-000000000904',
        productId: PRODUCT_ID,
        adjustmentType: 'DAMAGED',
        quantity: 2,
        quantityDelta: -2,
        resultingQuantity: 8,
        inventoryTransactionId: '00000000-0000-0000-0000-000000000905',
        createdAt: '2026-07-23T12:00:00Z',
        updatedAt: '2026-07-23T12:00:00Z',
        version: 0
      }
    ],
    createdAt: '2026-07-23T12:00:00Z',
    updatedAt: '2026-07-23T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function storePage(content: Store[]): StoreListResponse {
  return {
    content,
    page: 0,
    size: 100,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true
  };
}

function productPage(content: Product[]): ProductListResponse {
  return {
    content,
    page: 0,
    size: 100,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true
  };
}

function adjustmentPage(content: StockAdjustment[], overrides: Partial<StockAdjustmentListResponse> = {}): StockAdjustmentListResponse {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: 1,
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
    path: '/api/v1/inventory/adjustments',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Inventory adjustment pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders adjustment history and exposes new adjustment for managers', async () => {
    storeSession(['OWNER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/inventory/adjustments')) {
        return jsonResponse(adjustmentPage([adjustment()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/inventory/adjustments']} />);

    expect(await screen.findByRole('heading', { name: 'Inventory adjustments' })).toBeInTheDocument();
    expect(await screen.findByText('Cycle count')).toBeInTheDocument();
    expect(screen.getByText('POSTED')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'New adjustment' })).toBeInTheDocument();
  });

  it('hides creation from cashier users', async () => {
    storeSession(['CASHIER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      if (url.pathname.endsWith('/api/v1/inventory/adjustments')) {
        return jsonResponse(adjustmentPage([adjustment()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/inventory/adjustments']} />);

    expect(await screen.findByText('Cycle count')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'New adjustment' })).not.toBeInTheDocument();
  });

  it('creates a damaged-stock adjustment with one line', async () => {
    storeSession(['MANAGER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(storePage([store()]));
      }
      if (url.pathname.endsWith('/api/v1/products')) {
        return jsonResponse(productPage([product()]));
      }
      if (url.pathname.endsWith('/api/v1/inventory/adjustments') && init?.method === 'POST') {
        return jsonResponse(adjustment(), 201);
      }
      if (url.pathname.endsWith('/api/v1/inventory/adjustments')) {
        return jsonResponse(adjustmentPage([adjustment()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/inventory/adjustments/new']} />);

    expect(await screen.findByRole('heading', { name: 'New inventory adjustment' })).toBeInTheDocument();

    await userEvent.click(await screen.findByLabelText('Store'));
    await userEvent.click(await screen.findByRole('option', { name: 'Main Store (MAIN)' }));
    await userEvent.type(screen.getByLabelText('Reason'), 'Damaged in receiving');
    await userEvent.type(screen.getByLabelText('Notes'), 'Two bags torn');
    await userEvent.type(screen.getByLabelText('Approval notes'), 'Manager approved');
    await userEvent.click(screen.getByLabelText('Product'));
    await userEvent.click(await screen.findByRole('option', { name: 'House Coffee (COFFEE-12OZ)' }));
    await userEvent.click(screen.getByLabelText('Type'));
    await userEvent.click(await screen.findByRole('option', { name: 'DAMAGED' }));
    await userEvent.clear(screen.getByLabelText('Quantity'));
    await userEvent.type(screen.getByLabelText('Quantity'), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Create adjustment' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        if (!url.pathname.endsWith('/api/v1/inventory/adjustments') || init?.method !== 'POST') {
          return false;
        }
        const payload = JSON.parse(String(init.body));
        return payload.storeId === STORE_ID
          && payload.reason === 'Damaged in receiving'
          && payload.notes === 'Two bags torn'
          && payload.approvalNotes === 'Manager approved'
          && payload.lines[0].productId === PRODUCT_ID
          && payload.lines[0].adjustmentType === 'DAMAGED'
          && payload.lines[0].quantity === 2;
      })).toBe(true);
    });

    expect(await screen.findByText('Stock updated successfully.')).toBeInTheDocument();
  });
});
