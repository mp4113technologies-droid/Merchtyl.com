import { afterEach, describe, expect, it, vi } from 'vitest';
import type { KitchenTicket, ReceiptDocument } from '../../api/types';
import * as receiptPrinter from './receiptPrinter';
import { kitchenTicketHtml, printFoodDocuments } from './foodOrderPrinter';

const preferences: receiptPrinter.ReceiptPrinterPreferences = {
  ...receiptPrinter.defaultReceiptPrinterPreferences,
  mode: 'QZ_TRAY',
  qzPrinterName: 'One Restaurant Printer'
};

function ticket(reprint = false): KitchenTicket {
  return {
    documentType: 'KITCHEN_TICKET', saleId: 'sale-1', tokenNumber: 'A104', storeName: 'Merchtyl Restaurant',
    registerName: 'Restaurant Register 1', cashierName: 'John', orderTime: '2026-09-02T00:42:00Z',
    orderType: 'TAKEOUT', tableNumber: null, orderNotes: 'Extra sauce\nNo peanuts', reprint,
    items: [{ saleItemId: 'item-1', name: 'Monkey Fingers', quantity: 2, modifiers: ['+ Extra Cheese', '- No Onion'], preparationInstructions: 'Spicy' }]
  };
}

function receipt(): ReceiptDocument {
  return {
    ...receiptPrinter.testReceiptDocument(), tokenNumber: 'A104', subtotalAmount: 40.5, taxAmount: 6.08,
    totalAmount: 46.58, cashTendered: 50, changeDue: 3.42
  };
}

describe('food order printing', () => {
  afterEach(() => vi.restoreAllMocks());

  it('renders a prominent kitchen token and preparation details without financial data', () => {
    const html = kitchenTicketHtml(ticket());
    expect(html).toContain('TOKEN A104');
    expect(html).toContain('2 x MONKEY FINGERS');
    expect(html).toContain('+ Extra Cheese');
    expect(html).toContain('Extra sauce');
    expect(html).not.toMatch(/\$|Subtotal|Tax|Total|Payment|Tendered|Change/);
  });

  it('marks kitchen reprints while preserving the original token', () => {
    const html = kitchenTicketHtml(ticket(true));
    expect(html).toContain('*** REPRINT ***');
    expect(html).toContain('TOKEN A104');
  });

  it('prints kitchen first and customer second through the same printer preferences', async () => {
    const print = vi.spyOn(receiptPrinter, 'printHtmlWithFallback').mockResolvedValue({ printer: 'QZ_TRAY' });
    const statuses: string[] = [];
    await printFoodDocuments(ticket(), receipt(), preferences, () => true,
      (document, status) => statuses.push(`${document}:${status}`));
    expect(print).toHaveBeenCalledTimes(2);
    expect(print.mock.calls[0][1]).toContain('Kitchen ticket A104');
    expect(print.mock.calls[1][1]).toContain('Customer receipt A104');
    expect(print.mock.calls[0][2].qzPrinterName).toBe('One Restaurant Printer');
    expect(print.mock.calls[1][2].qzPrinterName).toBe('One Restaurant Printer');
    expect(statuses).toEqual(['KITCHEN_TICKET:PRINTING', 'KITCHEN_TICKET:PRINTED', 'CUSTOMER_RECEIPT:PRINTING', 'CUSTOMER_RECEIPT:PRINTED']);
  });

  it('does not retry or duplicate a document excluded by status selection', async () => {
    const print = vi.spyOn(receiptPrinter, 'printHtmlWithFallback').mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ printer: 'QZ_TRAY' });
    const statuses: string[] = [];
    await printFoodDocuments(ticket(), receipt(), preferences, (document) => document === 'KITCHEN_TICKET',
      (document, status) => statuses.push(`${document}:${status}`));
    expect(print).toHaveBeenCalledOnce();
    expect(statuses).toEqual(['KITCHEN_TICKET:PRINTING', 'KITCHEN_TICKET:FAILED']);
  });
});
