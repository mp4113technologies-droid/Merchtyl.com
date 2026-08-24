import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type { TaxCalculationPayload } from '../../api/client';
import type {
  AuthResponse,
  CurrentUserResponse,
  Product,
  ProductListResponse,
  Store,
  StoreListResponse,
  TaxCalculation,
  TaxCategory,
  TaxCategoryListResponse,
  TaxJurisdiction,
  TaxJurisdictionListResponse,
  UserRole
} from '../../api/types';

const storeId = '00000000-0000-0000-0000-000000000601';
const jurisdictionId = '10000000-0000-0000-0000-000000000204';
const productId = '00000000-0000-0000-0000-000000001301';
const taxCategoryId = '10000000-0000-0000-0000-000000000801';
const taxGroupId = '10000000-0000-0000-0000-000000000604';

function authResponse(roles: UserRole[] = ['OWNER']): AuthResponse {
  const now = Date.now();
  return {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: new Date(now + 15 * 60_000).toISOString(),
    refreshTokenExpiresAt: new Date(now + 7 * 24 * 60 * 60_000).toISOString(),
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'tax@example.local',
    displayName: 'Tax Tester',
    roles
  };
}

function currentUser(roles: UserRole[] = ['OWNER']): CurrentUserResponse {
  return {
    userId: '00000000-0000-0000-0000-000000000201',
    email: 'tax@example.local',
    displayName: 'Tax Tester',
    roles
  };
}

function store(): Store {
  return {
    id: storeId,
    code: 'NB-001',
    name: 'Moncton Store',
    legalName: null,
    countryCode: 'CA',
    administrativeAreaCode: 'NB',
    address: '100 Main Street',
    phone: null,
    email: null,
    currencyCode: 'CAD',
    locale: 'en-CA',
    timezone: 'America/Moncton',
    pricesIncludeTax: false,
    negativeStockAllowed: false,
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0
  };
}

function jurisdiction(): TaxJurisdiction {
  return {
    id: jurisdictionId,
    countryId: '10000000-0000-0000-0000-000000000001',
    administrativeAreaId: '10000000-0000-0000-0000-000000000104',
    code: 'CA-NB',
    name: 'New Brunswick tax jurisdiction',
    type: 'PROVINCIAL',
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0
  };
}

function taxCategory(): TaxCategory {
  return {
    id: taxCategoryId,
    taxGroupId: null,
    code: 'STANDARD',
    name: 'Standard taxable',
    treatment: 'STANDARD',
    description: null,
    active: true,
    createdAt: '2026-07-22T12:00:00Z',
    updatedAt: '2026-07-22T12:00:00Z',
    version: 0
  };
}

function product(): Product {
  return {
    id: productId,
    sku: 'COF-001',
    name: 'Coffee beans',
    description: null,
    sellableType: 'STANDARD_PRODUCT',
    unitOfMeasureId: null,
    cost: 7,
    price: 20,
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
    version: 0
  };
}

function page<T>(content: T[]): {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
} {
  return {
    content,
    page: 0,
    size: 100,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    first: true,
    last: true
  };
}

function calculationResponse(): TaxCalculation {
  return {
    storeId,
    storeJurisdictionId: null,
    supplyJurisdictionId: jurisdictionId,
    productId,
    productTaxCategoryId: taxCategoryId,
    transactionDate: '2026-07-22',
    saleChannel: 'POS',
    currencyCode: 'CAD',
    quantity: 2,
    unitPrice: 20,
    discountAmount: 5,
    pricesIncludeTax: false,
    netAmount: 35,
    taxAmount: 5.25,
    grossAmount: 40.25,
    zeroRated: false,
    exempt: false,
    outOfScope: false,
    includedPriceBehavior: 'USE_RATE_SETTING',
    roundingStrategy: 'HALF_UP',
    components: [
      {
        taxComponentId: '10000000-0000-0000-0000-000000000404',
        taxComponentCode: 'CA_NB_HST',
        taxComponentName: 'New Brunswick HST',
        taxRateId: '10000000-0000-0000-0000-000000000504',
        percentageRate: 15,
        taxableAmount: 35,
        taxAmount: 5.25,
        includedInPrice: false,
        compoundOnPreviousTax: false,
        calculationOrder: 0,
        effectiveFrom: '2026-01-01',
        effectiveTo: null,
        explanation: 'CA_NB_HST used 15% on 35.00, added to price, producing 5.25.'
      }
    ],
    explanations: [
      'Calculated tax for 2 unit(s) at 20 CAD.',
      'Applied discount 5 CAD before tax.',
      'Rounding strategy: HALF_UP.',
      'Net 35.00, tax 5.25, gross 40.25.',
      'Rule CA_NB_STANDARD matched at priority 100.'
    ],
    ruleEvaluation: {
      appliedTaxGroupIds: [taxGroupId],
      appliedTaxComponentIds: [],
      excludedTaxComponentIds: [],
      zeroRated: false,
      exempt: false,
      outOfScope: false,
      includedPriceBehavior: 'USE_RATE_SETTING',
      roundingStrategy: 'HALF_UP',
      ruleMatches: [
        {
          ruleId: '10000000-0000-0000-0000-000000000904',
          code: 'CA_NB_STANDARD',
          name: 'Apply New Brunswick HST',
          priority: 100,
          matched: true,
          conditions: [],
          actions: [],
          explanation: 'Rule matched all conditions'
        }
      ]
    }
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
    path: '/api/v1/tax/calculate',
    method: 'POST',
    correlationId: 'test-correlation',
    violations: [],
    timestamp: new Date().toISOString()
  }, status);
}

function storeSession(roles: UserRole[] = ['OWNER']) {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse(roles)));
}

async function choose(label: string, option: string) {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: label }));
  await userEvent.click(await screen.findByRole('option', { name: option }));
}

describe('Tax simulator page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('runs a tax simulation and displays matched rules, rates, taxable amount, rounding, total, and explanation', async () => {
    storeSession(['OWNER']);
    const calculationBodies: TaxCalculationPayload[] = [];
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['OWNER']));
      }
      if (url.pathname.endsWith('/api/v1/stores')) {
        return jsonResponse(page<Store>([store()]) satisfies StoreListResponse);
      }
      if (url.pathname.endsWith('/api/v1/tax/jurisdictions')) {
        return jsonResponse(page<TaxJurisdiction>([jurisdiction()]) satisfies TaxJurisdictionListResponse);
      }
      if (url.pathname.endsWith('/api/v1/products')) {
        return jsonResponse(page<Product>([product()]) satisfies ProductListResponse);
      }
      if (url.pathname.endsWith('/api/v1/tax/categories')) {
        return jsonResponse(page<TaxCategory>([taxCategory()]) satisfies TaxCategoryListResponse);
      }
      if (url.pathname.endsWith('/api/v1/tax/calculate') && init?.method === 'POST') {
        calculationBodies.push(JSON.parse(String(init.body)) as TaxCalculationPayload);
        return jsonResponse(calculationResponse());
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/settings/taxes/test']} />);

    expect(await screen.findByRole('heading', { name: 'Tax Simulator' })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Loading simulator inputs')).not.toBeInTheDocument());

    await choose('Store', 'NB-001 - Moncton Store');
    await choose('Jurisdiction', 'CA-NB - New Brunswick tax jurisdiction');
    await choose('Product', 'COF-001 - Coffee beans');
    await waitFor(() => expect(screen.getByLabelText('Tax category')).toHaveTextContent('STANDARD - Standard taxable'));
    await waitFor(() => expect(screen.getByLabelText('Price')).toHaveValue(20));
    await userEvent.clear(screen.getByLabelText('Quantity'));
    await userEvent.type(screen.getByLabelText('Quantity'), '2');
    await userEvent.clear(screen.getByLabelText('Discount'));
    await userEvent.type(screen.getByLabelText('Discount'), '5');
    fireEvent.submit(screen.getByRole('form', { name: 'Tax simulator form' }));

    await waitFor(() => expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith('/api/v1/tax/calculate') && init?.method === 'POST';
    })).toBe(true));
    expect(calculationBodies.at(-1)).toEqual(expect.objectContaining({
      storeId,
      supplyJurisdictionId: jurisdictionId,
      productTaxCategoryId: taxCategoryId,
      quantity: 2,
      unitPrice: 20,
      discountAmount: 5
    }));

    expect(await screen.findByText('CA_NB_HST')).toBeInTheDocument();
    expect(screen.getByText('CA_NB_STANDARD')).toBeInTheDocument();
    expect(screen.getByText('HALF UP')).toBeInTheDocument();
    expect(screen.getAllByText('$35.00')).not.toHaveLength(0);
    expect(screen.getByText('$40.25')).toBeInTheDocument();
    expect(screen.getByText('Applied discount 5 CAD before tax.')).toBeInTheDocument();
  });

  it('redirects cashier users away from the simulator', async () => {
    storeSession(['CASHIER']);
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser(['CASHIER']));
      }
      return apiError('Unexpected request');
    });

    render(<App initialEntries={['/settings/taxes/test']} />);

    expect(await screen.findByRole('heading', { name: 'Unauthorized' })).toBeInTheDocument();
  });
});
