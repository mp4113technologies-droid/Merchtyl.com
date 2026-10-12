import ClearIcon from '@mui/icons-material/Clear';
import PrintIcon from '@mui/icons-material/Print';
import SearchIcon from '@mui/icons-material/Search';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, Divider, IconButton, MenuItem, Paper, Skeleton, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, TextField, Typography, useMediaQuery,
  useTheme
} from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import * as React from 'react';
import { useSearchParams } from 'react-router-dom';
import { getSaleReceipt, listInvoiceHistory, listRefunds, listStores, reprintSaleReceipt } from '../../api/client';
import type { InvoiceHistorySummary, ReceiptDocument, Refund } from '../../api/types';
import { useSession } from '../../app/session';
import { loadReceiptPrinterPreferences, printReceiptWithFallback } from '../pos/receiptPrinter';

const PAGE_SIZE = 10;

function money(value: number, currency: string) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value);
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, character => character.toUpperCase());
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Unable to load invoice history.';
}

function DetailContent({ receipt, refunds }: { receipt: ReceiptDocument; refunds: Refund[] }) {
  return <Stack spacing={2}>
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between">
      <Box><Typography fontWeight={700}>Invoice / Receipt #{receipt.receiptNumber}</Typography><Typography color="text.secondary">{new Date(receipt.completedAt).toLocaleString()}</Typography></Box>
      <Box sx={{ textAlign: { sm: 'right' } }}><Typography>{receipt.store.name}</Typography><Typography color="text.secondary">{receipt.register.code} · {receipt.cashier.displayName}</Typography></Box>
    </Stack>
    {receipt.tokenNumber ? <Alert severity="info">Restaurant order {receipt.tokenNumber}</Alert> : null}
    <Divider />
    <Stack spacing={1}>{receipt.items.map(item => <Stack key={item.id} direction="row" justifyContent="space-between" spacing={2}>
      <Box><Typography>{item.productName}</Typography><Typography variant="caption" color="text.secondary">{item.quantity} × {money(item.unitPrice, receipt.currencyCode)}</Typography></Box>
      <Typography>{money(item.lineTotal, receipt.currencyCode)}</Typography>
    </Stack>)}</Stack>
    <Divider />
    <Stack spacing={0.5}>
      <Stack direction="row" justifyContent="space-between"><Typography>Subtotal</Typography><Typography>{money(receipt.subtotalAmount, receipt.currencyCode)}</Typography></Stack>
      {(receipt.containerDepositTotal ?? 0) !== 0 ? <Stack direction="row" justifyContent="space-between"><Typography>Container deposits</Typography><Typography>{money(receipt.containerDepositTotal ?? 0, receipt.currencyCode)}</Typography></Stack> : null}
      {receipt.discountAmount !== 0 ? <Stack direction="row" justifyContent="space-between"><Typography>Discount</Typography><Typography>-{money(receipt.discountAmount, receipt.currencyCode)}</Typography></Stack> : null}
      <Stack direction="row" justifyContent="space-between"><Typography>Tax</Typography><Typography>{money(receipt.taxAmount, receipt.currencyCode)}</Typography></Stack>
      {(receipt.cashbackAmount ?? 0) > 0 ? <><Stack direction="row" justifyContent="space-between"><Typography>Cash Back</Typography><Typography>{money(receipt.cashbackAmount ?? 0, receipt.currencyCode)}</Typography></Stack><Stack direction="row" justifyContent="space-between"><Typography>Cash Back Fee</Typography><Typography>{money(receipt.cashbackFeeAmount ?? 0, receipt.currencyCode)}</Typography></Stack></> : null}
      <Stack direction="row" justifyContent="space-between"><Typography fontWeight={700}>Total charged</Typography><Typography fontWeight={700}>{money(receipt.totalAmount, receipt.currencyCode)}</Typography></Stack>
      {(receipt.cashbackAmount ?? 0) > 0 ? <Stack direction="row" justifyContent="space-between"><Typography>Cash Given</Typography><Typography>{money(receipt.cashbackAmount ?? 0, receipt.currencyCode)}</Typography></Stack> : null}
    </Stack>
    <Divider />
    <Box><Typography variant="subtitle2" gutterBottom>Payments</Typography>{receipt.payments.map(payment => <Stack key={payment.id} direction="row" justifyContent="space-between"><Typography>{label(payment.method)}</Typography><Typography>{money(payment.amount, receipt.currencyCode)}</Typography></Stack>)}</Box>
    {refunds.length > 0 ? <><Divider /><Box><Typography variant="subtitle2" gutterBottom>Refunds</Typography><Stack spacing={1}>{refunds.map(refund => <Paper key={refund.id} variant="outlined" sx={{ p: 1.5 }}><Stack direction="row" justifyContent="space-between"><Box><Typography>{new Date(refund.occurredAt).toLocaleString()}</Typography><Typography variant="caption" color="text.secondary">{refund.reason}</Typography></Box><Typography fontWeight={700}>-{money(refund.totalAmount, refund.currencyCode)}</Typography></Stack></Paper>)}</Stack></Box></> : null}
  </Stack>;
}

export function InvoiceHistoryPage() {
  const { getValidAccessToken } = useSession();
  const theme = useTheme();
  const mobile = useMediaQuery(theme.breakpoints.down('md'));
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0);
  const search = params.get('search') ?? '';
  const storeId = params.get('store') ?? '';
  const [searchInput, setSearchInput] = React.useState(search);
  const [selected, setSelected] = React.useState<InvoiceHistorySummary | null>(null);

  React.useEffect(() => setSearchInput(search), [search]);
  React.useEffect(() => {
    const timer = window.setTimeout(() => {
      const normalized = searchInput.trim();
      if (normalized === search) return;
      const next = new URLSearchParams(params);
      normalized ? next.set('search', normalized) : next.delete('search');
      next.delete('page');
      setParams(next, { replace: true });
    }, 350);
    return () => window.clearTimeout(timer);
  }, [params, search, searchInput, setParams]);

  const stores = useQuery({ queryKey: ['stores', 'invoice-history'], queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100 }) });
  const invoices = useQuery({
    queryKey: ['invoice-history', storeId, search, page],
    queryFn: async () => listInvoiceHistory(await getValidAccessToken(), { storeId: storeId || undefined, invoiceNumber: search || undefined, page, size: PAGE_SIZE }),
    placeholderData: previous => previous
  });
  const detail = useQuery({
    queryKey: ['invoice-receipt', selected?.saleId],
    queryFn: async () => getSaleReceipt(await getValidAccessToken(), selected!.saleId),
    enabled: Boolean(selected)
  });
  const refunds = useQuery({
    queryKey: ['invoice-refunds', selected?.saleId],
    queryFn: async () => listRefunds(await getValidAccessToken(), { originalSaleId: selected!.saleId, page: 0, size: 100 }),
    enabled: Boolean(selected && selected.status !== 'COMPLETED')
  });
  const reprint = useMutation({
    mutationFn: async () => reprintSaleReceipt(await getValidAccessToken(), selected!.saleId),
    onSuccess: async receipt => { await printReceiptWithFallback(receipt.document, loadReceiptPrinterPreferences()); }
  });
  const data = invoices.data;
  const start = data && data.totalElements > 0 ? data.page * data.size + 1 : 0;
  const end = data ? Math.min((data.page + 1) * data.size, data.totalElements) : 0;

  const changeQuery = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    value ? next.set(key, value) : next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next);
  };

  const rowCard = (invoice: InvoiceHistorySummary) => <Paper key={invoice.saleId} variant="outlined" sx={{ p: 2 }}>
    <Stack spacing={1}>
      <Stack direction="row" justifyContent="space-between"><Box><Typography fontWeight={700}>Invoice #{invoice.receiptNumber}</Typography><Typography variant="body2" color="text.secondary">{new Date(invoice.completedAt).toLocaleString()}</Typography></Box><Typography fontWeight={700}>{money(invoice.totalAmount, invoice.currencyCode)}</Typography></Stack>
      <Typography variant="body2">{invoice.saleType === 'FOOD_SERVICE' ? 'Restaurant' : 'Retail'} · {invoice.registerCode} · {invoice.cashierName}</Typography>
      <Stack direction="row" justifyContent="space-between" alignItems="center"><Chip size="small" label={label(invoice.status)} color={invoice.status === 'COMPLETED' ? 'success' : 'warning'} /><Button startIcon={<VisibilityOutlinedIcon />} onClick={() => setSelected(invoice)}>View Invoice</Button></Stack>
    </Stack>
  </Paper>;

  return <Stack spacing={3}>
    <Box><Typography variant="h5" component="h1">Invoice History</Typography><Typography color="text.secondary">View, search and reprint completed customer invoices.</Typography></Box>
    <Paper variant="outlined" sx={{ p: 2 }}><Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
      <TextField fullWidth label="Search invoice #" placeholder="Search invoice #" value={searchInput} onChange={event => setSearchInput(event.target.value)} InputProps={{ startAdornment: <SearchIcon color="action" sx={{ mr: 1 }} />, endAdornment: searchInput ? <IconButton aria-label="Clear search" onClick={() => setSearchInput('')}><ClearIcon /></IconButton> : null }} />
      <TextField select label="Store" value={storeId} onChange={event => changeQuery('store', event.target.value)} sx={{ minWidth: { md: 240 } }}><MenuItem value="">All authorized stores</MenuItem>{stores.data?.content.map(store => <MenuItem key={store.id} value={store.id}>{store.name}</MenuItem>)}</TextField>
    </Stack></Paper>
    {invoices.isError ? <Alert severity="error">{errorMessage(invoices.error)}</Alert> : null}
    {invoices.isLoading ? <Paper variant="outlined" sx={{ p: 2 }}><Stack spacing={1}>{Array.from({ length: 5 }, (_, index) => <Skeleton key={index} height={44} />)}</Stack></Paper> : null}
    {!invoices.isLoading && data?.content.length === 0 ? <Alert severity="info">{search ? <>No invoice found for &quot;{search}&quot;. <Button onClick={() => setSearchInput('')}>Clear search</Button></> : <>No invoices yet. Completed sales will appear here.</>}</Alert> : null}
    {data?.content.length ? mobile ? <Stack spacing={1.5}>{data.content.map(rowCard)}</Stack> : <TableContainer component={Paper} variant="outlined" sx={{ opacity: invoices.isFetching ? 0.65 : 1 }}><Table aria-label="Invoice history"><TableHead><TableRow>{['Invoice #', 'Date / Time', 'Type', 'Register', 'Cashier', 'Customer / Order', 'Total', 'Payment', 'Status', 'Actions'].map(column => <TableCell key={column} align={column === 'Total' || column === 'Actions' ? 'right' : 'left'}>{column}</TableCell>)}</TableRow></TableHead><TableBody>{data.content.map(invoice => <TableRow hover key={invoice.saleId}><TableCell sx={{ fontWeight: 700 }}>#{invoice.receiptNumber}</TableCell><TableCell>{new Date(invoice.completedAt).toLocaleString()}</TableCell><TableCell>{invoice.saleType === 'FOOD_SERVICE' ? 'Restaurant' : 'Retail'}</TableCell><TableCell>{invoice.registerCode}</TableCell><TableCell>{invoice.cashierName}</TableCell><TableCell>{invoice.orderNumber ? `Pickup #${invoice.orderNumber}` : invoice.customerName ?? '—'}</TableCell><TableCell align="right">{money(invoice.totalAmount, invoice.currencyCode)}</TableCell><TableCell>{label(invoice.paymentSummary)}</TableCell><TableCell><Chip size="small" label={label(invoice.status)} color={invoice.status === 'COMPLETED' ? 'success' : 'warning'} /></TableCell><TableCell align="right"><Button startIcon={<VisibilityOutlinedIcon />} onClick={() => setSelected(invoice)}>View</Button></TableCell></TableRow>)}</TableBody></Table></TableContainer> : null}
    {data && data.totalElements > 0 ? <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems="center" spacing={1}><Typography color="text.secondary">Showing {start}–{end} of {data.totalElements}</Typography><Stack direction="row" alignItems="center" spacing={1}><Button disabled={data.first || invoices.isFetching} onClick={() => changeQuery('page', String(page - 1))}>Previous</Button><Typography>Page {data.page + 1} of {data.totalPages}</Typography><Button disabled={data.last || invoices.isFetching} onClick={() => changeQuery('page', String(page + 1))}>Next</Button></Stack></Stack> : null}
    <Dialog open={Boolean(selected)} onClose={() => setSelected(null)} fullWidth maxWidth="sm"><DialogTitle>Invoice Details</DialogTitle><DialogContent dividers>{selected?.status !== 'COMPLETED' ? <Alert severity="warning" sx={{ mb: 2 }}>This invoice is {label(selected?.status ?? '')}. Original receipt values are preserved below.</Alert> : null}{detail.isLoading || refunds.isLoading ? <Stack alignItems="center" sx={{ py: 5 }}><CircularProgress /></Stack> : detail.isError || refunds.isError ? <Alert severity="error">Unable to load this invoice.</Alert> : detail.data ? <DetailContent receipt={detail.data.document} refunds={refunds.data?.content ?? []} /> : null}{reprint.isError ? <Alert severity="error" sx={{ mt: 2 }}>Unable to reprint this receipt.</Alert> : null}</DialogContent><DialogActions><Button onClick={() => setSelected(null)}>Close</Button><Button startIcon={<PrintIcon />} disabled={!detail.data || reprint.isPending} onClick={() => reprint.mutate()}>{reprint.isPending ? 'Printing…' : 'Print / Reprint Receipt'}</Button></DialogActions></Dialog>
  </Stack>;
}
