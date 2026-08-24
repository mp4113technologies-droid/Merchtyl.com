import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  Product,
  ProductListResponse,
  StockCount,
  StockCountListResponse,
  Store,
  StoreListResponse,
  UserRole
} from '../../api/types';

const STORE_ID = '00000000-0000-0000-0000-000000000901';
const PRODUCT_ID = '00000000-0000-0000-0000-000000000902';
const COUNT_ID = '00000000-0000-0000-0000-000000000903';
const LINE_ID = '00000000-0000-0000-0000-000000000904';

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
    createdAt: '2026-07-27T12:00:00Z',
    updatedAt: '2026-07-27T12:00:00Z',
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
    createdAt: '2026-07-27T12:00:00Z',
    updatedAt: '2026-07-27T12:00:00Z',
    version: 0
  };
}

function count(overrides: Partial<StockCount> = {}): StockCount {
  return {
    id: COUNT_ID,
    storeId: STORE_ID,
    reference: 'Cycle count A',
    notes: 'Back shelf count',
    status: 'DRAFT',
    createdByUserId: '00000000-0000-0000-0000-000000000201',
    reviewedByUserId: null,
    reviewedAt: null,
    reviewNotes: null,
    postedByUserId: null,
    postedAt: null,
    postNotes: null,
    lines: [
      {
        id: LINE_ID,
        productId: PRODUCT_ID,
        expectedQuantity: 10,
        countedQuantity: null,
        varianceQuantity: null,
        balanceVersion: 0,
        resultingQuantity: null,
        inventoryTransactionId: null,
        createdAt: '2026-07-27T12:00:00Z',
        updatedAt: '2026-07-27T12:00:00Z',
        version: 0
      }
    ],
    createdAt: '2026-07-27T12:00:00Z',
    updatedAt: '2026-07-27T12:00:00Z',
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

function countPage(content: StockCount[]): StockCountListResponse {
  return {
    content,
    page: 0,
    size: 10,
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
    path: '/api/v1/inventory/counts',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Stock count pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders stock count history and exposes new count for managers', async () => {
    storeSession(['OWNER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/inventory/counts')) {
        return jsonResponse(countPage([count({ status: 'IN_REVIEW' })]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/inventory/counts']} />);

    expect(await screen.findByRole('heading', { name: 'Stock counts' })).toBeInTheDocument();
    expect(await screen.findByText('Cycle count A')).toBeInTheDocument();
    expect(screen.queryByText('IN_REVIEW')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'New count' })).toBeInTheDocument();
  });

  it('saves an actual stock count directly', async () => {
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
      if (url.pathname.endsWith('/api/v1/inventory/balances')) {
        return jsonResponse({ content: [{ productId: PRODUCT_ID, quantityOnHand: 10 }], page: 0, size: 100, totalElements: 1, totalPages: 1, first: true, last: true });
      }
      if (url.pathname.endsWith('/api/v1/inventory/counts') && init?.method === 'POST') {
        return jsonResponse(count(), 201);
      }
      if (url.pathname.endsWith(`/api/v1/inventory/counts/${COUNT_ID}`)) {
        return jsonResponse(count());
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/inventory/counts/new']} />);

    expect(await screen.findByRole('heading', { name: 'New stock count' })).toBeInTheDocument();
    await userEvent.click(await screen.findByLabelText('Store'));
    await userEvent.click(await screen.findByRole('option', { name: 'Main Store (MAIN)' }));
    await userEvent.type(screen.getByLabelText('Reference'), 'Cycle count A');
    await userEvent.type(screen.getByLabelText('Notes'), 'Back shelf count');
    await userEvent.click(screen.getByLabelText('Product'));
    await userEvent.click(await screen.findByRole('option', { name: 'House Coffee (COFFEE-12OZ)' }));
    const actualCount = screen.getByLabelText('Actual Count');
    await userEvent.clear(actualCount);
    await userEvent.type(actualCount, '7');
    await userEvent.click(screen.getByRole('button', { name: 'Save Count' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        if (!url.pathname.endsWith('/api/v1/inventory/counts') || init?.method !== 'POST') {
          return false;
        }
        const payload = JSON.parse(String(init.body));
        return payload.storeId === STORE_ID
          && payload.reference === 'Cycle count A'
          && payload.notes === 'Back shelf count'
          && payload.lines[0].productId === PRODUCT_ID
          && payload.lines[0].countedQuantity === 7;
      })).toBe(true);
    });
  });

  it('saves an existing in-review count without review or post actions', async () => {
    storeSession(['OWNER']);
    let currentCount = count({ status: 'IN_REVIEW' });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/products')) {
        return jsonResponse(productPage([product()]));
      }
      if (url.pathname.endsWith(`/api/v1/inventory/counts/${COUNT_ID}/lines`) && init?.method === 'PATCH') {
        currentCount = count({
          status: 'SAVED',
          lines: [{
            ...currentCount.lines[0],
            countedQuantity: 7,
            varianceQuantity: -3,
            resultingQuantity: 7
          }]
        });
        return jsonResponse(currentCount);
      }
      if (url.pathname.endsWith(`/api/v1/inventory/counts/${COUNT_ID}`)) {
        return jsonResponse(currentCount);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={[`/inventory/counts/${COUNT_ID}`]} />);

    expect(await screen.findByRole('heading', { name: 'Cycle count A' })).toBeInTheDocument();
    const countedInput = await screen.findByLabelText('Counted quantity for House Coffee (COFFEE-12OZ)');
    await userEvent.clear(countedInput);
    await userEvent.type(countedInput, '7');
    const saveButton = screen.getByRole('button', { name: 'Save Count' });
    await waitFor(() => expect(saveButton).toBeEnabled());
    await userEvent.click(saveButton);
    expect(await screen.findByText('-3')).toBeInTheDocument();
    expect(await screen.findByText('Stock count updated successfully.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Review' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Post' })).not.toBeInTheDocument();

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith(`/api/v1/inventory/counts/${COUNT_ID}/lines`)
          && init?.method === 'PATCH';
      })).toBe(true);
    });
  });
});
