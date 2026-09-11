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
  if (url.pathname.includes('/api/v1/products/barcodes/') && url.pathname.endsWith('/ownership')) {
    const parts = url.pathname.split('/');
    return jsonResponse({ barcode: decodeURIComponent(parts[parts.length - 2]), assigned: false, productActive: false });
  }
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

  it('defaults to active products and includes inactive products active-first when toggled', async () => {
    storeSession(['OWNER']);
    const active = product({ id: '00000000-0000-0000-0000-000000001211', name: 'Active Cola', active: true });
    const inactive = product({ id: '00000000-0000-0000-0000-000000001212', name: 'Old Cola', active: false });
    const requested: URL[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['OWNER']));
      const reference = mockReferenceEndpoints(url);
      if (reference) return reference;
      if (url.pathname.endsWith('/api/v1/products')) {
        requested.push(url);
        return jsonResponse(pageResponse(url.searchParams.get('includeInactive') === 'true'
          ? [active, inactive] : [active]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/products']} />);
    expect(await screen.findByText('Active Cola')).toBeVisible();
    expect(screen.queryByText('Old Cola')).not.toBeInTheDocument();
    expect(requested.some((url) => url.searchParams.get('includeInactive') === 'false')).toBe(true);

    await userEvent.click(screen.getByLabelText('Show inactive products'));
    expect(await screen.findByText('Old Cola')).toBeVisible();
    const names = screen.getAllByRole('row').slice(1).map((row) => row.textContent ?? '');
    expect(names[0]).toContain('Active Cola');
    expect(names[1]).toContain('Old Cola');
    expect(requested.some((url) => url.searchParams.get('includeInactive') === 'true')).toBe(true);
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
      if (url.pathname.endsWith('/api/v1/products')) {
        return jsonResponse(pageResponse([created]));
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
    expect(screen.getByLabelText('Available at all stores')).toBeChecked();
    await userEvent.click(screen.getByLabelText('Selected stores'));
    await userEvent.click(await screen.findByLabelText('Main'));
    await userEvent.click(await screen.findByRole('combobox', { name: 'Tax Category' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Standard Tax' }));
    expect(screen.queryByText('00000000-0000-0000-0000-000000000901')).not.toBeInTheDocument();
    expect(await screen.findByDisplayValue('Auto-generated when product is created')).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Name'), 'Iced Tea');
    await userEvent.clear(screen.getAllByLabelText('Cost')[0]);
    await userEvent.type(screen.getAllByLabelText('Cost')[0], '1.10');
    await userEvent.clear(screen.getAllByLabelText('Price')[0]);
    await userEvent.type(screen.getAllByLabelText('Price')[0], '2.75');
    await userEvent.click(screen.getByLabelText('ALLOW DISCOUNT'));

    expect(await screen.findByText('Base Variant')).toBeVisible();
    expect(screen.getByTestId('product-variant-card')).toHaveStyle({ width: '100%', maxWidth: '100%', minWidth: '0' });
    expect(screen.queryByText('Enter or scan a barcode.')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Scan Multiple Barcodes' })).toBeVisible();
    expect(screen.getByDisplayValue('Auto-generated when created')).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Variant name'), 'Large');
    await userEvent.clear(screen.getAllByLabelText('Cost')[1]);
    await userEvent.type(screen.getAllByLabelText('Cost')[1], '1.25');
    await userEvent.clear(screen.getAllByLabelText('Price')[1]);
    await userEvent.type(screen.getAllByLabelText('Price')[1], '3.25');

    const writesBeforeScanning = fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST' || init?.method === 'PUT').length;
    const scanner = screen.getByRole('textbox', { name: 'Scan or enter barcode' });
    await userEvent.type(scanner, '987654321098{enter}');
    await userEvent.type(scanner, '987654321099{enter}');
    await userEvent.type(scanner, '987654321098{enter}');
    expect(screen.getByText('Barcode already added.')).toBeInTheDocument();
    expect(scanner).toHaveFocus();
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST' || init?.method === 'PUT')).toHaveLength(writesBeforeScanning);
    expect(await screen.findAllByTestId('variant-barcode-chip')).toHaveLength(2);
    expect(screen.queryByRole('heading', { name: 'Barcodes' })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Assign To Variant' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create product' })).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Create product' }));

    expect(await screen.findByRole('heading', { name: 'Products' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'New product' })).not.toBeInTheDocument();
    expect(screen.getByText('Product created successfully.')).toBeVisible();
    expect(await screen.findByText('Iced Tea')).toBeVisible();
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      if (!url.pathname.endsWith('/api/v1/products') || init?.method !== 'POST') {
        return false;
      }
      const body = JSON.parse(String(init.body));
      return body.sku === undefined
        && body.variants[0].sku === undefined
        && body.variants[0].barcodes[0].barcode === '987654321098'
        && body.variants[0].barcodes[1].barcode === '987654321099'
        && body.barcodes === undefined
        && body.unitOfMeasureId === '00000000-0000-0000-0000-000000000803'
        && body.taxCategoryId === '00000000-0000-0000-0000-000000000901'
        && body.taxCategoryId !== 'Standard Tax'
        && body.availabilityScope === 'SELECTED_STORES'
        && body.storeIds[0] === '00000000-0000-0000-0000-000000000701'
        && body.capabilities.includes('ALLOW_DISCOUNT');
    })).toBe(true);
  });

  it('keeps the entered product on the create page when creation fails', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['OWNER']));
      if (url.pathname.endsWith('/api/v1/store-access/assigned-stores')) return jsonResponse([]);
      const referenceResponse = mockReferenceEndpoints(url);
      if (referenceResponse) return referenceResponse;
      if (url.pathname.endsWith('/api/v1/products') && init?.method === 'POST') {
        return apiError('A product with this name already exists.', 409, 'REQUEST_CONFLICT');
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/products/new']} />);
    await screen.findByRole('heading', { name: 'New product' });
    await userEvent.type(await screen.findByLabelText('Name'), 'Duplicate Tea');
    await userEvent.type(screen.getByLabelText('Variant name'), 'Base');
    await userEvent.click(await screen.findByRole('combobox', { name: 'Tax Category' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Standard Tax' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create product' }));

    await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(true));
    expect(await screen.findByText("We couldn't complete this action because the information conflicts with the current state. Refresh and try again.")).toBeVisible();
    expect(screen.getByRole('heading', { name: 'New product' })).toBeVisible();
    expect(screen.getByLabelText('Name')).toHaveValue('Duplicate Tea');
    expect(screen.queryByRole('heading', { name: 'Products' })).not.toBeInTheDocument();
  });

  it('keeps batch scans temporary, discards them on cancel, and merges them locally on Add All', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['OWNER']));
      if (url.pathname.endsWith('/api/v1/store-access/assigned-stores')) return jsonResponse([]);
      const referenceResponse = mockReferenceEndpoints(url);
      if (referenceResponse) return referenceResponse;
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/products/new']} />);
    await screen.findByRole('heading', { name: 'New product' });
    expect(await screen.findByText('Base Variant')).toBeVisible();
    const card = screen.getByTestId('product-variant-card');
    const inlineScanner = within(card).getByRole('textbox', { name: 'Scan or enter barcode' });
    await userEvent.type(inlineScanner, '001234567890{enter}');

    await userEvent.click(within(card).getByRole('button', { name: 'Scan Multiple Barcodes' }));
    let dialog = screen.getByRole('dialog', { name: 'Add Barcodes — Variant 1' });
    let batchScanner = within(dialog).getByRole('textbox', { name: 'Scan or enter barcode' });
    expect(batchScanner).toHaveFocus();
    await userEvent.type(batchScanner, '111{enter}222{enter}333{enter}');
    expect(within(dialog).getAllByTestId('batch-barcode-row')).toHaveLength(3);
    expect(within(card).getAllByTestId('variant-barcode-chip')).toHaveLength(1);
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST' || init?.method === 'PUT')).toHaveLength(0);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(within(card).getAllByTestId('variant-barcode-chip')).toHaveLength(1);

    await userEvent.type(within(card).getByLabelText('Variant name'), 'Green');
    await userEvent.click(within(card).getByRole('button', { name: 'Scan Multiple Barcodes' }));
    dialog = screen.getByRole('dialog', { name: 'Add Barcodes — Green' });
    batchScanner = within(dialog).getByRole('textbox', { name: 'Scan or enter barcode' });
    await userEvent.type(batchScanner, '222{enter}333{enter}222{enter}');
    expect(within(dialog).getByText('Barcode already scanned.')).toBeInTheDocument();
    expect(batchScanner).toHaveFocus();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add All (2)' }));
    expect(within(card).getAllByTestId('variant-barcode-chip')).toHaveLength(3);
    expect(within(card).getByText('001234567890')).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST' || init?.method === 'PUT')).toHaveLength(0);
  });

  it('shows inactive ownership and records reassignment only after confirmation', async () => {
    storeSession(['OWNER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['OWNER']));
      if (url.pathname.endsWith('/api/v1/store-access/assigned-stores')) return jsonResponse([]);
      if (url.pathname.endsWith('/api/v1/products/barcodes/123456789/ownership')) return jsonResponse({
        barcode: '123456789', assigned: true,
        assignmentId: '00000000-0000-0000-0000-000000009901', assignmentVersion: 4,
        productId: '00000000-0000-0000-0000-000000009902', productName: 'Pepsi',
        variantId: '00000000-0000-0000-0000-000000009903', variantName: '500 mL', productActive: false
      });
      const referenceResponse = mockReferenceEndpoints(url);
      return referenceResponse ?? apiError('Unexpected request');
    });

    render(<App initialEntries={['/products/new']} />);
    const scanner = await screen.findByRole('textbox', { name: 'Scan or enter barcode' });
    await userEvent.type(scanner, '123456789{enter}');
    const dialog = await screen.findByRole('dialog', { name: 'Reassign Barcode?' });
    expect(within(dialog).getByText(/Pepsi — 500 mL \(Inactive\)/)).toBeVisible();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByTestId('variant-barcode-chip')).not.toBeInTheDocument();

    await userEvent.type(scanner, '123456789{enter}');
    await userEvent.click(await screen.findByRole('button', { name: 'Reassign Barcode' }));
    expect(await screen.findByTestId('variant-barcode-chip')).toHaveTextContent('123456789');
  });

  it('reconciles an existing variant barcode list in the aggregate update', async () => {
    storeSession(['OWNER']);
    const baseBarcodeProduct = product({
      taxCategoryId: '00000000-0000-0000-0000-000000000901',
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

    const scanner = await screen.findByRole('textbox', { name: 'Scan or enter barcode' });
    expect(await screen.findByTestId('product-form')).toHaveStyle({ width: '100%', maxWidth: '100%', minWidth: '0' });
    expect(screen.getByTestId('product-variant-card')).toHaveStyle({ width: '100%', maxWidth: '100%', minWidth: '0' });
    expect(screen.getByTestId('variant-barcode-chip')).toHaveTextContent('012345678905');
    expect(screen.getByTestId('product-action-bar')).toHaveStyle({ position: 'sticky', bottom: '0' });
    await userEvent.click(within(screen.getByTestId('variant-barcode-chip')).getByTestId('DeleteIcon'));
    await userEvent.type(scanner, '123456789012{enter}');
    await userEvent.type(scanner, '123456789013{enter}');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(fetchMock.mock.calls.some(([input, init]) => {
      if (!String(input).includes(`/products/${baseBarcodeProduct.id}`) || init?.method !== 'PUT') return false;
      const body = JSON.parse(String(init.body));
      return body.variants[0].id === baseBarcodeProduct.variants[0].id
        && body.variants[0].barcodes.map((item: { barcode: string }) => item.barcode).join(',') === '123456789012,123456789013'
        && body.barcodes === undefined;
    })).toBe(true));
  });

  it('rejects a batch barcode owned by another variant and preserves scanner focus', async () => {
    storeSession(['OWNER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['OWNER']));
      if (url.pathname.endsWith('/api/v1/store-access/assigned-stores')) return jsonResponse([]);
      const referenceResponse = mockReferenceEndpoints(url);
      if (referenceResponse) return referenceResponse;
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/products/new']} />);
    await screen.findByRole('heading', { name: 'New product' });
    await userEvent.click(await screen.findByRole('button', { name: 'Add variant' }));
    const cards = screen.getAllByTestId('product-variant-card');
    const firstScanner = within(cards[0]).getByRole('textbox', { name: 'Scan or enter barcode' });
    await userEvent.type(firstScanner, '0012345678905{enter}');
    await userEvent.click(within(cards[1]).getByRole('button', { name: 'Scan Multiple Barcodes' }));
    const dialog = screen.getByRole('dialog', { name: 'Add Barcodes — Variant 2' });
    const batchScanner = within(dialog).getByRole('textbox', { name: 'Scan or enter barcode' });
    await userEvent.type(batchScanner, '0012345678905{enter}');

    expect(screen.getAllByTestId('variant-barcode-chip')).toHaveLength(1);
    expect(screen.getByText('This barcode is already assigned to another variant in this product.')).toBeInTheDocument();
    expect(batchScanner).toHaveFocus();
  });

  it('collects ten scans locally and sends one product update only when saved', async () => {
    storeSession(['OWNER']);const current=product({taxCategoryId:'00000000-0000-0000-0000-000000000901'});let updateBody:any;
    const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation((input,init)=>{const url=new URL(String(input),window.location.origin);if(url.pathname.endsWith('/api/v1/auth/me'))return jsonResponse(currentUser(['OWNER']));const reference=mockReferenceEndpoints(url);if(reference)return reference;if(url.pathname.endsWith(`/api/v1/products/${current.id}`)&&init?.method==='PUT'){updateBody=JSON.parse(String(init.body));return jsonResponse({...current,version:1});}if(url.pathname.endsWith(`/api/v1/products/${current.id}`))return jsonResponse(current);return apiError('Unexpected request');});
    render(<App initialEntries={[`/products/${current.id}`]}/>);await screen.findByRole('heading',{name:current.name});const scanner=screen.getByRole('textbox',{name:'Scan or enter barcode'});for(let index=1;index<=10;index+=1)await userEvent.type(scanner,`000${index.toString().padStart(2,'0')}{enter}`);expect(screen.getAllByTestId('variant-barcode-chip')).toHaveLength(11);expect(scanner).toHaveFocus();expect(fetchMock.mock.calls.filter(([,init])=>init?.method==='POST'||init?.method==='PUT')).toHaveLength(0);await userEvent.click(screen.getByRole('button',{name:'Save changes'}));await waitFor(()=>expect(updateBody.variants[0].barcodes).toHaveLength(11));expect(fetchMock.mock.calls.filter(([,init])=>init?.method==='PUT')).toHaveLength(1);expect(fetchMock.mock.calls.filter(([,init])=>init?.method==='POST')).toHaveLength(0);
  });

  it('keeps Add Variant enabled and supports at least five variant cards', async () => {
    storeSession(['OWNER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser(['OWNER']));
      if (url.pathname.endsWith('/api/v1/store-access/assigned-stores')) return jsonResponse([]);
      const reference = mockReferenceEndpoints(url);
      return reference ?? apiError('Unexpected request');
    });
    render(<App initialEntries={['/products/new']} />);
    await screen.findByRole('heading', { name: 'New product' });
    const add = await screen.findByRole('button', { name: 'Add variant' });
    for (let index = 0; index < 4; index += 1) await userEvent.click(add);
    expect(screen.getAllByTestId('product-variant-card')).toHaveLength(5);
    expect(screen.getByText('Base Variant')).toBeVisible();
    expect(screen.getByText('Variant 5')).toBeVisible();
    expect(add).toBeEnabled();
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
