import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import type {
  AuthResponse,
  CurrentUserResponse,
  Refund,
  Return,
  Sale,
  UserRole
} from '../../api/types';

const cashierId = '00000000-0000-0000-0000-000000000904';
const saleId = '00000000-0000-0000-0000-000000000910';
const saleItemId = '00000000-0000-0000-0000-000000000911';
const paymentId = '00000000-0000-0000-0000-000000000912';
const returnId = '00000000-0000-0000-0000-000000000913';
const returnItemId = '00000000-0000-0000-0000-000000000914';
const refundId = '00000000-0000-0000-0000-000000000915';

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

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  }));
}

function page<T>(content: T[]) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true
  };
}

function sale(): Sale {
  return {
    id: saleId,
    storeId: '00000000-0000-0000-0000-000000000901',
    registerId: '00000000-0000-0000-0000-000000000902',
    registerSessionId: '00000000-0000-0000-0000-000000000903',
    createdBy: cashierId,
    customerId: null,
    status: 'COMPLETED',
    businessDate: '2026-07-28',
    saleChannel: 'POS',
    currencyCode: 'USD',
    pricesIncludeTax: false,
    subtotalAmount: 10,
    discountAmount: 0,
    estimatedTaxAmount: 1.5,
    totalAmount: 11.5,
    heldAt: null,
    cancelledAt: null,
    completedBy: cashierId,
    completedAt: '2026-07-28T12:00:00Z',
    items: [{
      id: saleItemId,
      productId: '00000000-0000-0000-0000-000000000920',
      lineNumber: 1,
      productSku: 'COFFEE',
      productName: 'Coffee',
      quantity: 2,
      unitPrice: 5,
      discountAmount: 0,
      completedProductCost: 2,
      completedProductPrice: 5,
      completedProductCapabilities: 'ALLOW_RETURN,TRACK_INVENTORY',
      priceOverride: false,
      ageVerified: false,
      serialNumber: null,
      externalReference: null,
      customerId: null,
      paymentMethodCode: null,
      lineSubtotal: 10,
      estimatedTaxAmount: 1.5,
      lineTotal: 11.5,
      version: 0
    }],
    payments: [{
      id: paymentId,
      method: 'CASH',
      amount: 11.5,
      currencyCode: 'USD',
      cashTendered: 11.5,
      changeDue: 0,
      reference: null,
      notes: null,
      createdBy: cashierId,
      completedAt: '2026-07-28T12:00:00Z',
      createdAt: '2026-07-28T12:00:00Z',
      version: 0
    }],
    paidAmount: 11.5,
    balanceDue: 0,
    changeDue: 0,
    paymentComplete: true,
    createdAt: '2026-07-28T11:55:00Z',
    updatedAt: '2026-07-28T12:00:00Z',
    version: 1
  };
}

function returnRecord(): Return {
  return {
    id: returnId,
    originalSaleId: saleId,
    storeId: '00000000-0000-0000-0000-000000000901',
    registerId: '00000000-0000-0000-0000-000000000902',
    registerSessionId: '00000000-0000-0000-0000-000000000903',
    createdBy: cashierId,
    businessDate: '2026-07-28',
    occurredAt: '2026-07-28T12:10:00Z',
    currencyCode: 'USD',
    reason: 'Customer changed mind',
    totalQuantity: 1,
    subtotalAmount: 5,
    taxAmount: 0.75,
    totalAmount: 5.75,
    fullReturn: false,
    items: [{
      id: returnItemId,
      originalSaleItemId: saleItemId,
      productId: '00000000-0000-0000-0000-000000000920',
      lineNumber: 1,
      productSku: 'COFFEE',
      productName: 'Coffee',
      quantity: 1,
      reason: 'Customer changed mind',
      originalQuantity: 2,
      originalUnitPrice: 5,
      originalDiscountAmount: 0,
      originalLineSubtotal: 10,
      originalTaxAmount: 1.5,
      originalLineTotal: 11.5,
      originalProductCost: 2,
      originalProductPrice: 5,
      originalProductCapabilities: 'ALLOW_RETURN,TRACK_INVENTORY',
      originalProductTaxCategoryId: null,
      returnSubtotalAmount: 5,
      returnTaxAmount: 0.75,
      returnTotalAmount: 5.75,
      version: 0
    }],
    createdAt: '2026-07-28T12:10:00Z',
    updatedAt: '2026-07-28T12:10:00Z',
    version: 0
  };
}

function refund(): Refund {
  return {
    id: refundId,
    returnId,
    originalSaleId: saleId,
    storeId: '00000000-0000-0000-0000-000000000901',
    registerId: '00000000-0000-0000-0000-000000000902',
    registerSessionId: '00000000-0000-0000-0000-000000000903',
    createdBy: cashierId,
    businessDate: '2026-07-28',
    occurredAt: '2026-07-28T12:11:00Z',
    currencyCode: 'USD',
    reason: 'Customer changed mind',
    subtotalAmount: 5,
    taxAmount: 0.75,
    totalAmount: 5.75,
    approvedBy: cashierId,
    approvedAt: '2026-07-28T12:11:00Z',
    approvalNotes: 'Approved by manager',
    payments: [{
      id: '00000000-0000-0000-0000-000000000916',
      originalPaymentId: paymentId,
      lineNumber: 1,
      method: 'CASH',
      amount: 5.75,
      currencyCode: 'USD',
      reference: null,
      notes: null,
      version: 0
    }],
    itemTaxes: [{
      id: '00000000-0000-0000-0000-000000000917',
      returnItemId,
      originalSaleItemId: saleItemId,
      lineNumber: 1,
      productTaxCategoryId: null,
      taxComponentCode: 'TAX',
      taxComponentName: 'Original sales tax',
      taxableAmount: 5,
      taxAmount: 0.75,
      currencyCode: 'USD',
      version: 0
    }],
    createdAt: '2026-07-28T12:11:00Z',
    updatedAt: '2026-07-28T12:11:00Z',
    version: 0
  };
}

describe('return and refund pages', () => {
  beforeEach(() => {
    window.localStorage.setItem('merchtyl.session', JSON.stringify(authResponse()));
    vi.restoreAllMocks();
  });

  it('renders return history and filters by original sale', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser());
      }
      if (url.includes('/api/v1/returns')) {
        return jsonResponse(page([returnRecord()]));
      }
      return jsonResponse({ message: 'Unexpected request' }, 500);
    });

    render(<App initialEntries={['/returns']} />);

    expect(await screen.findByRole('heading', { name: 'Returns' })).toBeInTheDocument();
    expect(await screen.findByText('Customer changed mind')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Original sale ID'), saleId);
    await userEvent.click(screen.getByRole('button', { name: 'Filter' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining(`originalSaleId=${saleId}`), expect.anything());
    });
  });

  it('creates a return and processes a refund from the original sale', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser());
      }
      if (url.endsWith(`/api/v1/sales/${saleId}`)) {
        return jsonResponse(sale());
      }
      if (url.includes('/api/v1/returns') && init?.method !== 'POST') {
        return jsonResponse(page([]));
      }
      if (url.endsWith('/api/v1/returns') && init?.method === 'POST') {
        return jsonResponse(returnRecord(), 201);
      }
      if (url.endsWith('/api/v1/refunds') && init?.method === 'POST') {
        return jsonResponse(refund(), 201);
      }
      return jsonResponse({ message: 'Unexpected request' }, 500);
    });

    render(<App initialEntries={['/returns/new']} />);

    await screen.findByRole('heading', { name: 'New return' });
    await userEvent.type(screen.getByLabelText('Original sale ID'), saleId);
    await userEvent.click(screen.getByRole('button', { name: 'Lookup' }));

    expect(await screen.findByText('Coffee')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select Coffee' }));
    await userEvent.type(screen.getByLabelText('Main return reason'), 'Customer changed mind');
    await userEvent.type(screen.getByLabelText('Approval notes'), 'Approved by manager');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Create return and refund' })).toBeEnabled());
    await userEvent.click(screen.getByRole('button', { name: 'Create return and refund' }));

    expect(await screen.findByRole('heading', { name: 'Refund complete' })).toBeInTheDocument();
    expect(screen.getByText('$5.75')).toBeInTheDocument();

    const returnCall = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/api/v1/returns') && init?.method === 'POST');
    expect(returnCall?.[1]?.body).toContain('"originalSaleId"');
    const refundCall = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/api/v1/refunds') && init?.method === 'POST');
    expect(refundCall?.[1]?.headers).toEqual(expect.any(Headers));
    expect((refundCall?.[1]?.headers as Headers).get('Idempotency-Key')).toBeTruthy();
    expect(refundCall?.[1]?.body).toContain('"method":"CASH"');
  });

  it('shows return detail with refund result', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.endsWith('/api/v1/auth/me')) {
        return jsonResponse(currentUser());
      }
      if (url.endsWith(`/api/v1/returns/${returnId}`)) {
        return jsonResponse(returnRecord());
      }
      if (url.includes('/api/v1/refunds')) {
        return jsonResponse(page([refund()]));
      }
      return jsonResponse({ message: 'Unexpected request' }, 500);
    });

    render(<App initialEntries={[`/returns/${returnId}`]} />);

    expect(await screen.findByRole('heading', { name: 'Return detail' })).toBeInTheDocument();
    expect(await screen.findByText('Coffee')).toBeInTheDocument();
    expect(await screen.findByText('Refund result')).toBeInTheDocument();
    expect(screen.getByText('Approved by manager')).toBeInTheDocument();
  });
});
