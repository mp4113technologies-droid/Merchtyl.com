import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '@mui/material';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { theme } from '../../app/theme';
import { InvoiceHistoryPage } from './InvoiceHistoryPage';

const listInvoiceHistory = vi.fn();
const getSaleReceipt = vi.fn();
const reprintSaleReceipt = vi.fn();
const listRefunds = vi.fn();
const printReceiptWithFallback = vi.fn();

vi.mock('../../app/session', () => ({ useSession: () => ({ getValidAccessToken: async () => 'token' }) }));
vi.mock('../../api/client', () => ({
  listStores: async () => ({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, first: true, last: true }),
  listInvoiceHistory: (...args: unknown[]) => listInvoiceHistory(...args),
  getSaleReceipt: (...args: unknown[]) => getSaleReceipt(...args),
  reprintSaleReceipt: (...args: unknown[]) => reprintSaleReceipt(...args),
  listRefunds: (...args: unknown[]) => listRefunds(...args)
}));
vi.mock('../pos/receiptPrinter', () => ({ loadReceiptPrinterPreferences: () => ({ mode: 'BROWSER' }), printReceiptWithFallback: (...args: unknown[]) => printReceiptWithFallback(...args) }));

function page(pageNumber: number, last: boolean) {
  return {
    content: [{ saleId: `sale-${pageNumber}`, receiptNumber: `${1001 - pageNumber}`, completedAt: '2026-10-12T12:00:00Z', saleType: 'RETAIL', storeId: 'store-1', storeName: 'Main', registerId: 'register-1', registerCode: 'R1', cashierName: 'Cashier', customerName: null, orderNumber: null, totalAmount: 20, currencyCode: 'CAD', paymentSummary: 'DEBIT', status: 'COMPLETED' }],
    page: pageNumber, size: 10, totalElements: 11, totalPages: 2, first: pageNumber === 0, last
  };
}

function setMobile(matches: boolean) {
  Object.defineProperty(window, 'matchMedia', { configurable: true, value: vi.fn().mockImplementation(() => ({ matches, media: '(max-width:899.95px)', onchange: null, addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn() })) });
}

function setup() {
  return render(<ThemeProvider theme={theme}><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><InvoiceHistoryPage /></MemoryRouter></QueryClientProvider></ThemeProvider>);
}

function receipt() {
  const document = {
    brandName: 'Merchtyl', brandTagline: 'Receipt', store: { id: 'store-1', code: 'MAIN', name: 'Main', legalName: null, address: '1 Main', phone: null, email: null },
    register: { id: 'register-1', code: 'R1', name: 'Register 1' }, cashier: { id: 'cashier-1', displayName: 'Cashier', email: 'cashier@example.com' },
    receiptNumber: '1001', saleId: 'sale-0', saleNumber: 'sale-0', businessDate: '2026-10-12', completedAt: '2026-10-12T12:00:00Z', currencyCode: 'CAD',
    items: [{ id: 'item-1', productId: 'product-1', lineNumber: 1, productName: 'Coffee', quantity: 1, unitPrice: 20, completedProductCost: 10, completedProductPrice: 20, completedProductCapabilities: null, discountAmount: 0, lineSubtotal: 20, taxAmount: 0, lineTotal: 20 }],
    subtotalAmount: 20, containerDepositTotal: 0, discountAmount: 0, taxSummaries: [], taxAmount: 0, totalAmount: 20, payments: [{ id: 'payment-1', method: 'DEBIT', amount: 20, cashTendered: null, changeDue: 0, reference: null, completedAt: '2026-10-12T12:00:00Z' }], cashTendered: 0, changeDue: 0
  };
  return { id: 'receipt-1', saleId: 'sale-0', receiptNumber: '1001', generatedAt: document.completedAt, reprintCount: 0, lastReprintedAt: null, document, createdAt: document.completedAt, updatedAt: document.completedAt, version: 0 };
}

describe('Invoice History', () => {
  beforeEach(() => {
    setMobile(false);
    listInvoiceHistory.mockReset(); getSaleReceipt.mockReset(); reprintSaleReceipt.mockReset(); listRefunds.mockReset(); printReceiptWithFallback.mockReset();
    listInvoiceHistory.mockResolvedValue(page(0, false));
    getSaleReceipt.mockResolvedValue(receipt());
    reprintSaleReceipt.mockResolvedValue(receipt());
    listRefunds.mockResolvedValue({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, first: true, last: true });
  });

  it('loads the newest ten server-side and requests the next page', async () => {
    const user = userEvent.setup();
    listInvoiceHistory.mockResolvedValueOnce(page(0, false)).mockResolvedValueOnce(page(1, true));
    setup();
    expect(await screen.findByText('#1001')).toBeVisible();
    expect(listInvoiceHistory).toHaveBeenCalledWith('token', expect.objectContaining({ page: 0, size: 10 }));
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('#1000')).toBeVisible();
    expect(listInvoiceHistory).toHaveBeenLastCalledWith('token', expect.objectContaining({ page: 1, size: 10 }));
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  });

  it('debounces invoice-number search and resets to page zero', async () => {
    const user = userEvent.setup();
    setup();
    const search = await screen.findByLabelText('Search invoice #');
    await user.type(search, 'INV-102');
    await waitFor(() => expect(listInvoiceHistory).toHaveBeenLastCalledWith('token', expect.objectContaining({ invoiceNumber: 'INV-102', page: 0 })), { timeout: 1500 });
  });

  it('shows the purpose-built empty states', async () => {
    listInvoiceHistory.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true });
    setup();
    expect(await screen.findByText(/No invoices yet/)).toBeVisible();
  });

  it('uses compact invoice cards on mobile without the desktop table', async () => {
    setMobile(true);
    setup();
    expect(await screen.findByRole('button', { name: 'View Invoice' })).toBeVisible();
    expect(screen.queryByRole('table', { name: 'Invoice history' })).not.toBeInTheDocument();
  });

  it('keeps the page shell visible while the invoice list is loading', () => {
    listInvoiceHistory.mockReturnValue(new Promise(() => {}));
    const { container } = setup();
    expect(screen.getByRole('heading', { name: 'Invoice History' })).toBeVisible();
    expect(container.querySelectorAll('.MuiSkeleton-root').length).toBeGreaterThan(0);
  });

  it('clears a backend search and returns to the first latest-invoice page', async () => {
    const user = userEvent.setup();
    setup();
    const search = await screen.findByLabelText('Search invoice #');
    await user.type(search, '1001');
    await waitFor(() => expect(listInvoiceHistory).toHaveBeenLastCalledWith('token', expect.objectContaining({ invoiceNumber: '1001', page: 0 })), { timeout: 1500 });
    await user.click(screen.getByRole('button', { name: 'Clear search' }));
    await waitFor(() => expect(listInvoiceHistory).toHaveBeenLastCalledWith('token', expect.objectContaining({ invoiceNumber: undefined, page: 0 })), { timeout: 1500 });
  });

  it('opens immutable receipt detail and reprints through the existing receipt infrastructure', async () => {
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole('button', { name: 'View' }));
    expect(await screen.findByText('Invoice / Receipt #1001')).toBeVisible();
    expect(screen.getByText('Coffee')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Print / Reprint Receipt' }));
    await waitFor(() => expect(reprintSaleReceipt).toHaveBeenCalledWith('token', 'sale-0'));
    expect(printReceiptWithFallback).toHaveBeenCalledWith(expect.objectContaining({ receiptNumber: '1001' }), expect.anything());
  });

  it('identifies Restaurant and refunded invoices and loads refund history', async () => {
    const user = userEvent.setup();
    listInvoiceHistory.mockResolvedValue({ ...page(0, true), content: [{ ...page(0, true).content[0], saleType: 'FOOD_SERVICE', status: 'PARTIALLY_REFUNDED', orderNumber: 'A104' }] });
    listRefunds.mockResolvedValue({ content: [{ id: 'refund-1', originalSaleId: 'sale-0', returnId: 'return-1', storeId: 'store-1', registerId: 'register-1', registerSessionId: 'session-1', createdBy: 'manager-1', businessDate: '2026-10-12', occurredAt: '2026-10-12T13:00:00Z', currencyCode: 'CAD', reason: 'Customer return', subtotalAmount: 5, taxAmount: 0, totalAmount: 5, approvedBy: null, approvedAt: null, approvalNotes: null, payments: [], itemTaxes: [], createdAt: '2026-10-12T13:00:00Z', updatedAt: '2026-10-12T13:00:00Z', version: 0 }], page: 0, size: 100, totalElements: 1, totalPages: 1, first: true, last: true });
    setup();
    expect(await screen.findByText('Restaurant')).toBeVisible();
    expect(screen.getByText('Pickup #A104')).toBeVisible();
    expect(screen.getByText('Partially Refunded')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'View' }));
    expect(await screen.findByText('Refunds')).toBeVisible();
    expect(screen.getByText('Customer return')).toBeVisible();
    expect(listRefunds).toHaveBeenCalledWith('token', expect.objectContaining({ originalSaleId: 'sale-0' }));
  });
});
