import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material';
import { theme } from '../../app/theme';
import { ScannerTestPage } from './ScannerTestPage';
import { barcodeScannerPreferencesKey } from '../hardware/barcodeScanner';

function renderPage() {
  return render(
    <ThemeProvider theme={theme}>
      <ScannerTestPage />
    </ThemeProvider>
  );
}

describe('ScannerTestPage', () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it('captures scanner bursts, ignores duplicates, and supports manual fallback', async () => {
    renderPage();

    expect(screen.getByRole('heading', { name: 'Scanner test' })).toBeInTheDocument();

    for (const key of ['1', '2', '3', '4', '5', 'Enter']) {
      fireEvent.keyDown(window, { key });
    }

    expect(await screen.findByText('Captured scanner barcode 12345.')).toBeInTheDocument();
    expect(screen.getByText('12345')).toBeInTheDocument();

    for (const key of ['1', '2', '3', '4', '5', 'Enter']) {
      fireEvent.keyDown(window, { key });
    }

    expect(await screen.findByText('Ignored duplicate barcode 12345.')).toBeInTheDocument();

    await userEvent.type(screen.getByRole('textbox', { name: 'Manual barcode' }), 'ABC-9');
    await userEvent.click(screen.getByRole('button', { name: 'Record manual' }));

    expect(await screen.findByText('Manual barcode entry ABC-9.')).toBeInTheDocument();
    expect(screen.getByText('ABC-9')).toBeInTheDocument();
  });

  it('saves configurable minimum length and suffix', async () => {
    renderPage();

    await userEvent.clear(screen.getByRole('spinbutton', { name: 'Minimum length' }));
    await userEvent.type(screen.getByRole('spinbutton', { name: 'Minimum length' }), '6');
    await userEvent.click(screen.getByRole('combobox', { name: 'Suffix' }));
    await userEvent.click(within(screen.getByRole('listbox')).getByText('Tab'));
    await userEvent.click(screen.getByRole('button', { name: 'Save settings' }));

    expect(await screen.findByText('Scanner settings saved.')).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem(barcodeScannerPreferencesKey) ?? '{}')).toMatchObject({
      minLength: 6,
      suffix: 'Tab'
    });
  });
});
