import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import { PaymentDialog } from './PosPages';
import { applicationDeviceIdentifierKey } from '../../app/deviceIdentity';
import { clearDraftCartRecovery, loadDraftCartRecovery, saveDraftCartRecovery } from './draftCartRecovery';
import { barcodeScannerPreferencesKey, defaultBarcodeScannerPreferences } from '../hardware/barcodeScanner';
import type {
  AuthResponse,
  CurrentUserResponse,
  Device,
  Payment,
  PaymentMethod,
  Product,
  Receipt,
  Register,
  RegisterSession,
  Sale,
  Store,
  UserRole
} from '../../api/types';

const storeId = '00000000-0000-0000-0000-000000000901';
const registerId = '00000000-0000-0000-0000-000000000902';
const deviceId = '00000000-0000-0000-0000-000000000903';
const cashierId = '00000000-0000-0000-0000-000000000904';
const sessionId = '00000000-0000-0000-0000-000000000900';
const saleId = '00000000-0000-0000-0000-000000000910';
const itemId = '00000000-0000-0000-0000-000000000911';
const productId = '00000000-0000-0000-0000-000000000920';
const debitPaymentId = '00000000-0000-0000-0000-000000000930';
const cashPaymentId = '00000000-0000-0000-0000-000000000931';

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

function store(): Store {
  return {
    id: storeId,
    code: 'MAIN',
    name: 'Main Store',
    legalName: 'Main Store LLC',
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
    createdAt: '2026-07-21T12:00:00Z',
    updatedAt: '2026-07-21T12:00:00Z',
    version: 0
  };
}

function register(): Register {
  return {
    id: registerId,
    storeId,
    code: 'FRONT-1',
    name: 'Front Register',
    locationDescription: 'Front counter',
    active: true,
    createdAt: '2026-07-21T12:00:00Z',
    updatedAt: '2026-07-21T12:00:00Z',
    version: 0
  };
}

function device(): Device {
  return {
    id: deviceId,
    storeId,
    registerId,
    deviceIdentifier: 'browser:test-device',
    displayName: 'Front Browser',
    deviceType: 'BROWSER_POS',
    registeredAt: '2026-07-21T12:00:00Z',
    lastSeenAt: '2026-07-21T12:00:00Z',
    active: true,
    version: 0
  };
}

function registerSession(): RegisterSession {
  return {
    id: sessionId,
    storeId,
    registerId,
    deviceId,
    assignedCashierId: cashierId,
    assignedCashierEmail: 'cashier@example.local',
    assignedCashierDisplayName: 'Cashier One',
    status: 'OPEN',
    openingCash: 100,
    expectedCash: 100,
    countedCash: null,
    expectedCashAtClose: null,
    differenceCash: null,
    closedByUserId: null,
    closedByEmail: null,
    closedByDisplayName: null,
    closedAt: null,
    forceCloseReason: null,
    reconciliation: null,
    openedAt: '2026-07-21T12:00:00Z',
    createdAt: '2026-07-21T12:00:00Z',
    updatedAt: '2026-07-21T12:00:00Z',
    version: 0
  };
}

function product(): Product {
  return {
    id: productId,
    sku: 'COFFEE',
    name: 'Coffee',
    description: null,
    sellableType: 'STANDARD_PRODUCT',
    unitOfMeasureId: null,
    cost: 2,
    price: 5,
    categoryId: null,
    brandId: null,
    active: true,
    inventoryTrackingEnabled: true,
    decimalQuantityAllowed: false,
    imageUrl: null,
    taxCategoryId: null,
    variants: [],
    barcodes: [{
      id: '00000000-0000-0000-0000-000000000921',
      barcode: '12345',
      variantId: null,
      variantSku: null,
      primaryBarcode: true,
      active: true,
      createdAt: '2026-07-21T12:00:00Z',
      updatedAt: '2026-07-21T12:00:00Z',
      version: 0
    }],
    capabilities: ['TRACK_INVENTORY'],
    createdAt: '2026-07-21T12:00:00Z',
    updatedAt: '2026-07-21T12:00:00Z',
    version: 0
  };
}

function sale(status: Sale['status'] = 'DRAFT', quantity = 1): Sale {
  const lineSubtotal = 5 * quantity;
  const estimatedTaxAmount = 0.75 * quantity;
  const lineTotal = lineSubtotal + estimatedTaxAmount;
  return {
    id: saleId,
    storeId,
    registerId,
    registerSessionId: sessionId,
    createdBy: cashierId,
    customerId: null,
    status,
    businessDate: '2026-07-21',
    saleChannel: 'POS',
    currencyCode: 'USD',
    pricesIncludeTax: false,
    subtotalAmount: lineSubtotal,
    discountAmount: 0,
    estimatedTaxAmount,
    totalAmount: lineTotal,
    heldAt: status === 'HELD' ? '2026-07-21T12:10:00Z' : null,
    cancelledAt: null,
    completedBy: null,
    completedAt: null,
    items: [{
      id: itemId,
      productId,
      lineNumber: 1,
      productSku: 'COFFEE',
      productName: 'Coffee',
      quantity,
      unitPrice: 5,
      discountAmount: 0,
      completedProductCost: null,
      completedProductPrice: null,
      completedProductCapabilities: null,
      priceOverride: false,
      ageVerified: false,
      serialNumber: null,
      externalReference: null,
      customerId: null,
      paymentMethodCode: null,
      lineSubtotal,
      estimatedTaxAmount,
      lineTotal,
      version: 0
    }],
    payments: [],
    paidAmount: 0,
    balanceDue: lineTotal,
    changeDue: 0,
    paymentComplete: false,
    createdAt: '2026-07-21T12:00:00Z',
    updatedAt: '2026-07-21T12:05:00Z',
    version: status === 'HELD' ? 1 : 0
  };
}

function payment(method: PaymentMethod, amount: number, cashTendered: number | null = null, changeDue = 0, id = debitPaymentId): Payment {
  return {
    id,
    method,
    amount,
    currencyCode: 'USD',
    cashTendered,
    changeDue,
    reference: null,
    notes: null,
    createdBy: cashierId,
    completedAt: '2026-07-21T12:06:00Z',
    createdAt: '2026-07-21T12:06:00Z',
    version: 0
  };
}

function saleWithPayments(payments: Payment[], status: Sale['status'] = 'DRAFT'): Sale {
  const base = sale(status);
  const paidAmount = payments.reduce((total, item) => total + item.amount, 0);
  const changeDue = payments.reduce((total, item) => total + item.changeDue, 0);
  const completed = status === 'COMPLETED';
  return {
    ...base,
    status,
    payments,
    paidAmount,
    balanceDue: Math.max(0, Number((base.totalAmount - paidAmount).toFixed(2))),
    changeDue,
    paymentComplete: paidAmount >= base.totalAmount,
    completedBy: completed ? cashierId : null,
    completedAt: completed ? '2026-07-21T12:08:00Z' : null,
    items: base.items.map((item) => completed ? {
      ...item,
      completedProductCost: 2,
      completedProductPrice: 5,
      completedProductCapabilities: 'TRACK_INVENTORY'
    } : item)
  };
}

function receipt(reprintCount = 0): Receipt {
  return {
    id: '00000000-0000-0000-0000-000000000940',
    saleId,
    receiptNumber: 'RCT-2026-07-21-00000000',
    generatedAt: '2026-07-21T12:08:00Z',
    reprintCount,
    lastReprintedAt: reprintCount > 0 ? '2026-07-21T12:09:00Z' : null,
    createdAt: '2026-07-21T12:08:00Z',
    updatedAt: '2026-07-21T12:08:00Z',
    version: reprintCount,
    document: {
      brandName: 'Merchtyl',
      brandTagline: 'Point of sale receipt',
      store: {
        id: storeId,
        code: 'MAIN',
        name: 'Main Store',
        legalName: null,
        address: '100 Market Street',
        phone: null,
        email: null
      },
      register: {
        id: registerId,
        code: 'FRONT-1',
        name: 'Front Register'
      },
      cashier: {
        id: cashierId,
        displayName: 'Cashier One',
        email: 'cashier@example.local'
      },
      receiptNumber: 'RCT-2026-07-21-00000000',
      saleId,
      saleNumber: saleId,
      businessDate: '2026-07-21',
      completedAt: '2026-07-21T12:08:00Z',
      currencyCode: 'USD',
      items: [{
        id: itemId,
        productId,
        lineNumber: 1,
        productSku: 'COFFEE',
        productName: 'Coffee',
        quantity: 1,
        unitPrice: 5,
        completedProductCost: 2,
        completedProductPrice: 5,
        completedProductCapabilities: 'TRACK_INVENTORY',
        discountAmount: 0,
        lineSubtotal: 5,
        taxAmount: 0.75,
        lineTotal: 5.75
      }],
      subtotalAmount: 5,
      discountAmount: 0,
      taxSummaries: [{ componentCode: 'TAX', componentName: 'Sales tax', taxableAmount: 5, taxAmount: 0.75 }],
      taxAmount: 0.75,
      totalAmount: 5.75,
      payments: [{
        id: cashPaymentId,
        method: 'CASH',
        amount: 5.75,
        cashTendered: 10,
        changeDue: 4.25,
        reference: null,
        completedAt: '2026-07-21T12:07:00Z'
      }],
      cashTendered: 10,
      changeDue: 4.25
    }
  };
}

function emptySale(): Sale {
  return {
    ...sale('DRAFT'),
    subtotalAmount: 0,
    estimatedTaxAmount: 0,
    totalAmount: 0,
    items: []
  };
}

function page<T>(content: T[]) {
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

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  }));
}

function deferredJson(body: unknown) {
  let resolve!: () => void;
  const ready = new Promise<void>((done) => {
    resolve = done;
  });
  return {
    resolve,
    response: ready.then(() => new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    }))
  };
}

function storeSession() {
  window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse()));
  window.localStorage.setItem(applicationDeviceIdentifierKey, 'browser:test-device');
}

function commonApi(input: RequestInfo | URL) {
  const url = new URL(String(input), window.location.origin);
  if (url.pathname.endsWith('/api/v1/auth/me')) {
    return jsonResponse(currentUser());
  }
  if (url.pathname.endsWith('/api/v1/register-sessions/current')) {
    return jsonResponse(registerSession());
  }
  if (url.pathname.endsWith('/api/v1/stores')) {
    return jsonResponse(page([store()]));
  }
  if (url.pathname.endsWith('/api/v1/registers')) {
    return jsonResponse(page([register()]));
  }
  if (url.pathname.endsWith('/api/v1/devices')) {
    return jsonResponse(page([device()]));
  }
  return null;
}

describe('POS pages', () => {
  beforeEach(async () => {
    await clearDraftCartRecovery();
    window.localStorage.clear();
    storeSession();
    vi.restoreAllMocks();
  });

  it('starts cash at zero and accumulates CAD denominations before recording only the applied balance', async () => {
    const onSubmit = vi.fn();
    const cadSale = { ...sale(), currencyCode: 'CAD', subtotalAmount: 46.20, estimatedTaxAmount: 5.55, totalAmount: 51.75, balanceDue: 51.75 };
    render(<PaymentDialog open sale={cadSale} busy={false} onClose={vi.fn()} onSubmit={onSubmit} />);

    expect(screen.getByRole('button', { name: 'Cash' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('textbox', { name: 'Cash received' })).toHaveValue('0.00');
    expect(screen.getByRole('button', { name: 'Record payment' })).toBeDisabled();
    for (const denomination of ['$100', '$50', '$20', '$10', '$5', '$2', '$1', '25¢', '10¢', '5¢']) {
      expect(screen.getByRole('button', { name: denomination })).toBeVisible();
    }
    await userEvent.click(screen.getByRole('button', { name: '$20' }));
    await userEvent.click(screen.getByRole('button', { name: '$20' }));
    await userEvent.click(screen.getByRole('button', { name: '$10' }));
    await userEvent.click(screen.getByRole('button', { name: '$2' }));
    expect(screen.getByRole('textbox', { name: 'Cash received' })).toHaveValue('52.00');
    expect(screen.getAllByText('CA$0.25').length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ method: 'CASH', amount: 51.75, cashTendered: 52 }));
  });

  it('sets exact remaining only after the cashier explicitly selects Exact', async () => {
    render(<PaymentDialog open sale={{ ...sale(), currencyCode: 'CAD' }} busy={false} onClose={vi.fn()} onSubmit={vi.fn()} />);
    expect(screen.getByRole('textbox', { name: 'Cash received' })).toHaveValue('0.00');
    await userEvent.click(screen.getByRole('button', { name: 'Exact' }));
    expect(screen.getByRole('textbox', { name: 'Cash received' })).toHaveValue('5.75');
  });

  it('adds a barcode item locally and calculates tax only at checkout', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) {
        return common;
      }
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/products/barcodes/0012345')) {
        return jsonResponse({ productId, variantId: '00000000-0000-0000-0000-000000000199', productName: 'Coffee', variantName: 'Large', barcode: '0012345', sku: 'COFFEE-LARGE', unitOfMeasureId: null, price: 11.5, taxCategoryId: null, taxCategoryName: null, availableQuantity: 0, active: true });
      }
      if (url.pathname.endsWith('/api/v1/sales/checkout') && init?.method === 'POST') {
        return jsonResponse(sale('DRAFT'));
      }
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/hold`) && init?.method === 'POST') {
        return jsonResponse(sale('HELD'));
      }
      if (url.pathname.endsWith('/api/v1/sales')) {
        return jsonResponse(page([]));
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos']} />);

    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Primary navigation' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to Store Menu' })).toBeInTheDocument();
    expect((await screen.findAllByText('Main Store (MAIN)')).length).toBeGreaterThan(0);

    await userEvent.type(screen.getByRole('textbox', { name: 'Barcode' }), '0012345{enter}');

    expect(fetchMock.mock.calls.some(([input, init]) => {
        const url = new URL(String(input), window.location.origin);
        return url.pathname.endsWith('/api/v1/sales/checkout') && init?.method === 'POST';
      })).toBe(false);

    expect(await screen.findByText('System stock is currently 0. You can continue the sale.')).toBeInTheDocument();

    expect(await screen.findByText('Coffee — Large')).toBeInTheDocument();
    expect(screen.getAllByText('At checkout').length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Checkout' }));
    await screen.findByRole('heading', { name: 'Take payment' });
    expect(fetchMock.mock.calls.filter(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith('/api/v1/sales/checkout') && init?.method === 'POST';
    })).toHaveLength(1);
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.includes('/api/v1/register-sessions/') && init?.method === 'POST';
    })).toBe(false);
  });

  it('uses a fixed checkout viewport with only the cart body as the scrolling region', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => commonApi(input) ?? jsonResponse({}, 404));
    document.body.style.overflow = 'auto';

    const view = render(<App initialEntries={['/pos']} />);

    const shell = await screen.findByTestId('retail-checkout-shell');
    const cartScrollRegion = await screen.findByTestId('cart-scroll-region');
    expect(shell).toHaveStyle({ height: 'calc(100dvh - 88px)', overflow: 'hidden' });
    expect(cartScrollRegion).toHaveStyle({ overflowY: 'auto' });
    expect(document.body).toHaveStyle({ overflow: 'hidden' });
    expect(screen.getByRole('button', { name: 'Barcode' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('textbox', { name: 'Barcode' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Product search' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Checkout' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Complete sale' })).toBeVisible();

    view.unmount();
    expect(document.body).toHaveStyle({ overflow: 'auto' });
  });

  it('adds a permitted custom item and sends its explicit non-catalog checkout shape', async () => {
    let checkoutBody: any;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse({ ...currentUser(), permissions: ['POS_CUSTOM_ITEM'] });
      const common = commonApi(input);
      if (common) return common;
      if (url.pathname.endsWith('/api/v1/sales/checkout') && init?.method === 'POST') {
        checkoutBody = JSON.parse(String(init.body));
        return jsonResponse(sale());
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos']} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Custom Item' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Item Name / Description' }), 'Grocery Item');
    await userEvent.type(screen.getByRole('spinbutton', { name: 'Price (USD)' }), '7.99');
    await userEvent.click(screen.getByRole('combobox', { name: 'Tax Treatment' }));
    await userEvent.click(screen.getByRole('option', { name: 'Taxable' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add to Cart' }));
    expect(await screen.findByText('Grocery Item')).toBeInTheDocument();
    expect(screen.getAllByText('Custom Item').length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Add Custom Item' })).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Edit Grocery Item' }));
    const editDialog = screen.getByRole('dialog', { name: 'Edit Custom Item' });
    const editPrice = within(editDialog).getByRole('spinbutton', { name: 'Price (USD)' });
    await userEvent.clear(editPrice);
    await userEvent.type(editPrice, '8.50');
    await userEvent.click(within(editDialog).getByRole('combobox', { name: 'Tax Treatment' }));
    await userEvent.click(screen.getByRole('option', { name: 'Non-Taxable' }));
    await userEvent.click(within(editDialog).getByRole('button', { name: 'Update Item' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Calculate Tax' }));
    await waitFor(() => expect(checkoutBody).toBeDefined());
    expect(checkoutBody.items[0]).toEqual({ lineType: 'CUSTOM_ITEM', description: 'Grocery Item', unitPrice: 8.5, quantity: 1, taxTreatment: 'NON_TAXABLE' });
    expect(checkoutBody.items[0].productId).toBeUndefined();
  });

  it('does not expose Custom Item to a user without its permission', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => commonApi(input) ?? jsonResponse({}, 404));
    render(<App initialEntries={['/pos']} />);
    await screen.findByRole('heading', { name: 'Checkout' });
    expect(screen.queryByRole('button', { name: 'Custom Item' })).not.toBeInTheDocument();
  });

  it('keeps checkout controls visible while a long recovered cart stays in the internal cart scroller', async () => {
    const baseSale = sale('DRAFT');
    await saveDraftCartRecovery({
      ...baseSale,
      items: Array.from({ length: 24 }, (_, index) => ({
        ...baseSale.items[0],
        id: `00000000-0000-0000-0001-${String(index).padStart(12, '0')}`,
        productId: `00000000-0000-0000-0002-${String(index).padStart(12, '0')}`,
        lineNumber: index + 1,
        productName: `Cart Product ${index + 1}`,
        productSku: `SKU-${index + 1}`
      }))
    });
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => commonApi(input) ?? jsonResponse({}, 404));

    render(<App initialEntries={['/pos']} />);

    expect(await screen.findByText('Cart Product 24')).toBeInTheDocument();
    expect(screen.getByTestId('cart-scroll-region')).toHaveStyle({ overflowY: 'auto' });
    expect(screen.getByRole('button', { name: 'Clear cart' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Take payment' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Complete sale' })).toBeVisible();
  });

  it('loads a saved Store discount once and sends only its definition id at Retail checkout', async () => {
    let checkoutBody: unknown;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse({ ...currentUser(), permissions: ['POS_SALE_DISCOUNT'] });
      const common = commonApi(input);
      if (common) return common;
      if (url.pathname.endsWith(`/api/v1/stores/${storeId}/discounts`)) return jsonResponse([{ id: 'staff', name: 'Staff Discount', type: 'DISCOUNT_PERCENTAGE', value: 10, active: true }]);
      if (url.pathname.endsWith('/api/v1/products/barcodes/12345')) return jsonResponse({ productId, variantId: null, productName: 'Coffee', variantName: null, barcode: '12345', sku: 'COFFEE', unitOfMeasureId: null, price: 5, taxCategoryId: null, taxCategoryName: null, availableQuantity: 2, active: true });
      if (url.pathname.endsWith('/api/v1/sales/checkout') && init?.method === 'POST') { checkoutBody=JSON.parse(String(init.body)); return jsonResponse({ ...sale(), discountAmount:.5, discountName:'Staff Discount' }); }
      if (url.pathname.endsWith('/api/v1/sales')) return jsonResponse(page([]));
      return jsonResponse({},404);
    });

    render(<App initialEntries={['/pos']} />);
    await screen.findByRole('heading',{name:'Checkout'});
    await userEvent.type(await screen.findByRole('textbox',{name:'Barcode'}),'12345{enter}');
    await screen.findByText('Coffee');
    await userEvent.click(screen.getByRole('combobox',{name:'Discount'}));
    await userEvent.click(await screen.findByRole('option',{name:'Staff Discount — 10%'}));
    expect(fetchMock.mock.calls.filter(([input])=>new URL(String(input),window.location.origin).pathname.endsWith(`/api/v1/stores/${storeId}/discounts`))).toHaveLength(1);
    await userEvent.click(screen.getByRole('button',{name:'Checkout'}));
    await waitFor(()=>expect(checkoutBody).toEqual(expect.objectContaining({discount:{discountDefinitionId:'staff'}})));
  });

  it('submits the completed scanner value with a configured Tab suffix and adds it once', async () => {
    window.localStorage.setItem(barcodeScannerPreferencesKey, JSON.stringify({
      ...defaultBarcodeScannerPreferences,
      suffix: 'Tab'
    }));
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) return common;
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/products/barcodes/8901234567890')) {
        return jsonResponse({ productId, variantId: null, productName: 'Coca-Cola 500ml', variantName: null, barcode: '8901234567890', sku: 'COKE-500', unitOfMeasureId: null, price: 2.99, taxCategoryId: null, taxCategoryName: null, availableQuantity: -1, active: true });
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos']} />);
    const barcodeInput = await screen.findByRole('textbox', { name: 'Barcode' });
    await userEvent.type(barcodeInput, '8901234567890{tab}');

    expect(await screen.findByText('Coca-Cola 500ml')).toBeInTheDocument();
    expect(screen.getAllByText('$2.99').length).toBeGreaterThan(0);
    expect(fetchMock.mock.calls.filter(([input]) => new URL(String(input), window.location.origin).pathname.endsWith('/api/v1/products/barcodes/8901234567890'))).toHaveLength(1);
    expect(barcodeInput).toHaveValue('');

    await userEvent.type(barcodeInput, '8901234567890{tab}');
    await waitFor(() => expect(screen.getByDisplayValue('2')).toBeInTheDocument());
    await userEvent.type(barcodeInput, '8901234567890{tab}');
    await waitFor(() => expect(screen.getByDisplayValue('3')).toBeInTheDocument());

    expect(screen.getByDisplayValue('3')).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([input]) => new URL(String(input), window.location.origin).pathname.endsWith('/api/v1/products/barcodes/8901234567890'))).toHaveLength(3);
  });

  it('requires deliberate age confirmation before adding a restricted variant', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) return common;
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/products/barcodes/001919')) {
        return jsonResponse({
          productId,
          variantId: '00000000-0000-0000-0000-000000000199',
          productName: 'Restricted Product',
          variantName: 'Special Variant',
          barcode: '001919',
          sku: 'RESTRICTED-SPECIAL',
          unitOfMeasureId: null,
          price: 10,
          taxCategoryId: null,
          taxCategoryName: null,
          availableQuantity: 10,
          active: true,
          ageRestricted: true,
          minimumAge: 19
        });
      }
      if (url.pathname.endsWith('/api/v1/sales')) return jsonResponse(page([]));
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos']} />);
    const barcodeInput = await screen.findByRole('textbox', { name: 'Barcode' });
    await userEvent.type(barcodeInput, '001919{enter}');

    expect(await screen.findByRole('heading', { name: 'Age Verification Required' })).toBeInTheDocument();
    expect(screen.getByText('Required age: 19+')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => new URL(String(input), window.location.origin).pathname.endsWith('/items') && init?.method === 'POST')).toBe(false);

    await userEvent.keyboard('{Enter}');
    expect(screen.getByRole('heading', { name: 'Age Verification Required' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => new URL(String(input), window.location.origin).pathname.endsWith('/items') && init?.method === 'POST')).toBe(false);

    await userEvent.click(screen.getByRole('button', { name: 'Age Verified' }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith('/api/v1/sales/checkout') && init?.method === 'POST';
    })).toBe(false));
    expect(await screen.findByText('Restricted Product — Special Variant')).toBeInTheDocument();
  });

  it('leaves full-screen POS for Store Menu and resumes the same open session', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const common = commonApi(input);
      return common ?? jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos']} />);

    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Primary navigation' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: 'Back to Store Menu' }));

    expect(await screen.findByRole('heading', { name: 'Store Menu' })).toBeInTheDocument();
    expect(screen.getByText('Front Register (FRONT-1)')).toBeInTheDocument();
    expect(screen.getByText('Main Store (MAIN) • OPEN')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Inventory / Product Lookup' })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Returns' }).length).toBeGreaterThan(0);

    const returnToPosLinks = screen.getAllByRole('link', { name: 'Return to POS' });
    await userEvent.click(returnToPosLinks[returnToPosLinks.length - 1]);
    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Primary navigation' })).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.includes('/api/v1/register-sessions/') && init?.method === 'POST';
    })).toBe(false);
  });

  it('captures scanner input continuously and reports unknown barcodes', async () => {
    let unknownAttempts = 0;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) {
        return common;
      }
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/products/barcodes/99999')) {
        unknownAttempts += 1;
        return jsonResponse({ code: 'not_found', message: 'BARCODE_NOT_FOUND', status: 404 }, 404);
      }
      if (url.pathname.endsWith('/api/v1/products/barcodes/12345')) {
        return jsonResponse({ productId, variantId: null, productName: 'Coffee', variantName: null, barcode: '12345', sku: 'COFFEE', unitOfMeasureId: null, price: 11.5, taxCategoryId: null, taxCategoryName: null, availableQuantity: 10, active: true });
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos']} />);

    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument();
    expect((await screen.findAllByText('Main Store (MAIN)')).length).toBeGreaterThan(0);

    for (const key of ['9', '9', '9', '9', '9', 'Enter']) {
      fireEvent.keyDown(window, { key });
    }

    expect(await screen.findByText('No product was found for barcode 99999.')).toBeInTheDocument();

    for (const key of ['9', '9', '9', '9', '9', 'Enter']) {
      fireEvent.keyDown(window, { key });
    }

    expect(await screen.findByText('No product was found for barcode 99999.')).toBeInTheDocument();
    expect(unknownAttempts).toBe(2);

    for (const key of ['1', '2', '3', '4', '5', 'Enter']) {
      fireEvent.keyDown(window, { key });
    }

    expect((await screen.findAllByText('Coffee')).length).toBeGreaterThan(0);
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith('/api/v1/sales/checkout') && init?.method === 'POST';
    })).toBe(false);
  });

  it('records split payment, shows cash change, protects completion from duplicates, and shows success', async () => {
    const partial = saleWithPayments([payment('DEBIT', 2.75)]);
    const fullyPaid = saleWithPayments([
      payment('DEBIT', 2.75),
      payment('CASH', 3, 5, 2, cashPaymentId)
    ]);
    const completed = saleWithPayments(fullyPaid.payments, 'COMPLETED');
    const completion = deferredJson(completed);
    const completeCalls: Array<Headers> = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) {
        return common;
      }
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/products/barcodes/12345')) {
        return jsonResponse({ productId, variantId: null, productName: 'Coffee', variantName: null, barcode: '12345', sku: 'COFFEE', unitOfMeasureId: null, price: 11.5, taxCategoryId: null, taxCategoryName: null, availableQuantity: 10, active: true });
      }
      if (url.pathname.endsWith('/api/v1/sales/checkout') && init?.method === 'POST') {
        return jsonResponse(sale('DRAFT'));
      }
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/payments`) && init?.method === 'POST') {
        const body = JSON.parse(String(init.body));
        if (body.method === 'DEBIT') {
          return jsonResponse(partial);
        }
        return jsonResponse(fullyPaid);
      }
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/complete`) && init?.method === 'POST') {
        completeCalls.push(new Headers(init.headers));
        return completion.response;
      }
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/receipt`) && init?.method === undefined) {
        return jsonResponse(receipt());
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos']} />);

    await userEvent.type(await screen.findByRole('textbox', { name: 'Barcode' }), '12345{enter}');
    expect((await screen.findAllByText('Coffee')).length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('button', { name: 'Checkout' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Debit' }));
    await userEvent.clear(screen.getByRole('spinbutton', { name: 'Payment amount' }));
    await userEvent.type(screen.getByRole('spinbutton', { name: 'Payment amount' }), '2.75');
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));

    expect(await screen.findByText('Payments recorded')).toBeInTheDocument();
    expect(screen.getAllByText('$3.00').length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('button', { name: '$5' }));
    expect(await screen.findByText('$2.00')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getAllByText('$5.75').length).toBeGreaterThan(0);
    expect(screen.getAllByText('$2.00').length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('button', { name: 'Complete sale' }));
    expect(await screen.findByRole('button', { name: 'Completing sale...' })).toBeDisabled();
    expect(completeCalls).toHaveLength(1);
    expect(completeCalls[0].get('Idempotency-Key')).toBeTruthy();

    completion.resolve();
    expect(await screen.findByRole('heading', { name: 'Sale complete' })).toBeInTheDocument();
    expect(screen.getAllByText('$2.00').length).toBeGreaterThan(0);
    await waitFor(async () => expect(await loadDraftCartRecovery(sessionId)).toBeNull());
  });

  it('recovers a draft cart after refresh without a sale URL parameter', async () => {
    await saveDraftCartRecovery(sale('DRAFT', 2));
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) {
        return common;
      }
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}`) && init?.method !== 'POST') {
        return jsonResponse(sale('DRAFT', 2));
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos']} />);

    expect(await screen.findByText('Draft cart recovered after refresh.')).toBeInTheDocument();
    expect((await screen.findAllByText('Coffee')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('$11.50').length).toBeGreaterThan(0);
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith(`/api/v1/sales/${saleId}`) && init?.method !== 'POST';
    })).toBe(true);
  });

  it('accepts cash below the remaining balance as a partial payment', async () => {
    let submittedPayment: Record<string, unknown> | null = null;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) {
        return common;
      }
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/products/barcodes/12345')) {
        return jsonResponse({ productId, variantId: null, productName: 'Coffee', variantName: null, barcode: '12345', sku: 'COFFEE', unitOfMeasureId: null, price: 11.5, taxCategoryId: null, taxCategoryName: null, availableQuantity: 10, active: true });
      }
      if (url.pathname.endsWith('/api/v1/sales/checkout') && init?.method === 'POST') {
        return jsonResponse(sale('DRAFT'));
      }
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/payments`) && init?.method === 'POST') {
        submittedPayment = JSON.parse(String(init.body));
        return jsonResponse(saleWithPayments([payment('CASH', 1, 1, 0, cashPaymentId)]));
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos']} />);

    await userEvent.type(await screen.findByRole('textbox', { name: 'Barcode' }), '12345{enter}');
    await userEvent.click(await screen.findByRole('button', { name: 'Checkout' }));
    await userEvent.clear(screen.getByRole('textbox', { name: 'Cash received' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Cash received' }), '1');

    expect((await screen.findAllByText('$1.00')).length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));
    await screen.findByText('Payments recorded');
    expect(submittedPayment).toMatchObject({ method: 'CASH', amount: 1, cashTendered: 1 });
    expect(screen.getAllByText('Remaining').length).toBeGreaterThan(0);
    expect(screen.getAllByText('$4.75').length).toBeGreaterThan(0);
  });

  it('does not print or complete a sale when the payment API fails', async () => {
    window.localStorage.setItem('merchtyl.receiptPrinterPreferences', JSON.stringify({
      receiptPrintMode: 'KIOSK_AUTO_PRINT',
      autoPrintReceipt: true
    }));
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) return common;
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}`) && init?.method !== 'POST') return jsonResponse(sale('DRAFT'));
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/payments`) && init?.method === 'POST') {
        return jsonResponse({ message: 'Payment was declined', code: 'payment_declined' }, 422);
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={[`/pos?saleId=${saleId}`]} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Take payment' }));
    await userEvent.click(screen.getByRole('button', { name: 'Exact' }));
    await userEvent.click(screen.getByRole('button', { name: 'Record payment' }));

    expect(await screen.findByText('Payment was declined')).toBeInTheDocument();
    expect(print).not.toHaveBeenCalled();
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith(`/api/v1/sales/${saleId}/complete`) && init?.method === 'POST';
    })).toBe(false);
  });

  it('keeps a fully paid sale recoverable after completion failure', async () => {
    window.localStorage.setItem('merchtyl.receiptPrinterPreferences', JSON.stringify({
      receiptPrintMode: 'KIOSK_AUTO_PRINT',
      autoPrintReceipt: true
    }));
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      callback(0);
      return 1;
    });
    const fullyPaid = saleWithPayments([payment('CASH', 5.75, 10, 4.25, cashPaymentId)]);
    let completeAttempts = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) {
        return common;
      }
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}`) && init?.method !== 'POST') {
        return jsonResponse(fullyPaid);
      }
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/complete`) && init?.method === 'POST') {
        completeAttempts += 1;
        if (completeAttempts === 1) {
          return jsonResponse({ message: 'Inventory changed before completion', code: 'conflict' }, 409);
        }
        return jsonResponse(saleWithPayments(fullyPaid.payments, 'COMPLETED'));
      }
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/receipt`) && init?.method === undefined) {
        return jsonResponse(receipt());
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={[`/pos?saleId=${saleId}`]} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Complete sale' }));
    expect(await screen.findByText("We couldn't complete this action because the information conflicts with the current state. Refresh and try again.")).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Complete sale' })).toBeEnabled();
    expect(print).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Complete sale' }));
    expect(await screen.findByRole('heading', { name: 'Sale complete' })).toBeInTheDocument();
    expect(completeAttempts).toBe(2);
    await waitFor(() => expect(print).toHaveBeenCalledOnce());
  });

  it('previews receipts, prints configured copies, and reprints through the backend', async () => {
    const completed = saleWithPayments([payment('CASH', 5.75, 10, 4.25, cashPaymentId)], 'COMPLETED');
    const print = vi.fn();
    vi.spyOn(window, 'print').mockImplementation(print);
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      callback(0);
      return 1;
    });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) {
        return common;
      }
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}`) && init?.method !== 'POST') {
        return jsonResponse(completed);
      }
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/receipt`) && init?.method === undefined) {
        return jsonResponse(receipt());
      }
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/receipt/reprint`) && init?.method === 'POST') {
        return jsonResponse(receipt(1));
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={[`/pos?saleId=${saleId}`]} />);

    expect(await screen.findByRole('heading', { name: 'Sale complete' })).toBeInTheDocument();
    const receiptPreview = await screen.findByLabelText('Receipt preview');
    expect(within(receiptPreview).getByRole('heading', { name: 'Main Store' })).toBeInTheDocument();
    expect(within(receiptPreview).getByText('Powered by')).toBeInTheDocument();
    expect(within(receiptPreview).getByRole('img', { name: 'Merchtyl' })).toHaveAttribute('src', '/branding/Full black.svg');
    expect(screen.getAllByText('RCT-2026-07-21-00000000').length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('combobox', { name: 'Receipt width' }));
    await userEvent.click(await screen.findByRole('option', { name: '58 mm' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Copies' }));
    await userEvent.click(await screen.findByRole('option', { name: '2' }));
    await userEvent.click(screen.getByRole('button', { name: 'Print receipt' }));

    await waitFor(() => expect(print).toHaveBeenCalledOnce());

    await userEvent.click(screen.getByRole('button', { name: 'Reprint' }));
    await waitFor(() => expect(print).toHaveBeenCalledTimes(2));
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.endsWith(`/api/v1/sales/${saleId}/receipt/reprint`) && init?.method === 'POST';
    })).toBe(true);
    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return (url.pathname.endsWith('/api/v1/sales/drafts') || url.pathname.endsWith(`/api/v1/sales/${saleId}/complete`))
        && init?.method === 'POST';
    })).toBe(false);
  });

  it('reports auto-print failure without reversing a completed sale', async () => {
    window.localStorage.setItem('merchtyl.receiptPrinterPreferences', JSON.stringify({
      receiptPrintMode: 'KIOSK_AUTO_PRINT',
      widthMm: 80,
      copies: 1,
      autoPrintReceipt: true
    }));
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      callback(0);
      return 1;
    });
    vi.spyOn(window, 'print').mockImplementation(() => {
      throw new Error('print unavailable');
    });
    const fullyPaid = saleWithPayments([payment('CASH', 5.75, 10, 4.25, cashPaymentId)]);
    const completed = saleWithPayments(fullyPaid.payments, 'COMPLETED');
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) {
        return common;
      }
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}`) && init?.method !== 'POST') return jsonResponse(fullyPaid);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/complete`) && init?.method === 'POST') return jsonResponse(completed);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/receipt`) && init?.method === undefined) {
        return jsonResponse(receipt());
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={[`/pos?saleId=${saleId}`]} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Complete sale' }));
    expect(await screen.findByRole('heading', { name: 'Sale complete' })).toBeInTheDocument();
    expect(await screen.findByText('Sale completed. Receipt could not be printed automatically.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Print receipt' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'New sale' })).toBeEnabled();
  });

  it('auto-prints a newly completed kiosk receipt once after rendering', async () => {
    window.localStorage.setItem('merchtyl.receiptPrinterPreferences', JSON.stringify({
      receiptPrintMode: 'KIOSK_AUTO_PRINT',
      autoPrintReceipt: true,
      widthMm: 80
    }));
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      callback(0);
      return 1;
    });
    const fullyPaid = saleWithPayments([payment('CASH', 5.75, 10, 4.25, cashPaymentId)]);
    const completed = saleWithPayments(fullyPaid.payments, 'COMPLETED');
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) return common;
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}`) && init?.method !== 'POST') return jsonResponse(fullyPaid);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/complete`) && init?.method === 'POST') return jsonResponse(completed);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/receipt`) && init?.method === undefined) return jsonResponse(receipt());
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={[`/pos?saleId=${saleId}`]} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Complete sale' }));
    expect(await screen.findByLabelText('Receipt preview')).toHaveTextContent('RCT-2026-07-21-00000000');
    await waitFor(() => expect(print).toHaveBeenCalledOnce());
    await userEvent.click(screen.getByLabelText('Auto-print'));
    await userEvent.click(screen.getByLabelText('Auto-print'));
    fireEvent.focus(window);
    await Promise.resolve();
    expect(print).toHaveBeenCalledOnce();
  });

  it('does not auto-print a completed sale opened after refresh', async () => {
    window.localStorage.setItem('merchtyl.receiptPrinterPreferences', JSON.stringify({
      receiptPrintMode: 'KIOSK_AUTO_PRINT',
      autoPrintReceipt: true
    }));
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    const completed = saleWithPayments([payment('CASH', 5.75, 10, 4.25, cashPaymentId)], 'COMPLETED');
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) return common;
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}`) && init?.method !== 'POST') return jsonResponse(completed);
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/receipt`) && init?.method === undefined) return jsonResponse(receipt());
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={[`/pos?saleId=${saleId}`]} />);

    expect(await screen.findByLabelText('Receipt preview')).toBeInTheDocument();
    expect(print).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Print receipt' })).toBeEnabled();
  });

  it('adds a product from search, updates quantity, and removes the item', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) {
        return common;
      }
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/products')
        && url.searchParams.get('q') === 'coffee'
        && url.searchParams.get('storeId') === storeId
        && url.searchParams.get('active') === 'true') {
        return jsonResponse(page([product()]));
      }
      if (url.pathname.endsWith('/api/v1/sales/drafts') && init?.method === 'POST') {
        return jsonResponse(emptySale(), 201);
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos']} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Product Search' }));
    await userEvent.type(await screen.findByRole('textbox', { name: 'Product search' }), 'coffee');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Add Coffee' }));

    expect((await screen.findAllByText('Coffee')).length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Increase Coffee' }));
    await waitFor(() => expect(screen.getAllByText('$10.00').length).toBeGreaterThan(0));

    await userEvent.click(screen.getByRole('button', { name: 'Remove Coffee' }));
    expect(await screen.findByText('Cart is empty')).toBeInTheDocument();

    expect(fetchMock.mock.calls.some(([input, init]) => {
      const url = new URL(String(input), window.location.origin);
      return url.pathname.includes('/api/v1/sales/') && init?.method !== undefined;
    })).toBe(false);
  });

  it('debounces typed product-name search and sends only the current Store scope', async () => {
    const searchRequests: URL[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const common = commonApi(input);
      if (common) return common;
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/products')) {
        searchRequests.push(url);
        return jsonResponse(page(url.searchParams.get('q') === 'COCA' ? [{ ...product(), name: 'Coca Cola Classic' }] : []));
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos']} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Product Search' }));
    const input = await screen.findByRole('textbox', { name: 'Product search' });
    await userEvent.type(input, '  COCA  ');

    expect(await screen.findByText('Coca Cola Classic')).toBeInTheDocument();
    expect(searchRequests).toHaveLength(1);
    expect(searchRequests[0].searchParams.get('q')).toBe('COCA');
    expect(searchRequests[0].searchParams.get('storeId')).toBe(storeId);
    expect(searchRequests[0].searchParams.get('size')).toBe('20');
  });

  it('resumes a held sale back into the POS cart', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const common = commonApi(input);
      if (common) {
        return common;
      }
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/sales') && url.searchParams.get('status') === 'HELD') {
        return jsonResponse(page([sale('HELD')]));
      }
      if (url.pathname.endsWith(`/api/v1/sales/${saleId}/resume`) && init?.method === 'POST') {
        return jsonResponse(sale('DRAFT'));
      }
      return jsonResponse({}, 404);
    });

    render(<App initialEntries={['/pos/held-sales']} />);

    expect(await screen.findByRole('heading', { name: 'Held sales' })).toBeInTheDocument();
    expect(await screen.findByText('$5.75')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Resume' }));

    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument();
    expect(await screen.findByText('Coffee')).toBeInTheDocument();
  });

  it('secures and resumes the same Retail cart with backend PIN verification', async () => {
    const baseSession = { ...registerSession(), posPinConfigured: true };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/api/v1/auth/me')) return jsonResponse(currentUser());
      if (url.pathname.endsWith('/api/v1/register-sessions/current')) return jsonResponse(baseSession);
      if (url.pathname.endsWith(`/api/v1/register-sessions/${sessionId}/secure`)) return jsonResponse({ ...baseSession, tillSecured: true });
      if (url.pathname.endsWith(`/api/v1/register-sessions/${sessionId}/unlock`)) {
        expect(JSON.parse(String(init?.body))).toEqual({ pin: '123456' });
        return jsonResponse({ ...baseSession, tillSecured: false });
      }
      if (url.pathname.endsWith('/api/v1/stores')) return jsonResponse(page([store()]));
      if (url.pathname.endsWith('/api/v1/registers')) return jsonResponse(page([register()]));
      if (url.pathname.endsWith('/api/v1/devices')) return jsonResponse(page([device()]));
      if (url.pathname.endsWith('/api/v1/products')) return jsonResponse(page([product()]));
      return jsonResponse({}, 404);
    });
    render(<App initialEntries={['/pos']} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Product Search' }));
    await userEvent.type(screen.getByRole('textbox', { name: 'Product search' }), 'Coffee');
    await userEvent.click(await screen.findByRole('button', { name: /Add Coffee/ }));
    expect(screen.getByText('Coffee')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Secure Till' }));
    expect(await screen.findByRole('dialog', { name: 'Till Secured' })).toBeInTheDocument();
    for (const digit of ['1','2','3','4','5','6']) await userEvent.click(screen.getByRole('button', { name: digit }));
    await userEvent.click(screen.getByRole('button', { name: 'Resume Till' }));
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Till Secured' })).not.toBeInTheDocument());
    expect(screen.getByText('Coffee')).toBeInTheDocument();
  });
});
