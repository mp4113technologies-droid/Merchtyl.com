import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import DownloadIcon from '@mui/icons-material/Download';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Grid,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import * as React from 'react';
import { Navigate } from 'react-router-dom';
import {
  catalogueReferenceApi,
  getSalesReport,
  listProducts,
  listRegisters,
  listStores,
  listUsers,
  type SalesReportParams
} from '../../api/client';
import type { Product, Register, SalesReport, Store, UserAdmin, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

type FilterState = {
  storeId: string;
  registerId: string;
  cashierId: string;
  categoryId: string;
  productId: string;
  dateFrom: string;
  dateTo: string;
};

const today = new Date().toISOString().slice(0, 10);
const monthStart = `${today.slice(0, 8)}01`;

const emptyFilters: FilterState = {
  storeId: '',
  registerId: '',
  cashierId: '',
  categoryId: '',
  productId: '',
  dateFrom: monthStart,
  dateTo: today
};

function canViewSalesReports(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Sales report request failed';
}

function money(value: number) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(Number(value ?? 0));
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function cleanParams(filters: FilterState): SalesReportParams {
  return {
    storeId: filters.storeId || undefined,
    registerId: filters.registerId || undefined,
    cashierId: filters.cashierId || undefined,
    categoryId: filters.categoryId || undefined,
    productId: filters.productId || undefined,
    dateFrom: filters.dateFrom || undefined,
    dateTo: filters.dateTo || undefined
  };
}

function csvEscape(value: string | number | null | undefined) {
  const text = value === null || value === undefined ? '' : String(value);
  return `"${text.replaceAll('"', '""')}"`;
}

function downloadCsv(filename: string, rows: (string | number | null | undefined)[][]) {
  const csv = rows.map((row) => row.map(csvEscape).join(',')).join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function reportCsvRows(report: SalesReport) {
  return [
    ['Metric', 'Value'],
    ['Gross sales', report.grossSales],
    ['Net sales', report.netSales],
    ['Discounts', report.discounts],
    ['Refunds', report.refunds],
    ['Taxes', report.taxes],
    ['Payments', report.payments],
    ['Sale count', report.saleCount],
    ['Refund count', report.refundCount],
    [],
    ['Payment method', 'Collected', 'Refunded', 'Net'],
    ...report.paymentBreakdown.map((row) => [label(row.method), row.collected, row.refunded, row.net])
  ];
}

function MetricTile({ label: tileLabel, value, tone = 'default' }: { label: string; value: string; tone?: 'default' | 'positive' | 'warning' }) {
  const color = tone === 'positive' ? 'success.main' : tone === 'warning' ? 'warning.main' : 'text.primary';
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2, minHeight: 112 }}>
      <Stack spacing={1}>
        <Typography variant="body2" color="text.secondary">{tileLabel}</Typography>
        <Typography variant="h5" color={color}>{value}</Typography>
      </Stack>
    </Paper>
  );
}

export function SalesReportsPage() {
  const { currentUser, session, getValidAccessToken } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const allowed = canViewSalesReports(roles);
  const [draftFilters, setDraftFilters] = React.useState<FilterState>(emptyFilters);
  const [filters, setFilters] = React.useState<FilterState>(emptyFilters);

  const stores = useQuery({
    queryKey: ['stores', 'sales-report'],
    queryFn: async () => listStores(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: allowed
  });

  const registers = useQuery({
    queryKey: ['registers', 'sales-report', draftFilters.storeId],
    queryFn: async () => listRegisters(await getValidAccessToken(), { storeId: draftFilters.storeId || undefined, active: true, size: 100 }),
    enabled: allowed
  });

  const users = useQuery({
    queryKey: ['users', 'sales-report'],
    queryFn: async () => listUsers(await getValidAccessToken(), { enabled: true, size: 100 }),
    enabled: allowed
  });

  const categories = useQuery({
    queryKey: ['categories', 'sales-report'],
    queryFn: async () => catalogueReferenceApi.categories.list(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: allowed
  });

  const products = useQuery({
    queryKey: ['products', 'sales-report', draftFilters.categoryId],
    queryFn: async () => listProducts(await getValidAccessToken(), { categoryId: draftFilters.categoryId || undefined, active: true, size: 100 }),
    enabled: allowed
  });

  const report = useQuery({
    queryKey: ['reports', 'sales', filters],
    queryFn: async () => getSalesReport(await getValidAccessToken(), cleanParams(filters)),
    enabled: allowed
  });

  if (!allowed) {
    return <Navigate to="/unauthorized" replace />;
  }

  const data = report.data;

  return (
    <Stack spacing={3} sx={{ maxWidth: 1280 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <AssessmentOutlinedIcon color="primary" />
            <Typography variant="h5" component="h1">Sales reports</Typography>
          </Stack>
        </Box>
        <Tooltip title="Refresh report">
          <IconButton aria-label="Refresh report" onClick={() => void report.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        <Button
          startIcon={<DownloadIcon />}
          disabled={!data}
          onClick={() => data && downloadCsv('sales-report.csv', reportCsvRows(data))}
        >
          Export CSV
        </Button>
      </Stack>

      {report.isError ? <Alert severity="error">{errorMessage(report.error)}</Alert> : null}

      <Paper
        component="form"
        elevation={0}
        onSubmit={(event) => {
          event.preventDefault();
          setFilters(draftFilters);
        }}
        sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}
      >
        <Grid container spacing={2}>
          <Grid item xs={12} md={3}>
            <TextField
              select
              label="Store"
              value={draftFilters.storeId}
              onChange={(event) => setDraftFilters((current) => ({ ...current, storeId: event.target.value, registerId: '' }))}
              fullWidth
            >
              <MenuItem value="">All stores</MenuItem>
              {(stores.data?.content ?? []).map((store: Store) => <MenuItem key={store.id} value={store.id}>{store.name}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={3}>
            <TextField
              select
              label="Register"
              value={draftFilters.registerId}
              onChange={(event) => setDraftFilters((current) => ({ ...current, registerId: event.target.value }))}
              fullWidth
            >
              <MenuItem value="">All registers</MenuItem>
              {(registers.data?.content ?? []).map((register: Register) => <MenuItem key={register.id} value={register.id}>{register.name}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={3}>
            <TextField
              select
              label="Cashier"
              value={draftFilters.cashierId}
              onChange={(event) => setDraftFilters((current) => ({ ...current, cashierId: event.target.value }))}
              fullWidth
            >
              <MenuItem value="">All cashiers</MenuItem>
              {(users.data?.content ?? []).map((user: UserAdmin) => <MenuItem key={user.id} value={user.id}>{user.displayName}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={3}>
            <TextField
              select
              label="Category"
              value={draftFilters.categoryId}
              onChange={(event) => setDraftFilters((current) => ({ ...current, categoryId: event.target.value, productId: '' }))}
              fullWidth
            >
              <MenuItem value="">All categories</MenuItem>
              {(categories.data?.content ?? []).map((category) => <MenuItem key={category.id} value={category.id}>{category.name}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField
              select
              label="Product"
              value={draftFilters.productId}
              onChange={(event) => setDraftFilters((current) => ({ ...current, productId: event.target.value }))}
              fullWidth
            >
              <MenuItem value="">All products</MenuItem>
              {(products.data?.content ?? []).map((product: Product) => <MenuItem key={product.id} value={product.id}>{product.name}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={2}>
            <TextField
              label="From"
              type="date"
              value={draftFilters.dateFrom}
              onChange={(event) => setDraftFilters((current) => ({ ...current, dateFrom: event.target.value }))}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
          </Grid>
          <Grid item xs={12} md={2}>
            <TextField
              label="To"
              type="date"
              value={draftFilters.dateTo}
              onChange={(event) => setDraftFilters((current) => ({ ...current, dateTo: event.target.value }))}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
          </Grid>
          <Grid item xs={12} md={4}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ height: '100%' }} alignItems={{ sm: 'center' }}>
              <Button type="submit" variant="contained" startIcon={<SearchIcon />}>
                Apply filters
              </Button>
              <Button
                type="button"
                onClick={() => {
                  setDraftFilters(emptyFilters);
                  setFilters(emptyFilters);
                }}
              >
                Reset
              </Button>
            </Stack>
          </Grid>
        </Grid>
      </Paper>

      {report.isLoading ? (
        <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 320 }}>
          <CircularProgress aria-label="Loading sales report" />
          <Typography color="text.secondary">Loading sales report</Typography>
        </Stack>
      ) : data ? (
        <>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6} md={4}>
              <MetricTile label="Gross sales" value={money(data.grossSales)} />
            </Grid>
            <Grid item xs={12} sm={6} md={4}>
              <MetricTile label="Net sales" value={money(data.netSales)} tone="positive" />
            </Grid>
            <Grid item xs={12} sm={6} md={4}>
              <MetricTile label="Discounts" value={money(data.discounts)} />
            </Grid>
            <Grid item xs={12} sm={6} md={4}>
              <MetricTile label="Refunds" value={money(data.refunds)} tone="warning" />
            </Grid>
            <Grid item xs={12} sm={6} md={4}>
              <MetricTile label="Taxes" value={money(data.taxes)} />
            </Grid>
            <Grid item xs={12} sm={6} md={4}>
              <MetricTile label="Payments" value={money(data.payments)} />
            </Grid>
          </Grid>

          <Grid container spacing={3}>
            <Grid item xs={12} md={4}>
              <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
                <Stack spacing={1}>
                  <Typography variant="h6">Activity</Typography>
                  <Typography color="text.secondary">Generated {new Date(data.generatedAt).toLocaleString()}</Typography>
                  <Typography>Sales: {data.saleCount}</Typography>
                  <Typography>Refunds: {data.refundCount}</Typography>
                </Stack>
              </Paper>
            </Grid>
            <Grid item xs={12} md={8}>
              <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
                <TableContainer>
                  <Table aria-label="Payment breakdown">
                    <TableHead>
                      <TableRow>
                        <TableCell>Method</TableCell>
                        <TableCell align="right">Collected</TableCell>
                        <TableCell align="right">Refunded</TableCell>
                        <TableCell align="right">Net</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.paymentBreakdown.map((row) => (
                        <TableRow key={row.method}>
                          <TableCell>{label(row.method)}</TableCell>
                          <TableCell align="right">{money(row.collected)}</TableCell>
                          <TableCell align="right">{money(row.refunded)}</TableCell>
                          <TableCell align="right">{money(row.net)}</TableCell>
                        </TableRow>
                      ))}
                      {data.paymentBreakdown.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={4}>
                            <Alert severity="info">No payment activity found for this report.</Alert>
                          </TableCell>
                        </TableRow>
                      ) : null}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Paper>
            </Grid>
          </Grid>
        </>
      ) : null}
    </Stack>
  );
}
