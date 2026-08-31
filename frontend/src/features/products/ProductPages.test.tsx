import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CatalogueReference,
  CurrentUserResponse,
  Product,
  ProductCapability,
  ProductListResponse,
  SellableType,
  TaxCategory,
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
    email: 'product@example.local',
    displayName: 'Product User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER'], permissions?: string[]): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'product@example.local',
    displayName: 'Product User',
    roles,
    permissions
  };
}

function reference(overrides: Partial<CatalogueReference> = {}): CatalogueReference {
  return {
    id: '00000000-0000-0000-0000-000000000801',
    code: 'BEV',
    name: 'Beverages',
    description: null,
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function product(overrides: Partial<Product> = {}): Product {
  return {
    id: '00000000-0000-0000-0000-000000001201',
    sku: 'COFFEE-12OZ',
    name: 'House Coffee',
    description: 'Fresh brewed',
    sellableType: 'STANDARD_PRODUCT',
    unitOfMeasureId: '00000000-0000-0000-0000-000000000803',
    cost: 1.25,
    price: 3.25,
    categoryId: '00000000-0000-0000-0000-000000000801',
    brandId: '00000000-0000-0000-0000-000000000802',
    active: true,
    inventoryTrackingEnabled: true,
    decimalQuantityAllowed: false,
    imageUrl: 'https://cdn.example.test/coffee.png',
    taxCategoryId: null,
    variants: [
      {
        id: '00000000-0000-0000-0000-000000001202',
        sku: 'COFFEE-LARGE',
        name: 'Large',
        description: 'Large size',
        cost: 1.5,
        price: 4,
        active: true,
        createdAt: '2026-07-22T12:00:00Z',
        updatedAt: '2026-07-22T12:00:00Z',
        version: 0
      }
    ],
    barcodes: [
      {
        id: '00000000-0000-0000-0000-000000001203',
        barcode: '012345678905',
        variantId: '00000000-0000-0000-0000-000000001202',
        variantSku: 'COFFEE-LARGE',
        primaryBarcode: true,
        active: true,
        createdAt: '2026-07-22T12:00:00Z',
        updatedAt: '2026-07-22T12:00:00Z',
        version: 0
      }
    ],
    capabilities: ['TRACK_INVENTORY'],
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function pageResponse(content: Product[], overrides: Partial<ProductListResponse> = {}): ProductListResponse {
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

function referencePage(content: CatalogueReference[]) {
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
    path: '/api/v1/products',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

function mockReferenceEndpoints(url: URL) {
  if (url.pathname.endsWith('/api/v1/tax/categories')) {
    const category: TaxCategory = {
      id: '00000000-0000-0000-0000-000000000901', taxGroupId: null, code: 'STANDARD', name: 'Standard Tax',
      treatment: 'STANDARD', description: null, active: true, createdAt: '2026-07-22T12:00:00Z',
      updatedAt: '2026-07-22T12:00:00Z', version: 0
    };
    const zeroRated: TaxCategory = { ...category, id: '00000000-0000-0000-0000-000000000902', code: 'ZERO', name: 'Zero Rated', treatment: 'ZERO_RATED' };
    return jsonResponse({ ...referencePage([]), content: [category, zeroRated], totalElements: 2 });
  }
  if (url.pathname.endsWith('/api/v1/categories')) {
    return jsonResponse(referencePage([reference()]));
  }
  if (url.pathname.endsWith('/api/v1/brands')) {
    return jsonResponse(referencePage([reference({
      id: '00000000-0000-0000-0000-000000000802',
      code: 'HOUSE',
      name: 'House Brand'
    })]));
  }
  if (url.pathname.endsWith('/api/v1/units')) {
    return jsonResponse(referencePage([reference({
      id: '00000000-0000-0000-0000-000000000803',
      code: 'EA',
      name: 'Each'
    }), reference({
      id: '00000000-0000-0000-0000-000000000804',
      code: 'BTL',
      name: 'Bottle'
    })]));
  }
  return undefined;
}

describe('Product pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders the product table and applies search and reference filters', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/store-access/assigned-stores')) {
        return jsonResponse([{ storeId: '00000000-0000-0000-0000-000000000701', storeCode: 'MAIN', storeName: 'Main', city: null, administrativeDivisionCode: null, assignmentRole: 'MANAGER' }]);
      }
      const referenceResponse = mockReferenceEndpoints(url);
      if (referenceResponse) {
        return referenceResponse;
      }
      if (url.pathname.endsWith('/api/v1/products')) {
        return jsonResponse(pageResponse([product()], { totalElements: 1 }));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/products']} />);

    expect(await screen.findByRole('heading', { name: 'Products' })).toBeInTheDocument();
    expect(await screen.findByText('House Coffee')).toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Product filters' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Products' })).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Name'), 'coffee');
    await userEvent.type(screen.getByLabelText('SKU'), 'coffee-12oz');
    await userEvent.click(screen.getByLabelText('Category'));
    await userEvent.click(await screen.findByRole('option', { name: 'Beverages (BEV)' }));
    await userEvent.click(screen.getByLabelText('Brand'));
    await userEvent.click(await screen.findByRole('option', { name: 'House Brand (HOUSE)' }));
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/products')
          && url.searchParams.get('name') === 'coffee'
          && url.searchParams.get('sku') === 'coffee-12oz'
          && url.searchParams.get('categoryId') === '00000000-0000-0000-0000-000000000801'
          && url.searchParams.get('brandId') === '00000000-0000-0000-0000-000000000802';
      })).toBe(true);
    });
  });

  it('hides mutating actions from cashier users', async () => {
    storeSession(['CASHIER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      const referenceResponse = mockReferenceEndpoints(url);
      if (referenceResponse) {
        return referenceResponse;
      }
      if (url.pathname.endsWith('/api/v1/products')) {
        return jsonResponse(pageResponse([product()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/products']} />);

    expect(await screen.findByText('House Coffee')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New product' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Deactivate House Coffee/i })).not.toBeInTheDocument();
  });

  it('shows product creation to a manager with PRODUCT_CREATE permission', async () => {
    storeSession(['STORE_MANAGER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['STORE_MANAGER'], ['PRODUCT_VIEW', 'PRODUCT_CREATE']));
      }
      const referenceResponse = mockReferenceEndpoints(url);
      if (referenceResponse) return referenceResponse;
      if (url.pathname.endsWith('/api/v1/products')) return jsonResponse(pageResponse([]));
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/products']} />);

    expect(await screen.findByRole('link', { name: 'New product' })).toHaveAttribute('href', '/products/new');
  });

  it('creates a product with variants, barcodes, and capabilities', async () => {
    storeSession(['OWNER']);
    const created = product({
      id: '00000000-0000-0000-0000-000000001204',
      sku: 'TEA-12OZ',
      name: 'Iced Tea',
      capabilities: ['TRACK_INVENTORY', 'ALLOW_DISCOUNT'] as ProductCapability[]
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/store-access/assigned-stores')) {
        return jsonResponse([{ storeId: '00000000-0000-0000-0000-000000000701', storeCode: 'MAIN', storeName: 'Main', city: null, administrativeDivisionCode: null, assignmentRole: 'MANAGER' }]);
      }
      const referenceResponse = mockReferenceEndpoints(url);
      if (referenceResponse) {
        return referenceResponse;
      }
      if (url.pathname.endsWith('/api/v1/products') && init?.method === 'POST') {
        return jsonResponse(created, 201);
      }
      if (url.pathname.endsWith(`/api/v1/products/${created.id}`)) {
        return jsonResponse(created);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/products/new']} />);

    expect(await screen.findByRole('heading', { name: 'New product' })).toBeInTheDocument();
    expect(await screen.findByTestId('product-form')).toHaveStyle({ width: '100%', maxWidth: '100%', minWidth: '0' });
    expect(screen.getByTestId('product-action-bar')).toHaveStyle({ position: 'sticky', bottom: '0' });
    expect(screen.getByRole('link', { name: 'Cancel' })).toHaveAttribute('href', '/products');
    expect(screen.queryByLabelText('Tax category ID')).not.toBeInTheDocument();
    expect(await screen.findByRole('combobox', { name: 'Unit' })).toHaveTextContent('Each');
    expect(screen.queryByText('00000000-0000-0000-0000-000000000803')).not.toBeInTheDocument();
    await userEvent.click(await screen.findByRole('combobox', { name: 'Tax Category' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Standard Tax' }));
    expect(screen.queryByText('00000000-0000-0000-0000-000000000901')).not.toBeInTheDocument();
    await userEvent.type(await screen.findByLabelText('SKU'), 'tea-12oz');
    await userEvent.type(screen.getByLabelText('Name'), 'Iced Tea');
    await userEvent.clear(screen.getAllByLabelText('Cost')[0]);
    await userEvent.type(screen.getAllByLabelText('Cost')[0], '1.10');
    await userEvent.clear(screen.getAllByLabelText('Price')[0]);
    await userEvent.type(screen.getAllByLabelText('Price')[0], '2.75');
    await userEvent.click(screen.getByLabelText('ALLOW DISCOUNT'));

    await userEvent.click(screen.getByRole('button', { name: 'Add variant' }));
    await userEvent.type(screen.getByLabelText('Variant SKU'), 'tea-large');
    await userEvent.type(screen.getByLabelText('Variant name'), 'Large');
    await userEvent.clear(screen.getAllByLabelText('Cost')[1]);
    await userEvent.type(screen.getAllByLabelText('Cost')[1], '1.25');
    await userEvent.clear(screen.getAllByLabelText('Price')[1]);
    await userEvent.type(screen.getAllByLabelText('Price')[1], '3.25');

    await userEvent.click(screen.getByRole('button', { name: 'Add barcode' }));
    expect(screen.getByRole('button', { name: 'Create product' })).toBeVisible();
    await userEvent.click(screen.getByRole('combobox', { name: 'Assign To Variant' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Large — TEA-LARGE' }));
    await userEvent.type(screen.getByLabelText('Barcode'), '987654321098');
    await userEvent.click(screen.getByRole('button', { name: 'Create product' }));

    expect(await screen.findByRole('heading', { name: 'Iced Tea' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      if (!url.pathname.endsWith('/api/v1/products') || init?.method !== 'POST') {
        return false;
      }
      const body = JSON.parse(String(init.body));
      return body.sku === 'TEA-12OZ'
        && body.variants[0].sku === 'TEA-LARGE'
        && body.barcodes[0].barcode === '987654321098'
        && body.barcodes[0].variantSku === 'TEA-LARGE'
        && body.unitOfMeasureId === '00000000-0000-0000-0000-000000000803'
        && body.taxCategoryId === '00000000-0000-0000-0000-000000000901'
        && body.taxCategoryId !== 'Standard Tax'
        && body.capabilities.includes('ALLOW_DISCOUNT');
    })).toBe(true);
  });

  it('selects and persists the real base variant without losing the controlled value', async () => {
    storeSession(['OWNER']);
    const baseBarcodeProduct = product({
      taxCategoryId: '00000000-0000-0000-0000-000000000901',
      barcodes: [{
        id: '00000000-0000-0000-0000-000000001203',
        barcode: '123456789012',
        variantId: null,
        variantSku: null,
        primaryBarcode: true,
        active: true,
        createdAt: '2026-07-22T12:00:00Z',
        updatedAt: '2026-07-22T12:00:00Z',
        version: 0
      }]
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['OWNER']));
      const referenceResponse = mockReferenceEndpoints(url);
      if (referenceResponse) return referenceResponse;
      if (url.pathname.endsWith(`/api/v1/products/${baseBarcodeProduct.id}`) && init?.method === 'PUT') {
        return jsonResponse({ ...baseBarcodeProduct, version: 1 });
      }
      if (url.pathname.endsWith(`/api/v1/products/${baseBarcodeProduct.id}`)) return jsonResponse(baseBarcodeProduct);
      return apiError('Unexpected request');
    });

    render(<App initialEntries={[`/products/${baseBarcodeProduct.id}`]} />);

    const assignment = await screen.findByRole('combobox', { name: 'Assign To Variant' });
    expect(await screen.findByTestId('product-form')).toHaveStyle({ width: '100%', maxWidth: '100%', minWidth: '0' });
    expect(screen.getByTestId('product-action-bar')).toHaveStyle({ position: 'sticky', bottom: '0' });
    expect(assignment).toHaveTextContent('Base Variant');
    await userEvent.click(assignment);
    await userEvent.click(await screen.findByRole('option', { name: 'Large — COFFEE-LARGE' }));
    expect(assignment).toHaveTextContent('Large — COFFEE-LARGE');
    await userEvent.click(assignment);
    await userEvent.click(await screen.findByRole('option', { name: 'Base Variant' }));
    expect(assignment).toHaveTextContent('Base Variant');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(fetchMock.mock.calls.some(([input, init]) => {
      if (!String(input).includes(`/products/${baseBarcodeProduct.id}`) || init?.method !== 'PUT') return false;
      const body = JSON.parse(String(init.body));
      return body.variants[0].id === baseBarcodeProduct.variants[0].id
        && body.barcodes[0].id === baseBarcodeProduct.barcodes[0].id
        && body.barcodes[0].variantId == null
        && body.barcodes[0].variantSku == null;
    })).toBe(true));
  });

  it('validates, edits, and deactivates a product', async () => {
    storeSession(['MANAGER']);
    let current = product({ taxCategoryId: '00000000-0000-0000-0000-000000000901' });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      const referenceResponse = mockReferenceEndpoints(url);
      if (referenceResponse) {
        return referenceResponse;
      }
      if (url.pathname.endsWith(`/api/v1/products/${current.id}`) && init?.method === 'PUT') {
        const body = JSON.parse(String(init.body));
        current = product({
          ...current,
          ...body,
          name: body.name,
          sellableType: body.sellableType as SellableType,
          capabilities: body.capabilities,
          version: 1
        });
        return jsonResponse(current);
      }
      if (url.pathname.endsWith(`/api/v1/products/${current.id}/status`) && init?.method === 'PATCH') {
        current = product({ ...current, active: false, version: 2 });
        return jsonResponse(current);
      }
      if (url.pathname.endsWith(`/api/v1/products/${current.id}`)) {
        return jsonResponse(current);
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={[`/products/${current.id}`]} />);

    expect(await screen.findByRole('heading', { name: 'House Coffee' })).toBeInTheDocument();
    expect(await screen.findByRole('combobox', { name: 'Tax Category' })).toHaveTextContent('Standard Tax');
    expect(screen.getByRole('combobox', { name: 'Unit' })).toHaveTextContent('Each');
    await userEvent.click(screen.getByRole('combobox', { name: 'Unit' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Bottle' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Tax Category' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Zero Rated' }));
    await userEvent.clear(screen.getByLabelText('Name'));
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Name is required')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Name'), 'Updated Coffee');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Product saved.')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Updated Coffee' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => {
      if (!String(input).includes(`/products/${current.id}`) || init?.method !== 'PUT') return false;
      return JSON.parse(String(init.body)).taxCategoryId === '00000000-0000-0000-0000-000000000902';
    })).toBe(true);
    expect(fetchMock.mock.calls.some(([input, init]) => {
      if (!String(input).includes(`/products/${current.id}`) || init?.method !== 'PUT') return false;
      return JSON.parse(String(init.body)).unitOfMeasureId === '00000000-0000-0000-0000-000000000804';
    })).toBe(true);

    await userEvent.click(screen.getByRole('button', { name: 'Deactivate' }));
    const heading = await screen.findByRole('heading', { name: 'Updated Coffee' });
    expect(within(heading.closest('div')?.parentElement ?? document.body).getByText('Inactive')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => String(input).endsWith('/status') && init?.method === 'PATCH')).toBe(true);
  });
});
