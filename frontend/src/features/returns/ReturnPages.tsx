import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import LaunchIcon from '@mui/icons-material/Launch';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import SearchIcon from '@mui/icons-material/Search';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  FormControlLabel,
  Grid,
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
  Typography
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  createRefund,
  createReturn,
  getReturn,
  getSale,
  listRefunds,
  listReturns,
  type RefundCreatePayload,
  type ReturnCreatePayload
} from '../../api/client';
import type { PaymentMethod, Refund, Return, Sale, SaleItem } from '../../api/types';
import { useSession } from '../../app/session';

type SelectionState = {
  selected: boolean;
  quantity: string;
  reason: string;
};

const paymentMethods: Array<{ value: PaymentMethod; label: string }> = [
  { value: 'CASH', label: 'Cash' },
  { value: 'DEBIT', label: 'Debit' },
  { value: 'CREDIT', label: 'Credit' },
  { value: 'GIFT_CARD', label: 'Gift card' },
  { value: 'STORE_CREDIT', label: 'Store credit' },
  { value: 'OTHER', label: 'Other' }
];

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function money(value: number, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(value);
}

function requestKey(prefix: string) {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function formatId(id: string) {
  return id.slice(0, 8);
}

function isReturnable(item: SaleItem) {
  const capabilities = item.completedProductCapabilities?.split(',') ?? [];
  return capabilities.includes('ALLOW_RETURN') && !capabilities.includes('NON_REFUNDABLE');
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 220 }}>
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function ReturnHistoryTable({ returns }: { returns: Return[] }) {
  if (returns.length === 0) {
    return <Alert severity="info">No returns found.</Alert>;
  }

  return (
    <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
      <Table aria-label="Return history">
        <TableHead>
          <TableRow>
            <TableCell>Return</TableCell>
            <TableCell>Sale</TableCell>
            <TableCell>Occurred</TableCell>
            <TableCell>Reason</TableCell>
            <TableCell align="right">Items</TableCell>
            <TableCell align="right">Total</TableCell>
            <TableCell align="right">Open</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {returns.map((returnRecord) => (
            <TableRow key={returnRecord.id} hover>
              <TableCell sx={{ fontFamily: 'monospace' }}>{formatId(returnRecord.id)}</TableCell>
              <TableCell sx={{ fontFamily: 'monospace' }}>{formatId(returnRecord.originalSaleId)}</TableCell>
              <TableCell>{new Date(returnRecord.occurredAt).toLocaleString()}</TableCell>
              <TableCell>{returnRecord.reason}</TableCell>
              <TableCell align="right">{returnRecord.totalQuantity}</TableCell>
              <TableCell align="right">{money(returnRecord.totalAmount, returnRecord.currencyCode)}</TableCell>
              <TableCell align="right">
                <Button component={Link} to={`/returns/${returnRecord.id}`} size="small" endIcon={<LaunchIcon />}>
                  Open
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Paper>
  );
}

export function ReturnsPage() {
  const { getValidAccessToken } = useSession();
  const [searchParams, setSearchParams] = useSearchParams();
  const [saleId, setSaleId] = React.useState(searchParams.get('saleId') ?? '');
  const submittedSaleId = searchParams.get('saleId') ?? '';

  const returnsQuery = useQuery({
    queryKey: ['returns', 'history', submittedSaleId],
    queryFn: async () => listReturns(await getValidAccessToken(), {
      originalSaleId: submittedSaleId || undefined,
      size: 50
    })
  });

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Box>
          <Typography variant="h5" component="h1">Returns</Typography>
          <Typography color="text.secondary">Review return history and start a return from an original sale.</Typography>
        </Box>
        <Button component={Link} to="/returns/new" variant="contained">
          New return
        </Button>
      </Stack>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Box component="form" onSubmit={(event) => {
          event.preventDefault();
          const next = saleId.trim();
          setSearchParams(next ? { saleId: next } : {});
        }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <TextField
              label="Original sale ID"
              value={saleId}
              onChange={(event) => setSaleId(event.target.value)}
              fullWidth
              InputProps={{ startAdornment: <SearchIcon color="action" sx={{ mr: 1 }} /> }}
            />
            <Button type="submit" variant="outlined" startIcon={<SearchIcon />}>
              Filter
            </Button>
          </Stack>
        </Box>
      </Paper>

      {returnsQuery.isLoading ? <LoadingPanel label="Loading returns" /> : null}
      {returnsQuery.error ? <Alert severity="error">{errorMessage(returnsQuery.error)}</Alert> : null}
      {returnsQuery.data ? <ReturnHistoryTable returns={returnsQuery.data.content} /> : null}
    </Stack>
  );
}

export function NewReturnPage() {
  const { getValidAccessToken, currentUser, session } = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const [saleId, setSaleId] = React.useState(searchParams.get('saleId') ?? '');
  const [submittedSaleId, setSubmittedSaleId] = React.useState(searchParams.get('saleId') ?? '');
  const [globalReason, setGlobalReason] = React.useState('');
  const [itemState, setItemState] = React.useState<Record<string, SelectionState>>({});
  const [refundMethod, setRefundMethod] = React.useState<PaymentMethod>('CASH');
  const [refundReference, setRefundReference] = React.useState('');
  const [approvalNotes, setApprovalNotes] = React.useState('');
  const [createdReturn, setCreatedReturn] = React.useState<Return | null>(null);
  const [refundResult, setRefundResult] = React.useState<Refund | null>(null);
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const canApprove = roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');

  const saleQuery = useQuery({
    queryKey: ['sale', 'return-lookup', submittedSaleId],
    queryFn: async () => getSale(await getValidAccessToken(), submittedSaleId),
    enabled: Boolean(submittedSaleId)
  });

  const priorReturnsQuery = useQuery({
    queryKey: ['returns', 'sale', submittedSaleId],
    queryFn: async () => listReturns(await getValidAccessToken(), { originalSaleId: submittedSaleId, size: 100 }),
    enabled: Boolean(saleQuery.data?.id)
  });

  React.useEffect(() => {
    const sale = saleQuery.data;
    if (!sale) {
      return;
    }
    const next: Record<string, SelectionState> = {};
    sale.items.forEach((item) => {
      next[item.id] = {
        selected: false,
        quantity: '1',
        reason: ''
      };
    });
    setItemState(next);
    setCreatedReturn(null);
    setRefundResult(null);
  }, [saleQuery.data]);

  const returnedQuantities = React.useMemo(() => {
    const values: Record<string, number> = {};
    priorReturnsQuery.data?.content.forEach((returnRecord) => {
      returnRecord.items.forEach((item) => {
        values[item.originalSaleItemId] = (values[item.originalSaleItemId] ?? 0) + item.quantity;
      });
    });
    return values;
  }, [priorReturnsQuery.data]);

  const selectedItems = React.useMemo(() => {
    const sale = saleQuery.data;
    if (!sale) {
      return [];
    }
    return sale.items
      .map((item) => ({ item, state: itemState[item.id] }))
      .filter(({ state }) => state?.selected);
  }, [itemState, saleQuery.data]);

  const selectedTotal = React.useMemo(() => {
    return selectedItems.reduce((total, { item, state }) => {
      const quantity = Number(state.quantity);
      if (!Number.isFinite(quantity) || quantity <= 0) {
        return total;
      }
      return total + (item.lineTotal / item.quantity) * quantity;
    }, 0);
  }, [selectedItems]);

  const validation = (() => {
    const sale = saleQuery.data;
    if (!sale) {
      return 'Look up an original sale before creating a return.';
    }
    if (!['COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(sale.status)) {
      return 'Only completed sales can be returned.';
    }
    if (!globalReason.trim()) {
      return 'A return reason is required.';
    }
    if (selectedItems.length === 0) {
      return 'Select at least one return item.';
    }
    for (const { item, state } of selectedItems) {
      const remaining = Math.max(0, item.quantity - (returnedQuantities[item.id] ?? 0));
      const quantity = Number(state.quantity);
      if (!isReturnable(item)) {
        return `${item.productName} is not returnable.`;
      }
      if (!Number.isFinite(quantity) || quantity <= 0) {
        return `${item.productName} needs a positive return quantity.`;
      }
      if (quantity > remaining) {
        return `${item.productName} return quantity exceeds the remaining purchased quantity.`;
      }
    }
    if ((refundMethod === 'DEBIT' || refundMethod === 'CREDIT') && !refundReference.trim()) {
      return 'A reference is required for manual debit and credit refunds.';
    }
    return null;
  })();

  const refundMutation = useMutation({
    mutationFn: async (returnRecord: Return) => {
      const sale = saleQuery.data;
      if (!sale) {
        throw new Error('Original sale is unavailable');
      }
      const originalPayment = sale.payments.find((payment) => payment.method === refundMethod);
      const payload: RefundCreatePayload = {
        returnId: returnRecord.id,
        reason: globalReason.trim(),
        approvalNotes: approvalNotes.trim() || undefined,
        payments: [{
          method: refundMethod,
          amount: returnRecord.totalAmount,
          originalPaymentId: originalPayment?.id,
          reference: refundReference.trim() || undefined
        }]
      };
      return createRefund(await getValidAccessToken(), payload, requestKey('refund'));
    },
    onSuccess: async (refund) => {
      setRefundResult(refund);
      await queryClient.invalidateQueries({ queryKey: ['returns'] });
    }
  });

  const returnMutation = useMutation({
    mutationFn: async () => {
      if (validation) {
        throw new Error(validation);
      }
      const sale = saleQuery.data;
      if (!sale) {
        throw new Error('Original sale is unavailable');
      }
      const payload: ReturnCreatePayload = {
        originalSaleId: sale.id,
        reason: globalReason.trim(),
        items: selectedItems.map(({ item, state }) => ({
          originalSaleItemId: item.id,
          quantity: Number(state.quantity),
          reason: state.reason.trim() || undefined
        }))
      };
      return createReturn(await getValidAccessToken(), payload);
    },
    onSuccess: async (returnRecord) => {
      setCreatedReturn(returnRecord);
      await refundMutation.mutateAsync(returnRecord);
    }
  });

  const pageError = saleQuery.error ?? priorReturnsQuery.error ?? returnMutation.error ?? refundMutation.error;
  const sale = saleQuery.data;
  const busy = returnMutation.isPending || refundMutation.isPending;

  function updateItem(itemId: string, patch: Partial<SelectionState>) {
    setItemState((current) => ({
      ...current,
      [itemId]: {
        ...(current[itemId] ?? { selected: false, quantity: '1', reason: '' }),
        ...patch
      }
    }));
  }

  async function runSubmit() {
    if (validation || busy) {
      return;
    }
    if (createdReturn && !refundResult) {
      await refundMutation.mutateAsync(createdReturn);
      return;
    }
    returnMutation.mutate();
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    await runSubmit();
  }

  if (refundResult && createdReturn) {
    return (
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
          <Box>
            <Typography variant="h5" component="h1">Refund complete</Typography>
            <Typography color="text.secondary">Return {formatId(createdReturn.id)} was refunded.</Typography>
          </Box>
          <Stack direction="row" spacing={1}>
            <Button component={Link} to={`/returns/${createdReturn.id}`} variant="outlined" endIcon={<LaunchIcon />}>
              Open return
            </Button>
            <Button component={Link} to="/returns/new" variant="contained">
              New return
            </Button>
          </Stack>
        </Stack>
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Grid container spacing={2}>
            <Grid item xs={12} md={3}>
              <Typography color="text.secondary">Refund method</Typography>
              <Typography variant="h6">{refundResult.payments[0]?.method.replaceAll('_', ' ')}</Typography>
            </Grid>
            <Grid item xs={12} md={3}>
              <Typography color="text.secondary">Subtotal</Typography>
              <Typography variant="h6">{money(refundResult.subtotalAmount, refundResult.currencyCode)}</Typography>
            </Grid>
            <Grid item xs={12} md={3}>
              <Typography color="text.secondary">Tax</Typography>
              <Typography variant="h6">{money(refundResult.taxAmount, refundResult.currencyCode)}</Typography>
            </Grid>
            <Grid item xs={12} md={3}>
              <Typography color="text.secondary">Refund total</Typography>
              <Typography variant="h6">{money(refundResult.totalAmount, refundResult.currencyCode)}</Typography>
            </Grid>
          </Grid>
          {refundResult.approvedAt ? (
            <Alert severity="success" sx={{ mt: 2 }}>Approval recorded at {new Date(refundResult.approvedAt).toLocaleString()}.</Alert>
          ) : null}
        </Paper>
      </Stack>
    );
  }

  return (
    <Box component="form" onSubmit={(event) => void submit(event)}>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
          <Box>
            <Typography variant="h5" component="h1">New return</Typography>
            <Typography color="text.secondary">Find the original sale, select return lines, then process a refund.</Typography>
          </Box>
          <Button component={Link} to="/returns" variant="outlined" startIcon={<ArrowBackIcon />}>
            Return history
          </Button>
        </Stack>

        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <TextField
              label="Original sale ID"
              value={saleId}
              disabled={busy}
              onChange={(event) => setSaleId(event.target.value)}
              fullWidth
              InputProps={{ startAdornment: <SearchIcon color="action" sx={{ mr: 1 }} /> }}
            />
            <Button
              type="button"
              variant="contained"
              startIcon={<SearchIcon />}
              disabled={!saleId.trim() || busy}
              onClick={() => setSubmittedSaleId(saleId.trim())}
            >
              Lookup
            </Button>
          </Stack>
        </Paper>

        {saleQuery.isLoading ? <LoadingPanel label="Loading original sale" /> : null}
        {pageError ? <Alert severity="error">{errorMessage(pageError)}</Alert> : null}
        {createdReturn && !refundResult ? (
          <Alert severity="warning">
            Return {formatId(createdReturn.id)} was created, but refund processing did not complete. Submit again to retry the refund.
          </Alert>
        ) : null}

        {sale ? (
          <>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Grid container spacing={2}>
                <Grid item xs={12} md={3}>
                  <Typography color="text.secondary">Sale</Typography>
                  <Typography fontWeight={700} sx={{ fontFamily: 'monospace' }}>{formatId(sale.id)}</Typography>
                </Grid>
                <Grid item xs={12} md={3}>
                  <Typography color="text.secondary">Status</Typography>
                  <Chip label={sale.status} size="small" />
                </Grid>
                <Grid item xs={12} md={3}>
                  <Typography color="text.secondary">Completed</Typography>
                  <Typography>{sale.completedAt ? new Date(sale.completedAt).toLocaleString() : 'Not completed'}</Typography>
                </Grid>
                <Grid item xs={12} md={3}>
                  <Typography color="text.secondary">Sale total</Typography>
                  <Typography variant="h6">{money(sale.totalAmount, sale.currencyCode)}</Typography>
                </Grid>
              </Grid>
            </Paper>

            <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
              <Table aria-label="Return items">
                <TableHead>
                  <TableRow>
                    <TableCell padding="checkbox">Select</TableCell>
                    <TableCell>Item</TableCell>
                    <TableCell align="right">Purchased</TableCell>
                    <TableCell align="right">Returned</TableCell>
                    <TableCell align="right">Return quantity</TableCell>
                    <TableCell>Line reason</TableCell>
                    <TableCell align="right">Refund estimate</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {sale.items.map((item) => {
                    const state = itemState[item.id] ?? { selected: false, quantity: '1', reason: '' };
                    const returned = returnedQuantities[item.id] ?? 0;
                    const remaining = Math.max(0, item.quantity - returned);
                    const returnable = isReturnable(item) && remaining > 0;
                    const quantity = Number(state.quantity);
                    const estimate = Number.isFinite(quantity) && quantity > 0 ? (item.lineTotal / item.quantity) * quantity : 0;
                    return (
                      <TableRow key={item.id} hover>
                        <TableCell padding="checkbox">
                          <Checkbox
                            inputProps={{ 'aria-label': `Select ${item.productName}` }}
                            checked={state.selected}
                            disabled={!returnable || busy}
                            onChange={(event) => updateItem(item.id, { selected: event.target.checked })}
                          />
                        </TableCell>
                        <TableCell>
                          <Typography fontWeight={700}>{item.productName}</Typography>
                          <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{item.productSku}</Typography>
                          {!isReturnable(item) ? <Chip label="Not returnable" color="warning" size="small" sx={{ mt: 1 }} /> : null}
                        </TableCell>
                        <TableCell align="right">{item.quantity}</TableCell>
                        <TableCell align="right">{returned}</TableCell>
                        <TableCell align="right">
                          <TextField
                            aria-label={`Return quantity for ${item.productName}`}
                            type="number"
                            size="small"
                            value={state.quantity}
                            disabled={!state.selected || busy}
                            inputProps={{
                              'aria-label': `Return quantity for ${item.productName}`,
                              min: 0.0001,
                              max: remaining,
                              step: 1,
                              style: { textAlign: 'right' }
                            }}
                            sx={{ width: 120 }}
                            onChange={(event) => updateItem(item.id, { quantity: event.target.value })}
                          />
                        </TableCell>
                        <TableCell>
                          <TextField
                            value={state.reason}
                            disabled={!state.selected || busy}
                            placeholder="Uses main reason"
                            inputProps={{ 'aria-label': `Return reason for ${item.productName}` }}
                            onChange={(event) => updateItem(item.id, { reason: event.target.value })}
                            fullWidth
                          />
                        </TableCell>
                        <TableCell align="right">{money(estimate, sale.currencyCode)}</TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </Paper>

            <Grid container spacing={3}>
              <Grid item xs={12} lg={7}>
                <Paper variant="outlined" sx={{ p: 2 }}>
                  <Stack spacing={2}>
                    <Typography variant="h6" component="h2">Return details</Typography>
                    <TextField
                      label="Return reason"
                      inputProps={{ 'aria-label': 'Main return reason' }}
                      value={globalReason}
                      disabled={busy}
                      onChange={(event) => setGlobalReason(event.target.value)}
                      multiline
                      minRows={3}
                      fullWidth
                    />
                    <Alert severity="info">
                      The refund tax uses the original sale snapshots. Current tax rates are not recalculated.
                    </Alert>
                  </Stack>
                </Paper>
              </Grid>
              <Grid item xs={12} lg={5}>
                <Paper variant="outlined" sx={{ p: 2 }}>
                  <Stack spacing={2}>
                    <Typography variant="h6" component="h2">Refund</Typography>
                    <FormControl fullWidth>
                      <InputLabel id="refund-method-label">Refund method</InputLabel>
                      <Select
                        labelId="refund-method-label"
                        label="Refund method"
                        value={refundMethod}
                        disabled={busy}
                        onChange={(event) => setRefundMethod(event.target.value as PaymentMethod)}
                      >
                        {paymentMethods.map((method) => (
                          <MenuItem key={method.value} value={method.value}>{method.label}</MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    {refundMethod === 'DEBIT' || refundMethod === 'CREDIT' ? (
                      <TextField
                        label="Manual refund reference"
                        value={refundReference}
                        disabled={busy}
                        onChange={(event) => setRefundReference(event.target.value)}
                        fullWidth
                      />
                    ) : null}
                    <TextField
                      label="Approval notes"
                      value={approvalNotes}
                      disabled={busy}
                      onChange={(event) => setApprovalNotes(event.target.value)}
                      fullWidth
                    />
                    <FormControlLabel
                      control={<Checkbox checked={canApprove} disabled />}
                      label={canApprove ? 'Approval permission available' : 'Approval may require a manager'}
                    />
                    <Divider />
                    <Stack direction="row" justifyContent="space-between">
                      <Typography color="text.secondary">Refund estimate</Typography>
                      <Typography variant="h6">{money(selectedTotal, sale.currencyCode)}</Typography>
                    </Stack>
                    {validation ? <Alert severity="warning">{validation}</Alert> : null}
                    <Button
                      type="button"
                      variant="contained"
                      startIcon={<PaymentsOutlinedIcon />}
                      disabled={busy || Boolean(validation)}
                      onClick={() => void runSubmit()}
                    >
                      {busy ? 'Processing...' : 'Create return and refund'}
                    </Button>
                  </Stack>
                </Paper>
              </Grid>
            </Grid>
          </>
        ) : null}
      </Stack>
    </Box>
  );
}

export function ReturnDetailPage() {
  const { id } = useParams();
  const { getValidAccessToken } = useSession();
  const returnQuery = useQuery({
    queryKey: ['return', id],
    queryFn: async () => getReturn(await getValidAccessToken(), id ?? ''),
    enabled: Boolean(id)
  });
  const refundQuery = useQuery({
    queryKey: ['refunds', 'return', id],
    queryFn: async () => listRefunds(await getValidAccessToken(), { returnId: id, size: 1 }),
    enabled: Boolean(id)
  });

  const returnRecord = returnQuery.data;
  const refund = refundQuery.data?.content[0];
  const pageError = returnQuery.error ?? refundQuery.error;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Box>
          <Typography variant="h5" component="h1">Return detail</Typography>
          <Typography color="text.secondary">{id ? `Return ${formatId(id)}` : 'Return'}</Typography>
        </Box>
        <Button component={Link} to="/returns" variant="outlined" startIcon={<ArrowBackIcon />}>
          Return history
        </Button>
      </Stack>

      {returnQuery.isLoading ? <LoadingPanel label="Loading return" /> : null}
      {pageError ? <Alert severity="error">{errorMessage(pageError)}</Alert> : null}

      {returnRecord ? (
        <>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Grid container spacing={2}>
              <Grid item xs={12} md={3}>
                <Typography color="text.secondary">Original sale</Typography>
                <Typography sx={{ fontFamily: 'monospace' }}>{formatId(returnRecord.originalSaleId)}</Typography>
              </Grid>
              <Grid item xs={12} md={3}>
                <Typography color="text.secondary">Occurred</Typography>
                <Typography>{new Date(returnRecord.occurredAt).toLocaleString()}</Typography>
              </Grid>
              <Grid item xs={12} md={3}>
                <Typography color="text.secondary">Return status</Typography>
                <Chip label={returnRecord.fullReturn ? 'FULL RETURN' : 'PARTIAL RETURN'} size="small" />
              </Grid>
              <Grid item xs={12} md={3}>
                <Typography color="text.secondary">Return total</Typography>
                <Typography variant="h6">{money(returnRecord.totalAmount, returnRecord.currencyCode)}</Typography>
              </Grid>
            </Grid>
            <Typography sx={{ mt: 2 }}>{returnRecord.reason}</Typography>
          </Paper>

          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Table aria-label="Returned items">
              <TableHead>
                <TableRow>
                  <TableCell>Item</TableCell>
                  <TableCell align="right">Quantity</TableCell>
                  <TableCell>Reason</TableCell>
                  <TableCell align="right">Subtotal</TableCell>
                  <TableCell align="right">Tax</TableCell>
                  <TableCell align="right">Total</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {returnRecord.items.map((item) => (
                  <TableRow key={item.id} hover>
                    <TableCell>
                      <Typography fontWeight={700}>{item.productName}</Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{item.productSku}</Typography>
                    </TableCell>
                    <TableCell align="right">{item.quantity}</TableCell>
                    <TableCell>{item.reason}</TableCell>
                    <TableCell align="right">{money(item.returnSubtotalAmount, returnRecord.currencyCode)}</TableCell>
                    <TableCell align="right">{money(item.returnTaxAmount, returnRecord.currencyCode)}</TableCell>
                    <TableCell align="right">{money(item.returnTotalAmount, returnRecord.currencyCode)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={2}>
              <Stack direction="row" spacing={1} alignItems="center">
                <ReceiptLongOutlinedIcon color="primary" />
                <Typography variant="h6" component="h2">Refund result</Typography>
              </Stack>
              {refundQuery.isLoading ? <LoadingPanel label="Loading refund" /> : null}
              {!refund && !refundQuery.isLoading ? <Alert severity="info">No refund has been recorded for this return.</Alert> : null}
              {refund ? (
                <Grid container spacing={2}>
                  <Grid item xs={12} md={3}>
                    <Typography color="text.secondary">Method</Typography>
                    <Typography fontWeight={700}>{refund.payments[0]?.method.replaceAll('_', ' ') ?? 'Refund'}</Typography>
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <Typography color="text.secondary">Refunded</Typography>
                    <Typography fontWeight={700}>{new Date(refund.occurredAt).toLocaleString()}</Typography>
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <Typography color="text.secondary">Tax</Typography>
                    <Typography fontWeight={700}>{money(refund.taxAmount, refund.currencyCode)}</Typography>
                  </Grid>
                  <Grid item xs={12} md={3}>
                    <Typography color="text.secondary">Total</Typography>
                    <Typography variant="h6">{money(refund.totalAmount, refund.currencyCode)}</Typography>
                  </Grid>
                  {refund.approvalNotes ? (
                    <Grid item xs={12}>
                      <Alert severity={refund.approvedAt ? 'success' : 'info'}>{refund.approvalNotes}</Alert>
                    </Grid>
                  ) : null}
                </Grid>
              ) : null}
            </Stack>
          </Paper>
        </>
      ) : null}
    </Stack>
  );
}
