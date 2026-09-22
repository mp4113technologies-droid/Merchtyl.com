import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutline';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import PauseCircleOutlineIcon from '@mui/icons-material/PauseCircleOutline';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline';
import PointOfSaleOutlinedIcon from '@mui/icons-material/PointOfSaleOutlined';
import PaymentOutlinedIcon from '@mui/icons-material/PaymentOutlined';
import PrintOutlinedIcon from '@mui/icons-material/PrintOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import ConfirmationNumberOutlinedIcon from '@mui/icons-material/ConfirmationNumberOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
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
  ApiClientError,
  cancelSale,
  checkoutSaleCart,
  completeSale,
  getCurrentRegisterSession,
  getEffectiveStoreCapability,
  getSale,
  holdSale,
  listDevices,
  listProducts,
  listActiveStoreDiscounts,
  lookupPosBarcode,
  listRegisters,
  listSales,
  listStores,
  recordSalePayment,
  getSaleReceipt,
  reprintSaleReceipt,
  resumeSale,
} from '../../api/client';
import type { Device, DiscountDefinition, PaymentMethod, PosBarcodeLookup, Product, Receipt, ReceiptDocument, Register, RegisterSession, Sale, SaleItem, Store } from '../../api/types';
import { getApplicationDeviceIdentifier } from '../../app/deviceIdentity';
import { useSession } from '../../app/session';
import { bestMultiBuyPromotion } from './multiBuyPricing';
import { posTokens } from '../../app/theme';
import {
  KeyboardWedgeScanner,
  loadBarcodeScannerPreferences,
  type BarcodeScannerPreferences
} from '../hardware/barcodeScanner';
import {
  clearDraftCartRecovery,
  isPendingCheckoutStatus,
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
import { cashDenominations, centsToInput, decimalInputToCents, moneyToCents } from './paymentDenominations';
import { DiscountDialog, type OrderDiscount } from './DiscountDialog';
import { SecureTill } from './SecureTill';
import { CustomerReceiptFooter, CustomerReceiptHeader } from './ReceiptBranding';

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function posErrorMessage(error: unknown) {
  if (error instanceof ApiClientError && error.code?.toLowerCase() === 'validation_failed') {
    const checkoutViolation = error.violations.find((violation) =>
      violation.field === 'items' || violation.field.startsWith('items[') || violation.field.includes('validShape'));
    if (checkoutViolation) {
      return 'One or more cart items is missing required checkout information. Review the cart and try again.';
    }
    return 'The checkout request is incomplete. Review the cart and try again.';
  }
  const message = errorMessage(error);
  if (message.includes('BARCODE_NOT_FOUND')) return 'No product was found for this barcode.';
  if (message.includes('PRODUCT_NOT_ACTIVE') || message.includes('PRODUCT_NOT_AVAILABLE_IN_STORE') || message.includes('PRODUCT_NOT_AVAILABLE_AT_STORE')) return 'This product is not available for sale at this store. Remove it from the cart or ask a manager to review its store availability.';
  if (message.includes('PRODUCT_OUT_OF_STOCK')) return 'This item is out of stock.';
  if (message.includes('BARCODE_AMBIGUOUS')) return 'This barcode is linked to more than one product. Please ask a manager to correct the product setup.';
  if (message.includes('AGE_VERIFICATION_REQUIRED')) return "Please verify the customer's age before completing this sale.";
  if (message.includes('CUSTOM_ITEM_DESCRIPTION_REQUIRED')) return 'Enter an item description.';
  if (message.includes('CUSTOM_ITEM_PRICE_REQUIRED') || message.includes('CUSTOM_ITEM_PRICE_INVALID')) return 'Enter a valid price greater than $0.';
  if (message.includes('CUSTOM_ITEM_TAX_TREATMENT_REQUIRED')) return 'Choose whether this item is taxable or non-taxable.';
  if (message.includes('CUSTOM_ITEM_NOT_ALLOWED')) return 'Your account does not have permission to add custom items.';
  if (message.includes('CUSTOM_ITEM_TAX_CATEGORY_NOT_CONFIGURED')) return 'The Store tax configuration is missing a standard Custom Item tax category.';
  if (message.includes('RETAIL_REGISTER_REQUIRED')) return 'Custom Items are available from Retail registers only.';
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

function reportPosTiming(operation: string, startedAt: number) {
  window.requestAnimationFrame(() => {
    const durationMs = performance.now() - startedAt;
    performance.measure(`pos:${operation}`, { start: startedAt, end: performance.now() });
    if (import.meta.env.DEV) console.debug(`[POS PERF] ${operation} ${durationMs.toFixed(1)}ms`);
  });
}

function roundCashPayable(value: number, currencyCode: string) {
  if (currencyCode.toUpperCase() !== 'CAD') return roundedMoney(value);
  return Math.round((value * 100) / 5) * 5 / 100;
}

function completionKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `complete-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function LotteryAmountDialog({ open, mode, currencyCode, busy, error, onClose, onSubmit }: {
  open: boolean; mode: 'SOLD' | 'WIN'; currencyCode: string; busy: boolean; error?: string;
  onClose: () => void; onSubmit: (amount: number) => void;
}) {
  const [amount, setAmount] = React.useState('');
  React.useEffect(() => { if (open) setAmount(''); }, [open, mode]);
  const numericAmount = Number(amount);
  const valid = Number.isFinite(numericAmount) && numericAmount > 0 && Math.round(numericAmount * 100) === numericAmount * 100;
  return <Dialog open={open} onClose={busy ? undefined : onClose} slotProps={{ paper: { sx: { width: 430, maxWidth: 'calc(100vw - 32px)', m: 2, height: 'auto', borderRadius: 2 } } }}>
    <Box component="form" onSubmit={(event) => { event.preventDefault(); if (valid && !busy) onSubmit(numericAmount); }}>
      <DialogTitle>{mode === 'SOLD' ? 'Lottery Sold' : 'Lottery Win'}</DialogTitle>
      <DialogContent><Stack spacing={1.5} sx={{ pt: 1 }}>
        <TextField autoFocus fullWidth required label={mode === 'SOLD' ? `Amount (${currencyCode})` : `Payout Amount (${currencyCode})`} value={amount} onChange={(event) => setAmount(event.target.value)} type="number" inputProps={{ min: 0.01, step: 0.01, inputMode: 'decimal' }} InputProps={{ startAdornment: <InputAdornment position="start">$</InputAdornment> }} />
        {error ? <Alert severity="error">{error}</Alert> : null}
      </Stack></DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button type="submit" variant="contained" color={mode === 'WIN' ? 'warning' : 'primary'} disabled={!valid || busy}>{busy ? 'Adding…' : 'Add to Cart'}</Button>
      </DialogActions>
    </Box>
  </Dialog>;
}

function DepositPayoutDialog({ open, currencyCode, onClose, onSubmit }: {
  open: boolean; currencyCode: string; onClose: () => void; onSubmit: (amount: number) => void;
}) {
  const [amount, setAmount] = React.useState('');
  React.useEffect(() => { if (open) setAmount(''); }, [open]);
  const numericAmount = Number(amount);
  const valid = Number.isFinite(numericAmount) && numericAmount > 0 && Math.round(numericAmount * 100) === numericAmount * 100;
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
    <Box component="form" onSubmit={(event) => { event.preventDefault(); if (valid) onSubmit(numericAmount); }}>
      <DialogTitle>Deposit Payout</DialogTitle>
      <DialogContent sx={{ pt: '8px !important' }}>
        <TextField autoFocus fullWidth required label={`Amount (${currencyCode})`} value={amount} onChange={(event) => setAmount(event.target.value)} type="number" inputProps={{ min: 0.01, step: 0.01, inputMode: 'decimal' }} InputProps={{ startAdornment: <InputAdornment position="start">$</InputAdornment> }} />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" color="warning" disabled={!valid}>Add to Cart</Button>
      </DialogActions>
    </Box>
  </Dialog>;
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

function CompactIdentitySummary({
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
  const entries = [
    ['Store', storeLabel(store)],
    ['Register', registerLabel(register)],
    ['Device', deviceLabel(device)],
    ['Cashier', session.assignedCashierDisplayName]
  ];
  return (
    <Paper variant="outlined" sx={{ p: 1.25, borderColor: posTokens.colors.border, borderRadius: `${posTokens.radius.card}px`, bgcolor: posTokens.colors.card }}>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', columnGap: 1.5, rowGap: 0.75 }}>
        {entries.map(([label, value]) => (
          <Box key={label} sx={{ minWidth: 0 }}>
            <Typography variant="caption" color="primary.main" fontWeight={700}>{label}</Typography>
            <Typography variant="body2" fontWeight={700} noWrap title={value}>{value}</Typography>
          </Box>
        ))}
      </Box>
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
  type SearchResultRow = {
    key: string;
    product: Product;
    variantId?: string;
    variantName: string | null;
    sku: string;
    price: number;
  };
  const rows = products.flatMap<SearchResultRow>((product) => {
    const activeVariants = product.variants.filter((variant) => variant.active);
    if (activeVariants.length === 0) {
      if (product.variants.length > 0) return [];
      return [{ key: product.id, product, variantId: undefined, variantName: null, sku: product.sku, price: product.price }];
    }
    return activeVariants.map((variant) => ({
      key: `${product.id}:${variant.id}`,
      product,
      variantId: variant.id,
      variantName: variant.name,
      sku: variant.sku,
      price: variant.price
    }));
  });

  if (rows.length === 0) {
    return <Alert severity="info">No products found in this store.</Alert>;
  }

  return (
    <Table size="small" aria-label="Product search results" sx={{ '& .MuiTableCell-root': { borderColor: posTokens.colors.border, py: 0.75 }, '& .MuiTableRow-root:hover': { bgcolor: posTokens.colors.blueLight } }}>
      <TableHead>
        <TableRow>
          <TableCell>Product</TableCell>
          <TableCell>SKU</TableCell>
          <TableCell align="right">Price</TableCell>
          <TableCell align="right">Add</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.key} hover>
            <TableCell>
              <Typography fontWeight={700}>{row.product.name}</Typography>
              <Typography variant="body2" color="text.secondary">{row.variantName ?? row.product.sellableType.replaceAll('_', ' ')}</Typography>
            </TableCell>
            <TableCell sx={{ fontFamily: 'monospace' }}>{row.sku}</TableCell>
            <TableCell align="right">{money(row.price, currencyCode)}</TableCell>
            <TableCell align="right">
              <Tooltip title={`Add ${row.product.name}${row.variantName ? ` — ${row.variantName}` : ''}`}>
                <span>
                  <IconButton
                    aria-label={`Add ${row.product.name}${row.variantName ? ` — ${row.variantName}` : ''}`}
                    onClick={() => onAdd({
                      ...row.product,
                      sku: row.sku,
                      price: row.price,
                      variants: row.variantId ? row.product.variants.filter((variant) => variant.id === row.variantId) : []
                    })}
                    disabled={disabled || !row.product.active}
                  >
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
  items,
  currencyCode,
  totalsCalculated,
  onQuantity,
  onRemove,
  onEdit,
  busy
}: {
  items: import('../../api/types').SaleItem[];
  currencyCode: string;
  totalsCalculated: boolean;
  onQuantity: (itemId: string, quantity: number) => void;
  onRemove: (itemId: string) => void;
  onEdit: (item: SaleItem) => void;
  busy: boolean;
}) {
  if (items.length === 0) {
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
    <Table aria-label="Cart lines" size="small" stickyHeader sx={{ tableLayout: 'fixed', '& .MuiTableCell-root': { py: 0.75, px: 1, borderColor: posTokens.colors.border }, '& .MuiTableHead-root .MuiTableCell-root': { color: posTokens.colors.navy, bgcolor: posTokens.colors.blueSoft, fontWeight: 800 }, '& .MuiTableRow-root': { height: 54 }, '& .MuiTableBody-root .MuiTableRow-root:hover': { bgcolor: posTokens.colors.blueSoft } }}>
      <TableHead>
        <TableRow>
          <TableCell sx={{ width: '31%' }}>Item</TableCell>
          <TableCell align="center" sx={{ width: 150 }}>Qty</TableCell>
          <TableCell align="right">Unit</TableCell>
          <TableCell align="right">Tax</TableCell>
          <TableCell align="right">Total</TableCell>
          <TableCell align="right" sx={{ width: 48 }} aria-label="Actions" />
        </TableRow>
      </TableHead>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.id} hover>
            <TableCell sx={{ minWidth: 0 }}>
              <Typography fontWeight={700} color="text.primary" noWrap title={item.productName}>{item.productName}</Typography>
              {item.lineType === 'CUSTOM_ITEM'
                ? <Stack direction="row" spacing={0.5}><Chip size="small" label="Custom Item" variant="outlined" /><Typography variant="caption" color="text.secondary">{item.customItemTaxTreatment === 'TAXABLE' ? 'Taxable' : 'Non-Taxable'}</Typography></Stack>
                : item.lineType === 'LOTTERY_SOLD' || item.lineType === 'LOTTERY_WIN'
                  ? <Chip size="small" label={item.lineType === 'LOTTERY_WIN' ? 'Lottery offset' : 'Lottery'} color={item.lineType === 'LOTTERY_WIN' ? 'warning' : 'primary'} variant="outlined" />
                : item.lineType === 'DEPOSIT_PAYOUT'
                  ? <Chip size="small" label="Deposit payout" color="warning" variant="outlined" />
                : <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block', fontFamily: 'monospace' }}>{item.variantSku ?? item.productSku}</Typography>}
              {(item.depositTotal ?? 0) > 0 ? (
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                  {depositLabel(item.depositType)}: {item.quantity > 1
                    ? `${item.depositQuantity ?? item.quantity} × ${money(item.depositUnitAmount ?? 0, currencyCode)} = ${money(item.depositTotal ?? 0, currencyCode)}`
                    : money(item.depositTotal ?? 0, currencyCode)}
                </Typography>
              ) : null}
            </TableCell>
            <TableCell align="center">
              {item.lineType === 'DEPOSIT_PAYOUT' ? <Typography>1</Typography> : <Stack direction="row" spacing={0.25} justifyContent="center" alignItems="center">
                <Tooltip title={`Decrease ${item.productName}`}>
                  <span>
                    <IconButton
                      size="small"
                      color="primary"
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
                  sx={{ width: 56, '& .MuiInputBase-input': { px: 0.5, py: 0.75 } }}
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
                      size="small"
                      color="primary"
                      aria-label={`Increase ${item.productName}`}
                      disabled={busy}
                      onClick={() => onQuantity(item.id, Number((item.quantity + 1).toFixed(4)))}
                    >
                      <AddCircleOutlineIcon />
                    </IconButton>
                  </span>
                </Tooltip>
              </Stack>}
            </TableCell>
            <TableCell align="right">{money(item.lineType === 'LOTTERY_WIN' || item.lineType === 'DEPOSIT_PAYOUT' ? -item.unitPrice : item.unitPrice, currencyCode)}</TableCell>
            <TableCell align="right">{
              item.lineType?.startsWith('LOTTERY_') || item.lineType === 'DEPOSIT_PAYOUT'
                ? '—'
                : item.lineType === 'CUSTOM_ITEM' && item.customItemTaxTreatment === 'NON_TAXABLE'
                  ? 'Non-Taxable'
                  : totalsCalculated
                    ? money(item.estimatedTaxAmount, currencyCode)
                    : 'At checkout'
            }</TableCell>
            <TableCell align="right" sx={{ fontWeight: 700 }}>{money(item.lineTotal, currencyCode)}</TableCell>
            <TableCell align="right">
              {item.lineType === 'CUSTOM_ITEM' ? <Tooltip title={`Edit ${item.productName}`}><span><IconButton size="small" aria-label={`Edit ${item.productName}`} disabled={busy} onClick={() => onEdit(item)}><EditOutlinedIcon /></IconButton></span></Tooltip> : null}
              <Tooltip title={`Remove ${item.productName}`}>
                <span>
                  <IconButton size="small" aria-label={`Remove ${item.productName}`} disabled={busy} onClick={() => onRemove(item.id)}>
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

function TotalsPanel({ sale, currencyCode, provisionalSubtotal = 0, discount, automaticPromotion }: { sale: Sale | null; currencyCode: string; provisionalSubtotal?: number; discount?: OrderDiscount | null; automaticPromotion?:ReturnType<typeof bestMultiBuyPromotion> }) {
  const depositTotal = sale?.items.reduce((sum, item) => sum + (item.depositTotal ?? 0), 0) ?? 0;
  const subtotal = sale ? sale.subtotalAmount - depositTotal : provisionalSubtotal;
  const discountAmount = sale?.discountAmount ?? (discount ? Math.min(provisionalSubtotal,discount.type==='DISCOUNT_PERCENTAGE'?provisionalSubtotal*discount.value/100:discount.value):(automaticPromotion?.amount??0));
  const tax = sale?.estimatedTaxAmount ?? 0;
  const total = sale?.totalAmount ?? 0;

  return (
    <Paper variant="outlined" sx={{ p: 1.25, borderColor: posTokens.colors.border, borderRadius: `${posTokens.radius.card}px`, bgcolor: posTokens.colors.card }}>
      <Stack spacing={0.75}>
        <Stack direction="row" justifyContent="space-between">
          <Typography color="text.secondary">Subtotal</Typography>
          <Typography>{money(subtotal, currencyCode)}</Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between">
          <Typography color="text.secondary">{sale?.discountName ?? discount?.name ?? automaticPromotion?.definition.name ?? 'Discount'}</Typography>
          <Typography>{money(-discountAmount, currencyCode)}</Typography>
        </Stack>
        {depositTotal > 0 ? <Stack direction="row" justifyContent="space-between"><Typography color="text.secondary">Container deposits</Typography><Typography>{money(depositTotal, currencyCode)}</Typography></Stack> : null}
        <Stack direction="row" justifyContent="space-between">
          <Typography color="text.secondary">Estimated tax</Typography>
          <Typography>{sale ? money(tax, currencyCode) : 'At checkout'}</Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between" sx={{ pt: 0.75, borderTop: '1px solid', borderColor: 'divider' }}>
          <Typography variant="h6" color="primary.dark" fontWeight={800}>Total</Typography>
          <Typography variant="h6" color="primary.dark" fontWeight={800}>{sale ? money(total, currencyCode) : '—'}</Typography>
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

function CashPaymentPanel({ currencyCode, cashReceivedCents, manualCashInput, busy, onManualInput, onKeypad, onAdd, onExact }: {
  currencyCode: string; cashReceivedCents: number; manualCashInput: string; busy: boolean;
  onManualInput: (value: string) => void; onKeypad: (value: string) => void;
  onAdd: (cents: number) => void; onExact: () => void;
}) {
  const denominations = cashDenominations(currencyCode);
  return <Paper variant="outlined" sx={{ p: 1.5, height: '100%', borderColor: posTokens.colors.border, bgcolor: posTokens.colors.blueSoft }}><Stack spacing={1}>
    <Stack direction="row" justifyContent="space-between" alignItems="center"><Box><Typography variant="subtitle2">Cash received</Typography><Typography variant="h4" color="primary.dark" fontWeight={800}>{money(cashReceivedCents / 100, currencyCode)}</Typography></Box><Button variant="outlined" onClick={onExact} disabled={busy} sx={{ minHeight: 44, bgcolor: '#fff' }}>Exact</Button></Stack>
    {denominations ? <>
      <Typography variant="caption" color="text.secondary">Bills</Typography>
      <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>{denominations.bills.map(item => <Button key={item.label} variant="outlined" disabled={busy} onClick={() => onAdd(item.cents)} sx={{ minHeight: 44, minWidth: 62 }}>{item.label}</Button>)}</Stack>
      <Typography variant="caption" color="text.secondary">Coins</Typography>
      <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>{denominations.coins.map(item => <Button key={item.label} variant="outlined" disabled={busy} onClick={() => onAdd(item.cents)} sx={{ minHeight: 44, minWidth: 62 }}>{item.label}</Button>)}</Stack>
    </> : <Alert severity="info">Use manual amount entry for {currencyCode} cash.</Alert>}
    <Typography variant="caption" color="text.secondary" textAlign="center">Or enter amount</Typography>
    <TextField label="Cash received" value={manualCashInput} disabled={busy} onChange={event => onManualInput(event.target.value)} inputProps={{ inputMode: 'decimal' }} InputProps={{ startAdornment: <InputAdornment position="start">$</InputAdornment> }} size="small" />
    <Grid container spacing={0.5} aria-label="Cash received keypad">{['7', '8', '9', 'back', '4', '5', '6', 'clear', '1', '2', '3', '0', '00', '.'].map(value => <Grid item xs={3} key={value}><Button fullWidth variant="text" disabled={busy} onClick={() => onKeypad(value)} sx={{ minHeight: 36 }}>{value === 'back' ? '⌫' : value === 'clear' ? 'C' : value}</Button></Grid>)}</Grid>
  </Stack></Paper>;
}

function PaymentSummary({ sale, method, currencyCode, cashReceived, appliedAmount, changeDue, balanceDue }: {
  sale: Sale | null; method: PaymentMethod; currencyCode: string; cashReceived: number;
  appliedAmount: number; changeDue: number; balanceDue: number;
}) {
  const cashDue = method === 'CASH' ? roundCashPayable(balanceDue, currencyCode) : balanceDue;
  const rounding = roundedMoney(cashDue - balanceDue);
  const remaining = roundedMoney(Math.max(0, balanceDue - appliedAmount));
  return <Paper variant="outlined" sx={{ p: 1.5, height: '100%', bgcolor: posTokens.colors.blueLight, borderColor: posTokens.colors.border }}><Stack spacing={1}>
    <Typography variant="subtitle1" fontWeight={800}>Payment summary</Typography>
    <Stack direction="row" justifyContent="space-between"><Typography color="text.secondary">Total</Typography><Typography fontWeight={700}>{money(sale?.totalAmount ?? 0, currencyCode)}</Typography></Stack>
    <Stack direction="row" justifyContent="space-between"><Typography color="text.secondary">Paid</Typography><Typography>{money(sale?.paidAmount ?? 0, currencyCode)}</Typography></Stack>
    {method === 'CASH' && rounding !== 0 ? <><Stack direction="row" justifyContent="space-between"><Typography color="text.secondary">Cash rounding</Typography><Typography>{money(rounding, currencyCode)}</Typography></Stack><Stack direction="row" justifyContent="space-between"><Typography fontWeight={700}>Cash due</Typography><Typography fontWeight={700}>{money(cashDue, currencyCode)}</Typography></Stack></> : null}
    <Stack direction="row" justifyContent="space-between"><Typography color="text.secondary">{method === 'CASH' ? 'Cash received' : 'This payment'}</Typography><Typography>{money(method === 'CASH' ? cashReceived : appliedAmount, currencyCode)}</Typography></Stack>
    {method === 'CASH' ? <Stack direction="row" justifyContent="space-between"><Typography color="text.secondary">Cash applied</Typography><Typography>{money(appliedAmount, currencyCode)}</Typography></Stack> : null}
    <Divider />
    <Stack direction="row" justifyContent="space-between"><Typography fontWeight={800}>{changeDue > 0 ? 'Change due' : 'Remaining'}</Typography><Typography variant="h6" color={changeDue > 0 ? 'success.main' : 'primary.dark'}>{money(changeDue > 0 ? changeDue : remaining, currencyCode)}</Typography></Stack>
  </Stack></Paper>;
}

export function PaymentDialog({
  open,
  sale,
  busy,
  error,
  onClose,
  onSubmit
}: {
  open: boolean;
  sale: Sale | null;
  busy: boolean;
  error?: unknown;
  onClose: () => void;
  onSubmit: (payment: { method: PaymentMethod; amount: number; cashTendered?: number; reference?: string; notes?: string }) => void;
}) {
  const balanceDue = roundedMoney(Math.max(0, sale?.balanceDue ?? sale?.totalAmount ?? 0));
  const balanceDueCents = moneyToCents(balanceDue);
  const currencyCode = sale?.currencyCode ?? 'USD';
  const cashDueCents = moneyToCents(roundCashPayable(balanceDue, currencyCode));
  const [method, setMethod] = React.useState<PaymentMethod>('CASH');
  const [amount, setAmount] = React.useState('');
  const [cashReceivedCents, setCashReceivedCents] = React.useState(0);
  const [manualCashInput, setManualCashInput] = React.useState('0.00');
  const [reference, setReference] = React.useState('');
  const [notes, setNotes] = React.useState('');

  React.useEffect(() => {
    if (open) {
      setMethod('CASH');
      setAmount(balanceDue > 0 ? balanceDue.toFixed(2) : '');
      setCashReceivedCents(0);
      setManualCashInput('0.00');
      setReference('');
      setNotes('');
    }
  }, [balanceDue, open]);

  const parsedAmount = Number(amount);
  const cashReceived = cashReceivedCents / 100;
  const appliedAmount = method === 'CASH'
    ? Math.min(cashReceivedCents, balanceDueCents) / 100
    : roundedMoney(parsedAmount);
  const changeDue = method === 'CASH'
    ? Math.max(0, cashReceivedCents - cashDueCents) / 100
    : 0;
  const validation = (() => {
    if (!sale || sale.items.length === 0) {
      return 'Add at least one item before taking payment.';
    }
    if (!Number.isFinite(appliedAmount) || appliedAmount <= 0) {
      return 'Payment amount must be greater than zero.';
    }
    if (appliedAmount > balanceDue) {
      return 'Payment amount cannot exceed the remaining balance.';
    }
    if (method === 'CASH' && cashReceivedCents <= 0) {
      return 'Cash received must be greater than zero.';
    }
    return null;
  })();

  function setCashFromInput(value: string) {
    if (!/^\d*(?:\.\d{0,2})?$/.test(value)) return;
    setManualCashInput(value);
    setCashReceivedCents(decimalInputToCents(value) ?? 0);
  }

  function appendCashInput(value: string) {
    setManualCashInput((current) => {
      let next: string;
      if (value === 'clear') {
        next = '0.00';
      } else if (value === 'back') {
        next = current.slice(0, -1) || '0';
      } else if (value === '.' && current.includes('.')) {
        next = current;
      } else {
        next = current === '0.00' ? value : `${current}${value}`;
      }
      setCashReceivedCents(decimalInputToCents(next) ?? 0);
      return next;
    });
  }

  function addDenomination(cents: number) {
    setCashReceivedCents((current) => {
      const next = current + cents;
      setManualCashInput(centsToInput(next));
      return next;
    });
  }

  function submit(event: React.FormEvent) {
    event.preventDefault();
    if (validation || !sale || busy) {
      return;
    }
    onSubmit({
      method,
      amount: appliedAmount,
      cashTendered: method === 'CASH' ? cashReceived : undefined,
      reference: reference.trim() || undefined,
      notes: notes.trim() || undefined
    });
  }

  return <Dialog open={open} onClose={busy ? undefined : onClose} fullWidth maxWidth="md" PaperProps={{ sx: { maxHeight: 'calc(100dvh - 24px)', m: 1.5 } }}>
    <Box component="form" onSubmit={submit} sx={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <DialogTitle aria-label="Take payment" sx={{ py: 1.25 }}>💵 Take payment<Typography component="div" variant="body2" color="text.secondary">Collect payment for this sale</Typography></DialogTitle>
      <DialogContent dividers sx={{ py: 1.25 }}><Stack spacing={1.25}>
        {error ? <Alert severity="error" sx={{ py: 0 }}>{posErrorMessage(error)}</Alert> : null}
        <Grid container spacing={1}>{[['Subtotal', sale?.subtotalAmount ?? 0], ['Tax', sale?.estimatedTaxAmount ?? 0], ['Total', sale?.totalAmount ?? 0], ['Paid', sale?.paidAmount ?? 0], ['Remaining', balanceDue]].map(([label, value]) => <Grid item xs={label === 'Remaining' ? 4 : 2} key={String(label)}><Paper variant="outlined" sx={{ px: 1, py: .75 }}><Typography variant="caption" color="text.secondary">{label}</Typography><Typography fontWeight={700}>{money(Number(value), currencyCode)}</Typography></Paper></Grid>)}</Grid>
        {sale && sale.payments.length > 0 ? <Paper variant="outlined" sx={{ p: 1 }}><Typography variant="subtitle2">Payments recorded</Typography><Stack direction="row" spacing={2} useFlexGap flexWrap="wrap">{sale.payments.map(payment => <Stack key={payment.id} direction="row" spacing={1}><Typography color="text.secondary">{payment.method.replaceAll('_', ' ')}</Typography><Typography>{money(payment.amount, sale.currencyCode)}</Typography></Stack>)}</Stack></Paper> : null}
        <Box><Typography variant="subtitle2" gutterBottom>Payment method</Typography><Stack direction="row" spacing={.75} useFlexGap flexWrap="wrap">{paymentMethods.map(item => <Button key={item.value} aria-pressed={method === item.value} variant={method === item.value ? 'contained' : 'outlined'} disabled={busy} onClick={() => setMethod(item.value)} sx={{ minHeight: 44, minWidth: 92 }}>{item.label}</Button>)}</Stack></Box>
        <Grid container spacing={1.25} alignItems="stretch"><Grid item xs={12} md={8}>{method === 'CASH' ? <CashPaymentPanel currencyCode={currencyCode} cashReceivedCents={cashReceivedCents} manualCashInput={manualCashInput} busy={busy} onManualInput={setCashFromInput} onKeypad={appendCashInput} onAdd={addDenomination} onExact={() => { setCashReceivedCents(cashDueCents); setManualCashInput(centsToInput(cashDueCents)); }} /> : <Paper variant="outlined" sx={{ p: 1.5 }}><Stack spacing={1.25}><TextField label="Payment amount" type="number" value={amount} disabled={busy} inputProps={{ min: .01, step: .01 }} onChange={event => setAmount(event.target.value)} InputProps={{ startAdornment: <InputAdornment position="start">$</InputAdornment> }} /><TextField label="Reference" value={reference} disabled={busy} onChange={event => setReference(event.target.value)} /></Stack></Paper>}</Grid><Grid item xs={12} md={4}><PaymentSummary sale={sale} method={method} currencyCode={currencyCode} cashReceived={cashReceived} appliedAmount={appliedAmount} changeDue={changeDue} balanceDue={balanceDue} /></Grid></Grid>
        <TextField label="Notes (optional)" placeholder="Add a note for this payment" value={notes} disabled={busy} onChange={event => setNotes(event.target.value)} size="small" />
        {validation ? <Alert severity="warning" sx={{ py: 0 }}>{validation}</Alert> : null}
      </Stack></DialogContent>
      <DialogActions sx={{ px: 3, py: 1 }}><Button onClick={onClose} disabled={busy}>Cancel</Button><Button type="submit" variant="contained" disabled={busy || Boolean(validation)}>Record payment</Button></DialogActions>
    </Box>
  </Dialog>;
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
        <CustomerReceiptHeader receipt={receipt} />
        <Divider />
        <Stack spacing={0.5}>
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2">Receipt</Typography>
            <Typography variant="body2" fontWeight={700}>#{receipt.receiptNumber}</Typography>
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
                <Typography variant="caption" color="text.secondary">{`${item.quantity} × ${money(item.unitPrice, receipt.currencyCode)}`}</Typography>
              </Box>
              <Typography variant="body2">{money(item.lineSubtotal - (item.depositTotal ?? 0), receipt.currencyCode)}</Typography>
            </Stack>
            {item.discountAmount > 0 ? (
              <Typography variant="caption" color="text.secondary">Discount {money(item.discountAmount, receipt.currencyCode)}</Typography>
            ) : null}
            {(item.foodMenuComponents?.length || item.foodMenuModifiers?.length) ? <Box sx={{pl:1}}>{(item.foodMenuComponents??[]).filter(value=>value.state==='REMOVED').map(value=><Typography key={`${value.componentId}-removed`} variant="caption" color="text.secondary" display="block">No {value.name}</Typography>)}{(item.foodMenuModifiers??[]).map(value=><Typography key={value} variant="caption" color="text.secondary" display="block">{value}</Typography>)}{(item.foodMenuComponents??[]).filter(value=>value.state==='EXTRA').map(value=><Typography key={`${value.componentId}-extra`} variant="caption" color="text.secondary" display="block">Extra {value.name}{value.priceAdjustment>0?` +${money(value.priceAdjustment,receipt.currencyCode)}`:''}</Typography>)}</Box>:null}
            {(item.depositTotal ?? 0) > 0 ? (
              <Box sx={{ ml: 1.5, mt: 0.25 }}>
                <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="baseline">
                  <Typography variant="caption" fontWeight={500}>{depositLabel(item.depositType)}</Typography>
                  <Typography variant="caption" sx={{ whiteSpace: 'nowrap' }}>{money(item.depositTotal ?? 0, receipt.currencyCode)}</Typography>
                </Stack>
                {(item.depositQuantity ?? item.quantity) > 1 ? (
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{`${item.depositQuantity ?? item.quantity} × ${money(item.depositUnitAmount ?? 0, receipt.currencyCode)}`}</Typography>
                ) : null}
              </Box>
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
            <Typography variant="body2">Discount</Typography>
            <Typography variant="body2">{money(-receipt.discountAmount, receipt.currencyCode)}</Typography>
          </Stack>
          {(receipt.containerDepositTotal ?? 0) > 0 ? <Stack direction="row" justifyContent="space-between"><Typography variant="body2">Container Deposits</Typography><Typography variant="body2">{money(receipt.containerDepositTotal ?? 0, receipt.currencyCode)}</Typography></Stack> : null}
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
          {(receipt.cashRoundingAdjustment ?? 0) !== 0 ? <><Stack direction="row" justifyContent="space-between"><Typography variant="body2">Cash rounding</Typography><Typography variant="body2">{money(receipt.cashRoundingAdjustment ?? 0, receipt.currencyCode)}</Typography></Stack><Stack direction="row" justifyContent="space-between"><Typography fontWeight={700}>Cash total</Typography><Typography fontWeight={700}>{money(receipt.cashTotal ?? receipt.totalAmount, receipt.currencyCode)}</Typography></Stack></> : null}
        </Stack>
        <Divider />
        {receipt.payments.map((payment) => (
          <Stack direction="row" justifyContent="space-between" key={payment.id}>
            <Typography variant="body2">{payment.method.replaceAll('_', ' ')}</Typography>
            <Typography variant="body2">{money(payment.method === 'CASH' ? (payment.cashSettlementAmount ?? payment.amount) : payment.amount, receipt.currencyCode)}</Typography>
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
        {(receipt.cashDueToCustomer ?? 0) > 0 ? (
          <Stack direction="row" justifyContent="space-between">
            <Typography fontWeight={700}>Cash due to customer</Typography>
            <Typography fontWeight={700}>{money(receipt.cashDueToCustomer ?? 0, receipt.currencyCode)}</Typography>
          </Stack>
        ) : null}
        <Typography textAlign="center" variant="body2">Thank you</Typography>
        <CustomerReceiptFooter />
      </Stack>
    </Paper>
  );
}

function depositLabel(type?: 'BOTTLE' | 'CAN' | 'CASE' | 'OTHER' | null) {
  return type === 'BOTTLE' ? 'Bottle Deposit' : type === 'CAN' ? 'Can Deposit' : type === 'CASE' ? 'Case Deposit' : 'Container Deposit';
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
    <Paper
      data-testid="completed-sale"
      variant="outlined"
      sx={{ p: { xs: 1.5, md: 2 }, height: '100%', minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
    >
      <Stack spacing={1.5} sx={{ height: '100%', minHeight: 0 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1} sx={{ flexShrink: 0 }}>
          <Box>
            <Typography variant="h5" component="h2">Sale complete</Typography>
            <Typography color="text.secondary">Final total {money(sale.totalAmount, sale.currencyCode)}</Typography>
          </Box>
          <Chip color="success" label="COMPLETED" />
        </Stack>
        <Divider />
        <Grid container spacing={1.5} sx={{ flexShrink: 0 }}>
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
        <Stack spacing={1.5} sx={{ flex: 1, minHeight: 0 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1.5} sx={{ flexShrink: 0 }}>
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
          <Box
            data-testid="completed-sale-content"
            sx={{ flex: 1, minHeight: 0, overflowY: 'auto', overscrollBehavior: 'contain', pr: { xs: 0, sm: 0.5 } }}
          >
            <Stack spacing={1.5}>
              {receiptLoading ? <LoadingPanel label="Loading receipt" /> : null}
              {receiptError ? <Alert severity="warning">{errorMessage(receiptError)}</Alert> : null}
              {printError ? <Alert severity="warning">{printError}</Alert> : null}
              {receipt ? <ReceiptPreview receipt={receipt.document} widthMm={preferences.widthMm} /> : null}
            </Stack>
          </Box>
          <Stack data-testid="completed-sale-actions" direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ flexShrink: 0 }}>
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

type CustomItemTaxTreatment = 'TAXABLE' | 'NON_TAXABLE';
type CustomItemInput = { description: string; price: number; quantity: number; taxTreatment: CustomItemTaxTreatment };
const customItemDefaultDescription = (taxTreatment: CustomItemTaxTreatment) => taxTreatment === 'TAXABLE'
  ? 'Custom Taxable Item'
  : 'Custom Non-Taxable Item';
const posActionButtonSx = {
  minHeight: 38,
  px: 1.25,
  justifyContent: 'flex-start',
  borderRadius: posTokens.radius.card,
  whiteSpace: 'normal',
  lineHeight: 1.15,
  height: '100%'
} as const;

function CustomItemDialog({ open, initialItem, initialDescription, taxTreatment, currencyCode, onClose, onAdd }: { open: boolean; initialItem?: SaleItem; initialDescription: string; taxTreatment: CustomItemTaxTreatment; currencyCode: string; onClose: () => void; onAdd: (item: CustomItemInput) => void }) {
  const [description, setDescription] = React.useState('');
  const [price, setPrice] = React.useState('');
  const [quantity, setQuantity] = React.useState('1');
  React.useEffect(() => { if (open) { setDescription(initialItem?.productName ?? initialDescription); setPrice(initialItem ? String(initialItem.unitPrice) : ''); setQuantity(initialItem ? String(initialItem.quantity) : '1'); } }, [initialDescription, initialItem, open, taxTreatment]);
  const numericPrice = Number(price); const numericQuantity = Number(quantity);
  const valid = Boolean(description.trim() && numericPrice > 0 && numericQuantity > 0);
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" slotProps={{ paper: { sx: { width: 520, maxWidth: 'calc(100vw - 32px)', m: 2 } } }}>
    <DialogTitle>{initialItem ? 'Edit Custom Item' : 'Add Custom Item'}</DialogTitle>
    <DialogContent><Stack spacing={1.5} sx={{ pt: 1 }}>
      <TextField required label="Item Description" value={description} inputProps={{ maxLength: 180 }} onChange={(event) => setDescription(event.target.value)} />
      <Grid container spacing={1}>
        <Grid item xs={8}><TextField autoFocus fullWidth required label={`Price (${currencyCode})`} type="number" value={price} inputProps={{ min: 0.01, step: 0.01 }} onChange={(event) => setPrice(event.target.value)} /></Grid>
        <Grid item xs={4}><TextField fullWidth required label="Quantity" type="number" value={quantity} inputProps={{ min: 0.0001, step: 1 }} onChange={(event) => setQuantity(event.target.value)} /></Grid>
      </Grid>
      <Alert severity="warning" sx={{ py: 0 }}>Do not use Custom Item for regulated or special-duty products.</Alert>
    </Stack></DialogContent>
    <DialogActions><Button onClick={onClose}>Cancel</Button><Button variant="contained" disabled={!valid} onClick={() => onAdd({ description: description.trim(), price: numericPrice, quantity: numericQuantity, taxTreatment })}>{initialItem ? 'Update Item' : 'Add to Cart'}</Button></DialogActions>
  </Dialog>;
}

export function PosCartPage() {
  const { currentUser, getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);
  const saleId = searchParams.get('saleId');
  const [activeSale, setActiveSale] = React.useState<Sale | null>(null);
  const [cartItems, setCartItems] = React.useState<SaleItem[]>([]);
  const transactionEpochRef = React.useRef(0);
  const cartRevisionRef = React.useRef(0);
  const openPaymentAfterQuoteRef = React.useRef(false);
  const checkoutClickStartedAtRef = React.useRef<number | null>(null);
  const paymentStartedAtRef = React.useRef<number | null>(null);
  const completionStartedAtRef = React.useRef<number | null>(null);
  const barcodeStartedAtRef = React.useRef<number | null>(null);
  const [barcode, setBarcode] = React.useState('');
  const [searchMode, setSearchMode] = React.useState<'BARCODE' | 'PRODUCT'>('BARCODE');
  const [productSearch, setProductSearch] = React.useState('');
  const [customItemOpen, setCustomItemOpen] = React.useState(false);
  const [customItemDescription, setCustomItemDescription] = React.useState('');
  const [customItemTaxTreatment, setCustomItemTaxTreatment] = React.useState<CustomItemTaxTreatment>('TAXABLE');
  const [editingCustomItem, setEditingCustomItem] = React.useState<SaleItem | undefined>();
  const [discount,setDiscount]=React.useState<OrderDiscount|null>(null);
  const [discountOpen,setDiscountOpen]=React.useState(false);
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
    sku: string;
    price: number;
    minimumAge: number | null;
  } | null>(null);
  const barcodeInputRef = React.useRef<HTMLInputElement | null>(null);
  const [receiptPreferences, setReceiptPreferences] = React.useState<ReceiptPrinterPreferences>(() => loadReceiptPrinterPreferences());
  const [receiptPrintError, setReceiptPrintError] = React.useState<string | null>(null);
  const [draftRecovered, setDraftRecovered] = React.useState(false);
  const [printingReceipt, setPrintingReceipt] = React.useState(false);
  const [lotteryAction, setLotteryAction] = React.useState<'SOLD' | 'WIN' | null>(null);
  const [depositPayoutOpen, setDepositPayoutOpen] = React.useState(false);
  const [lotteryNotice, setLotteryNotice] = React.useState<string | null>(null);
  const [payoutConfirmationOpen, setPayoutConfirmationOpen] = React.useState(false);
  const completionKeyRef = React.useRef<string | null>(null);
  const automaticPrintSaleIdRef = React.useRef<string | null>(null);
  const autoPrintedReceiptRef = React.useRef<string | null>(null);
  const recoveryCheckedRef = React.useRef(false);
  const previousSearchStoreIdRef = React.useRef<string | undefined>(undefined);

  React.useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (activeSale && isPendingCheckoutStatus(activeSale.status) && activeSale.items.length > 0) {
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

  React.useEffect(() => {
    if (current.data?.registerType === 'FOOD_SERVICE') navigate('/pos/food', { replace: true });
  }, [current.data?.registerType, navigate]);

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
    if (saleId && saleQuery.data?.id === saleId) {
      setCartItems(saleQuery.data.items);
      rememberSale(saleQuery.data);
    }
  }, [saleId, saleQuery.data]);

  const productResults = useQuery({
    queryKey: ['products', 'pos-search', current.data?.storeId, submittedSearch],
    queryFn: async () => listProducts(await getValidAccessToken(), { q: submittedSearch, storeId: current.data?.storeId, active: true, size: 20 }),
    enabled: submittedSearch.trim().length > 0 && Boolean(current.data?.storeId)
  });
  const savedDiscounts=useQuery({queryKey:['active-pos-discounts',current.data?.storeId],queryFn:async()=>listActiveStoreDiscounts(await getValidAccessToken(),current.data?.storeId??''),enabled:Boolean(current.data?.storeId),staleTime:5*60_000});
  React.useEffect(() => {
    if (searchMode !== 'PRODUCT') return;
    const normalized = productSearch.trim();
    const timer = window.setTimeout(() => setSubmittedSearch(normalized), 250);
    return () => window.clearTimeout(timer);
  }, [productSearch, searchMode]);

  React.useEffect(() => {
    const storeId = current.data?.storeId;
    if (previousSearchStoreIdRef.current !== undefined && previousSearchStoreIdRef.current !== storeId) {
      setProductSearch('');
      setSubmittedSearch('');
    }
    previousSearchStoreIdRef.current = storeId;
  }, [current.data?.storeId]);

  const store = stores.data?.content.find((item) => item.id === current.data?.storeId);
  const register = registers.data?.content.find((item) => item.id === current.data?.registerId);
  const device = devices.data?.content.find((item) => item.id === current.data?.deviceId);
  const lotteryEntitlement = useQuery({
    queryKey: ['stores', current.data?.storeId, 'capabilities', 'LOTTERY', 'effective'],
    queryFn: async () => getEffectiveStoreCapability(await getValidAccessToken(), current.data?.storeId ?? '', 'LOTTERY'),
    enabled: Boolean(current.data?.storeId && store?.capabilities?.includes('LOTTERY')),
    staleTime: 30_000
  });
  const lotteryEnabled = lotteryEntitlement.data?.enabled === true;
  const currencyCode = activeSale?.currencyCode ?? store?.currencyCode ?? 'USD';
  const provisionalSubtotal = cartItems.reduce((sum, item) => sum + (item.lineType === 'LOTTERY_WIN' || item.lineType === 'DEPOSIT_PAYOUT' ? -1 : 1) * item.unitPrice * item.quantity - item.discountAmount, 0);
  const automaticPromotion=bestMultiBuyPromotion(savedDiscounts.data??[],'RETAIL',cartItems.filter(item=>item.productId).map(item=>({id:item.id,quantity:item.quantity,unitPrice:item.unitPrice,targets:{PRODUCT:item.productId??undefined,PRODUCT_VARIANT:item.variantId??undefined}})));

  function changeCart(update: (items: SaleItem[]) => SaleItem[]) {
    cartRevisionRef.current += 1;
    openPaymentAfterQuoteRef.current = false;
    recalculateMutation.reset();
    setCartItems(update);
    if (activeSale && isPendingCheckoutStatus(activeSale.status) && activeSale.payments.length === 0) {
      void getValidAccessToken().then(token => cancelSale(token, activeSale.id)).catch(() => undefined);
      setSearchParams({});
    }
    setActiveSale(null);
    setPaymentDialogOpen(false);
  }

  function localItem(item: { productId: string; variantId?: string; name: string; sku: string; price: number; ageVerified?: boolean }): SaleItem {
    return {
      id: `local:${item.productId}:${item.variantId ?? ''}`,
      productId: item.productId, variantId: item.variantId, lineNumber: cartItems.length + 1,
      productSku: item.sku, productName: item.name, variantSku: null, variantName: null,
      quantity: 1, unitPrice: item.price, discountAmount: 0, completedProductCost: null,
      completedProductPrice: null, completedProductCapabilities: null, priceOverride: false,
      ageVerified: Boolean(item.ageVerified), serialNumber: null, externalReference: null,
      customerId: null, paymentMethodCode: null, lineSubtotal: item.price,
      estimatedTaxAmount: 0, lineTotal: item.price, version: 0
    };
  }

  function addCustomItem(item: CustomItemInput) {
    const updated = { id: editingCustomItem?.id ?? `local:custom:${crypto.randomUUID()}`, lineType: 'CUSTOM_ITEM' as const, productId: null, variantId: null,
      lineNumber: cartItems.length + 1, productSku: null, productName: item.description, variantSku: null, variantName: null,
      customItemTaxTreatment: item.taxTreatment, quantity: item.quantity, unitPrice: item.price, discountAmount: 0,
      completedProductCost: null, completedProductPrice: null, completedProductCapabilities: null, priceOverride: false,
      ageVerified: false, serialNumber: null, externalReference: null, customerId: null, paymentMethodCode: null,
      lineSubtotal: item.price * item.quantity, estimatedTaxAmount: 0, lineTotal: item.price * item.quantity, version: 0 };
    if (editingCustomItem) changeCart(items => items.map(candidate => candidate.id === editingCustomItem.id ? updated : candidate)); else addLocalItem(updated);
    closeCustomItem();
  }

  function openCustomItem(taxTreatment: CustomItemTaxTreatment, description = '') {
    setEditingCustomItem(undefined);
    setCustomItemDescription(description || customItemDefaultDescription(taxTreatment));
    setCustomItemTaxTreatment(taxTreatment);
    setCustomItemOpen(true);
  }

  function closeCustomItem() {
    setCustomItemOpen(false);
    setEditingCustomItem(undefined);
    setCustomItemDescription('');
    setCustomItemTaxTreatment('TAXABLE');
  }

  function editCustomItem(item: SaleItem) {
    setEditingCustomItem(item);
    setCustomItemDescription(item.productName);
    setCustomItemTaxTreatment(item.customItemTaxTreatment ?? 'NON_TAXABLE');
    setCustomItemOpen(true);
  }

  function addLotteryItem(mode: 'SOLD' | 'WIN', amount: number) {
    const win = mode === 'WIN';
    addLocalItem({ id: `local:lottery:${crypto.randomUUID()}`, lineType: win ? 'LOTTERY_WIN' : 'LOTTERY_SOLD',
      productId: null, variantId: null, lineNumber: cartItems.length + 1, productSku: null,
      productName: win ? 'Lottery Win' : 'Lottery Sold', variantSku: null, variantName: null,
      quantity: 1, unitPrice: amount, discountAmount: 0, completedProductCost: null,
      completedProductPrice: null, completedProductCapabilities: null, priceOverride: false,
      ageVerified: false, serialNumber: null, externalReference: null, customerId: null,
      paymentMethodCode: null, lineSubtotal: win ? -amount : amount, estimatedTaxAmount: 0,
      lineTotal: win ? -amount : amount, version: 0 });
    setLotteryAction(null);
    setLotteryNotice(`${win ? 'Lottery Win' : 'Lottery Sold'} added to cart.`);
  }

  function addDepositPayout(amount: number) {
    addLocalItem({ id: `local:deposit-payout:${crypto.randomUUID()}`, lineType: 'DEPOSIT_PAYOUT',
      productId: null, variantId: null, lineNumber: cartItems.length + 1, productSku: null,
      productName: 'Deposit Payout', variantSku: null, variantName: null, quantity: 1,
      unitPrice: amount, discountAmount: 0, completedProductCost: null, completedProductPrice: null,
      completedProductCapabilities: null, priceOverride: false, ageVerified: false, serialNumber: null,
      externalReference: null, customerId: null, paymentMethodCode: null, lineSubtotal: -amount,
      estimatedTaxAmount: 0, lineTotal: -amount, version: 0 });
    setDepositPayoutOpen(false);
  }

  function addLocalItem(item: SaleItem) {
    changeCart(items => {
      const existing = !item.lineType || item.lineType === 'CATALOG_PRODUCT'
        ? items.find(candidate => (!candidate.lineType || candidate.lineType === 'CATALOG_PRODUCT')
          && candidate.productId === item.productId
          && (candidate.variantId ?? undefined) === (item.variantId ?? undefined))
        : undefined;
      return existing ? items.map(candidate => candidate.id === existing.id
        ? { ...candidate, quantity: candidate.quantity + 1, lineSubtotal: candidate.lineSubtotal + candidate.unitPrice, lineTotal: candidate.lineTotal + candidate.unitPrice }
        : candidate) : [...items, item];
    });
    window.setTimeout(() => barcodeInputRef.current?.focus(), 0);
  }

  function rememberSale(sale: Sale | null) {
    setActiveSale(sale);
    if (sale && isPendingCheckoutStatus(sale.status)) {
      queryClient.setQueryData(['sale', sale.id], sale);
      setSearchParams({ saleId: sale.id });
      void saveDraftCartRecovery(sale);
      return;
    }
    void clearDraftCartRecovery();
    setSearchParams({});
  }

  function startNewSale() {
    const startedAt = performance.now();
    transactionEpochRef.current += 1;
    cartRevisionRef.current += 1;
    openPaymentAfterQuoteRef.current = false;
    checkoutClickStartedAtRef.current = null;
    paymentStartedAtRef.current = null;
    completionStartedAtRef.current = null;
    barcodeStartedAtRef.current = null;
    barcodeMutation.reset();
    recalculateMutation.reset();
    holdMutation.reset();
    cancelMutation.reset();
    paymentMutation.reset();
    completeMutation.reset();
    reprintReceiptMutation.reset();
    setCartItems([]);
    setDiscount(null);
    setDiscountOpen(false);
    setActiveSale(null);
    setPaymentDialogOpen(false);
    setSearchMode('BARCODE');
    setBarcode('');
    setProductSearch('');
    setSubmittedSearch('');
    setUnknownBarcode(null);
    setInventoryWarning(null);
    setPendingAgeVerification(null);
    closeCustomItem();
    setLotteryAction(null);
    setDepositPayoutOpen(false);
    setLotteryNotice(null);
    setPayoutConfirmationOpen(false);
    setReceiptPrintError(null);
    setDraftRecovered(false);
    completionKeyRef.current = null;
    automaticPrintSaleIdRef.current = null;
    autoPrintedReceiptRef.current = null;
    void clearDraftCartRecovery();
    setSearchParams({});
    window.setTimeout(() => barcodeInputRef.current?.focus(), 0);
    reportPosTiming('new-sale-ready', startedAt);
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
      setCartItems(recoveredSale.items);
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

  function addResolvedProduct(item: { productId: string; variantId?: string; ageVerified?: boolean; name?: string; sku?: string; price?: number }) {
      posScanDebug('CART_ADD_CALLED', {
        productId: item.productId,
        variantId: item.variantId ?? null,
        ageVerified: Boolean(item.ageVerified),
        cartItemsBefore: activeSale?.items.length ?? 0
      });
      const resolved = localItem({ productId: item.productId, variantId: item.variantId, name: item.name ?? 'Product', sku: item.sku ?? '', price: item.price ?? 0, ageVerified: item.ageVerified });
      posScanDebug('CART_STATE_AFTER_ADD', {
        cartItemsAfter: cartItems.length + 1
      });
      setPendingAgeVerification(null);
      addLocalItem(resolved);
  }

  function isVerifiedInCurrentSale(productId: string, variantId?: string) {
    return Boolean(cartItems.some((item) => item.productId === productId
      && (item.variantId ?? undefined) === variantId && item.ageVerified));
  }

  function queueRestrictedItem(item: { productId: string; variantId?: string; label: string; sku: string; price: number; minimumAge: number | null }) {
    if (isVerifiedInCurrentSale(item.productId, item.variantId)) {
      addResolvedProduct({ productId: item.productId, variantId: item.variantId, ageVerified: true, name: item.label, sku: item.sku, price: item.price });
      return;
    }
    setPendingAgeVerification(item);
  }

  function addProduct(product: Product) {
    const selectedVariant = product.variants.length === 1 ? product.variants[0] : undefined;
    if (product.capabilities.includes('REQUIRE_AGE_VERIFICATION')) {
      queueRestrictedItem({ productId: product.id, variantId: selectedVariant?.id,
        label: selectedVariant ? `${product.name} — ${selectedVariant.name}` : product.name,
        sku: selectedVariant?.sku ?? product.sku, price: selectedVariant?.price ?? product.price,
        minimumAge: product.minimumAge ?? null });
      return;
    }
    addResolvedProduct({ productId: product.id, variantId: selectedVariant?.id,
      name: selectedVariant ? `${product.name} — ${selectedVariant.name}` : product.name,
      sku: selectedVariant?.sku ?? product.sku, price: selectedVariant?.price ?? product.price });
  }

  const barcodeMutation = useMutation({
    mutationFn: async (value: string) => {
      const epoch = transactionEpochRef.current;
      barcodeStartedAtRef.current = performance.now();
      const token = await getValidAccessToken();
      const normalized = value.trim();
      posScanDebug('BARCODE_LOOKUP_STARTED', { barcode: normalized });
      if (!current.data?.storeId) throw new Error('Open a register before scanning products');
      const product = await lookupPosBarcode(token, normalized, current.data.storeId);
      return { product, epoch };
    },
    onSuccess: ({ product, epoch }: { product: PosBarcodeLookup; epoch: number }) => {
      if (epoch !== transactionEpochRef.current) return;
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
        sku: product.sku,
        price: product.price,
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
        addResolvedProduct({ productId: item.productId, variantId: item.variantId, name: item.label, sku: product.sku, price: product.price });
      }
      if (barcodeStartedAtRef.current !== null) {
        reportPosTiming('barcode-to-cart', barcodeStartedAtRef.current);
        barcodeStartedAtRef.current = null;
      }
    },
    onError: (error, value) => {
      if (errorMessage(error).includes('BARCODE_NOT_FOUND')) {
        setUnknownBarcode(value.trim());
      }
    }
  });

  const recalculateMutation = useMutation({
    mutationFn: async () => {
      if (!current.data || cartItems.length === 0) throw new Error('Cart is empty');
      const epoch = transactionEpochRef.current;
      const revision = cartRevisionRef.current;
      const sale = await checkoutSaleCart(await getValidAccessToken(), {
        registerSessionId: current.data.id, saleChannel: 'POS',
        items: cartItems.map(item => item.lineType === 'CUSTOM_ITEM'
          ? { lineType: 'CUSTOM_ITEM' as const, description: item.productName, unitPrice: item.unitPrice, quantity: item.quantity, taxTreatment: item.customItemTaxTreatment ?? undefined }
          : item.lineType === 'LOTTERY_SOLD' || item.lineType === 'LOTTERY_WIN' || item.lineType === 'DEPOSIT_PAYOUT'
            ? { lineType: item.lineType, unitPrice: item.unitPrice, quantity: item.quantity }
          : { lineType: 'CATALOG_PRODUCT' as const, productId: item.productId ?? undefined, variantId: item.variantId ?? undefined, quantity: item.quantity, ageVerified: item.ageVerified }),
        discount: discount ? (discount.definitionId ? {discountDefinitionId:discount.definitionId}:{type:discount.type,value:discount.value,reason:discount.reason||undefined}) : undefined
      });
      return { sale, revision, epoch };
    },
    onSuccess: ({ sale, revision, epoch }) => {
      if (epoch !== transactionEpochRef.current || revision !== cartRevisionRef.current) return;
      setCartItems(sale.items);
      rememberSale(sale);
      if (openPaymentAfterQuoteRef.current && sale.totalAmount > 0) {
        openPaymentAfterQuoteRef.current = false;
        setPaymentDialogOpen(true);
        if (checkoutClickStartedAtRef.current !== null) {
          reportPosTiming('checkout-to-payment-ui', checkoutClickStartedAtRef.current);
          checkoutClickStartedAtRef.current = null;
        }
      }
    },
    onError: () => { openPaymentAfterQuoteRef.current = false; }
  });

  function requestAuthoritativeCheckout(openPayment: boolean) {
    if (openPayment) {
      openPaymentAfterQuoteRef.current = true;
      checkoutClickStartedAtRef.current ??= performance.now();
    }
    if (!recalculateMutation.isPending) recalculateMutation.mutate();
  }

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
      const epoch = transactionEpochRef.current;
      const requestedSaleId = activeSale.id;
      const sale = await recordSalePayment(await getValidAccessToken(), requestedSaleId, payment);
      return { sale, epoch, requestedSaleId };
    },
    onSuccess: ({ sale, epoch, requestedSaleId }) => {
      if (epoch !== transactionEpochRef.current || sale.id !== requestedSaleId) return;
      rememberSale(sale);
      setPaymentDialogOpen(!sale.paymentComplete);
      if (paymentStartedAtRef.current !== null) {
        reportPosTiming('record-payment', paymentStartedAtRef.current);
        paymentStartedAtRef.current = null;
      }
    }
  });

  const completeMutation = useMutation({
    mutationFn: async () => {
      if (!activeSale) {
        throw new Error('No active sale');
      }
      const key = completionKeyRef.current ?? completionKey();
      completionKeyRef.current = key;
      const epoch = transactionEpochRef.current;
      const requestedSaleId = activeSale.id;
      const sale = await completeSale(await getValidAccessToken(), requestedSaleId, key);
      return { sale, epoch, requestedSaleId };
    },
    onSuccess: ({ sale, epoch, requestedSaleId }) => {
      if (epoch !== transactionEpochRef.current || sale.id !== requestedSaleId) return;
      completionKeyRef.current = null;
      automaticPrintSaleIdRef.current = sale.id;
      setPaymentDialogOpen(false);
      setPayoutConfirmationOpen(false);
      rememberSale(sale);
      void queryClient.invalidateQueries({ queryKey: ['sales'], refetchType: 'none' });
      if (completionStartedAtRef.current !== null) {
        reportPosTiming('complete-sale', completionStartedAtRef.current);
        completionStartedAtRef.current = null;
      }
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

  const busy = barcodeMutation.isPending
    || recalculateMutation.isPending
    || holdMutation.isPending
    || cancelMutation.isPending
    || paymentMutation.isPending
    || completeMutation.isPending;
  const cartLocked = busy || Boolean(current.data?.tillSecured) || Boolean(activeSale?.payments.length) || activeSale?.status === 'COMPLETED';
  const barcodeError = barcodeMutation.error && !errorMessage(barcodeMutation.error).includes('BARCODE_NOT_FOUND')
    ? barcodeMutation.error
    : null;
  const discountPricingError = discount && recalculateMutation.error
    ? posErrorMessage(recalculateMutation.error)
    : null;
  const pageError = current.error ?? saleQuery.error ?? barcodeError
    ?? (discount ? null : recalculateMutation.error) ?? holdMutation.error ?? cancelMutation.error
    ?? (paymentDialogOpen ? null : paymentMutation.error) ?? completeMutation.error;

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
    <Stack data-testid="retail-checkout-shell" spacing={1} sx={{ height: '100%', minHeight: 0, minWidth: 0, overflow: 'hidden', color: posTokens.colors.text }}>
      <CustomItemDialog open={customItemOpen} initialItem={editingCustomItem} initialDescription={customItemDescription} taxTreatment={customItemTaxTreatment} currencyCode={currencyCode} onClose={closeCustomItem} onAdd={addCustomItem} />
      <GlobalStyles styles={receiptPrintStyles} />
      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1} sx={{ minHeight: 48, flexShrink: 0 }}>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h5" component="h1" sx={{ lineHeight: 1.1, color: posTokens.colors.navy, fontSize: { xs: 24, md: 28 } }}>Checkout</Typography>
          <Typography variant="body2" color="text.secondary" noWrap>Scan, verify pricing and tax, then record payment.</Typography>
        </Box>
        <Stack direction="row" spacing={0.75} sx={{ flexShrink: 0 }}>
          {current.data ? <SecureTill session={current.data} storeName={store?.name} busy={busy} /> : null}
          <Button size="small" component={Link} to="/pos/held-sales" variant="outlined" startIcon={<PlayCircleOutlineIcon />} sx={{ bgcolor: posTokens.colors.card }}>
            Held sales
          </Button>
          <Button size="small" variant="outlined" startIcon={<PauseCircleOutlineIcon />} disabled={!activeSale || cartLocked} onClick={() => holdMutation.mutate()} sx={{ bgcolor: posTokens.colors.card }}>
            Hold sale
          </Button>
          <Button
            size="small"
            variant="contained"
            startIcon={<RefreshIcon />}
            disabled={cartItems.length === 0 || busy}
            onClick={() => requestAuthoritativeCheckout(false)}
          >
            {recalculateMutation.isPending ? 'Calculating…' : 'Calculate Tax'}
          </Button>
        </Stack>
      </Stack>

      {current.isLoading ? <LoadingPanel label="Loading register context" /> : null}
      {!current.isLoading && !current.data ? (
        <Alert severity="info" action={<Button component={Link} to="/register/open">Open</Button>}>
          No register session is open for this device.
        </Alert>
      ) : null}
      {(pageError || inventoryWarning || (draftRecovered && isPendingCheckoutStatus(activeSale?.status)) || unknownBarcode) ? (
        <Box sx={{ flexShrink: 0, maxHeight: 54, overflowY: 'auto' }}>
          {pageError ? <Alert severity="error" sx={{ py: 0 }}>{posErrorMessage(pageError)}</Alert> : null}
          {!pageError && inventoryWarning ? <Alert severity="warning" onClose={() => setInventoryWarning(null)} sx={{ py: 0 }}>{inventoryWarning}</Alert> : null}
          {!pageError && !inventoryWarning && draftRecovered && isPendingCheckoutStatus(activeSale?.status) ? <Alert severity="success" onClose={() => setDraftRecovered(false)} sx={{ py: 0 }}>Checkout recovered after refresh.</Alert> : null}
          {!pageError && !inventoryWarning && !(draftRecovered && isPendingCheckoutStatus(activeSale?.status)) && unknownBarcode ? <Alert severity="warning" sx={{ py: 0 }} action={currentUser?.permissions?.includes('POS_CUSTOM_ITEM') ? <Stack direction="row" spacing={0.5}><Button size="small" onClick={() => openCustomItem('TAXABLE')}>Taxable Custom Item</Button><Button size="small" onClick={() => openCustomItem('NON_TAXABLE')}>Non-Taxable Custom Item</Button></Stack> : undefined}>{`No product was found for barcode ${unknownBarcode}.`}</Alert> : null}
        </Box>
      ) : null}
      {lotteryNotice ? <Alert severity="success" onClose={() => setLotteryNotice(null)} sx={{ py: 0 }}>{lotteryNotice}</Alert> : null}

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
            onNewSale={startNewSale}
          />
        ) : (
        <Box sx={{ display: 'grid', gridTemplateColumns: 'minmax(0, 2fr) minmax(320px, 0.95fr)', gap: { xs: 1, md: 1.5 }, flex: 1, minHeight: 0, minWidth: 0 }}>
          <Stack spacing={1} sx={{ minWidth: 0, minHeight: 0 }}>
            <Paper variant="outlined" sx={{ p: 1, position: 'relative', zIndex: 2, flexShrink: 0, borderRadius: posTokens.radius.card }}>
              <Stack spacing={0.75}>
                <Stack direction="row" spacing={0.5} role="group" aria-label="Search mode">
                  <Button size="small" variant={searchMode === 'BARCODE' ? 'contained' : 'outlined'} aria-pressed={searchMode === 'BARCODE'} onClick={() => { setSearchMode('BARCODE'); setSubmittedSearch(''); window.setTimeout(() => barcodeInputRef.current?.focus(), 0); }} sx={searchMode === 'BARCODE' ? undefined : { bgcolor: posTokens.colors.blueLight }}>Barcode</Button>
                  <Button size="small" variant={searchMode === 'PRODUCT' ? 'contained' : 'outlined'} aria-pressed={searchMode === 'PRODUCT'} onClick={() => setSearchMode('PRODUCT')} sx={searchMode === 'PRODUCT' ? undefined : { bgcolor: posTokens.colors.blueLight }}>Product Search</Button>
                </Stack>
                {searchMode === 'BARCODE' ? (
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
                      placeholder="Scan barcode or enter code"
                      value={barcode}
                      onChange={(event) => {
                        posScanDebug('BARCODE_RAW_INPUT', { length: event.target.value.length });
                        setBarcode(event.target.value);
                      }}
                      fullWidth
                      size="small"
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
                ) : (
                  <Box component="form" onSubmit={(event) => {
                    event.preventDefault();
                    setSubmittedSearch(productSearch.trim());
                  }}>
                    <Stack direction="row" spacing={0.75}>
                      <TextField
                        label="Product search"
                        placeholder="Search by product name, SKU or barcode"
                        value={productSearch}
                        onChange={(event) => setProductSearch(event.target.value)}
                        fullWidth
                        size="small"
                        disabled={cartLocked}
                        InputProps={{
                          startAdornment: (
                            <InputAdornment position="start">
                              <SearchIcon />
                            </InputAdornment>
                          )
                        }}
                      />
                      <Button size="small" type="submit" variant="contained" startIcon={<SearchIcon />} disabled={cartLocked || !productSearch.trim()}>
                        Search
                      </Button>
                    </Stack>
                  </Box>
                )}
                {searchMode === 'PRODUCT' && (productResults.isFetching || (submittedSearch && productResults.data)) ? (
                  <Paper elevation={2} sx={{ position: 'absolute', top: 'calc(100% - 2px)', left: 8, right: 8, maxHeight: 260, overflowY: 'auto', zIndex: 5, border: '1px solid', borderColor: 'divider' }}>
                    {productResults.isFetching ? <Box sx={{ p: 1.5 }}><CircularProgress size={22} aria-label="Searching products" /></Box> : null}
                    {!productResults.isFetching && productResults.data ? <><ProductSearchResults products={productResults.data.content} currencyCode={currencyCode} disabled={cartLocked} onAdd={(product) => { addProduct(product); setProductSearch(''); setSubmittedSearch(''); setSearchMode('BARCODE'); }} />{currentUser?.permissions?.includes('POS_CUSTOM_ITEM') ? <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 0.75, p: 1 }}><Button variant="outlined" startIcon={<AddCircleOutlineIcon />} disabled={cartLocked} onClick={() => openCustomItem('TAXABLE', productResults.data.content.length === 0 ? productSearch.trim() : '')}>Taxable Custom Item</Button><Button variant="outlined" startIcon={<AddCircleOutlineIcon />} disabled={cartLocked} onClick={() => openCustomItem('NON_TAXABLE', productResults.data.content.length === 0 ? productSearch.trim() : '')}>Non-Taxable Custom Item</Button></Box> : null}</> : null}
                  </Paper>
                ) : null}
                <Box data-testid="pos-action-grid" sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', md: 'repeat(3, minmax(0, 1fr))' }, gap: 0.75, width: '100%' }}>
                  {currentUser?.permissions?.includes('POS_CUSTOM_ITEM') ? <Button size="small" variant="outlined" startIcon={<AddCircleOutlineIcon />} disabled={cartLocked} sx={posActionButtonSx} onClick={() => openCustomItem('TAXABLE')}>Taxable Custom Item</Button> : null}
                  {currentUser?.permissions?.includes('POS_CUSTOM_ITEM') ? <Button size="small" variant="outlined" startIcon={<AddCircleOutlineIcon />} disabled={cartLocked} sx={posActionButtonSx} onClick={() => openCustomItem('NON_TAXABLE')}>Non-Taxable Custom Item</Button> : null}
                  {currentUser?.permissions?.includes('POS_DEPOSIT_PAYOUT') ? <Button size="small" variant="outlined" color="warning" startIcon={<PaymentsOutlinedIcon />} disabled={cartLocked} sx={posActionButtonSx} onClick={() => setDepositPayoutOpen(true)}>Deposit Payout</Button> : null}
                  {lotteryEnabled && currentUser?.permissions?.includes('LOTTERY_SALE_RECORD') ? <Button size="small" variant="outlined" startIcon={<ConfirmationNumberOutlinedIcon />} disabled={cartLocked} sx={posActionButtonSx} onClick={() => setLotteryAction('SOLD')}>Lottery Sold</Button> : null}
                  {lotteryEnabled && currentUser?.permissions?.includes('LOTTERY_PAYOUT_RECORD') ? <Button size="small" variant="outlined" color="warning" startIcon={<PaymentsOutlinedIcon />} disabled={cartLocked} sx={posActionButtonSx} onClick={() => setLotteryAction('WIN')}>Lottery Win</Button> : null}
                </Box>
              </Stack>
            </Paper>

            <Paper variant="outlined" sx={{ flex: 1, minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column', borderRadius: posTokens.radius.card }}>
              <Box data-testid="cart-scroll-region" sx={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
                <CartLines
                  items={cartItems}
                  currencyCode={currencyCode}
                  totalsCalculated={activeSale !== null}
                  busy={cartLocked}
                  onQuantity={(itemId, quantity) => changeCart(items => items.map(item => item.id === itemId ? { ...item, quantity, lineSubtotal: (item.lineType === 'LOTTERY_WIN' || item.lineType === 'DEPOSIT_PAYOUT' ? -1 : 1) * item.unitPrice * quantity, lineTotal: (item.lineType === 'LOTTERY_WIN' || item.lineType === 'DEPOSIT_PAYOUT' ? -1 : 1) * item.unitPrice * quantity, estimatedTaxAmount: 0 } : item))}
                  onRemove={(itemId) => changeCart(items => items.filter(item => item.id !== itemId))}
                  onEdit={editCustomItem}
                />
              </Box>
              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ px: 1, py: 0.75, borderTop: '1px solid', borderColor: 'divider', flexShrink: 0 }}>
                <Button size="small" color="error" variant="outlined" disabled={!cartItems.length || cartLocked} onClick={() => { changeCart(() => []); setDiscount(null); }}>Clear cart</Button>
                <Typography variant="body2" color="text.secondary">{cartItems.length} item(s)</Typography>
              </Stack>
            </Paper>
          </Stack>

          <Stack spacing={1} sx={{ minWidth: 0, minHeight: 0, overflow: 'hidden' }}>
            <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 1 }}>
              <CompactIdentitySummary session={current.data} store={store} register={register} device={device} />
              <TotalsPanel sale={activeSale} currencyCode={currencyCode} provisionalSubtotal={provisionalSubtotal} discount={discountPricingError ? null : discount} automaticPromotion={discount?undefined:automaticPromotion} />
              <Paper variant="outlined" sx={{ p: 1.25, borderRadius: posTokens.radius.card }}>
                <Stack spacing={0.75}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center"><Typography variant="subtitle2" sx={{ color: posTokens.colors.navy }}>Discount</Typography><Chip label={discountPricingError ? 'NOT APPLICABLE' : discount ? (activeSale ? 'APPLIED' : 'SELECTED') : automaticPromotion ? 'AUTO' : 'NONE'} size="small" color={discountPricingError ? 'error' : discount||automaticPromotion ? 'primary' : 'default'} variant="outlined" /></Stack>
                  {currentUser?.permissions?.includes('POS_SALE_DISCOUNT') ? <>
                    <TextField select size="small" label="Discount" value={discount?.definitionId??(discount?'__custom__':'')} disabled={!cartItems.length||busy} onChange={event=>{if(event.target.value==='__custom__'){setDiscountOpen(true);return;}const selected=savedDiscounts.data?.find((value:DiscountDefinition)=>value.id===event.target.value);if(selected&&selected.type!=='MULTI_BUY_FIXED_PRICE'){setDiscount({definitionId:selected.id,name:selected.name,type:selected.type,value:selected.value,reason:''});cartRevisionRef.current+=1;setActiveSale(null);setPaymentDialogOpen(false);recalculateMutation.reset();}}}>
                      <MenuItem value=""><em>Select discount</em></MenuItem>{(savedDiscounts.data??[]).filter(value=>value.type!=='MULTI_BUY_FIXED_PRICE').map((value:DiscountDefinition)=><MenuItem key={value.id} value={value.id}>{value.name} — {value.type==='DISCOUNT_PERCENTAGE'?`${value.value}%`:money(value.value,currencyCode)}</MenuItem>)}<Divider/><MenuItem value="__custom__">Custom Discount</MenuItem>
                    </TextField>
                    {discountPricingError ? <Alert severity="warning" sx={{ py: 0, '& .MuiAlert-message': { py: 0.25 }, fontSize: 13 }}>{discountPricingError}</Alert> : null}
                    {discount?<Button size="small" color="error" sx={{ alignSelf: 'flex-start' }} onClick={()=>{setDiscount(null);cartRevisionRef.current+=1;setActiveSale(null);setPaymentDialogOpen(false);recalculateMutation.reset();}}>Remove Discount</Button>:null}
                  </>:null}
                </Stack>
              </Paper>
              <Paper variant="outlined" sx={{ p: 1.25, borderRadius: posTokens.radius.card }}>
                <Stack spacing={0.75}>
                  <Stack direction="row" justifyContent="space-between"><Typography variant="subtitle2" sx={{ color: posTokens.colors.navy }}>Payment</Typography><Typography variant="body2" fontWeight={700} color="primary.dark">{activeSale ? (activeSale.totalAmount < 0 ? `Payout Due ${money(Math.abs(activeSale.totalAmount), currencyCode)}` : `Remaining ${money(activeSale.balanceDue ?? activeSale.totalAmount, currencyCode)}`) : 'Remaining —'}</Typography></Stack>
                  <Stack direction="row" justifyContent="space-between">
                    <Typography color="text.secondary">Paid</Typography>
                    <Typography>{money(activeSale?.paidAmount ?? 0, currencyCode)}</Typography>
                  </Stack>
                  <Stack direction="row" justifyContent="space-between">
                    <Typography color="text.secondary">Balance due</Typography>
                    <Typography fontWeight={700}>{activeSale ? money(activeSale.balanceDue ?? activeSale.totalAmount, currencyCode) : '—'}</Typography>
                  </Stack>
                  <Stack direction="row" justifyContent="space-between">
                    <Typography color="text.secondary">Change due</Typography>
                    <Typography>{money(activeSale?.changeDue ?? 0, currencyCode)}</Typography>
                  </Stack>
                  <Button
                    size="small"
                    variant="contained"
                    startIcon={<PaymentOutlinedIcon />}
                    disabled={cartItems.length === 0 || busy || Boolean(activeSale?.paymentComplete)}
                    onClick={() => {
                      checkoutClickStartedAtRef.current = performance.now();
                      if (activeSale) {
                        setPaymentDialogOpen(true);
                        reportPosTiming('checkout-to-payment-ui', checkoutClickStartedAtRef.current);
                        checkoutClickStartedAtRef.current = null;
                      } else requestAuthoritativeCheckout(true);
                    }}
                  >
                    {recalculateMutation.isPending ? 'Calculating total…' : activeSale ? 'Take payment' : 'Checkout'}
                  </Button>
                  {!activeSale && cartItems.length > 0 ? <Typography variant="caption" color="text.secondary">Checkout calculates the authoritative total before payment.</Typography> : null}
                </Stack>
              </Paper>
            </Box>
            <Stack direction="row" spacing={0.75} sx={{ flexShrink: 0 }}>
              <Button fullWidth size="small" variant="outlined" disabled={!activeSale || cartLocked} onClick={() => cancelMutation.mutate()} sx={{ bgcolor: posTokens.colors.card }}>Cancel draft</Button>
              <Button fullWidth size="small" variant="contained" color={activeSale && activeSale.totalAmount < 0 ? 'warning' : 'primary'} disabled={!activeSale?.paymentComplete || completeMutation.isPending || busy} onClick={() => { if (completeMutation.isPending) return; if (activeSale && activeSale.totalAmount < 0) setPayoutConfirmationOpen(true); else { completionStartedAtRef.current = performance.now(); completeMutation.mutate(); } }}>{completeMutation.isPending ? 'Completing sale...' : activeSale && activeSale.totalAmount < 0 ? `Pay out ${money(Math.abs(activeSale.totalAmount), currencyCode)} cash` : 'Complete sale'}</Button>
            </Stack>
          </Stack>
        </Box>
        )
      ) : null}
      <PaymentDialog
        open={paymentDialogOpen}
        sale={activeSale}
        busy={paymentMutation.isPending}
        error={paymentMutation.error}
        onClose={() => { paymentMutation.reset(); setPaymentDialogOpen(false); }}
        onSubmit={(payment) => { paymentStartedAtRef.current = performance.now(); paymentMutation.mutate(payment); }}
      />
      <DiscountDialog open={discountOpen} initial={discount} currencyCode={currencyCode} onClose={()=>setDiscountOpen(false)} onApply={value=>{setDiscount(value);setDiscountOpen(false);cartRevisionRef.current+=1;setActiveSale(null);setPaymentDialogOpen(false);recalculateMutation.reset();}}/>
      <LotteryAmountDialog
        open={lotteryAction !== null}
        mode={lotteryAction ?? 'SOLD'}
        currencyCode={currencyCode}
        busy={false}
        onClose={() => setLotteryAction(null)}
        onSubmit={(amount) => lotteryAction && addLotteryItem(lotteryAction, amount)}
      />
      <DepositPayoutDialog open={depositPayoutOpen} currencyCode={currencyCode} onClose={() => setDepositPayoutOpen(false)} onSubmit={addDepositPayout} />
      <Dialog open={payoutConfirmationOpen} onClose={completeMutation.isPending ? undefined : () => setPayoutConfirmationOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>{activeSale?.items.some(item => item.lineType === 'DEPOSIT_PAYOUT') ? 'Confirm deposit cash payout' : 'Confirm lottery cash payout'}</DialogTitle>
        <DialogContent>
          <Stack spacing={1} sx={{ pt: 1 }}>
            <Typography>Pay the customer {money(Math.abs(activeSale?.totalAmount ?? 0), currencyCode)} in cash?</Typography>
            <Alert severity="warning">Confirm the cash has been handed to the customer. This will complete the transaction and record the drawer payout.</Alert>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button disabled={completeMutation.isPending} onClick={() => setPayoutConfirmationOpen(false)}>Cancel</Button>
          <Button variant="contained" color="warning" disabled={completeMutation.isPending} onClick={() => completeMutation.mutate()}>{completeMutation.isPending ? 'Recording payout…' : 'Confirm cash payout'}</Button>
        </DialogActions>
      </Dialog>
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
            addResolvedProduct({
              productId: pendingAgeVerification.productId,
              variantId: pendingAgeVerification.variantId,
              ageVerified: true,
              name: pendingAgeVerification.label,
              sku: pendingAgeVerification.sku,
              price: pendingAgeVerification.price
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
    <Stack spacing={3} sx={{ height: '100%', minHeight: 0, overflowY: 'auto' }}>
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
