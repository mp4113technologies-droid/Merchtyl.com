import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material';
import { theme } from '../../app/theme';
import { PrinterSettingsPage } from './PrinterSettingsPage';
import { receiptPrinterPreferencesKey, type ReceiptPrinterPreferences } from '../pos/receiptPrinter';

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

function renderPage() {
  return render(
    <ThemeProvider theme={theme}>
      <PrinterSettingsPage />
    </ThemeProvider>
  );
}

function qzMock() {
  const qz = {
    websocket: {
      isActive: vi.fn(() => true),
      connect: vi.fn().mockResolvedValue(undefined),
      disconnect: vi.fn().mockResolvedValue(undefined)
    },
    printers: {
      find: vi.fn().mockResolvedValue('Receipt Printer')
    },
    configs: {
      create: vi.fn().mockReturnValue({ printer: 'Receipt Printer' })
    },
    print: vi.fn().mockResolvedValue(undefined)
  };
  qzState.qz = qz;
  return qz;
}

function qzPreferences(overrides: Partial<ReceiptPrinterPreferences> = {}): ReceiptPrinterPreferences {
  return {
    mode: 'QZ_TRAY',
    widthMm: 58,
    copies: 2,
    autoPrint: true,
    qzPrinterName: 'Receipt Printer',
    fallbackToBrowser: false,
    cashDrawerPulse: { enabled: true, command: '\\x1Bp\\x00\\x19\\xFA' },
    ...overrides
  };
}

describe('PrinterSettingsPage', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
    qzState.qz = null;
  });

  it('loads saved QZ settings, checks availability, test prints, and clears status', async () => {
    const qz = qzMock();
    window.localStorage.setItem(receiptPrinterPreferencesKey, JSON.stringify(qzPreferences()));

    renderPage();

    expect(screen.getByLabelText('QZ printer name')).toHaveValue('Receipt Printer');
    await userEvent.click(screen.getByRole('button', { name: 'Check QZ' }));

    expect(await screen.findByText('QZ Tray is connected and the configured printer is available.')).toBeInTheDocument();
    expect(qz.printers.find).toHaveBeenCalledWith('Receipt Printer');

    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));
    expect(screen.queryByText('QZ Tray is connected and the configured printer is available.')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Test print' }));

    expect(await screen.findByText('QZ Tray test receipt sent.')).toBeInTheDocument();
    expect(qz.print).toHaveBeenCalledTimes(3);
    expect(qz.print).toHaveBeenNthCalledWith(1, { printer: 'Receipt Printer' }, [expect.objectContaining({ type: 'raw' })]);
    expect(qz.print).toHaveBeenNthCalledWith(2, { printer: 'Receipt Printer' }, [expect.objectContaining({ type: 'pixel', data: expect.stringContaining('width: 58mm') })]);
  });

  it('saves browser fallback printer settings', async () => {
    renderPage();

    await userEvent.click(screen.getByLabelText('Auto-print receipts'));
    await userEvent.click(screen.getByRole('combobox', { name: 'Receipt width' }));
    await userEvent.click(within(screen.getByRole('listbox')).getByText('112 mm'));
    await userEvent.click(screen.getByRole('combobox', { name: 'Copies' }));
    await userEvent.click(within(screen.getByRole('listbox')).getByText('3'));
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }));

    expect(await screen.findByText('Printer settings saved.')).toBeInTheDocument();
    await waitFor(() => {
      const saved = JSON.parse(window.localStorage.getItem(receiptPrinterPreferencesKey) ?? '{}') as ReceiptPrinterPreferences;
      expect(saved).toMatchObject({
        mode: 'BROWSER',
        widthMm: 112,
        copies: 3,
        autoPrint: true,
        fallbackToBrowser: true
      });
    });
  });
});
