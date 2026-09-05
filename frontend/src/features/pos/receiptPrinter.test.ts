import {
  BrowserReceiptPrinter,
  printReceiptWithFallback,
  QzTrayReceiptPrinter,
  receiptHtml,
  receiptPrintStyles,
  type ReceiptPrinterPreferences
} from './receiptPrinter';
import type { ReceiptDocument } from '../../api/types';

const qzState = vi.hoisted(() => ({
  qz: null as unknown
}));

vi.mock('qz-tray', () => ({
  default: {
    websocket: {
      isActive: () => (qzState.qz as any).websocket.isActive(),
      connect: () => (qzState.qz as any).websocket.connect(),
      disconnect: () => (qzState.qz as any).websocket.disconnect()
    },
    printers: {
      find: (printerName?: string) => (qzState.qz as any).printers.find(printerName)
    },
    configs: {
      create: (printerName: string, options?: Record<string, unknown>) => (qzState.qz as any).configs.create(printerName, options)
    },
    print: (config: unknown, data: unknown[]) => (qzState.qz as any).print(config, data)
  }
}));

function receipt(): ReceiptDocument {
  return {
    brandName: 'Merchtyl',
    brandTagline: 'Point of sale receipt',
    store: {
      id: 'store-id',
      code: 'MAIN',
      name: 'Main Store',
      legalName: null,
      address: '100 Market Street',
      phone: null,
      email: null
    },
    register: {
      id: 'register-id',
      code: 'FRONT-1',
      name: 'Front Register'
    },
    cashier: {
      id: 'cashier-id',
      displayName: 'Cashier One',
      email: 'cashier@example.local'
    },
    receiptNumber: 'RCT-2026-07-27-00000000',
    saleId: 'sale-id',
    saleNumber: 'sale-id',
    businessDate: '2026-07-27',
    completedAt: '2026-07-27T12:00:00Z',
    currencyCode: 'USD',
    items: [{
      id: 'item-id',
      productId: 'product-id',
      lineNumber: 1,
      productSku: 'COFFEE',
      productName: 'Coffee',
      quantity: 2,
      unitPrice: 5,
      completedProductCost: 2,
      completedProductPrice: 5,
      completedProductCapabilities: 'TRACK_INVENTORY',
      discountAmount: 0,
      lineSubtotal: 10,
      taxAmount: 1.5,
      lineTotal: 11.5
    }],
    subtotalAmount: 10,
    discountAmount: 0,
    taxSummaries: [{ componentCode: 'TAX', componentName: 'Sales tax', taxableAmount: 10, taxAmount: 1.5 }],
    taxAmount: 1.5,
    totalAmount: 11.5,
    payments: [{
      id: 'payment-id',
      method: 'CASH',
      amount: 11.5,
      cashTendered: 20,
      changeDue: 8.5,
      reference: null,
      completedAt: '2026-07-27T12:00:00Z'
    }],
    cashTendered: 20,
    changeDue: 8.5
  };
}

describe('BrowserReceiptPrinter', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    qzState.qz = null;
  });

  it('renders receipt HTML with print width styles', () => {
    const html = receiptHtml(receipt(), 58);

    expect(html).toContain('@page { size: 58mm auto; margin: 0; }');
    expect(html).toContain('Merchtyl');
    expect(html).toContain('Coffee');
    expect(html).toContain('Sales tax');
  });

  it('defines an 80mm print-only receipt layout', () => {
    const printCss = JSON.stringify(receiptPrintStyles);
    expect(printCss).toContain('@media print');
    expect(printCss).toContain('80mm auto');
    expect(printCss).toContain('.receipt-print-root');
    expect(printCss).toContain("[role='dialog']");
  });

  it('renders, prints, waits, and cleans up through a hidden POS frame without opening a popup', async () => {
    const calls: string[] = [];
    const popup = vi.spyOn(window, 'open');
    const printer = new BrowserReceiptPrinter({ widthMm: 80, createPrintFrame: () => ({
      render: async (html) => { expect(html).toContain('width: 80mm'); calls.push('render'); },
      printAndWait: async () => { calls.push('print'); },
      cleanup: () => { calls.push('cleanup'); }
    }) });
    await expect(printer.isAvailable()).resolves.toBe(true);
    await printer.print(receipt());

    expect(calls).toEqual(['render', 'print', 'cleanup']);
    expect(popup).not.toHaveBeenCalled();
  });

  it('cleans up a failed POS print frame', async () => {
    const cleanup = vi.fn();
    const printer = new BrowserReceiptPrinter({ createPrintFrame: () => ({
      render: async () => undefined,
      printAndWait: async () => { throw new Error('print unavailable'); },
      cleanup
    }) });

    await expect(printer.print(receipt())).rejects.toThrow('print unavailable');
    expect(cleanup).toHaveBeenCalledOnce();
  });
});

describe('QzTrayReceiptPrinter', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    qzState.qz = null;
  });

  function qzMock(overrides: Partial<{
    active: boolean;
    connect: ReturnType<typeof vi.fn>;
    disconnect: ReturnType<typeof vi.fn>;
    find: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    print: ReturnType<typeof vi.fn>;
  }> = {}) {
    const qz = {
      websocket: {
        isActive: vi.fn(() => overrides.active ?? false),
        connect: overrides.connect ?? vi.fn().mockResolvedValue(undefined),
        disconnect: overrides.disconnect ?? vi.fn().mockResolvedValue(undefined)
      },
      printers: {
        find: overrides.find ?? vi.fn().mockResolvedValue('Receipt Printer')
      },
      configs: {
        create: overrides.create ?? vi.fn().mockReturnValue({ printer: 'Receipt Printer' })
      },
      print: overrides.print ?? vi.fn().mockResolvedValue(undefined)
    };
    qzState.qz = qz;
    return qz;
  }

  it('detects configured QZ printer availability', async () => {
    const qz = qzMock();

    await expect(new QzTrayReceiptPrinter({ printerName: 'Receipt Printer' }).isAvailable()).resolves.toBe(true);

    expect(qz.websocket.connect).toHaveBeenCalledOnce();
    expect(qz.printers.find).toHaveBeenCalledWith('Receipt Printer');
  });

  it('prints configured copies and an optional cash drawer pulse', async () => {
    const qz = qzMock({ active: true });
    const printer = new QzTrayReceiptPrinter({
      printerName: 'Receipt Printer',
      widthMm: 58,
      copies: 2,
      cashDrawerPulse: { enabled: true, command: '\\x1Bp\\x00\\x19\\xFA' }
    });

    await printer.print(receipt());

    expect(qz.websocket.connect).not.toHaveBeenCalled();
    expect(qz.configs.create).toHaveBeenCalledWith('Receipt Printer', expect.objectContaining({ jobName: expect.stringContaining('Merchtyl receipt') }));
    expect(qz.print).toHaveBeenCalledTimes(3);
    expect(qz.print).toHaveBeenNthCalledWith(1, { printer: 'Receipt Printer' }, [expect.objectContaining({ type: 'raw', data: '\x1Bp\x00\x19\xFA' })]);
    expect(qz.print).toHaveBeenNthCalledWith(2, { printer: 'Receipt Printer' }, [expect.objectContaining({ type: 'pixel', data: expect.stringContaining('width: 58mm') })]);
  });

  it('reports unavailable QZ when printer lookup fails', async () => {
    qzMock({ find: vi.fn().mockRejectedValue(new Error('missing printer')) });

    await expect(new QzTrayReceiptPrinter({ printerName: 'Receipt Printer' }).isAvailable()).resolves.toBe(false);
  });

  it('falls back to browser printing when QZ printing fails', async () => {
    qzMock({ print: vi.fn().mockRejectedValue(new Error('offline')) });
    const print = vi.fn();
    const browserPrinter = new BrowserReceiptPrinter({ createPrintFrame: () => ({
      render: async () => undefined, printAndWait: async () => { print(); }, cleanup: vi.fn()
    }) });
    const preferences: ReceiptPrinterPreferences = {
      mode: 'QZ_TRAY',
      receiptPrintMode: 'BROWSER_DIALOG',
      widthMm: 80,
      copies: 1,
      autoPrint: false,
      autoPrintReceipt: false,
      qzPrinterName: 'Receipt Printer',
      fallbackToBrowser: true,
      cashDrawerPulse: { enabled: false, command: '\\x1Bp\\x00\\x19\\xFA' }
    };

    await expect(printReceiptWithFallback(receipt(), preferences, browserPrinter)).resolves.toEqual({
      printer: 'BROWSER',
      fallbackReason: 'QZ Tray printing failed: offline'
    });
    expect(print).toHaveBeenCalledOnce();
  });
});
