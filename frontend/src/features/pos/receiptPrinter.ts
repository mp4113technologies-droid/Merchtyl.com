import type { ReceiptDocument } from '../../api/types';
import { brandAssets } from '../../app/brandAssets';
import { posPrintQueue } from './posPrintQueue';

export interface ReceiptPrinter {
  isAvailable(): Promise<boolean>;
  print(receipt: ReceiptDocument): Promise<void>;
}

export type ReceiptPrinterMode = 'BROWSER' | 'QZ_TRAY';
export type ReceiptPrintMode = 'BROWSER_DIALOG' | 'KIOSK_AUTO_PRINT';

export type BrowserReceiptPrinterOptions = {
  widthMm?: number;
  createPrintFrame?: () => PosPrintFrame;
};

export type PosPrintFrame = {
  render(html: string): Promise<void>;
  printAndWait(): Promise<void>;
  cleanup(): void;
};

export type QzTrayCashDrawerPulse = {
  enabled: boolean;
  command: string;
};

export type ReceiptPrinterPreferences = {
  mode: ReceiptPrinterMode;
  receiptPrintMode: ReceiptPrintMode;
  widthMm: number;
  copies: number;
  autoPrint: boolean;
  autoPrintReceipt: boolean;
  qzPrinterName: string;
  fallbackToBrowser: boolean;
  cashDrawerPulse: QzTrayCashDrawerPulse;
};

export type QzTrayReceiptPrinterOptions = {
  printerName?: string;
  widthMm?: number;
  copies?: number;
  cashDrawerPulse?: QzTrayCashDrawerPulse;
};

export type ReceiptPrintResult = {
  printer: ReceiptPrinterMode;
  fallbackReason?: string;
};

export type ReceiptPrintContext = {
  saleId: string;
  registerId: string;
};

type QzTrayApi = {
  websocket: {
    isActive(): boolean;
    connect(): Promise<void>;
    disconnect(): Promise<void>;
  };
  printers: {
    find(printerName?: string): Promise<string>;
  };
  configs: {
    create(printerName: string, options?: Record<string, unknown>): unknown;
  };
  print(config: unknown, data: QzTrayPrintData[]): Promise<void>;
};

type QzTrayPrintData = {
  type: 'pixel' | 'raw';
  format: 'html' | 'command';
  flavor: 'plain';
  data: string;
};

export const receiptPrinterPreferencesKey = 'merchtyl.receiptPrinterPreferences';

export const receiptPrintStyles = {
  '@media print': {
    '@page': { size: '80mm auto', margin: 0 },
    'body *': { visibility: 'hidden !important' },
    '.receipt-print-root, .receipt-print-root *': { visibility: 'visible !important' },
    '.receipt-print-root': {
      position: 'absolute !important',
      inset: '0 auto auto 0 !important',
      width: '80mm',
      margin: '0 !important',
      color: '#000 !important',
      background: '#fff !important'
    },
    "nav, aside, button, [role='dialog']": { display: 'none !important' }
  }
} as const;

export const defaultCashDrawerPulseCommand = '\\x1Bp\\x00\\x19\\xFA';

export const defaultReceiptPrinterPreferences: ReceiptPrinterPreferences = {
  mode: 'BROWSER',
  receiptPrintMode: 'BROWSER_DIALOG',
  widthMm: 80,
  copies: 1,
  autoPrint: false,
  autoPrintReceipt: false,
  qzPrinterName: '',
  fallbackToBrowser: true,
  cashDrawerPulse: {
    enabled: false,
    command: defaultCashDrawerPulseCommand
  }
};

export class BrowserReceiptPrinter implements ReceiptPrinter {
  private readonly widthMm: number;
  private readonly createPrintFrame: () => PosPrintFrame;

  constructor(options: BrowserReceiptPrinterOptions = {}) {
    this.widthMm = options.widthMm ?? 80;
    this.createPrintFrame = options.createPrintFrame ?? createBrowserPrintFrame;
  }

  async isAvailable() {
    return typeof window !== 'undefined' && typeof document !== 'undefined' && Boolean(document.body);
  }

  async print(receipt: ReceiptDocument) {
    await this.printHtml(receiptHtml(receipt, this.widthMm));
  }

  async printHtml(html: string) {
    if (!(await this.isAvailable())) {
      throw new Error('Browser receipt printing is unavailable');
    }

    const frame = this.createPrintFrame();
    try {
      await frame.render(html);
      await frame.printAndWait();
    } finally {
      frame.cleanup();
    }
  }
}

export class QzTrayReceiptPrinter implements ReceiptPrinter {
  private readonly printerName: string;
  private readonly widthMm: number;
  private readonly copies: number;
  private readonly cashDrawerPulse: QzTrayCashDrawerPulse;

  constructor(options: QzTrayReceiptPrinterOptions = {}) {
    this.printerName = options.printerName?.trim() ?? '';
    this.widthMm = options.widthMm ?? 80;
    this.copies = clampCopies(options.copies ?? 1);
    this.cashDrawerPulse = {
      enabled: Boolean(options.cashDrawerPulse?.enabled),
      command: options.cashDrawerPulse?.command || defaultCashDrawerPulseCommand
    };
  }

  async isAvailable() {
    try {
      const qz = await this.connect();
      if (this.printerName) {
        await qz.printers.find(this.printerName);
      }
      return true;
    } catch {
      return false;
    }
  }

  async print(receipt: ReceiptDocument) {
    await this.printHtml(receiptHtml(receipt, this.widthMm), `Merchtyl receipt ${receipt.receiptNumber}`);
  }

  async printHtml(html: string, jobName: string) {
    if (!this.printerName) {
      throw new Error('Configure a QZ Tray printer name before printing.');
    }

    const qz = await this.connect();
    const matchedPrinterName = await this.findPrinter(qz);
    const config = qz.configs.create(matchedPrinterName, { jobName });

    if (this.cashDrawerPulse.enabled) {
      await this.printCashDrawerPulse(qz, config);
    }

    const data: QzTrayPrintData[] = [{
      type: 'pixel',
      format: 'html',
      flavor: 'plain',
      data: html
    }];

    for (let copy = 0; copy < this.copies; copy += 1) {
      try {
        await qz.print(config, data);
      } catch (error) {
        throw new Error(`QZ Tray printing failed: ${errorMessage(error)}`);
      }
    }
  }

  async testPrint() {
    await this.print(testReceiptDocument());
  }

  static async disconnect() {
    const qz = await loadQzTray();
    if (qz.websocket.isActive()) {
      await qz.websocket.disconnect();
    }
  }

  private async connect() {
    const qz = await loadQzTray();
    if (!qz.websocket.isActive()) {
      try {
        await qz.websocket.connect();
      } catch (error) {
        throw new Error(`Could not connect to QZ Tray. Confirm the desktop app is running. ${errorMessage(error)}`);
      }
    }
    return qz;
  }

  private async findPrinter(qz: QzTrayApi) {
    try {
      return await qz.printers.find(this.printerName);
    } catch (error) {
      throw new Error(`QZ Tray printer "${this.printerName}" was not found. ${errorMessage(error)}`);
    }
  }

  private async printCashDrawerPulse(qz: QzTrayApi, config: unknown) {
    try {
      await qz.print(config, [{
        type: 'raw',
        format: 'command',
        flavor: 'plain',
        data: decodeEscapedCommand(this.cashDrawerPulse.command)
      }]);
    } catch (error) {
      throw new Error(`QZ Tray cash drawer pulse failed: ${errorMessage(error)}`);
    }
  }
}

export function loadReceiptPrinterPreferences(): ReceiptPrinterPreferences {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(receiptPrinterPreferencesKey) ?? '{}') as Partial<ReceiptPrinterPreferences>;
    return normalizeReceiptPrinterPreferences(parsed);
  } catch {
    return { ...defaultReceiptPrinterPreferences, cashDrawerPulse: { ...defaultReceiptPrinterPreferences.cashDrawerPulse } };
  }
}

export function saveReceiptPrinterPreferences(preferences: ReceiptPrinterPreferences) {
  window.localStorage.setItem(receiptPrinterPreferencesKey, JSON.stringify(normalizeReceiptPrinterPreferences(preferences)));
}

export function normalizeReceiptPrinterPreferences(preferences: Partial<ReceiptPrinterPreferences>): ReceiptPrinterPreferences {
  const cashDrawerPulse = (preferences.cashDrawerPulse ?? {}) as Partial<QzTrayCashDrawerPulse>;
  const autoPrintReceipt = typeof preferences.autoPrintReceipt === 'boolean'
    ? preferences.autoPrintReceipt
    : Boolean(preferences.autoPrint);
  return {
    mode: preferences.mode === 'QZ_TRAY' ? 'QZ_TRAY' : 'BROWSER',
    receiptPrintMode: preferences.receiptPrintMode === 'KIOSK_AUTO_PRINT' ? 'KIOSK_AUTO_PRINT' : 'BROWSER_DIALOG',
    widthMm: [58, 80, 112].includes(Number(preferences.widthMm)) ? Number(preferences.widthMm) : 80,
    copies: clampCopies(preferences.copies),
    autoPrint: autoPrintReceipt,
    autoPrintReceipt,
    qzPrinterName: typeof preferences.qzPrinterName === 'string' ? preferences.qzPrinterName : '',
    fallbackToBrowser: typeof preferences.fallbackToBrowser === 'boolean' ? preferences.fallbackToBrowser : true,
    cashDrawerPulse: {
      enabled: Boolean(cashDrawerPulse.enabled),
      command: typeof cashDrawerPulse.command === 'string' && cashDrawerPulse.command.length > 0
        ? cashDrawerPulse.command
        : defaultCashDrawerPulseCommand
    }
  };
}

export async function printRenderedReceipt(context: ReceiptPrintContext) {
  if (typeof window === 'undefined' || typeof window.print !== 'function') {
    throw new Error('Browser receipt printing is unavailable');
  }
  await posPrintQueue.printMany([{
    transactionId: context.saleId,
    type: 'RETAIL_RECEIPT',
    print: async () => {
      await afterNextPaint();
      if (import.meta.env.DEV) console.info('RECEIPT_PRINT_REQUESTED', { saleId: context.saleId, registerId: context.registerId });
      window.print();
    }
  }]);
}

export async function printReceiptWithFallback(
  receipt: ReceiptDocument,
  preferences: ReceiptPrinterPreferences,
  browserPrinter?: BrowserReceiptPrinter
): Promise<ReceiptPrintResult> {
  const normalized = normalizeReceiptPrinterPreferences(preferences);
  if (normalized.mode === 'QZ_TRAY') {
    try {
      await new QzTrayReceiptPrinter({
        printerName: normalized.qzPrinterName,
        widthMm: normalized.widthMm,
        copies: normalized.copies,
        cashDrawerPulse: normalized.cashDrawerPulse
      }).print(receipt);
      return { printer: 'QZ_TRAY' };
    } catch (error) {
      if (!normalized.fallbackToBrowser) {
        throw error;
      }
      await printWithBrowser(receipt, normalized, browserPrinter);
      return { printer: 'BROWSER', fallbackReason: errorMessage(error) };
    }
  }

  await printWithBrowser(receipt, normalized, browserPrinter);
  return { printer: 'BROWSER' };
}

export async function printHtmlWithFallback(
  html: string,
  jobName: string,
  preferences: ReceiptPrinterPreferences
): Promise<ReceiptPrintResult> {
  const normalized = normalizeReceiptPrinterPreferences(preferences);
  if (normalized.mode === 'QZ_TRAY') {
    try {
      await new QzTrayReceiptPrinter({
        printerName: normalized.qzPrinterName,
        widthMm: normalized.widthMm,
        copies: normalized.copies,
        cashDrawerPulse: { ...normalized.cashDrawerPulse, enabled: false }
      }).printHtml(html, jobName);
      return { printer: 'QZ_TRAY' };
    } catch (error) {
      if (!normalized.fallbackToBrowser) throw error;
      await printHtmlWithBrowser(html, normalized);
      return { printer: 'BROWSER', fallbackReason: errorMessage(error) };
    }
  }
  await printHtmlWithBrowser(html, normalized);
  return { printer: 'BROWSER' };
}

export function testReceiptDocument(): ReceiptDocument {
  const now = new Date().toISOString();
  return {
    brandName: 'Merchtyl',
    brandTagline: 'Point of sale receipt',
    store: {
      id: 'test-store',
      code: 'TEST',
      name: 'Printer Test Store',
      legalName: null,
      address: 'QZ Tray setup test',
      phone: null,
      email: null
    },
    register: {
      id: 'test-register',
      code: 'TEST-1',
      name: 'Test Register'
    },
    cashier: {
      id: 'test-cashier',
      displayName: 'Printer Setup',
      email: 'setup@example.local'
    },
    receiptNumber: `TEST-${Date.now()}`,
    saleId: 'test-sale',
    saleNumber: 'TEST-SALE',
    businessDate: now.slice(0, 10),
    completedAt: now,
    currencyCode: 'USD',
    items: [{
      id: 'test-item',
      productId: 'test-product',
      lineNumber: 1,
      productSku: 'TEST',
      productName: 'Receipt printer test',
      quantity: 1,
      unitPrice: 1,
      completedProductCost: 0,
      completedProductPrice: 1,
      completedProductCapabilities: 'TEST_PRINT',
      discountAmount: 0,
      lineSubtotal: 1,
      taxAmount: 0,
      lineTotal: 1
    }],
    subtotalAmount: 1,
    discountAmount: 0,
    taxSummaries: [],
    taxAmount: 0,
    totalAmount: 1,
    payments: [{
      id: 'test-payment',
      method: 'CASH',
      amount: 1,
      cashTendered: 1,
      changeDue: 0,
      reference: null,
      completedAt: now
    }],
    cashTendered: 1,
    changeDue: 0,
    tokenNumber: null
  };
}

export function receiptHtml(receipt: ReceiptDocument, widthMm = 80) {
  return `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <title>${escapeHtml(receipt.receiptNumber)}</title>
  <style>
    @page { size: ${widthMm}mm auto; margin: 0; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      color: #111;
      background: #fff;
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace;
      font-size: 11px;
      line-height: 1.35;
    }
    .receipt {
      width: ${widthMm}mm;
      padding: 4mm;
    }
    .store-name {
      max-width: 100%;
      font-size: 22px;
      font-weight: 800;
      line-height: 1.15;
      overflow-wrap: anywhere;
      word-break: normal;
    }
    .store-address { margin-top: 1mm; font-size: 12px; line-height: 1.25; }
    .receipt-type { margin-top: 2mm; font-size: 13px; font-weight: 600; }
    .receipt-branding { margin-top: 4mm; text-align: center; break-inside: avoid; }
    .powered-by { margin-bottom: 1mm; font-size: 10px; font-weight: 500; line-height: 1.2; }
    .brand-logo {
      display: block;
      width: min(48%, 34mm);
      height: auto;
      max-height: 7mm;
      object-fit: contain;
      margin: 0 auto;
    }
    .center { text-align: center; }
    .muted { color: #444; }
    .rule { border-top: 1px dashed #777; margin: 3mm 0; }
    .row { display: flex; justify-content: space-between; gap: 4mm; }
    .row span:first-child { min-width: 0; overflow-wrap: anywhere; }
    .total { font-weight: 700; font-size: 13px; }
    .token { margin: 3mm 0; text-align: center; font-size: 22px; font-weight: 900; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 1mm 0; vertical-align: top; }
    th { text-align: left; border-bottom: 1px solid #aaa; }
    td.qty, td.money, th.money { text-align: right; white-space: nowrap; }
    .deposit-cell { padding: 0 0 1mm; }
    .deposit-block { margin-left: 2mm; }
    .deposit-row { display: flex; justify-content: space-between; align-items: baseline; gap: 2mm; }
    .deposit-label { min-width: 0; font-weight: 500; overflow-wrap: anywhere; }
    .deposit-amount { flex: 0 0 auto; white-space: nowrap; text-align: right; }
    .deposit-calculation { margin-top: .25mm; font-size: 10px; line-height: 1.2; }
    .food-customization { padding: 0 0 .5mm 2mm; font-size: 10px; color: #444; }
    @media screen {
      body { background: #f4f4f5; padding: 16px; }
      .receipt { margin: 0 auto; background: #fff; box-shadow: 0 12px 36px rgba(15, 23, 42, .18); }
    }
    @media print {
      body { background: #fff; }
      .brand-logo { filter: none; opacity: 1; }
    }
  </style>
</head>
<body>
  <main class="receipt">
    ${receiptBodyHtml(receipt)}
  </main>
</body>
</html>`;
}

function receiptBodyHtml(receipt: ReceiptDocument) {
  const itemRows = receipt.items.map((item) => `
    <tr>
      <td>
        <strong>${escapeHtml(item.productName)}</strong><br>
        <span class="muted">${formatQuantity(item.quantity)} × ${formatMoney(item.unitPrice, receipt.currencyCode)}</span>
      </td>
      <td class="qty"></td>
      <td class="money">${formatMoney(item.lineSubtotal - (item.depositTotal ?? 0), receipt.currencyCode)}</td>
    </tr>
      ${item.discountAmount > 0 ? `<tr><td colspan="3" class="muted">${escapeHtml(item.promotionName || 'Discount')} -${formatMoney(item.discountAmount, receipt.currencyCode)}</td></tr>` : ''}
      ${(item.foodMenuComponents?.length || item.foodMenuModifiers?.length) ? `<tr><td colspan="3" class="food-customization">${(item.foodMenuComponents??[]).filter(value=>value.state==='REMOVED').map(value=>`<div>No ${escapeHtml(value.name)}</div>`).join('')}${(item.foodMenuModifiers??[]).map(value=>`<div>${escapeHtml(value)}</div>`).join('')}${(item.foodMenuComponents??[]).filter(value=>value.state==='EXTRA').map(value=>`<div>Extra ${escapeHtml(value.name)}${value.priceAdjustment>0?` +${formatMoney(value.priceAdjustment,receipt.currencyCode)}`:''}</div>`).join('')}</td></tr>` : ''}
      ${(item.depositTotal ?? 0) > 0 ? `<tr><td colspan="3" class="deposit-cell"><div class="deposit-block"><div class="deposit-row"><span class="deposit-label">${escapeHtml(receiptDepositLabel(item.depositType))}</span><span class="deposit-amount">${formatMoney(item.depositTotal ?? 0, receipt.currencyCode)}</span></div>${(item.depositQuantity ?? item.quantity) > 1 ? `<div class="deposit-calculation muted">${formatQuantity(item.depositQuantity ?? item.quantity)} × ${formatMoney(item.depositUnitAmount ?? 0, receipt.currencyCode)}</div>` : ''}</div></td></tr>` : ''}
  `).join('');

  const taxRows = receipt.taxSummaries.map((tax) => `
    <div class="row">
      <span>${escapeHtml(tax.componentName)}</span>
      <strong>${formatMoney(tax.taxAmount, receipt.currencyCode)}</strong>
    </div>
  `).join('');

  const paymentRows = receipt.payments.map((payment) => `
    <div class="row">
      <span>${escapeHtml(payment.method.replaceAll('_', ' '))}</span>
      <strong>${formatMoney(payment.method === 'CASH' ? (payment.cashSettlementAmount ?? payment.amount) : payment.amount, receipt.currencyCode)}</strong>
    </div>
  `).join('');

  return `
    <div class="center">
      ${receipt.store.legalName?.trim() && receipt.store.legalName.trim() !== receipt.store.name?.trim() ? `<div><strong>${escapeHtml(receipt.store.legalName.trim())}</strong></div>` : ''}
      <div class="store-name">${escapeHtml(receipt.store.name?.trim() || receipt.store.legalName?.trim() || 'Store')}</div>
      ${receipt.store.address?.trim() ? `<div class="store-address">${escapeHtml(receipt.store.address.trim())}</div>` : ''}
      ${receipt.store.phone ? `<br>${escapeHtml(receipt.store.phone)}` : ''}
      ${receipt.store.email ? `<br>${escapeHtml(receipt.store.email)}` : ''}
      <div class="receipt-type">${escapeHtml(receipt.brandTagline)}</div>
    </div>
    ${receipt.tokenNumber ? `<div class="token">ORDER ${escapeHtml(receipt.tokenNumber)}</div>` : ''}
    <div class="rule"></div>
    <div class="row"><span>Receipt</span><strong>#${escapeHtml(receipt.receiptNumber)}</strong></div>
    ${receipt.tokenNumber ? `<div class="row"><span>Order</span><strong>${escapeHtml(receipt.tokenNumber)}</strong></div>` : ''}
    <div class="row"><span>Date</span><strong>${escapeHtml(new Date(receipt.completedAt).toLocaleString())}</strong></div>
    <div class="row"><span>Register</span><strong>${escapeHtml(receipt.register.name)}</strong></div>
    <div class="row"><span>Cashier</span><strong>${escapeHtml(receipt.cashier.displayName)}</strong></div>
    <div class="rule"></div>
    <table>
      <thead>
        <tr><th>Item</th><th class="money">Qty</th><th class="money">Total</th></tr>
      </thead>
      <tbody>${itemRows}</tbody>
    </table>
    <div class="rule"></div>
    <div class="row"><span>Subtotal</span><strong>${formatMoney(receipt.subtotalAmount, receipt.currencyCode)}</strong></div>
    <div class="row"><span>${escapeHtml(receipt.discountName || 'Discount')}</span><strong>${receipt.discountAmount > 0 ? '-' : ''}${formatMoney(receipt.discountAmount, receipt.currencyCode)}</strong></div>
    ${(receipt.containerDepositTotal ?? 0) > 0 ? `<div class="row"><span>Container Deposits</span><strong>${formatMoney(receipt.containerDepositTotal ?? 0, receipt.currencyCode)}</strong></div>` : ''}
    ${taxRows}
    <div class="row total"><span>Total</span><strong>${formatMoney(receipt.totalAmount, receipt.currencyCode)}</strong></div>
    ${(receipt.cashRoundingAdjustment ?? 0) !== 0 ? `<div class="row"><span>Cash rounding</span><strong>${formatSignedMoney(receipt.cashRoundingAdjustment ?? 0, receipt.currencyCode)}</strong></div>
    <div class="row total"><span>Cash total</span><strong>${formatMoney(receipt.cashTotal ?? receipt.totalAmount, receipt.currencyCode)}</strong></div>` : ''}
    <div class="rule"></div>
    ${paymentRows}
    <div class="row"><span>Cash tendered</span><strong>${formatMoney(receipt.cashTendered, receipt.currencyCode)}</strong></div>
    <div class="row"><span>Change</span><strong>${formatMoney(receipt.changeDue, receipt.currencyCode)}</strong></div>
    <div class="rule"></div>
    ${receipt.tokenNumber ? `<div class="token">ORDER ${escapeHtml(receipt.tokenNumber)}</div>` : ''}
    <div class="center">Thank you</div>
    <div class="receipt-branding">
      <div class="powered-by">Powered by</div>
      <img class="brand-logo" src="${brandAssets.blackLogo}" alt="Merchtyl" onerror="this.remove()">
    </div>
  `;
}

function receiptDepositLabel(type?: 'BOTTLE' | 'CAN' | 'CASE' | 'OTHER' | null) {
  return type === 'BOTTLE' ? 'Bottle Deposit' : type === 'CAN' ? 'Can Deposit' : type === 'CASE' ? 'Case Deposit' : 'Container Deposit';
}

async function waitForPrintImages(document: Document, timeoutMs = 750) {
  const pending = Array.from(document.images).filter((image) => !image.complete);
  if (pending.length === 0) return;
  await Promise.race([
    Promise.all(pending.map((image) => new Promise<void>((resolve) => {
      image.addEventListener('load', () => resolve(), { once: true });
      image.addEventListener('error', () => resolve(), { once: true });
    }))),
    new Promise<void>((resolve) => window.setTimeout(resolve, timeoutMs))
  ]);
}

function escapeHtml(value: unknown) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function formatMoney(value: number, currencyCode: string) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(value);
}

function formatSignedMoney(value: number, currencyCode: string) {
  const formatted = formatMoney(Math.abs(value), currencyCode);
  return value < 0 ? `-${formatted}` : formatted;
}

function formatQuantity(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(4).replace(/0+$/, '').replace(/\.$/, '');
}

async function loadQzTray(): Promise<QzTrayApi> {
  if (typeof window === 'undefined') {
    throw new Error('QZ Tray is only available in a browser.');
  }

  try {
    const module = await import('qz-tray');
    return module.default as QzTrayApi;
  } catch (error) {
    const qz = (window as Window & { qz?: QzTrayApi }).qz;
    if (qz) {
      return qz;
    }
    throw new Error(`QZ Tray JavaScript bridge is not loaded. ${errorMessage(error)}`);
  }
}

async function printWithBrowser(receipt: ReceiptDocument, preferences: ReceiptPrinterPreferences, configuredPrinter?: BrowserReceiptPrinter) {
  const printer = configuredPrinter ?? new BrowserReceiptPrinter({ widthMm: preferences.widthMm });
  if (!(await printer.isAvailable())) {
    throw new Error('Browser receipt printing is unavailable');
  }
  for (let copy = 0; copy < preferences.copies; copy += 1) {
    await printer.print(receipt);
  }
}

async function printHtmlWithBrowser(html: string, preferences: ReceiptPrinterPreferences) {
  const printer = new BrowserReceiptPrinter({ widthMm: preferences.widthMm });
  if (!(await printer.isAvailable())) {
    throw new Error('Browser receipt printing is unavailable');
  }
  for (let copy = 0; copy < preferences.copies; copy += 1) {
    await printer.printHtml(html);
  }
}

function clampCopies(value: unknown) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return 1;
  }
  return Math.min(Math.max(Math.trunc(parsed), 1), 5);
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : String(error || 'Unknown error');
}

function decodeEscapedCommand(value: string) {
  return value.replace(/\\x([0-9a-fA-F]{2})/g, (_match, hex: string) => String.fromCharCode(Number.parseInt(hex, 16)));
}

function afterNextPaint() {
  return new Promise<void>((resolve) => {
    window.requestAnimationFrame(() => window.requestAnimationFrame(() => resolve()));
  });
}

function createBrowserPrintFrame(): PosPrintFrame {
  const frame = document.createElement('iframe');
  frame.title = 'Merchtyl POS print document';
  frame.setAttribute('aria-hidden', 'true');
  frame.style.position = 'fixed';
  frame.style.width = '0';
  frame.style.height = '0';
  frame.style.border = '0';
  frame.style.visibility = 'hidden';
  document.body.appendChild(frame);

  const printWindow = frame.contentWindow;
  if (!printWindow) {
    frame.remove();
    throw new Error('POS print document could not be created');
  }
  return {
    async render(html) {
      printWindow.document.open();
      printWindow.document.write(html);
      printWindow.document.close();
      if (printWindow.document.fonts?.ready) await printWindow.document.fonts.ready;
      await waitForPrintImages(printWindow.document);
      await nextPaint(printWindow);
    },
    async printAndWait() {
      printWindow.focus();
      await invokePrintAndWait(printWindow);
    },
    cleanup() { frame.remove(); }
  };
}

function nextPaint(target: Window) {
  return new Promise<void>((resolve) => target.requestAnimationFrame(() => target.requestAnimationFrame(() => resolve())));
}

function invokePrintAndWait(target: Window) {
  return new Promise<void>((resolve, reject) => {
    let settled = false;
    const finish = () => { if (!settled) { settled = true; clearTimeout(fallback); resolve(); } };
    const fallback = window.setTimeout(finish, 1500);
    target.addEventListener('afterprint', finish, { once: true });
    try {
      target.print();
    } catch (error) {
      clearTimeout(fallback);
      reject(error);
    }
  });
}
