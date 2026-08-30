import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutline';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import PauseCircleOutlineIcon from '@mui/icons-material/PauseCircleOutline';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline';
import PointOfSaleOutlinedIcon from '@mui/icons-material/PointOfSaleOutlined';
import PaymentOutlinedIcon from '@mui/icons-material/PaymentOutlined';
import PrintOutlinedIcon from '@mui/icons-material/PrintOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import RemoveCircleOutlineIcon from '@mui/icons-material/RemoveCircleOutline';
import SearchIcon from '@mui/icons-material/Search';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  GlobalStyles,
  Grid,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  addSaleItem,
  cancelSale,
  completeSale,
  createSaleDraft,
  getCurrentRegisterSession,
  getSale,
  holdSale,
  listDevices,
  listProducts,
  lookupPosBarcode,
  listRegisters,
  listSales,
  listStores,
  recalculateSale,
  recordSalePayment,
  getSaleReceipt,
  reprintSaleReceipt,
  removeSaleItem,
  resumeSale,
  updateSaleItemQuantity
} from '../../api/client';
import type { Device, PaymentMethod, PosBarcodeLookup, Product, Receipt, ReceiptDocument, Register, RegisterSession, Sale, Store } from '../../api/types';
import { getApplicationDeviceIdentifier } from '../../app/deviceIdentity';
import { useSession } from '../../app/session';
import {
  KeyboardWedgeScanner,
  loadBarcodeScannerPreferences,
  type BarcodeScannerPreferences
} from '../hardware/barcodeScanner';
import {
  clearDraftCartRecovery,
  loadDraftCartRecovery,
  saleFromDraftCartRecord,
  saveDraftCartRecovery
} from './draftCartRecovery';
import { registerSessionKeys } from '../registersessions/registerSessionKeys';
import {
  loadReceiptPrinterPreferences,
  printReceiptWithFallback,
  printRenderedReceipt,
  receiptPrintStyles,
  saveReceiptPrinterPreferences,
  type ReceiptPrinterPreferences
} from './receiptPrinter';

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function posErrorMessage(error: unknown) {
  const message = errorMessage(error);
  if (message.includes('BARCODE_NOT_FOUND')) return 'No product was found for this barcode.';
  if (message.includes('PRODUCT_NOT_ACTIVE') || message.includes('PRODUCT_NOT_AVAILABLE_IN_STORE')) return 'This product is not available for sale.';
  if (message.includes('PRODUCT_OUT_OF_STOCK')) return 'This item is out of stock.';
  if (message.includes('BARCODE_AMBIGUOUS')) return 'This barcode is linked to more than one product. Please ask a manager to correct the product setup.';
  if (message.includes('AGE_VERIFICATION_REQUIRED')) return "Please verify the customer's age before completing this sale.";
  return message;
}

function posScanDebug(stage: string, details?: Record<string, unknown>) {
  if (import.meta.env.DEV) {
    console.debug(`[POS SCAN] ${stage}`, details ?? {});
  }
}

function money(value: number, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(value);
}

function roundedMoney(value: number) {
  return Number(value.toFixed(2));
}

function completionKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `complete-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function storeLabel(store?: Store) {
  return store ? `${store.name} (${store.code})` : 'Unknown store';
}

function registerLabel(register?: Register) {
  return register ? `${register.name} (${register.code})` : 'Unknown register';
}

function deviceLabel(device?: Device) {
  return device ? `${device.displayName} (${device.deviceIdentifier})` : 'Not recorded';
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 260 }}>
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function IdentityStrip({
  session,
  store,
  register,
  device
}: {
  session: RegisterSession;
  store?: Store;
  register?: Register;
  device?: Device;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Grid container spacing={2}>
        <Grid item xs={12} md={3}>
          <Typography variant="body2" color="text.secondary">Store</Typography>
          <Typography fontWeight={700}>{storeLabel(store)}</Typography>
        </Grid>
        <Grid item xs={12} md={3}>
          <Typography variant="body2" color="text.secondary">Register</Typography>
          <Typography fontWeight={700}>{registerLabel(register)}</Typography>
        </Grid>
        <Grid item xs={12} md={3}>
          <Typography variant="body2" color="text.secondary">Device</Typography>
          <Typography fontWeight={700}>{deviceLabel(device)}</Typography>
        </Grid>
        <Grid item xs={12} md={3}>
          <Typography variant="body2" color="text.secondary">Cashier</Typography>
          <Typography fontWeight={700}>{session.assignedCashierDisplayName}</Typography>
          <Typography variant="body2" color="text.secondary">{session.assignedCashierEmail}</Typography>
        </Grid>
      </Grid>
    </Paper>
  );
}

function ProductSearchResults({
  products,
  currencyCode,
  onAdd,
  disabled
}: {
  products: Product[];
  currencyCode: string;
  onAdd: (product: Product) => void;
  disabled: boolean;
}) {
  if (products.length === 0) {
    return <Alert severity="info">No products match the current search.</Alert>;
  }

  return (
    <Table size="small" aria-label="Product search results">
      <TableHead>
        <TableRow>
          <TableCell>Product</TableCell>
          <TableCell>SKU</TableCell>
          <TableCell align="right">Price</TableCell>
          <TableCell align="right">Add</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {products.map((product) => (
          <TableRow key={product.id} hover>
            <TableCell>
              <Typography fontWeight={700}>{product.name}</Typography>
              <Typography variant="body2" color="text.secondary">{product.sellableType.replaceAll('_', ' ')}</Typography>
            </TableCell>
            <TableCell sx={{ fontFamily: 'monospace' }}>{product.sku}</TableCell>
            <TableCell align="right">{money(product.price, currencyCode)}</TableCell>
            <TableCell align="right">
              <Tooltip title={`Add ${product.name}`}>
                <span>
                  <IconButton aria-label={`Add ${product.name}`} onClick={() => onAdd(product)} disabled={disabled || !product.active}>
                    <AddCircleOutlineIcon />
                  </IconButton>
                </span>
              </Tooltip>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function CartLines({
  sale,
  onQuantity,
  onRemove,
  busy
}: {
  sale: Sale | null;
  onQuantity: (itemId: string, quantity: number) => void;
  onRemove: (itemId: string) => void;
  busy: boolean;
}) {
  if (!sale || sale.items.length === 0) {
    return (
      <Box sx={{ p: 3 }}>
        <Stack spacing={1} alignItems="center" textAlign="center">
          <PointOfSaleOutlinedIcon color="primary" sx={{ fontSize: 40 }} />
          <Typography variant="h6">Cart is empty</Typography>
          <Typography color="text.secondary">Scan a barcode or add a product from search.</Typography>
        </Stack>
      </Box>
    );
  }

  return (
    <Table aria-label="Cart lines">
      <TableHead>
        <TableRow>
          <TableCell>Item</TableCell>
          <TableCell align="center">Quantity</TableCell>
          <TableCell align="right">Unit</TableCell>
          <TableCell align="right">Tax</TableCell>
          <TableCell align="right">Total</TableCell>
          <TableCell align="right">Remove</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {sale.items.map((item) => (
          <TableRow key={item.id} hover>
            <TableCell>
              <Typography fontWeight={700}>{item.productName}</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{item.productSku}</Typography>
            </TableCell>
            <TableCell align="center">
              <Stack direction="row" spacing={1} justifyContent="center" alignItems="center">
                <Tooltip title={`Decrease ${item.productName}`}>
                  <span>
                    <IconButton
                      aria-label={`Decrease ${item.productName}`}
                      disabled={busy || item.quantity <= 1}
                      onClick={() => onQuantity(item.id, Number((item.quantity - 1).toFixed(4)))}
                    >
                      <RemoveCircleOutlineIcon />
                    </IconButton>
                  </span>
                </Tooltip>
                <TextField
                  key={`${item.id}:${item.quantity}`}
                  aria-label={`Quantity for ${item.productName}`}
                  type="number"
                  size="small"
                  defaultValue={item.quantity}
                  inputProps={{ min: 0.0001, step: 1, style: { textAlign: 'center' } }}
                  sx={{ width: 96 }}
                  disabled={busy}
                  onBlur={(event) => {
                    const next = Number(event.currentTarget.value);
                    if (Number.isFinite(next) && next > 0 && next !== item.quantity) {
                      onQuantity(item.id, next);
                    }
                  }}
                />
                <Tooltip title={`Increase ${item.productName}`}>
                  <span>
                    <IconButton
                      aria-label={`Increase ${item.productName}`}
                      disabled={busy}
                      onClick={() => onQuantity(item.id, Number((item.quantity + 1).toFixed(4)))}
                    >
                      <AddCircleOutlineIcon />
                    </IconButton>
                  </span>
                </Tooltip>
              </Stack>
            </TableCell>
            <TableCell align="right">{money(item.unitPrice, sale.currencyCode)}</TableCell>
            <TableCell align="right">{money(item.estimatedTaxAmount, sale.currencyCode)}</TableCell>
            <TableCell align="right">{money(item.lineTotal, sale.currencyCode)}</TableCell>
            <TableCell align="right">
              <Tooltip title={`Remove ${item.productName}`}>
                <span>
                  <IconButton aria-label={`Remove ${item.productName}`} disabled={busy} onClick={() => onRemove(item.id)}>
                    <DeleteOutlineIcon />
                  </IconButton>
                </span>
              </Tooltip>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function TotalsPanel({ sale, currencyCode }: { sale: Sale | null; currencyCode: string }) {
  const subtotal = sale?.subtotalAmount ?? 0;
  const discount = sale?.discountAmount ?? 0;
  const tax = sale?.estimatedTaxAmount ?? 0;
  const total = sale?.totalAmount ?? 0;

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Stack direction="row" justifyContent="space-between">
          <Typography color="text.secondary">Subtotal</Typography>
          <Typography>{money(subtotal, currencyCode)}</Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between">
          <Typography color="text.secondary">Discount</Typography>
          <Typography>{money(discount, currencyCode)}</Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between">
          <Typography color="text.secondary">Estimated tax</Typography>
          <Typography>{money(tax, currencyCode)}</Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between" sx={{ pt: 1, borderTop: '1px solid', borderColor: 'divider' }}>
          <Typography variant="h6">Total</Typography>
          <Typography variant="h6">{money(total, currencyCode)}</Typography>
        </Stack>
      </Stack>
    </Paper>
  );
}

const paymentMethods: Array<{ value: PaymentMethod; label: string }> = [
  { value: 'CASH', label: 'Cash' },
  { value: 'DEBIT', label: 'Debit' },
  { value: 'CREDIT', label: 'Credit' },
  { value: 'GIFT_CARD', label: 'Gift card' },
  { value: 'STORE_CREDIT', label: 'Store credit' },
  { value: 'OTHER', label: 'Other' }
];

export function PaymentDialog({
  open,
  sale,
  busy,
  onClose,
  onSubmit
}: {
  open: boolean;
  sale: Sale | null;
  busy: boolean;
  onClose: () => void;
  onSubmit: (payment: { method: PaymentMethod; amount: number; cashTendered?: number; reference?: string; notes?: string }) => void;
}) {
  const balanceDue = roundedMoney(Math.max(0, sale?.balanceDue ?? sale?.totalAmount ?? 0));
  const currencyCode = sale?.currencyCode ?? 'USD';
  const [method, setMethod] = React.useState<PaymentMethod>('CASH');
  const [amount, setAmount] = React.useState('');
  const [cashTendered, setCashTendered] = React.useState('');
  const [reference, setReference] = React.useState('');
  const [notes, setNotes] = React.useState('');

  React.useEffect(() => {
    if (open) {
      setMethod('CASH');
      setAmount(balanceDue > 0 ? balanceDue.toFixed(2) : '');
      setCashTendered(balanceDue > 0 ? balanceDue.toFixed(2) : '');
      setReference('');
      setNotes('');
    }
  }, [balanceDue, open]);

  const parsedAmount = Number(amount);
  const parsedCashTendered = Number(cashTendered);
  const changeDue = method === 'CASH' && Number.isFinite(parsedCashTendered) && Number.isFinite(parsedAmount)
    ? roundedMoney(Math.max(0, parsedCashTendered - parsedAmount))
    : 0;
  const validation = (() => {
    if (!sale || sale.items.length === 0) {
      return 'Add at least one item before taking payment.';
    }
    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      return 'Payment amount must be greater than zero.';
    }
    if (parsedAmount > balanceDue) {
      return 'Payment amount cannot exceed the remaining balance.';
    }
    if (method === 'CASH' && (!Number.isFinite(parsedCashTendered) || parsedCashTendered < parsedAmount)) {
      return 'Cash tendered must cover the payment amount.';
    }
    return null;
  })();

  function appendCashInput(value: string) {
    setCashTendered((current) => {
      if (value === 'clear') {
        return '';
      }
      if (value === 'back') {
        return current.slice(0, -1);
      }
      if (value === '.' && current.includes('.')) {
        return current;
      }
      return `${current}${value}`;
    });
  }

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (validation || !sale || busy) {
      return;
    }
    onSubmit({
      method,
      amount: roundedMoney(parsedAmount),
      cashTendered: method === 'CASH' ? roundedMoney(parsedCashTendered) : undefined,
      reference: reference.trim() || undefined,
      notes: notes.trim() || undefined
    });
  }

  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} fullWidth maxWidth="sm">
      <Box component="form" onSubmit={submit}>
        <DialogTitle>Take payment</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Stack direction="row" justifyContent="space-between">
              <Typography color="text.secondary">Remaining balance</Typography>
              <Typography variant="h6">{money(balanceDue, currencyCode)}</Typography>
            </Stack>
            {sale && sale.payments.length > 0 ? (
              <Paper variant="outlined" sx={{ p: 1.5 }}>
                <Typography variant="subtitle2" gutterBottom>Payments recorded</Typography>
                <Stack spacing={1}>
                  {sale.payments.map((payment) => (
                    <Stack key={payment.id} direction="row" justifyContent="space-between">
                      <Typography color="text.secondary">{payment.method.replaceAll('_', ' ')}</Typography>
                      <Typography>{money(payment.amount, sale.currencyCode)}</Typography>
                    </Stack>
                  ))}
                </Stack>
              </Paper>
            ) : null}
            <FormControl fullWidth>
              <InputLabel id="payment-method-label">Payment method</InputLabel>
              <Select
                labelId="payment-method-label"
                label="Payment method"
                value={method}
                disabled={busy}
                onChange={(event) => setMethod(event.target.value as PaymentMethod)}
              >
                {paymentMethods.map((item) => (
                  <MenuItem key={item.value} value={item.value}>{item.label}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Payment amount"
              type="number"
              value={amount}
              disabled={busy}
              inputProps={{ min: 0.01, step: 0.01 }}
              onChange={(event) => setAmount(event.target.value)}
              InputProps={{ startAdornment: <InputAdornment position="start">$</InputAdornment> }}
              fullWidth
            />
            {method === 'CASH' ? (
              <Stack spacing={1.5}>
                <TextField
                  label="Cash tendered"
                  type="number"
                  value={cashTendered}
                  disabled={busy}
                  inputProps={{ min: 0.01, step: 0.01 }}
                  onChange={(event) => setCashTendered(event.target.value)}
                  InputProps={{ startAdornment: <InputAdornment position="start">$</InputAdornment> }}
                  fullWidth
                />
                <Grid container spacing={1} aria-label="Cash tender keypad">
                  {['7', '8', '9', '4', '5', '6', '1', '2', '3', '.', '0', '00'].map((value) => (
                    <Grid item xs={4} key={value}>
                      <Button fullWidth variant="outlined" disabled={busy} onClick={() => appendCashInput(value)}>{value}</Button>
                    </Grid>
                  ))}
                  <Grid item xs={4}>
                    <Button fullWidth variant="outlined" disabled={busy} onClick={() => setCashTendered(balanceDue.toFixed(2))}>Exact</Button>
                  </Grid>
                  <Grid item xs={4}>
                    <Button fullWidth variant="outlined" disabled={busy} onClick={() => appendCashInput('back')}>Back</Button>
                  </Grid>
                  <Grid item xs={4}>
                    <Button fullWidth variant="outlined" disabled={busy} onClick={() => appendCashInput('clear')}>Clear</Button>
                  </Grid>
                </Grid>
                <Stack direction="row" justifyContent="space-between">
                  <Typography color="text.secondary">Change due</Typography>
                  <Typography variant="h6">{money(changeDue, currencyCode)}</Typography>
                </Stack>
              </Stack>
            ) : (
              <TextField
                label="Reference"
                value={reference}
                disabled={busy}
                onChange={(event) => setReference(event.target.value)}
                fullWidth
              />
            )}
            <TextField
              label="Notes"
              value={notes}
              disabled={busy}
              onChange={(event) => setNotes(event.target.value)}
              fullWidth
            />
            {validation ? <Alert severity="warning">{validation}</Alert> : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={busy}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={busy || Boolean(validation)}>
            Record payment
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

function ReceiptPreview({ receipt, widthMm }: { receipt: ReceiptDocument; widthMm: number }) {
  return (
    <Paper
      className="receipt-print-root"
      variant="outlined"
      aria-label="Receipt preview"
      sx={{
        width: '100%',
        maxWidth: `${Math.min(Math.max(widthMm * 4, 260), 420)}px`,
        mx: 'auto',
        p: 2,
        fontFamily: 'monospace',
        fontSize: 12,
        bgcolor: 'background.paper',
        '@media print': {
          width: `${widthMm}mm`,
          maxWidth: 'none',
          m: 0,
          p: '4mm',
          border: 0,
          boxShadow: 'none'
        }
      }}
    >
      <Stack spacing={1}>
        <Box textAlign="center">
          <Typography variant="h6" sx={{ fontFamily: 'inherit' }}>{receipt.brandName}</Typography>
          <Typography variant="body2" color="text.secondary">{receipt.brandTagline}</Typography>
          <Typography fontWeight={700}>{receipt.store.name}</Typography>
          <Typography variant="body2">{receipt.store.address}</Typography>
        </Box>
        <Divider />
        <Stack spacing={0.5}>
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2">Receipt</Typography>
            <Typography variant="body2" fontWeight={700}>{receipt.receiptNumber}</Typography>
          </Stack>
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2">Register</Typography>
            <Typography variant="body2">{receipt.register.name}</Typography>
          </Stack>
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2">Cashier</Typography>
            <Typography variant="body2">{receipt.cashier.displayName}</Typography>
          </Stack>
        </Stack>
        <Divider />
        {receipt.items.map((item) => (
          <Box key={item.id}>
            <Stack direction="row" justifyContent="space-between" spacing={1}>
              <Box>
                <Typography variant="body2" fontWeight={700}>{item.productName}</Typography>
                <Typography variant="caption" color="text.secondary">{item.productSku} x {item.quantity}</Typography>
              </Box>
              <Typography variant="body2">{money(item.lineTotal, receipt.currencyCode)}</Typography>
            </Stack>
            {item.discountAmount > 0 ? (
              <Typography variant="caption" color="text.secondary">Discount {money(item.discountAmount, receipt.currencyCode)}</Typography>
            ) : null}
          </Box>
        ))}
        <Divider />
        <Stack spacing={0.5}>
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2">Subtotal</Typography>
            <Typography variant="body2">{money(receipt.subtotalAmount, receipt.currencyCode)}</Typography>
          </Stack>
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2">Discounts</Typography>
            <Typography variant="body2">{money(receipt.discountAmount, receipt.currencyCode)}</Typography>
          </Stack>
          {receipt.taxSummaries.map((tax) => (
            <Stack direction="row" justifyContent="space-between" key={tax.componentCode}>
              <Typography variant="body2">{tax.componentName}</Typography>
              <Typography variant="body2">{money(tax.taxAmount, receipt.currencyCode)}</Typography>
            </Stack>
          ))}
          <Stack direction="row" justifyContent="space-between">
            <Typography fontWeight={700}>Total</Typography>
            <Typography fontWeight={700}>{money(receipt.totalAmount, receipt.currencyCode)}</Typography>
          </Stack>
        </Stack>
        <Divider />
        {receipt.payments.map((payment) => (
          <Stack direction="row" justifyContent="space-between" key={payment.id}>
            <Typography variant="body2">{payment.method.replaceAll('_', ' ')}</Typography>
            <Typography variant="body2">{money(payment.amount, receipt.currencyCode)}</Typography>
          </Stack>
        ))}
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2">Cash tendered</Typography>
          <Typography variant="body2">{money(receipt.cashTendered, receipt.currencyCode)}</Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2">Change</Typography>
          <Typography variant="body2">{money(receipt.changeDue, receipt.currencyCode)}</Typography>
        </Stack>
      </Stack>
    </Paper>
  );
}

function SuccessfulSaleScreen({
  sale,
  receipt,
  receiptLoading,
  receiptError,
  printError,
  printing,
  preferences,
  onPreferencesChange,
  onPrint,
  onReprint,
  onNewSale
}: {
  sale: Sale;
  receipt?: Receipt;
  receiptLoading: boolean;
  receiptError: unknown;
  printError: string | null;
  printing: boolean;
  preferences: ReceiptPrinterPreferences;
  onPreferencesChange: (preferences: ReceiptPrinterPreferences) => void;
  onPrint: () => void;
  onReprint: () => void;
  onNewSale: () => void;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Stack spacing={2}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={2}>
          <Box>
            <Typography variant="h5" component="h2">Sale complete</Typography>
            <Typography color="text.secondary">Final total {money(sale.totalAmount, sale.currencyCode)}</Typography>
          </Box>
          <Chip color="success" label="COMPLETED" />
        </Stack>
        <Divider />
        <Grid container spacing={2}>
          <Grid item xs={12} sm={4}>
            <Typography color="text.secondary">Paid</Typography>
            <Typography variant="h6">{money(sale.paidAmount, sale.currencyCode)}</Typography>
          </Grid>
          <Grid item xs={12} sm={4}>
            <Typography color="text.secondary">Change due</Typography>
            <Typography variant="h6">{money(sale.changeDue, sale.currencyCode)}</Typography>
          </Grid>
          <Grid item xs={12} sm={4}>
            <Typography color="text.secondary">Completed</Typography>
            <Typography variant="h6">{sale.completedAt ? new Date(sale.completedAt).toLocaleTimeString() : 'Now'}</Typography>
          </Grid>
        </Grid>
        <Divider />
        <Stack spacing={2}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
            <Box>
              <Typography variant="h6" component="h3">Receipt</Typography>
              <Typography color="text.secondary">{receipt?.receiptNumber ?? 'Generating receipt'}</Typography>
            </Box>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              <FormControl size="small" sx={{ width: 150 }}>
                <InputLabel id="receipt-width-label">Receipt width</InputLabel>
                <Select
                  labelId="receipt-width-label"
                  label="Receipt width"
                  value={preferences.widthMm}
                  onChange={(event) => onPreferencesChange({ ...preferences, widthMm: Number(event.target.value) })}
                >
                  <MenuItem value={58}>58 mm</MenuItem>
                  <MenuItem value={80}>80 mm</MenuItem>
                  <MenuItem value={112}>112 mm</MenuItem>
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ width: 120 }}>
                <InputLabel id="receipt-copies-label">Copies</InputLabel>
                <Select
                  labelId="receipt-copies-label"
                  label="Copies"
                  value={preferences.copies}
                  onChange={(event) => onPreferencesChange({ ...preferences, copies: Number(event.target.value) })}
                >
                  {[1, 2, 3, 4, 5].map((copyCount) => (
                    <MenuItem key={copyCount} value={copyCount}>{copyCount}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={preferences.autoPrintReceipt}
                    onChange={(event) => onPreferencesChange({
                      ...preferences,
                      autoPrint: event.target.checked,
                      autoPrintReceipt: event.target.checked
                    })}
                  />
                )}
                label="Auto-print"
              />
            </Stack>
          </Stack>
          {receiptLoading ? <LoadingPanel label="Loading receipt" /> : null}
          {receiptError ? <Alert severity="warning">{errorMessage(receiptError)}</Alert> : null}
          {printError ? <Alert severity="warning">{printError}</Alert> : null}
          {receipt ? <ReceiptPreview receipt={receipt.document} widthMm={preferences.widthMm} /> : null}
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <Button
              variant="contained"
              startIcon={<PrintOutlinedIcon />}
              disabled={!receipt || printing}
              onClick={onPrint}
            >
              {printing ? 'Printing...' : 'Print receipt'}
            </Button>
            <Button
              variant="outlined"
              startIcon={<ReceiptLongOutlinedIcon />}
              disabled={printing}
              onClick={onReprint}
            >
              Reprint
            </Button>
            <Button variant="outlined" onClick={onNewSale}>New sale</Button>
          </Stack>
        </Stack>
      </Stack>
    </Paper>
  );
}

export function PosCartPage() {
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);
  const saleId = searchParams.get('saleId');
  const [activeSale, setActiveSale] = React.useState<Sale | null>(null);
  const [barcode, setBarcode] = React.useState('');
  const [productSearch, setProductSearch] = React.useState('');
  const [submittedSearch, setSubmittedSearch] = React.useState('');
  const [paymentDialogOpen, setPaymentDialogOpen] = React.useState(false);
  const [scannerPreferences] = React.useState<BarcodeScannerPreferences>(() => loadBarcodeScannerPreferences());
  const scannerRef = React.useRef(new KeyboardWedgeScanner({ ...scannerPreferences, duplicatePreventionMs: 0 }));
  const [unknownBarcode, setUnknownBarcode] = React.useState<string | null>(null);
  const [inventoryWarning, setInventoryWarning] = React.useState<string | null>(null);
  const [pendingAgeVerification, setPendingAgeVerification] = React.useState<{
    productId: string;
    variantId?: string;
    label: string;
    minimumAge: number | null;
  } | null>(null);
  const barcodeInputRef = React.useRef<HTMLInputElement | null>(null);
  const [receiptPreferences, setReceiptPreferences] = React.useState<ReceiptPrinterPreferences>(() => loadReceiptPrinterPreferences());
  const [receiptPrintError, setReceiptPrintError] = React.useState<string | null>(null);
  const [draftRecovered, setDraftRecovered] = React.useState(false);
  const [printingReceipt, setPrintingReceipt] = React.useState(false);
  const completionKeyRef = React.useRef<string | null>(null);
  const automaticPrintSaleIdRef = React.useRef<string | null>(null);
  const autoPrintedReceiptRef = React.useRef<string | null>(null);
  const recoveryCheckedRef = React.useRef(false);

  React.useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (activeSale?.status === 'DRAFT' && activeSale.items.length > 0) {
        event.preventDefault();
      }
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [activeSale]);

  const current = useQuery({
    queryKey: registerSessionKeys.current(browserDeviceIdentifier),
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier })
  });

  const stores = useQuery({
    queryKey: ['stores', 'pos'],
    queryFn: async () => listStores(await getValidAccessToken(), { size: 100 })
  });

  const registers = useQuery({
    queryKey: ['registers', 'pos'],
    queryFn: async () => listRegisters(await getValidAccessToken(), { size: 100 })
  });

  const devices = useQuery({
    queryKey: ['devices', 'pos'],
    queryFn: async () => listDevices(await getValidAccessToken(), { size: 100 })
  });

  const saleQuery = useQuery({
    queryKey: ['sale', saleId],
    queryFn: async () => getSale(await getValidAccessToken(), saleId ?? ''),
    enabled: Boolean(saleId)
  });

  const receiptQuery = useQuery({
    queryKey: ['sale-receipt', activeSale?.id],
    queryFn: async () => getSaleReceipt(await getValidAccessToken(), activeSale?.id ?? ''),
    enabled: activeSale?.status === 'COMPLETED'
  });

  React.useEffect(() => {
    if (saleQuery.data) {
      rememberSale(saleQuery.data);
    }
  }, [saleQuery.data]);

  const productResults = useQuery({
    queryKey: ['products', 'pos-search', current.data?.storeId, submittedSearch],
    queryFn: async () => listProducts(await getValidAccessToken(), { name: submittedSearch, storeId: current.data?.storeId, active: true, size: 10 }),
    enabled: submittedSearch.trim().length > 0 && Boolean(current.data?.storeId)
  });

  const store = stores.data?.content.find((item) => item.id === current.data?.storeId);
  const register = registers.data?.content.find((item) => item.id === current.data?.registerId);
  const device = devices.data?.content.find((item) => item.id === current.data?.deviceId);
  const currencyCode = activeSale?.currencyCode ?? store?.currencyCode ?? 'USD';

  function rememberSale(sale: Sale | null) {
    setActiveSale(sale);
    if (sale?.status === 'DRAFT') {
      queryClient.setQueryData(['sale', sale.id], sale);
      setSearchParams({ saleId: sale.id });
      void saveDraftCartRecovery(sale);
      return;
    }
    void clearDraftCartRecovery();
    setSearchParams({});
  }

  React.useEffect(() => {
    if (!current.data || saleId || activeSale || recoveryCheckedRef.current) {
      return;
    }
    recoveryCheckedRef.current = true;
    void loadDraftCartRecovery(current.data.id).then((record) => {
      if (!record) {
        return;
      }
      const recoveredSale = saleFromDraftCartRecord(record);
      setDraftRecovered(true);
      setActiveSale(recoveredSale);
      setSearchParams({ saleId: recoveredSale.id });
    }).catch((error) => {
      console.error('Draft cart recovery failed', error);
    });
  }, [activeSale, current.data, saleId]);

  function updateReceiptPreferences(preferences: ReceiptPrinterPreferences) {
    setReceiptPreferences(preferences);
    saveReceiptPrinterPreferences(preferences);
  }

  const printReceiptDocument = React.useCallback(async (receipt: ReceiptDocument, automatic = false) => {
    setPrintingReceipt(true);
    setReceiptPrintError(null);
    try {
      const result = receiptPreferences.mode === 'BROWSER'
        ? (await printRenderedReceipt({ saleId: receipt.saleId, registerId: receipt.register.id }), { printer: 'BROWSER' as const })
        : await printReceiptWithFallback(receipt, receiptPreferences);
      if (result.fallbackReason) {
        setReceiptPrintError(`QZ Tray failed: ${result.fallbackReason}. Printed with browser instead.`);
      }
    } catch (error) {
      setReceiptPrintError(automatic
        ? 'Sale completed. Receipt could not be printed automatically.'
        : errorMessage(error));
    } finally {
      setPrintingReceipt(false);
    }
  }, [receiptPreferences]);

  async function ensureDraft(token: string) {
    if (activeSale?.status === 'DRAFT') {
      return activeSale;
    }
    if (!current.data) {
      throw new Error('Open a register before starting a sale');
    }
    const draft = await createSaleDraft(token, {
      registerSessionId: current.data.id,
      saleChannel: 'POS'
    });
    rememberSale(draft);
    return draft;
  }

  const addProductMutation = useMutation({
    mutationFn: async (item: { productId: string; variantId?: string; ageVerified?: boolean }) => {
      posScanDebug('CART_ADD_CALLED', {
        productId: item.productId,
        variantId: item.variantId ?? null,
        ageVerified: Boolean(item.ageVerified),
        cartItemsBefore: activeSale?.items.length ?? 0
      });
      const token = await getValidAccessToken();
      const sale = await ensureDraft(token);
      return addSaleItem(token, sale.id, { ...item, quantity: 1 });
    },
    onSuccess: (sale) => {
      posScanDebug('CART_STATE_AFTER_ADD', {
        saleId: sale.id,
        cartItemsAfter: sale.items.length,
        items: sale.items.map((item) => ({ productId: item.productId, variantId: item.variantId ?? null, quantity: item.quantity }))
      });
      setPendingAgeVerification(null);
      rememberSale(sale);
      window.setTimeout(() => barcodeInputRef.current?.focus(), 0);
    }
  });

  function isVerifiedInCurrentSale(productId: string, variantId?: string) {
    return Boolean(activeSale?.items.some((item) => item.productId === productId
      && (item.variantId ?? undefined) === variantId && item.ageVerified));
  }

  function queueRestrictedItem(item: { productId: string; variantId?: string; label: string; minimumAge: number | null }) {
    if (isVerifiedInCurrentSale(item.productId, item.variantId)) {
      addProductMutation.mutate({ productId: item.productId, variantId: item.variantId, ageVerified: true });
      return;
    }
    setPendingAgeVerification(item);
  }

  function addProduct(product: Product) {
    if (product.capabilities.includes('REQUIRE_AGE_VERIFICATION')) {
      queueRestrictedItem({ productId: product.id, label: product.name, minimumAge: product.minimumAge ?? null });
      return;
    }
    addProductMutation.mutate({ productId: product.id });
  }

  const barcodeMutation = useMutation({
    mutationFn: async (value: string) => {
      const token = await getValidAccessToken();
      const normalized = value.trim();
      posScanDebug('BARCODE_LOOKUP_STARTED', { barcode: normalized });
      if (!current.data?.storeId) throw new Error('Open a register before scanning products');
      const product = await lookupPosBarcode(token, normalized, current.data.storeId);
      return product;
    },
    onSuccess: (product: PosBarcodeLookup) => {
      posScanDebug('BARCODE_LOOKUP_RESPONSE', {
        productId: product.productId,
        variantId: product.variantId,
        productName: product.productName,
        price: product.price,
        active: product.active,
        ageRestricted: product.ageRestricted
      });
      setBarcode('');
      setUnknownBarcode(null);
      setInventoryWarning(product.availableQuantity <= 0
        ? `System stock is currently ${product.availableQuantity}. You can continue the sale.`
        : null);
      const item = {
        productId: product.productId,
        variantId: product.variantId ?? undefined,
        label: product.variantName ? `${product.productName} — ${product.variantName}` : product.productName,
        minimumAge: product.minimumAge ?? null
      };
      if (product.ageRestricted) {
        posScanDebug('CART_ADD_DEFERRED_AGE_VERIFICATION', {
          productId: item.productId,
          variantId: item.variantId ?? null,
          minimumAge: item.minimumAge
        });
        queueRestrictedItem(item);
      } else {
        addProductMutation.mutate({ productId: item.productId, variantId: item.variantId });
      }
    },
    onError: (error, value) => {
      if (errorMessage(error).includes('BARCODE_NOT_FOUND')) {
        setUnknownBarcode(value.trim());
      }
    }
  });

  const quantityMutation = useMutation({
    mutationFn: async ({ itemId, quantity }: { itemId: string; quantity: number }) => {
      if (!activeSale) {
        throw new Error('No active sale');
      }
      return updateSaleItemQuantity(await getValidAccessToken(), activeSale.id, itemId, { quantity });
    },
    onSuccess: (sale) => rememberSale(sale)
  });

  const removeMutation = useMutation({
    mutationFn: async (itemId: string) => {
      if (!activeSale) {
        throw new Error('No active sale');
      }
      return removeSaleItem(await getValidAccessToken(), activeSale.id, itemId);
    },
    onSuccess: (sale) => rememberSale(sale)
  });

  const recalculateMutation = useMutation({
    mutationFn: async () => {
      if (!activeSale) {
        throw new Error('No active sale');
      }
      return recalculateSale(await getValidAccessToken(), activeSale.id);
    },
    onSuccess: (sale) => rememberSale(sale)
  });

  const holdMutation = useMutation({
    mutationFn: async () => {
      if (!activeSale) {
        throw new Error('No active sale');
      }
      return holdSale(await getValidAccessToken(), activeSale.id);
    },
    onSuccess: async () => {
      rememberSale(null);
      await queryClient.invalidateQueries({ queryKey: ['sales', 'held'] });
      navigate('/pos/held-sales');
    }
  });

  const cancelMutation = useMutation({
    mutationFn: async () => {
      if (!activeSale) {
        throw new Error('No active sale');
      }
      return cancelSale(await getValidAccessToken(), activeSale.id);
    },
    onSuccess: () => rememberSale(null)
  });

  const paymentMutation = useMutation({
    mutationFn: async (payment: { method: PaymentMethod; amount: number; cashTendered?: number; reference?: string; notes?: string }) => {
      if (!activeSale) {
        throw new Error('No active sale');
      }
      return recordSalePayment(await getValidAccessToken(), activeSale.id, payment);
    },
    onSuccess: (sale) => {
      rememberSale(sale);
      setPaymentDialogOpen(!sale.paymentComplete);
    }
  });

  const completeMutation = useMutation({
    mutationFn: async () => {
      if (!activeSale) {
        throw new Error('No active sale');
      }
      const key = completionKeyRef.current ?? completionKey();
      completionKeyRef.current = key;
      return completeSale(await getValidAccessToken(), activeSale.id, key);
    },
    onSuccess: (sale) => {
      completionKeyRef.current = null;
      automaticPrintSaleIdRef.current = sale.id;
      setPaymentDialogOpen(false);
      rememberSale(sale);
      void queryClient.invalidateQueries({ queryKey: ['sales'] });
    },
    onError: () => {
      completionKeyRef.current = null;
    }
  });

  const reprintReceiptMutation = useMutation({
    mutationFn: async () => {
      if (!activeSale) {
        throw new Error('No active sale');
      }
      return reprintSaleReceipt(await getValidAccessToken(), activeSale.id);
    },
    onSuccess: async (receipt) => {
      queryClient.setQueryData(['sale-receipt', receipt.saleId], receipt);
      await printReceiptDocument(receipt.document);
    }
  });

  React.useEffect(() => {
    const receipt = receiptQuery.data;
    if (receiptPreferences.receiptPrintMode !== 'KIOSK_AUTO_PRINT'
      || !receiptPreferences.autoPrintReceipt
      || !receipt
      || automaticPrintSaleIdRef.current !== receipt.saleId
      || autoPrintedReceiptRef.current === receipt.id) {
      return;
    }
    automaticPrintSaleIdRef.current = null;
    autoPrintedReceiptRef.current = receipt.id;
    void printReceiptDocument(receipt.document, true);
  }, [printReceiptDocument, receiptPreferences.autoPrintReceipt, receiptPreferences.receiptPrintMode, receiptQuery.data]);

  const busy = addProductMutation.isPending
    || barcodeMutation.isPending
    || quantityMutation.isPending
    || removeMutation.isPending
    || recalculateMutation.isPending
    || holdMutation.isPending
    || cancelMutation.isPending
    || paymentMutation.isPending
    || completeMutation.isPending;
  const cartLocked = busy || Boolean(activeSale?.payments.length) || activeSale?.status === 'COMPLETED';
  const barcodeError = barcodeMutation.error && !errorMessage(barcodeMutation.error).includes('BARCODE_NOT_FOUND')
    ? barcodeMutation.error
    : null;
  const pageError = current.error ?? saleQuery.error ?? addProductMutation.error ?? barcodeError
    ?? quantityMutation.error ?? removeMutation.error ?? recalculateMutation.error ?? holdMutation.error ?? cancelMutation.error
    ?? paymentMutation.error ?? completeMutation.error;

  React.useEffect(() => {
    scannerRef.current = new KeyboardWedgeScanner({ ...scannerPreferences, duplicatePreventionMs: 0 });
  }, [scannerPreferences]);

  React.useEffect(() => {
    function handleScannerKeyDown(event: KeyboardEvent) {
      if (!current.data || cartLocked || paymentDialogOpen || pendingAgeVerification) {
        return;
      }
      const target = event.target;
      if (target instanceof HTMLInputElement && target.dataset.scannerManualFallback === 'true') {
        return;
      }
      const result = scannerRef.current.handleKeyDown(event);
      if (result?.type === 'scan') {
        posScanDebug('BARCODE_COMPLETED', { barcode: result.value, source: 'window-hid-buffer', suffix: event.key });
        setBarcode('');
        setUnknownBarcode(null);
        barcodeMutation.mutate(result.value);
      }
    }

    window.addEventListener('keydown', handleScannerKeyDown, true);
    return () => window.removeEventListener('keydown', handleScannerKeyDown, true);
  }, [barcodeMutation, cartLocked, current.data, paymentDialogOpen, pendingAgeVerification]);

  return (
    <Stack spacing={2} sx={{ minHeight: 'calc(100vh - 88px)' }}>
      <GlobalStyles styles={receiptPrintStyles} />
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Box>
          <Typography variant="h5" component="h1">Checkout</Typography>
          <Typography color="text.secondary">Scan, verify pricing and tax, then record payment.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button component={Link} to="/pos/held-sales" variant="outlined" startIcon={<PauseCircleOutlineIcon />}>
            Held sales
          </Button>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            disabled={!activeSale || busy}
            onClick={() => recalculateMutation.mutate()}
          >
            Recalculate
          </Button>
        </Stack>
      </Stack>

      {current.isLoading ? <LoadingPanel label="Loading register context" /> : null}
      {!current.isLoading && !current.data ? (
        <Alert severity="info" action={<Button component={Link} to="/register/open">Open</Button>}>
          No register session is open for this device.
        </Alert>
      ) : null}
      {pageError ? <Alert severity="error">{posErrorMessage(pageError)}</Alert> : null}
      {inventoryWarning ? <Alert severity="warning" onClose={() => setInventoryWarning(null)}>{inventoryWarning}</Alert> : null}
      {draftRecovered && activeSale?.status === 'DRAFT' ? (
        <Alert severity="success" onClose={() => setDraftRecovered(false)}>
          Draft cart recovered after refresh.
        </Alert>
      ) : null}
      {unknownBarcode ? (
        <Alert severity="warning">
          {`No product was found for barcode ${unknownBarcode}.`}
        </Alert>
      ) : null}
      {current.data ? <IdentityStrip session={current.data} store={store} register={register} device={device} /> : null}

      {current.data ? (
        activeSale?.status === 'COMPLETED' ? (
          <SuccessfulSaleScreen
            sale={activeSale}
            receipt={receiptQuery.data}
            receiptLoading={receiptQuery.isLoading}
            receiptError={receiptQuery.error ?? reprintReceiptMutation.error}
            printError={receiptPrintError}
            printing={printingReceipt || reprintReceiptMutation.isPending}
            preferences={receiptPreferences}
            onPreferencesChange={updateReceiptPreferences}
            onPrint={() => {
              if (receiptQuery.data) {
                void printReceiptDocument(receiptQuery.data.document);
              }
            }}
            onReprint={() => reprintReceiptMutation.mutate()}
            onNewSale={() => rememberSale(null)}
          />
        ) : (
        <Grid container spacing={3}>
          <Grid item xs={12} lg={8}>
            <Stack spacing={2}>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack spacing={2}>
                  <Box component="form" onSubmit={(event) => {
                    event.preventDefault();
                    const completedBarcode = barcodeInputRef.current?.value ?? barcode;
                    if (completedBarcode.trim() && !barcodeMutation.isPending) {
                      posScanDebug('BARCODE_COMPLETED', { barcode: completedBarcode.trim(), source: 'form-submit', suffix: 'Enter' });
                      barcodeMutation.mutate(completedBarcode);
                    }
                  }}>
                    <TextField
                      label="Barcode"
                      value={barcode}
                      onChange={(event) => {
                        posScanDebug('BARCODE_RAW_INPUT', { length: event.target.value.length });
                        setBarcode(event.target.value);
                      }}
                      fullWidth
                      autoFocus
                      inputRef={barcodeInputRef}
                      disabled={cartLocked}
                      onKeyDown={(event) => {
                        if (event.key !== scannerPreferences.suffix) {
                          return;
                        }
                        event.preventDefault();
                        event.stopPropagation();
                        const completedBarcode = barcodeInputRef.current?.value ?? barcode;
                        if (completedBarcode.trim() && !barcodeMutation.isPending) {
                          posScanDebug('BARCODE_COMPLETED', { barcode: completedBarcode.trim(), source: 'focused-input', suffix: event.key });
                          barcodeMutation.mutate(completedBarcode);
                        }
                      }}
                      inputProps={{ 'data-scanner-manual-fallback': 'true' }}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <PointOfSaleOutlinedIcon />
                          </InputAdornment>
                        )
                      }}
                    />
                  </Box>

                  <Box component="form" onSubmit={(event) => {
                    event.preventDefault();
                    setSubmittedSearch(productSearch.trim());
                  }}>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                      <TextField
                        label="Product search"
                        value={productSearch}
                        onChange={(event) => setProductSearch(event.target.value)}
                        fullWidth
                        disabled={cartLocked}
                        InputProps={{
                          startAdornment: (
                            <InputAdornment position="start">
                              <SearchIcon />
                            </InputAdornment>
                          )
                        }}
                      />
                      <Button type="submit" variant="contained" startIcon={<SearchIcon />} disabled={cartLocked || !productSearch.trim()}>
                        Search
                      </Button>
                    </Stack>
                  </Box>

                  {productResults.isFetching ? <LoadingPanel label="Searching products" /> : null}
                  {submittedSearch && !productResults.isFetching && productResults.data ? (
                    <ProductSearchResults
                      products={productResults.data.content}
                      currencyCode={currencyCode}
                      disabled={cartLocked}
                      onAdd={addProduct}
                    />
                  ) : null}
                </Stack>
              </Paper>

              <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
                <CartLines
                  sale={activeSale}
                  busy={cartLocked}
                  onQuantity={(itemId, quantity) => quantityMutation.mutate({ itemId, quantity })}
                  onRemove={(itemId) => removeMutation.mutate(itemId)}
                />
              </Paper>
            </Stack>
          </Grid>

          <Grid item xs={12} lg={4}>
            <Stack spacing={2}>
              <TotalsPanel sale={activeSale} currencyCode={currencyCode} />
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack spacing={1.5}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography color="text.secondary">Status</Typography>
                    <Chip label={activeSale?.status ?? 'NO SALE'} size="small" />
                  </Stack>
                  <Button
                    variant="contained"
                    startIcon={<PauseCircleOutlineIcon />}
                    disabled={!activeSale || cartLocked}
                    onClick={() => holdMutation.mutate()}
                  >
                    Hold sale
                  </Button>
                  <Button
                    variant="outlined"
                    color="error"
                    disabled={!activeSale || cartLocked}
                    onClick={() => cancelMutation.mutate()}
                  >
                    Cancel draft
                  </Button>
                </Stack>
              </Paper>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack spacing={1.5}>
                  <Stack direction="row" justifyContent="space-between">
                    <Typography color="text.secondary">Paid</Typography>
                    <Typography>{money(activeSale?.paidAmount ?? 0, currencyCode)}</Typography>
                  </Stack>
                  <Stack direction="row" justifyContent="space-between">
                    <Typography color="text.secondary">Balance due</Typography>
                    <Typography fontWeight={700}>{money(activeSale?.balanceDue ?? activeSale?.totalAmount ?? 0, currencyCode)}</Typography>
                  </Stack>
                  <Stack direction="row" justifyContent="space-between">
                    <Typography color="text.secondary">Change due</Typography>
                    <Typography>{money(activeSale?.changeDue ?? 0, currencyCode)}</Typography>
                  </Stack>
                  <Button
                    variant="contained"
                    startIcon={<PaymentOutlinedIcon />}
                    disabled={!activeSale || activeSale.items.length === 0 || busy || activeSale.paymentComplete}
                    onClick={() => setPaymentDialogOpen(true)}
                  >
                    Take payment
                  </Button>
                  <Button
                    variant="contained"
                    color="success"
                    disabled={!activeSale?.paymentComplete || completeMutation.isPending || busy}
                    onClick={() => {
                      if (!completeMutation.isPending) {
                        completeMutation.mutate();
                      }
                    }}
                  >
                    {completeMutation.isPending ? 'Completing sale...' : 'Complete sale'}
                  </Button>
                </Stack>
              </Paper>
            </Stack>
          </Grid>
        </Grid>
        )
      ) : null}
      <PaymentDialog
        open={paymentDialogOpen}
        sale={activeSale}
        busy={paymentMutation.isPending}
        onClose={() => setPaymentDialogOpen(false)}
        onSubmit={(payment) => paymentMutation.mutate(payment)}
      />
      <Dialog
        open={Boolean(pendingAgeVerification)}
        onClose={() => {
          setPendingAgeVerification(null);
          window.setTimeout(() => barcodeInputRef.current?.focus(), 0);
        }}
        onKeyDown={(event) => {
          if (event.key === 'Enter') event.preventDefault();
        }}
      >
        <DialogTitle>Age Verification Required</DialogTitle>
        <DialogContent>
          <Stack spacing={1} sx={{ pt: 1 }}>
            <Typography>{pendingAgeVerification?.label}</Typography>
            <Typography>This item requires age verification.</Typography>
            {pendingAgeVerification?.minimumAge ? <Typography fontWeight={700}>Required age: {pendingAgeVerification.minimumAge}+</Typography> : null}
            <Typography color="text.secondary">Please verify the customer's government-issued ID.</Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => {
            setPendingAgeVerification(null);
            window.setTimeout(() => barcodeInputRef.current?.focus(), 0);
          }}>Cancel</Button>
          <Button variant="contained" onClick={() => {
            if (!pendingAgeVerification) return;
            addProductMutation.mutate({
              productId: pendingAgeVerification.productId,
              variantId: pendingAgeVerification.variantId,
              ageVerified: true
            });
          }}>Age Verified</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

export function HeldSalesPage() {
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);

  const current = useQuery({
    queryKey: ['register-session-current', browserDeviceIdentifier],
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier })
  });

  const stores = useQuery({
    queryKey: ['stores', 'pos-held'],
    queryFn: async () => listStores(await getValidAccessToken(), { size: 100 })
  });

  const registers = useQuery({
    queryKey: ['registers', 'pos-held'],
    queryFn: async () => listRegisters(await getValidAccessToken(), { size: 100 })
  });

  const devices = useQuery({
    queryKey: ['devices', 'pos-held'],
    queryFn: async () => listDevices(await getValidAccessToken(), { size: 100 })
  });

  const heldSales = useQuery({
    queryKey: ['sales', 'held', current.data?.id],
    queryFn: async () => listSales(await getValidAccessToken(), { registerSessionId: current.data?.id, status: 'HELD', size: 50 }),
    enabled: Boolean(current.data?.id)
  });

  const resumeMutation = useMutation({
    mutationFn: async (saleId: string) => resumeSale(await getValidAccessToken(), saleId),
    onSuccess: async (sale) => {
      queryClient.setQueryData(['sale', sale.id], sale);
      await queryClient.invalidateQueries({ queryKey: ['sales', 'held'] });
      navigate(`/pos?saleId=${sale.id}`);
    }
  });

  const store = stores.data?.content.find((item) => item.id === current.data?.storeId);
  const register = registers.data?.content.find((item) => item.id === current.data?.registerId);
  const device = devices.data?.content.find((item) => item.id === current.data?.deviceId);
  const pageError = current.error ?? heldSales.error ?? resumeMutation.error;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Box>
          <Typography variant="h5" component="h1">Held sales</Typography>
          <Typography color="text.secondary">Resume draft carts held on the current register session.</Typography>
        </Box>
        <Button component={Link} to="/pos" variant="outlined" startIcon={<ArrowBackIcon />}>
          Back to POS
        </Button>
      </Stack>

      {current.isLoading ? <LoadingPanel label="Loading register context" /> : null}
      {!current.isLoading && !current.data ? (
        <Alert severity="info" action={<Button component={Link} to="/register/open">Open</Button>}>
          No register session is open for this device.
        </Alert>
      ) : null}
      {pageError ? <Alert severity="error">{errorMessage(pageError)}</Alert> : null}
      {current.data ? <IdentityStrip session={current.data} store={store} register={register} device={device} /> : null}

      {heldSales.isFetching ? <LoadingPanel label="Loading held sales" /> : null}
      {heldSales.data?.content.length === 0 ? <Alert severity="info">No held sales for this register session.</Alert> : null}
      {(heldSales.data?.content.length ?? 0) > 0 ? (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          <Table aria-label="Held sales">
            <TableHead>
              <TableRow>
                <TableCell>Sale</TableCell>
                <TableCell>Held</TableCell>
                <TableCell align="right">Items</TableCell>
                <TableCell align="right">Estimated tax</TableCell>
                <TableCell align="right">Total</TableCell>
                <TableCell align="right">Resume</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {heldSales.data?.content.map((sale) => (
                <TableRow key={sale.id} hover>
                  <TableCell sx={{ fontFamily: 'monospace' }}>{sale.id.slice(0, 8)}</TableCell>
                  <TableCell>{sale.heldAt ? new Date(sale.heldAt).toLocaleString() : 'Held'}</TableCell>
                  <TableCell align="right">{sale.items.length}</TableCell>
                  <TableCell align="right">{money(sale.estimatedTaxAmount, sale.currencyCode)}</TableCell>
                  <TableCell align="right">{money(sale.totalAmount, sale.currencyCode)}</TableCell>
                  <TableCell align="right">
                    <Button
                      variant="contained"
                      size="small"
                      startIcon={<PlayCircleOutlineIcon />}
                      disabled={resumeMutation.isPending}
                      onClick={() => resumeMutation.mutate(sale.id)}
                    >
                      Resume
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      ) : null}
    </Stack>
  );
}
