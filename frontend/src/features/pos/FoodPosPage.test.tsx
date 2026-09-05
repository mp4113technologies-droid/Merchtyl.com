import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import { testReceiptDocument } from './receiptPrinter';
import * as receiptPrinter from './receiptPrinter';

const storeId = '00000000-0000-0000-0000-000000000901';
const sessionId = '00000000-0000-0000-0000-000000000902';
const saleId = '00000000-0000-0000-0000-000000000903';
const itemId = '00000000-0000-0000-0000-000000000904';
const productId = '00000000-0000-0000-0000-000000000905';

function response(body: unknown, status = 200) { return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })); }
function page(content: unknown[]) { return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1, first: true, last: true }; }
function sale(quantity = 0, paid = false, completed = false) {
  const subtotal = quantity * 12;
  const tax = quantity * 1.8;
  return { id: saleId, storeId, registerId: 'register', registerSessionId: sessionId, createdBy: 'user', customerId: null, status: completed ? 'COMPLETED' : 'DRAFT', businessDate: '2026-08-28', saleChannel: 'POS', currencyCode: 'CAD', pricesIncludeTax: false, subtotalAmount: subtotal, discountAmount: 0, estimatedTaxAmount: tax, totalAmount: subtotal + tax, heldAt: null, cancelledAt: null, completedBy: completed ? 'user' : null, completedAt: completed ? '2026-08-28T12:00:00Z' : null, items: quantity ? [{ id: itemId, productId, lineNumber: 1, productSku: 'PIZZA', productName: 'Pepperoni Pizza', quantity, unitPrice: 12, discountAmount: 0, completedProductCost: null, completedProductPrice: null, completedProductCapabilities: null, priceOverride: false, ageVerified: false, serialNumber: null, externalReference: null, customerId: null, paymentMethodCode: null, lineSubtotal: subtotal, estimatedTaxAmount: tax, lineTotal: subtotal + tax, version: 0 }] : [], payments: paid ? [{ id: 'payment', method: 'CASH', amount: subtotal + tax, currencyCode: 'CAD', cashTendered: subtotal + tax, changeDue: 0, reference: null, notes: null, createdBy: 'user', completedAt: '2026-08-28T12:00:00Z', createdAt: '2026-08-28T12:00:00Z', version: 0 }] : [], paidAmount: paid ? subtotal + tax : 0, balanceDue: paid ? 0 : subtotal + tax, changeDue: 0, paymentComplete: paid, createdAt: '2026-08-28T12:00:00Z', updatedAt: '2026-08-28T12:00:00Z', version: 0 };
}

describe('Food POS', () => {
  beforeEach(() => {
    const now = Date.now();
    window.localStorage.setItem('merchtyl.session', JSON.stringify({ accessToken: 'token', refreshToken: 'refresh', tokenType: 'Bearer', accessTokenExpiresAt: new Date(now + 900_000).toISOString(), refreshTokenExpiresAt: new Date(now + 86_400_000).toISOString(), userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'] }));
    vi.restoreAllMocks();
  });

  it('loads tiles and completes a taxed sale through shared checkout', async () => {
    const calls: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin); calls.push(`${init?.method ?? 'GET'} ${url.pathname}`);
      if (url.pathname.endsWith('/auth/me')) return response({ userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'], permissions: ['FOOD_POS_ACCESS'] });
      if (url.pathname.endsWith('/register-sessions/current')) return response({ id: sessionId, storeId, registerId: 'register', status: 'OPEN' });
      if (url.pathname.endsWith('/stores')) return response(page([{ id: storeId, code: 'MAIN', name: 'Main', currencyCode: 'CAD', capabilities: ['FOOD_SERVICE'] }]));
      if (url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`)) return response({ storeId, restaurantPosEnabled: true, kitchenDisplayName: "Joe's Kitchen" });
      if (url.pathname.endsWith('/food-menu/categories')) return response([{ id: 'pizza', storeId, name: 'Pizza', displayOrder: 1, active: true, imageUrl: null, version: 0 }]);
      if (url.pathname.endsWith('/food-menu/items')) return response([{ id: 'menu-item', storeId, productId, productName: 'Pepperoni Pizza', displayName: 'Pepperoni Pizza', price: 12, categoryId: 'pizza', categoryName: 'Pizza', displayOrder: 1, available: true, imageUrl: null, version: 0 }]);
      if (url.pathname.endsWith('/sales/drafts')) return response(sale(), 201);
      if (url.pathname.endsWith(`/food-menu/items/menu-item/sales/${saleId}`)) return response(sale(1));
      if (url.pathname.endsWith(`/sales/${saleId}/items/${itemId}/quantity`)) return response(sale(2));
      if (url.pathname.endsWith(`/sales/${saleId}/payments`)) return response(sale(2, true));
      if (url.pathname.endsWith(`/sales/${saleId}/complete`)) return response(sale(2, true, true));
      if (url.pathname.endsWith(`/sales/${saleId}/receipt`)) return response({ receiptNumber: 'RCT-FOOD-1' });
      return response({ message: `Unexpected ${url.pathname}` }, 500);
    });

    render(<App initialEntries={['/pos/food']} />);
    expect(await screen.findByText("Joe's Kitchen")).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Pizza' })).toBeInTheDocument();
    await userEvent.click(await screen.findByText('Pepperoni Pizza'));
    expect((await screen.findAllByText(/13\.80/)).length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Increase Pepperoni Pizza' }));
    expect((await screen.findAllByText(/27\.60/)).length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Pay' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Record payment' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Complete order' }));
    expect(await screen.findByText(/RCT-FOOD-1/)).toBeInTheDocument();
    await waitFor(() => expect(calls).toEqual(expect.arrayContaining([`POST /api/v1/sales/${saleId}/payments`, `POST /api/v1/sales/${saleId}/complete`, `GET /api/v1/sales/${saleId}/receipt`])));
  });

  it('blocks users without food POS permission', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => String(input).endsWith('/auth/me') ? response({ userId: 'user', email: 'x', displayName: 'Retail', roles: ['CASHIER'], permissions: ['POS_ACCESS'] }) : response({}, 500));
    render(<App initialEntries={['/pos/food']} />);
    expect(await screen.findByText('FOOD_POS_ACCESS is required.')).toBeInTheDocument();
  });

  it('offers persisted print choices and Print Both queues exactly two jobs before permitting a kitchen reprint', async () => {
    const print = vi.spyOn(receiptPrinter, 'printHtmlWithFallback').mockResolvedValue({ printer: 'BROWSER' });
    const persistedReceipt = { id: 'receipt-food-1', saleId, receiptNumber: 'RCT-FOOD-1', document: { ...testReceiptDocument(), saleId, receiptNumber: 'RCT-FOOD-1', tokenNumber: 'A104' } };
    const persistedTicket = {
      documentType: 'KITCHEN_TICKET', saleId, tokenNumber: 'A104', storeName: 'Main', registerName: 'Restaurant Register',
      cashierName: 'Kitchen', orderTime: '2026-08-28T12:00:00Z', orderType: 'TAKEOUT', tableNumber: null,
      orderNotes: 'Extra napkins', reprint: false,
      items: [{ saleItemId: itemId, name: 'Pepperoni Pizza', quantity: 1, modifiers: [], preparationInstructions: null }]
    };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/auth/me')) return response({ userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'], permissions: ['FOOD_POS_ACCESS'] });
      if (url.pathname.endsWith('/register-sessions/current')) return response({ id: sessionId, storeId, registerId: 'register', status: 'OPEN' });
      if (url.pathname.endsWith('/stores')) return response(page([{ id: storeId, code: 'MAIN', name: 'Main', currencyCode: 'CAD', capabilities: ['FOOD_SERVICE'] }]));
      if (url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`)) return response({ storeId, restaurantPosEnabled: true, kitchenDisplayName: "Joe's Kitchen" });
      if (url.pathname.endsWith('/food-menu/categories')) return response([{ id: 'pizza', storeId, name: 'Pizza', displayOrder: 1, active: true }]);
      if (url.pathname.endsWith('/food-menu/items')) return response([{ id: 'menu-item', storeId, displayName: 'Pepperoni Pizza', price: 12, categoryId: 'pizza', available: true }]);
      if (url.pathname.endsWith('/sales/drafts')) return response(sale(), 201);
      if (url.pathname.endsWith(`/food-menu/items/menu-item/sales/${saleId}`)) return response(sale(1));
      if (url.pathname.endsWith(`/sales/${saleId}/payments`)) return response(sale(1, true));
      if (url.pathname.endsWith(`/sales/${saleId}/complete`)) return response(sale(1, true, true));
      if (url.pathname.endsWith(`/sales/${saleId}/receipt/reprint`) && init?.method === 'POST') return response({ ...persistedReceipt, reprint: true });
      if (url.pathname.endsWith(`/sales/${saleId}/kitchen-ticket/reprint`) && init?.method === 'POST') return response({ ...persistedTicket, reprint: true });
      if (url.pathname.endsWith(`/sales/${saleId}/receipt`)) return response(persistedReceipt);
      if (url.pathname.endsWith(`/sales/${saleId}/kitchen-ticket`)) return response(persistedTicket);
      return response({ message: `Unexpected ${url.pathname}` }, 500);
    });

    render(<App initialEntries={['/pos/food']} />);
    await userEvent.click(await screen.findByText('Pepperoni Pizza'));
    await userEvent.click(screen.getByRole('button', { name: 'Pay' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Record payment' }));
    expect(print).not.toHaveBeenCalled();
    await userEvent.click(await screen.findByRole('button', { name: 'Complete order' }));
    expect(await screen.findByRole('button', { name: 'Print Both' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Kitchen Ticket' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Customer Receipt' })).toBeEnabled();
    expect(print).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Print Both' }));
    await waitFor(() => expect(print).toHaveBeenCalledTimes(2));
    expect(print.mock.calls[0][1]).toContain('Kitchen ticket A104');
    expect(print.mock.calls[1][1]).toContain('Customer receipt A104');
    fireEvent.focus(window);
    await Promise.resolve();
    expect(print).toHaveBeenCalledTimes(2);
    await userEvent.click(screen.getByRole('button', { name: 'Reprint Kitchen Ticket' }));
    await waitFor(() => expect(print).toHaveBeenCalledTimes(3));
  });

  it('does not print when restaurant order completion fails', async () => {
    window.localStorage.setItem('merchtyl.receiptPrinterPreferences', JSON.stringify({ receiptPrintMode: 'KIOSK_AUTO_PRINT', autoPrintReceipt: true }));
    const print = vi.spyOn(receiptPrinter, 'printHtmlWithFallback').mockResolvedValue({ printer: 'BROWSER' });
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = new URL(String(input), window.location.origin);
      if (url.pathname.endsWith('/auth/me')) return response({ userId: 'user', email: 'kitchen@test', displayName: 'Kitchen', roles: ['KITCHEN'], permissions: ['FOOD_POS_ACCESS'] });
      if (url.pathname.endsWith('/register-sessions/current')) return response({ id: sessionId, storeId, registerId: 'register', status: 'OPEN' });
      if (url.pathname.endsWith('/stores')) return response(page([{ id: storeId, name: 'Main', currencyCode: 'CAD' }]));
      if (url.pathname.endsWith(`/stores/${storeId}/food-service/configuration`)) return response({ storeId, restaurantPosEnabled: true, kitchenDisplayName: "Joe's Kitchen" });
      if (url.pathname.endsWith('/food-menu/categories')) return response([{ id: 'pizza', active: true, name: 'Pizza' }]);
      if (url.pathname.endsWith('/food-menu/items')) return response([{ id: 'menu-item', displayName: 'Pepperoni Pizza', price: 12, categoryId: 'pizza', available: true }]);
      if (url.pathname.endsWith('/sales/drafts')) return response(sale(), 201);
      if (url.pathname.endsWith(`/food-menu/items/menu-item/sales/${saleId}`)) return response(sale(1));
      if (url.pathname.endsWith(`/sales/${saleId}/payments`)) return response(sale(1, true));
      if (url.pathname.endsWith(`/sales/${saleId}/complete`)) return response({ message: 'Completion failed' }, 500);
      return response({}, 404);
    });
    render(<App initialEntries={['/pos/food']} />);
    await userEvent.click(await screen.findByText('Pepperoni Pizza'));
    await userEvent.click(screen.getByRole('button', { name: 'Pay' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Record payment' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Complete order' }));
    await waitFor(() => expect(print).not.toHaveBeenCalled());
    expect(screen.queryByText('Order completed')).not.toBeInTheDocument();
  });
});
