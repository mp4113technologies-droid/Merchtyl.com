import type { KitchenTicket, ReceiptDocument } from '../../api/types';
import { printHtmlWithFallback, receiptHtml, type ReceiptPrinterPreferences } from './receiptPrinter';

export type FoodPrintStatus = 'READY' | 'PRINTING' | 'PRINTED' | 'FAILED';
export type FoodPrintDocument = 'KITCHEN_TICKET' | 'CUSTOMER_RECEIPT';

export function kitchenTicketHtml(ticket: KitchenTicket, widthMm = 80) {
  const items = ticket.items.map((item) => `
    <section class="item">
      <strong>${quantity(item.quantity)} x ${escapeHtml(item.name).toUpperCase()}</strong>
      ${item.modifiers.map((modifier) => `<div class="modifier">${escapeHtml(modifier)}</div>`).join('')}
      ${item.preparationInstructions ? `<div class="instruction">${escapeHtml(item.preparationInstructions)}</div>` : ''}
    </section>`).join('');
  return `<!doctype html><html><head><meta charset="utf-8"><title>Kitchen ${escapeHtml(ticket.tokenNumber)}</title><style>
    @page{size:${widthMm}mm auto;margin:0}*{box-sizing:border-box}body{margin:0;background:#fff;color:#000;font-family:ui-monospace,monospace;font-size:14px}
    main{width:${widthMm}mm;padding:4mm}.center{text-align:center}.token{font-size:28px;font-weight:900;margin:2mm 0}.reprint{font-size:20px;font-weight:900;border:2px solid #000;padding:2mm;margin-bottom:3mm}
    .rule{border-top:2px dashed #000;margin:3mm 0}.meta{font-size:12px}.item{margin:3mm 0;font-size:18px}.modifier,.instruction{font-size:14px;margin-left:5mm}.instruction{font-weight:700}
    .notes{font-size:16px;font-weight:700;white-space:pre-wrap}@media screen{body{background:#eee;padding:16px}main{margin:auto;background:#fff}}
  </style></head><body><main>
    ${ticket.reprint ? '<div class="center reprint">*** REPRINT ***</div>' : ''}
    <div class="center"><strong>KITCHEN ORDER</strong><div class="token">${escapeHtml(ticket.tokenNumber)}</div><div class="token">TOKEN ${escapeHtml(ticket.tokenNumber)}</div></div>
    <div class="rule"></div><div class="meta">Time: ${escapeHtml(new Date(ticket.orderTime).toLocaleTimeString())}</div>
    ${ticket.orderType ? `<div class="meta">Order Type: ${escapeHtml(ticket.orderType)}</div>` : ''}
    <div class="meta">Register: ${escapeHtml(ticket.registerName)}</div><div class="meta">Cashier: ${escapeHtml(ticket.cashierName)}</div>
    <div class="rule"></div>${items}<div class="rule"></div>
    ${ticket.orderNotes ? `<div>ORDER NOTES:</div><div class="notes">${escapeHtml(ticket.orderNotes)}</div><div class="rule"></div>` : ''}
    <div class="center token">${escapeHtml(ticket.tokenNumber)}</div>
  </main></body></html>`;
}

export async function printKitchenTicket(ticket: KitchenTicket, preferences: ReceiptPrinterPreferences) {
  return printHtmlWithFallback(kitchenTicketHtml(ticket, preferences.widthMm), `Kitchen ticket ${ticket.tokenNumber}`, preferences);
}

export async function printCustomerReceipt(receipt: ReceiptDocument, preferences: ReceiptPrinterPreferences) {
  return printHtmlWithFallback(receiptHtml(receipt, preferences.widthMm), `Customer receipt ${receipt.tokenNumber ?? receipt.receiptNumber}`, preferences);
}

export async function printFoodDocuments(
  ticket: KitchenTicket,
  receipt: ReceiptDocument,
  preferences: ReceiptPrinterPreferences,
  shouldPrint: (document: FoodPrintDocument) => boolean,
  onStatus: (document: FoodPrintDocument, status: FoodPrintStatus, error?: string) => void
) {
  if (shouldPrint('KITCHEN_TICKET')) {
    onStatus('KITCHEN_TICKET', 'PRINTING');
    try {
      await printKitchenTicket(ticket, preferences);
      onStatus('KITCHEN_TICKET', 'PRINTED');
    } catch (error) {
      onStatus('KITCHEN_TICKET', 'FAILED', message(error));
    }
  }
  if (shouldPrint('CUSTOMER_RECEIPT')) {
    onStatus('CUSTOMER_RECEIPT', 'PRINTING');
    try {
      await printCustomerReceipt(receipt, preferences);
      onStatus('CUSTOMER_RECEIPT', 'PRINTED');
    } catch (error) {
      onStatus('CUSTOMER_RECEIPT', 'FAILED', message(error));
    }
  }
}

function quantity(value: number) {
  return Number.isInteger(value) ? String(value) : String(value);
}

function escapeHtml(value: unknown) {
  return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

function message(error: unknown) {
  return error instanceof Error ? error.message : String(error);
}
