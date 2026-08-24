import QrCodeScannerOutlinedIcon from '@mui/icons-material/QrCodeScannerOutlined';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import {
  Alert,
  Box,
  Button,
  Checkbox,
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
  KeyboardWedgeScanner,
  loadBarcodeScannerPreferences,
  saveBarcodeScannerPreferences,
  type BarcodeScannerPreferences
} from '../hardware/barcodeScanner';

type ScanMessage = {
  severity: 'success' | 'info' | 'warning';
  text: string;
};

export function ScannerTestPage() {
  const [preferences, setPreferences] = React.useState<BarcodeScannerPreferences>(() => loadBarcodeScannerPreferences());
  const [message, setMessage] = React.useState<ScanMessage | null>(null);
  const [lastScan, setLastScan] = React.useState<string | null>(null);
  const [manualValue, setManualValue] = React.useState('');
  const scannerRef = React.useRef(new KeyboardWedgeScanner(preferences));

  React.useEffect(() => {
    scannerRef.current = new KeyboardWedgeScanner(preferences);
  }, [preferences]);

  React.useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      const result = scannerRef.current.handleKeyDown(event);
      if (result?.type === 'scan') {
        setLastScan(result.value);
        setMessage({ severity: 'success', text: `Captured scanner barcode ${result.value}.` });
      }
      if (result?.type === 'duplicate') {
        setMessage({ severity: 'warning', text: `Ignored duplicate barcode ${result.value}.` });
      }
    }

    window.addEventListener('keydown', handleKeyDown, true);
    return () => window.removeEventListener('keydown', handleKeyDown, true);
  }, []);

  function update(next: BarcodeScannerPreferences) {
    setPreferences(next);
    setMessage(null);
  }

  function save() {
    saveBarcodeScannerPreferences(preferences);
    setMessage({ severity: 'success', text: 'Scanner settings saved.' });
  }

  function recordManual(event: React.FormEvent) {
    event.preventDefault();
    const value = manualValue.trim();
    if (!value) {
      return;
    }
    setLastScan(value);
    setMessage({ severity: 'info', text: `Manual barcode entry ${value}.` });
    setManualValue('');
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 980 }}>
      <Box>
        <Typography variant="h5" component="h1">Scanner test</Typography>
        <Typography color="text.secondary">Test keyboard-wedge barcode scanners without WebUSB, WebHID, or vendor drivers.</Typography>
      </Box>

      {message ? (
        <Alert
          severity={message.severity}
          action={<Button color="inherit" size="small" onClick={() => setMessage(null)}>Clear</Button>}
        >
          {message.text}
        </Alert>
      ) : null}

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Stack spacing={3}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={4}>
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={preferences.enabled}
                    onChange={(event) => update({ ...preferences, enabled: event.target.checked })}
                  />
                )}
                label="Enable scanner capture"
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField
                label="Minimum length"
                type="number"
                value={preferences.minLength}
                inputProps={{ min: 1, max: 64 }}
                onChange={(event) => update({ ...preferences, minLength: Number(event.target.value) })}
                fullWidth
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <FormControl fullWidth>
                <InputLabel id="scanner-suffix-label">Suffix</InputLabel>
                <Select
                  labelId="scanner-suffix-label"
                  label="Suffix"
                  value={preferences.suffix}
                  onChange={(event) => update({ ...preferences, suffix: event.target.value as BarcodeScannerPreferences['suffix'] })}
                >
                  <MenuItem value="Enter">Enter</MenuItem>
                  <MenuItem value="Tab">Tab</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={6}>
              <TextField
                label="Maximum key delay ms"
                type="number"
                value={preferences.maxInterKeyDelayMs}
                inputProps={{ min: 10, max: 250 }}
                onChange={(event) => update({ ...preferences, maxInterKeyDelayMs: Number(event.target.value) })}
                fullWidth
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <TextField
                label="Duplicate prevention ms"
                type="number"
                value={preferences.duplicatePreventionMs}
                inputProps={{ min: 0, max: 10000 }}
                onChange={(event) => update({ ...preferences, duplicatePreventionMs: Number(event.target.value) })}
                fullWidth
              />
            </Grid>
          </Grid>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <Button variant="contained" startIcon={<SaveOutlinedIcon />} onClick={save}>
              Save settings
            </Button>
            <Button variant="outlined" startIcon={<QrCodeScannerOutlinedIcon />} onClick={() => scannerRef.current.reset()}>
              Reset capture
            </Button>
          </Stack>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Stack spacing={2}>
          <Typography variant="h6" component="h2">Capture result</Typography>
          <Box sx={{ border: '1px dashed', borderColor: 'divider', borderRadius: 1, p: 2 }}>
            <Typography color="text.secondary">Last barcode</Typography>
            <Typography variant="h5" sx={{ fontFamily: 'monospace' }}>{lastScan ?? 'None'}</Typography>
          </Box>
          <Box component="form" onSubmit={recordManual}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              <TextField
                label="Manual barcode"
                value={manualValue}
                onChange={(event) => setManualValue(event.target.value)}
                fullWidth
              />
              <Button type="submit" variant="outlined" disabled={!manualValue.trim()}>
                Record manual
              </Button>
            </Stack>
          </Box>
        </Stack>
      </Paper>
    </Stack>
  );
}
