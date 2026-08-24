import CableOutlinedIcon from '@mui/icons-material/CableOutlined';
import PrintOutlinedIcon from '@mui/icons-material/PrintOutlined';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Divider,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import * as React from 'react';
import {
  defaultCashDrawerPulseCommand,
  loadReceiptPrinterPreferences,
  printReceiptWithFallback,
  QzTrayReceiptPrinter,
  saveReceiptPrinterPreferences,
  testReceiptDocument,
  type ReceiptPrinterPreferences
} from '../pos/receiptPrinter';

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Printer request failed';
}

type PrinterStatus = {
  severity: 'success' | 'info' | 'warning' | 'error';
  message: string;
};

export function PrinterSettingsPage() {
  const [preferences, setPreferences] = React.useState<ReceiptPrinterPreferences>(() => loadReceiptPrinterPreferences());
  const [status, setStatus] = React.useState<PrinterStatus | null>(null);
  const [busy, setBusy] = React.useState(false);

  function update(next: ReceiptPrinterPreferences) {
    setPreferences(next);
    setStatus(null);
  }

  function save() {
    saveReceiptPrinterPreferences(preferences);
    setStatus({ severity: 'success', message: 'Printer settings saved.' });
  }

  async function checkQzAvailability() {
    setBusy(true);
    setStatus(null);
    try {
      const printer = new QzTrayReceiptPrinter({
        printerName: preferences.qzPrinterName,
        widthMm: preferences.widthMm,
        copies: preferences.copies,
        cashDrawerPulse: preferences.cashDrawerPulse
      });
      const available = await printer.isAvailable();
      setStatus(available
        ? { severity: 'success', message: 'QZ Tray is connected and the configured printer is available.' }
        : { severity: 'warning', message: 'QZ Tray is unavailable or the configured printer could not be found.' });
    } catch (error) {
      setStatus({ severity: 'error', message: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  }

  async function testPrint() {
    setBusy(true);
    setStatus(null);
    try {
      const result = await printReceiptWithFallback(testReceiptDocument(), preferences);
      setStatus(result.fallbackReason
        ? { severity: 'warning', message: `QZ Tray failed: ${result.fallbackReason}. Printed with browser instead.` }
        : { severity: 'success', message: result.printer === 'QZ_TRAY' ? 'QZ Tray test receipt sent.' : 'Browser test receipt opened.' });
    } catch (error) {
      setStatus({ severity: 'error', message: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  }

  async function disconnectQz() {
    setBusy(true);
    setStatus(null);
    try {
      await QzTrayReceiptPrinter.disconnect();
      setStatus({ severity: 'info', message: 'QZ Tray connection closed.' });
    } catch (error) {
      setStatus({ severity: 'error', message: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 980 }}>
      <Box>
        <Typography variant="h5" component="h1">Hardware printers</Typography>
        <Typography color="text.secondary">Configure receipt printing for this browser and device.</Typography>
      </Box>

      {status ? (
        <Alert
          severity={status.severity}
          action={<Button color="inherit" size="small" onClick={() => setStatus(null)}>Clear</Button>}
        >
          {status.message}
        </Alert>
      ) : null}

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Stack spacing={3}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <FormControl fullWidth>
                <InputLabel id="printer-mode-label">Receipt printer</InputLabel>
                <Select
                  labelId="printer-mode-label"
                  label="Receipt printer"
                  value={preferences.mode}
                  onChange={(event) => update({ ...preferences, mode: event.target.value as ReceiptPrinterPreferences['mode'] })}
                >
                  <MenuItem value="BROWSER">Browser print dialog</MenuItem>
                  <MenuItem value="QZ_TRAY">QZ Tray printer</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={6}>
              <TextField
                label="QZ printer name"
                value={preferences.qzPrinterName}
                disabled={preferences.mode !== 'QZ_TRAY'}
                onChange={(event) => update({ ...preferences, qzPrinterName: event.target.value })}
                fullWidth
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <FormControl fullWidth>
                <InputLabel id="settings-receipt-width-label">Receipt width</InputLabel>
                <Select
                  labelId="settings-receipt-width-label"
                  label="Receipt width"
                  value={preferences.widthMm}
                  onChange={(event) => update({ ...preferences, widthMm: Number(event.target.value) })}
                >
                  <MenuItem value={58}>58 mm</MenuItem>
                  <MenuItem value={80}>80 mm</MenuItem>
                  <MenuItem value={112}>112 mm</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={4}>
              <FormControl fullWidth>
                <InputLabel id="settings-receipt-copies-label">Copies</InputLabel>
                <Select
                  labelId="settings-receipt-copies-label"
                  label="Copies"
                  value={preferences.copies}
                  onChange={(event) => update({ ...preferences, copies: Number(event.target.value) })}
                >
                  {[1, 2, 3, 4, 5].map((copyCount) => (
                    <MenuItem key={copyCount} value={copyCount}>{copyCount}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={4}>
              <Stack spacing={1}>
                <FormControlLabel
                  control={(
                    <Checkbox
                      checked={preferences.autoPrint}
                      onChange={(event) => update({ ...preferences, autoPrint: event.target.checked })}
                    />
                  )}
                  label="Auto-print receipts"
                />
                <FormControlLabel
                  control={(
                    <Checkbox
                      checked={preferences.fallbackToBrowser}
                      disabled={preferences.mode !== 'QZ_TRAY'}
                      onChange={(event) => update({ ...preferences, fallbackToBrowser: event.target.checked })}
                    />
                  )}
                  label="Fallback to browser"
                />
              </Stack>
            </Grid>
          </Grid>

          <Divider />

          <Stack spacing={2}>
            <Typography variant="h6" component="h2">Cash drawer</Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} md={4}>
                <FormControlLabel
                  control={(
                    <Checkbox
                      checked={preferences.cashDrawerPulse.enabled}
                      disabled={preferences.mode !== 'QZ_TRAY'}
                      onChange={(event) => update({
                        ...preferences,
                        cashDrawerPulse: { ...preferences.cashDrawerPulse, enabled: event.target.checked }
                      })}
                    />
                  )}
                  label="Pulse on receipt print"
                />
              </Grid>
              <Grid item xs={12} md={8}>
                <TextField
                  label="ESC/POS pulse command"
                  value={preferences.cashDrawerPulse.command}
                  disabled={preferences.mode !== 'QZ_TRAY' || !preferences.cashDrawerPulse.enabled}
                  onChange={(event) => update({
                    ...preferences,
                    cashDrawerPulse: { ...preferences.cashDrawerPulse, command: event.target.value || defaultCashDrawerPulseCommand }
                  })}
                  fullWidth
                />
              </Grid>
            </Grid>
          </Stack>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <Button variant="contained" startIcon={<SaveOutlinedIcon />} onClick={save}>
              Save settings
            </Button>
            <Button variant="outlined" startIcon={<CableOutlinedIcon />} disabled={busy} onClick={checkQzAvailability}>
              Check QZ
            </Button>
            <Button variant="outlined" startIcon={<PrintOutlinedIcon />} disabled={busy} onClick={testPrint}>
              Test print
            </Button>
            <Button variant="text" disabled={busy} onClick={disconnectQz}>
              Disconnect QZ
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Stack>
  );
}
