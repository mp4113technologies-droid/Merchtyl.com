import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AdministrativeArea,
  AdministrativeAreaListResponse,
  AuthResponse,
  Country,
  CountryListResponse,
  CurrentUserResponse,
  Product,
  ProductListResponse,
  ProductTaxCategoryAssignment,
  ProductTaxCategoryAssignmentListResponse,
  TaxCategory,
  TaxCategoryListResponse,
  TaxComponent,
  TaxComponentListResponse,
  TaxGroup,
  TaxGroupComponent,
  TaxGroupComponentListResponse,
  TaxGroupListResponse,
  TaxJurisdiction,
  TaxJurisdictionListResponse,
  TaxRate,
  TaxRateListResponse,
  TaxRule,
  TaxRuleListResponse,
  TaxType,
  TaxTypeListResponse,
  UserRole
} from '../../api/types';

const countryId = '00000000-0000-0000-0000-000000001001';
const areaId = '00000000-0000-0000-0000-000000001002';
const jurisdictionId = '00000000-0000-0000-0000-000000001003';
const taxTypeId = '00000000-0000-0000-0000-000000001101';
const taxComponentId = '00000000-0000-0000-0000-000000001102';
const taxRateId = '00000000-0000-0000-0000-000000001103';
const taxGroupId = '00000000-0000-0000-0000-000000001201';
const taxGroupComponentId = '00000000-0000-0000-0000-000000001202';
const taxCategoryId = '00000000-0000-0000-0000-000000001203';
const productId = '00000000-0000-0000-0000-000000001301';
const assignmentId = '00000000-0000-0000-0000-000000001401';
const taxRuleId = '00000000-0000-0000-0000-000000001501';

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
    displayName: 'Tax User',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'user@example.local',
    displayName: 'Tax User',
    roles
  };
}

function country(overrides: Partial<Country> = {}): Country {
  return {
    id: countryId,
    code: 'CA',
    name: 'Canada',
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function area(overrides: Partial<AdministrativeArea> = {}): AdministrativeArea {
  return {
    id: areaId,
    countryId,
    code: 'NB',
    name: 'New Brunswick',
    type: 'PROVINCE',
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function jurisdiction(overrides: Partial<TaxJurisdiction> = {}): TaxJurisdiction {
  return {
    id: jurisdictionId,
    countryId,
    administrativeAreaId: null,
    code: 'GST',
    name: 'GST',
    type: 'NATIONAL',
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function taxType(overrides: Partial<TaxType> = {}): TaxType {
  return {
    id: taxTypeId,
    code: 'GST',
    name: 'GST',
    description: 'Federal tax',
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function taxComponent(overrides: Partial<TaxComponent> = {}): TaxComponent {
  return {
    id: taxComponentId,
    taxTypeId,
    taxJurisdictionId: jurisdictionId,
    code: 'GST',
    name: 'GST component',
    description: null,
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function taxRate(overrides: Partial<TaxRate> = {}): TaxRate {
  return {
    id: taxRateId,
    taxComponentId,
    percentageRate: 15,
    effectiveFrom: '2026-01-01',
    effectiveTo: '2026-12-31',
    includedInPrice: false,
    compoundOnPreviousTax: false,
    calculationOrder: 0,
    status: 'ACTIVE',
    source: 'Revenue bulletin',
    sourceReference: 'https://example.test/tax',
    verifiedBy: 'Tax Admin',
    verifiedAt: '2026-07-22T12:00:00Z',
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function taxGroup(overrides: Partial<TaxGroup> = {}): TaxGroup {
  return {
    id: taxGroupId,
    code: 'NB-HST',
    name: 'New Brunswick HST',
    description: 'Federal plus provincial tax',
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function taxGroupComponent(overrides: Partial<TaxGroupComponent> = {}): TaxGroupComponent {
  return {
    id: taxGroupComponentId,
    taxGroupId,
    taxComponentId,
    calculationOrder: 1,
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function taxCategory(overrides: Partial<TaxCategory> = {}): TaxCategory {
  return {
    id: taxCategoryId,
    taxGroupId,
    code: 'STANDARD',
    name: 'Standard taxable goods',
    treatment: 'STANDARD',
    description: 'Default taxable products',
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function product(overrides: Partial<Product> = {}): Product {
  return {
    id: productId,
    sku: 'SKU-100',
    name: 'Coffee beans',
    description: null,
    sellableType: 'STANDARD_PRODUCT',
    unitOfMeasureId: null,
    cost: 7,
    price: 12,
    categoryId: null,
    brandId: null,
    active: true,
    inventoryTrackingEnabled: true,
    decimalQuantityAllowed: false,
    imageUrl: null,
    taxCategoryId,
    variants: [],
    barcodes: [],
    capabilities: [],
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function assignment(overrides: Partial<ProductTaxCategoryAssignment> = {}): ProductTaxCategoryAssignment {
  return {
    id: assignmentId,
    productId,
    taxCategoryId,
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function taxRule(overrides: Partial<TaxRule> = {}): TaxRule {
  return {
    id: taxRuleId,
    code: 'STANDARD-POS',
    name: 'Standard POS tax',
    description: 'Apply standard tax at the register',
    priority: 10,
    effectiveFrom: '2026-01-01',
    effectiveTo: null,
    active: true,
    conditions: [
      {
        id: '00000000-0000-0000-0000-000000001502',
        conditionType: 'SALE_CHANNEL',
        operator: 'EQUALS',
        value: 'POS',
        secondValue: null
      }
    ],
    actions: [
      {
        id: '00000000-0000-0000-0000-000000001503',
        actionType: 'APPLY_TAX_GROUP',
        taxGroupId,
        taxComponentId: null,
        value: null
      }
    ],
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0,
    ...overrides
  };
}

function countryPage(content: Country[], overrides: Partial<CountryListResponse> = {}): CountryListResponse {
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

function areaPage(content: AdministrativeArea[], overrides: Partial<AdministrativeAreaListResponse> = {}): AdministrativeAreaListResponse {
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

function jurisdictionPage(content: TaxJurisdiction[], overrides: Partial<TaxJurisdictionListResponse> = {}): TaxJurisdictionListResponse {
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

function taxTypePage(content: TaxType[], overrides: Partial<TaxTypeListResponse> = {}): TaxTypeListResponse {
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

function taxComponentPage(content: TaxComponent[], overrides: Partial<TaxComponentListResponse> = {}): TaxComponentListResponse {
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

function taxRatePage(content: TaxRate[], overrides: Partial<TaxRateListResponse> = {}): TaxRateListResponse {
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

function taxGroupPage(content: TaxGroup[], overrides: Partial<TaxGroupListResponse> = {}): TaxGroupListResponse {
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

function taxGroupComponentPage(content: TaxGroupComponent[], overrides: Partial<TaxGroupComponentListResponse> = {}): TaxGroupComponentListResponse {
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

function taxCategoryPage(content: TaxCategory[], overrides: Partial<TaxCategoryListResponse> = {}): TaxCategoryListResponse {
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

function productPage(content: Product[], overrides: Partial<ProductListResponse> = {}): ProductListResponse {
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

function assignmentPage(content: ProductTaxCategoryAssignment[], overrides: Partial<ProductTaxCategoryAssignmentListResponse> = {}): ProductTaxCategoryAssignmentListResponse {
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

function taxRulePage(content: TaxRule[], overrides: Partial<TaxRuleListResponse> = {}): TaxRuleListResponse {
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
    path: '/api/v1/tax',
    method: 'GET',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

describe('Tax geography pages', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('renders countries, applies search, and creates a country', async () => {
    storeSession(['OWNER']);
    const created = country({
      id: '00000000-0000-0000-0000-000000001004',
      code: 'US',
      name: 'United States'
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/tax/countries') && init?.method === 'POST') {
        return jsonResponse(created, 201);
      }
      if (url.pathname.endsWith('/api/v1/tax/countries')) {
        return jsonResponse(countryPage([country()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/countries']} />);

    expect(await screen.findByRole('heading', { name: 'Countries' })).toBeInTheDocument();
    expect(await screen.findByText('Canada')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Name'), 'can');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/tax/countries') && url.searchParams.get('name') === 'can';
      })).toBe(true);
    });

    await userEvent.click(screen.getByRole('button', { name: 'New country' }));
    const dialog = await screen.findByRole('dialog', { name: 'New country' });
    await userEvent.type(within(dialog).getByLabelText('Code'), 'us');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'United States');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create country' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/tax/countries') && init?.method === 'POST';
      })).toBe(true);
    });
  });

  it('redirects cashier users away from tax pages', async () => {
    storeSession(['CASHIER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/countries']} />);

    expect(await screen.findByRole('heading', { name: "You don't have access to this feature" })).toBeInTheDocument();
  });

  it('creates an administrative area with a selected country', async () => {
    storeSession(['MANAGER']);
    const created = area({
      id: '00000000-0000-0000-0000-000000001005',
      code: 'ON',
      name: 'Ontario'
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/tax/countries')) {
        return jsonResponse(countryPage([country()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/administrative-areas') && init?.method === 'POST') {
        return jsonResponse(created, 201);
      }
      if (url.pathname.endsWith('/api/v1/tax/administrative-areas')) {
        return jsonResponse(areaPage([area()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/administrative-areas']} />);

    expect(await screen.findByRole('heading', { name: 'Administrative areas' })).toBeInTheDocument();
    expect(await screen.findByText('New Brunswick')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New area' }));
    const dialog = await screen.findByRole('dialog', { name: 'New administrative area' });
    await userEvent.type(within(dialog).getByLabelText('Code'), 'on');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Ontario');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create area' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        if (!url.pathname.endsWith('/api/v1/tax/administrative-areas') || init?.method !== 'POST') {
          return false;
        }
        const body = JSON.parse(String(init.body));
        return body.countryId === countryId && body.code === 'ON';
      })).toBe(true);
    });
  });

  it('validates national jurisdictions and toggles jurisdiction status', async () => {
    storeSession(['OWNER']);
    const provincial = jurisdiction({
      administrativeAreaId: areaId,
      code: 'NB-HST',
      name: 'New Brunswick HST',
      type: 'PROVINCIAL'
    });
    let current = provincial;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/tax/countries')) {
        return jsonResponse(countryPage([country()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/administrative-areas')) {
        return jsonResponse(areaPage([area()]));
      }
      if (url.pathname.endsWith(`/api/v1/tax/jurisdictions/${current.id}/status`) && init?.method === 'PATCH') {
        current = { ...current, active: false, version: 1 };
        return jsonResponse(current);
      }
      if (url.pathname.endsWith('/api/v1/tax/jurisdictions')) {
        return jsonResponse(jurisdictionPage([current]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/jurisdictions']} />);

    expect(await screen.findByRole('heading', { name: 'Tax jurisdictions' })).toBeInTheDocument();
    expect(await screen.findByText('New Brunswick HST')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'New jurisdiction' }));
    const dialog = await screen.findByRole('dialog', { name: 'New tax jurisdiction' });
    await userEvent.click(within(dialog).getByLabelText('Administrative area'));
    await userEvent.click(await screen.findByRole('option', { name: 'NB - New Brunswick' }));
    await userEvent.type(within(dialog).getByLabelText('Code'), 'gst');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'GST');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create jurisdiction' }));
    expect(await screen.findByText('National jurisdictions cannot use an administrative area')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    await userEvent.click(await screen.findByRole('button', { name: 'Deactivate New Brunswick HST' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => String(input).endsWith('/status') && init?.method === 'PATCH')).toBe(true);
    });
  });

  it('renders tax types, applies search, and creates a type', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/tax/types') && init?.method === 'POST') {
        return jsonResponse(taxType({ code: 'HST', name: 'HST' }), 201);
      }
      if (url.pathname.endsWith('/api/v1/tax/types')) {
        return jsonResponse(taxTypePage([taxType()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/types']} />);

    expect(await screen.findByRole('heading', { name: 'Tax types' })).toBeInTheDocument();
    expect(await screen.findByText('Federal tax')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Name'), 'gst');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/tax/types') && url.searchParams.get('name') === 'gst';
      })).toBe(true);
    });

    await userEvent.click(screen.getByRole('button', { name: 'New type' }));
    const dialog = await screen.findByRole('dialog', { name: 'New tax type' });
    await userEvent.type(within(dialog).getByLabelText('Code'), 'hst');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'HST');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create type' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/tax/types') && init?.method === 'POST';
      })).toBe(true);
    });
  });

  it('creates a tax component using tax type and jurisdiction options', async () => {
    storeSession(['MANAGER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/tax/types')) {
        return jsonResponse(taxTypePage([taxType()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/jurisdictions')) {
        return jsonResponse(jurisdictionPage([jurisdiction()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/components') && init?.method === 'POST') {
        return jsonResponse(taxComponent({ code: 'HST', name: 'HST component' }), 201);
      }
      if (url.pathname.endsWith('/api/v1/tax/components')) {
        return jsonResponse(taxComponentPage([taxComponent()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/components']} />);

    expect(await screen.findByRole('heading', { name: 'Tax components' })).toBeInTheDocument();
    expect(await screen.findByText('GST component')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New component' }));
    const dialog = await screen.findByRole('dialog', { name: 'New tax component' });
    await userEvent.type(within(dialog).getByLabelText('Code'), 'hst');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'HST component');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create component' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        if (!url.pathname.endsWith('/api/v1/tax/components') || init?.method !== 'POST') {
          return false;
        }
        const body = JSON.parse(String(init.body));
        return body.taxTypeId === taxTypeId && body.taxJurisdictionId === jurisdictionId && body.code === 'HST';
      })).toBe(true);
    });
  });

  it('validates, creates, and changes status for tax rates', async () => {
    storeSession(['OWNER']);
    const currentRate = taxRate();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/tax/components')) {
        return jsonResponse(taxComponentPage([taxComponent()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/rates') && init?.method === 'POST') {
        return jsonResponse(taxRate({ status: 'SCHEDULED' }), 201);
      }
      if (url.pathname.endsWith(`/api/v1/tax/rates/${taxRateId}/status`) && init?.method === 'PATCH') {
        return jsonResponse(taxRate({ status: 'RETIRED', version: 1 }));
      }
      if (url.pathname.endsWith('/api/v1/tax/rates')) {
        return jsonResponse(taxRatePage([currentRate]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/rates']} />);

    expect(await screen.findByRole('heading', { name: 'Tax rates' })).toBeInTheDocument();
    expect(await screen.findByText('15.000000%')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New rate' }));
    const dialog = await screen.findByRole('dialog', { name: 'New tax rate' });
    await userEvent.type(within(dialog).getByLabelText('Effective from'), '2026-12-31');
    await userEvent.type(within(dialog).getByLabelText('Effective to'), '2026-01-01');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create rate' }));
    expect(await screen.findByText('Effective to must be on or after effective from')).toBeInTheDocument();

    await userEvent.clear(within(dialog).getByLabelText('Effective from'));
    await userEvent.clear(within(dialog).getByLabelText('Effective to'));
    await userEvent.type(within(dialog).getByLabelText('Effective from'), '2027-01-01');
    await userEvent.type(within(dialog).getByLabelText('Percentage rate'), '8.25');
    await userEvent.click(within(dialog).getByLabelText('Status'));
    await userEvent.click(await screen.findByRole('option', { name: 'Scheduled' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create rate' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/tax/rates') && init?.method === 'POST';
      })).toBe(true);
    });
    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'New tax rate' })).not.toBeInTheDocument();
    });

    const rateRow = screen.getByRole('row', { name: /GST 15\.000000%/ });
    fireEvent.mouseDown(within(rateRow).getByRole('combobox'));
    await userEvent.click(await screen.findByRole('option', { name: 'Retired' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => String(input).endsWith('/status') && init?.method === 'PATCH')).toBe(true);
    });
  });

  it('renders tax groups, applies search, and creates a group', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/tax/groups') && init?.method === 'POST') {
        return jsonResponse(taxGroup({ code: 'PEI-HST', name: 'Prince Edward Island HST' }), 201);
      }
      if (url.pathname.endsWith('/api/v1/tax/groups')) {
        return jsonResponse(taxGroupPage([taxGroup()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/groups']} />);

    expect(await screen.findByRole('heading', { name: 'Tax groups' })).toBeInTheDocument();
    expect(await screen.findByText('Federal plus provincial tax')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Name'), 'hst');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/tax/groups') && url.searchParams.get('name') === 'hst';
      })).toBe(true);
    });

    await userEvent.click(screen.getByRole('button', { name: 'New group' }));
    const dialog = await screen.findByRole('dialog', { name: 'New tax group' });
    await userEvent.type(within(dialog).getByLabelText('Code'), 'pei-hst');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Prince Edward Island HST');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create group' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        if (!url.pathname.endsWith('/api/v1/tax/groups') || init?.method !== 'POST') {
          return false;
        }
        const body = JSON.parse(String(init.body));
        return body.code === 'PEI-HST' && body.name === 'Prince Edward Island HST';
      })).toBe(true);
    });
  });

  it('creates a tax group component using group and component options', async () => {
    storeSession(['MANAGER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['MANAGER']));
      }
      if (url.pathname.endsWith('/api/v1/tax/groups')) {
        return jsonResponse(taxGroupPage([taxGroup()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/components')) {
        return jsonResponse(taxComponentPage([taxComponent()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/group-components') && init?.method === 'POST') {
        return jsonResponse(taxGroupComponent({ calculationOrder: 2 }), 201);
      }
      if (url.pathname.endsWith('/api/v1/tax/group-components')) {
        return jsonResponse(taxGroupComponentPage([taxGroupComponent()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/group-components']} />);

    expect(await screen.findByRole('heading', { name: 'Tax group components' })).toBeInTheDocument();
    expect(await screen.findByRole('cell', { name: 'NB-HST' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New group component' }));
    const dialog = await screen.findByRole('dialog', { name: 'New group component' });
    await userEvent.clear(within(dialog).getByLabelText('Calculation order'));
    await userEvent.type(within(dialog).getByLabelText('Calculation order'), '2');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create group component' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        if (!url.pathname.endsWith('/api/v1/tax/group-components') || init?.method !== 'POST') {
          return false;
        }
        const body = JSON.parse(String(init.body));
        return body.taxGroupId === taxGroupId && body.taxComponentId === taxComponentId && body.calculationOrder === 2;
      })).toBe(true);
    });
  });

  it('creates a tax category with treatment and optional tax group', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/tax/groups')) {
        return jsonResponse(taxGroupPage([taxGroup()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/categories') && init?.method === 'POST') {
        return jsonResponse(taxCategory({ code: 'EXEMPT', name: 'Exempt products', treatment: 'EXEMPT', taxGroupId: null }), 201);
      }
      if (url.pathname.endsWith('/api/v1/tax/categories')) {
        return jsonResponse(taxCategoryPage([taxCategory()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/categories']} />);

    expect(await screen.findByRole('heading', { name: 'Tax categories' })).toBeInTheDocument();
    expect(await screen.findByText('Standard taxable goods')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New category' }));
    const dialog = await screen.findByRole('dialog', { name: 'New tax category' });
    await userEvent.click(within(dialog).getByLabelText('Tax group'));
    await userEvent.click(await screen.findByRole('option', { name: 'None' }));
    await userEvent.type(within(dialog).getByLabelText('Code'), 'exempt');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Exempt products');
    await userEvent.click(within(dialog).getByLabelText('Treatment'));
    await userEvent.click(await screen.findByRole('option', { name: 'Exempt' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create category' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        if (!url.pathname.endsWith('/api/v1/tax/categories') || init?.method !== 'POST') {
          return false;
        }
        const body = JSON.parse(String(init.body));
        return body.code === 'EXEMPT' && body.treatment === 'EXEMPT' && body.taxGroupId === undefined;
      })).toBe(true);
    });
  });

  it('creates and deactivates product tax category assignments', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/products')) {
        return jsonResponse(productPage([product()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/categories')) {
        return jsonResponse(taxCategoryPage([taxCategory()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/product-category-assignments') && init?.method === 'POST') {
        return jsonResponse(assignment(), 201);
      }
      if (url.pathname.endsWith(`/api/v1/tax/product-category-assignments/${assignmentId}/status`) && init?.method === 'PATCH') {
        return jsonResponse(assignment({ active: false, version: 1 }));
      }
      if (url.pathname.endsWith('/api/v1/tax/product-category-assignments')) {
        return jsonResponse(assignmentPage([assignment()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/product-category-assignments']} />);

    expect(await screen.findByRole('heading', { name: 'Product tax assignments' })).toBeInTheDocument();
    expect(await screen.findByRole('cell', { name: 'SKU-100' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'New assignment' }));
    const dialog = await screen.findByRole('dialog', { name: 'New product tax assignment' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create assignment' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        if (!url.pathname.endsWith('/api/v1/tax/product-category-assignments') || init?.method !== 'POST') {
          return false;
        }
        const body = JSON.parse(String(init.body));
        return body.productId === productId && body.taxCategoryId === taxCategoryId;
      })).toBe(true);
    });

    await userEvent.click(await screen.findByRole('button', { name: 'Deactivate SKU-100 STANDARD' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => String(input).endsWith('/status') && init?.method === 'PATCH')).toBe(true);
    });
  });

  it('renders tax rules, applies search, and creates a prioritized rule', async () => {
    storeSession(['OWNER']);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/tax/groups')) {
        return jsonResponse(taxGroupPage([taxGroup()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/components')) {
        return jsonResponse(taxComponentPage([taxComponent()]));
      }
      if (url.pathname.endsWith('/api/v1/tax/rules') && init?.method === 'POST') {
        return jsonResponse(taxRule({ code: 'KIOSK-STANDARD', name: 'Kiosk standard tax' }), 201);
      }
      if (url.pathname.endsWith('/api/v1/tax/rules')) {
        return jsonResponse(taxRulePage([taxRule()]));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/tax/rules']} />);

    expect(await screen.findByRole('heading', { name: 'Tax rules' })).toBeInTheDocument();
    expect(await screen.findByText('Standard POS tax')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Code'), 'standard');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/tax/rules') && url.searchParams.get('code') === 'standard';
      })).toBe(true);
    });

    await userEvent.click(screen.getByRole('button', { name: 'New rule' }));
    const dialog = await screen.findByRole('dialog', { name: 'New tax rule' });
    await userEvent.type(within(dialog).getByLabelText('Code'), 'kiosk-standard');
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Kiosk standard tax');
    await userEvent.type(within(dialog).getByLabelText('Priority'), '5');
    await userEvent.type(within(dialog).getByLabelText('Effective from'), '2026-01-01');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add condition' }));
    await userEvent.type(within(dialog).getAllByLabelText('Value')[0], 'KIOSK');
    await userEvent.click(within(dialog).getByLabelText('Tax group'));
    await userEvent.click(await screen.findByRole('option', { name: 'NB-HST - New Brunswick HST' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create rule' }));

    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        if (!url.pathname.endsWith('/api/v1/tax/rules') || init?.method !== 'POST') {
          return false;
        }
        const body = JSON.parse(String(init.body));
        return body.code === 'KIOSK-STANDARD'
          && body.priority === 5
          && body.conditions[0].conditionType === 'SALE_CHANNEL'
          && body.conditions[0].value === 'KIOSK'
          && body.actions[0].taxGroupId === taxGroupId;
      })).toBe(true);
    });
  });
});
