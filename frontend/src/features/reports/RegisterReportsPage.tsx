import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import DownloadIcon from '@mui/icons-material/Download';
import RefreshIcon from '@mui/icons-material/Refresh';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
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
  getRegisterReport,
  listRegisters,
  listStores,
  listUsers,
  type RegisterReportParams
} from '../../api/client';
import type { Register, RegisterReport, RegisterReportRow, RegisterSessionStatus, Store, UserAdmin, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

type FilterState = {
  storeId: string;
  registerId: string;
  cashierId: string;
  status: RegisterSessionStatus | '';
  dateFrom: string;
  dateTo: string;
};

const today = new Date().toISOString().slice(0, 10);
const monthStart = `${today.slice(0, 8)}01`;

const defaultFilters: FilterState = {
  storeId: '',
  registerId: '',
  cashierId: '',
  status: '',
  dateFrom: monthStart,
  dateTo: today
};

const statuses: RegisterSessionStatus[] = ['OPEN', 'CLOSING', 'CLOSED', 'FORCE_CLOSED'];

function canViewRegisterReports(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Register report request failed';
}

function money(value: number, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(Number(value ?? 0));
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function storeLabel(store?: Store) {
  return store ? `${store.name} (${store.code})` : 'Unknown store';
}

function registerLabel(register?: Register) {
  return register ? `${register.name} (${register.code})` : 'Unknown register';
}

function userLabel(user?: UserAdmin) {
  return user ? `${user.displayName} (${user.email})` : 'Unknown cashier';
}

function csvEscape(value: string | number | null | undefined) {
  const text = value === null || value === undefined ? '' : String(value);
  return `"${text.replaceAll('"', '""')}"`;
}

function downloadCsv(filename: string, rows: string[][]) {
  const csv = rows.map((row) => row.map(csvEscape).join(',')).join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function cleanParams(filters: FilterState): RegisterReportParams {
  return {
    storeId: filters.storeId || undefined,
    registerId: filters.registerId || undefined,
    cashierId: filters.cashierId || undefined,
    status: filters.status || undefined,
    dateFrom: filters.dateFrom || undefined,
    dateTo: filters.dateTo || undefined
  };
}

function currencyFor(report?: RegisterReport) {
  return report?.rows[0]?.currencyCode ?? 'USD';
}

function MetricCard({ title, value, detail }: { title: string; value: string; detail?: string }) {
  return (
    <Card variant="outlined" sx={{ borderRadius: 2, height: '100%' }}>
      <CardContent>
        <Typography variant="body2" color="text.secondary">{title}</Typography>
        <Typography variant="h5" component="p" sx={{ mt: 0.75 }}>{value}</Typography>
        {detail ? <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>{detail}</Typography> : null}
      </CardContent>
    </Card>
  );
}

function SummaryCards({ report }: { report: RegisterReport }) {
  const currencyCode = currencyFor(report);
  const metrics = [
    { title: 'Opening cash', value: money(report.openingCash, currencyCode), detail: `${report.sessionCount} sessions` },
    { title: 'Retail cash', value: money(report.retailCash, currencyCode), detail: `Received ${money(report.retailCashReceived, currencyCode)} - change ${money(report.retailChange, currencyCode)}` },
    { title: 'Lottery cash', value: money(report.lotteryCash, currencyCode), detail: `Sales ${money(report.lotteryCashSales, currencyCode)} - payouts ${money(report.lotteryPayouts, currencyCode)}` },
    { title: 'Refunds', value: money(report.refunds, currencyCode), detail: 'Cash refunds' },
    { title: 'Cash movements', value: money(report.cashMovements, currencyCode), detail: `In ${money(report.cashMovementIn, currencyCode)} - out ${money(report.cashMovementOut, currencyCode)}` },
    { title: 'Expected cash', value: money(report.expectedCash, currencyCode), detail: 'Ledger expected drawer cash' },
    { title: 'Counted cash', value: money(report.countedCash, currencyCode), detail: `${report.closedSessionCount} counted sessions` },
    { title: 'Variance', value: money(report.variance, currencyCode), detail: 'Counted minus expected at close' }
  ];

  return (
    <Grid container spacing={2}>
      {metrics.map((metric) => (
        <Grid item xs={12} sm={6} lg={3} key={metric.title}>
          <MetricCard title={metric.title} value={metric.value} detail={metric.detail} />
        </Grid>
      ))}
    </Grid>
  );
}

function BarChart({
  title,
  rows,
  currencyCode
}: {
  title: string;
  rows: Array<{ label: string; value: number; tone?: 'positive' | 'negative' | 'neutral' }>;
  currencyCode: string;
}) {
  const max = Math.max(1, ...rows.map((row) => Math.abs(row.value)));
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2, height: '100%' }}>
      <Typography variant="h6" sx={{ mb: 2 }}>{title}</Typography>
      <Stack spacing={1.25}>
        {rows.map((row) => {
          const width = `${Math.max(4, (Math.abs(row.value) / max) * 100)}%`;
          const color = row.tone === 'negative' ? 'error.main' : row.tone === 'positive' ? 'success.main' : 'primary.main';
          return (
            <Box key={row.label}>
              <Stack direction="row" justifyContent="space-between" spacing={2}>
                <Typography variant="body2">{row.label}</Typography>
                <Typography variant="body2" fontWeight={700}>{money(row.value, currencyCode)}</Typography>
              </Stack>
              <Box sx={{ height: 10, bgcolor: 'action.hover', borderRadius: 1, overflow: 'hidden', mt: 0.5 }}>
                <Box sx={{ height: '100%', width, bgcolor: color }} />
              </Box>
            </Box>
          );
        })}
      </Stack>
    </Paper>
  );
}

function Charts({ report }: { report: RegisterReport }) {
  const currencyCode = currencyFor(report);
  return (
    <Grid container spacing={2}>
      <Grid item xs={12} lg={6}>
        <BarChart
          title="Cash sources"
          currencyCode={currencyCode}
          rows={[
            { label: 'Opening cash', value: report.openingCash, tone: 'neutral' },
            { label: 'Retail cash', value: report.retailCash, tone: 'positive' },
            { label: 'Lottery cash', value: report.lotteryCash, tone: report.lotteryCash < 0 ? 'negative' : 'positive' },
            { label: 'Cash movements', value: report.cashMovements, tone: report.cashMovements < 0 ? 'negative' : 'positive' }
          ]}
        />
      </Grid>
      <Grid item xs={12} lg={6}>
        <BarChart
          title="Reconciliation"
          currencyCode={currencyCode}
          rows={[
            { label: 'Expected cash', value: report.expectedCash, tone: 'neutral' },
            { label: 'Counted cash', value: report.countedCash, tone: 'neutral' },
            { label: 'Variance', value: report.variance, tone: report.variance === 0 ? 'positive' : 'negative' },
            { label: 'Refunds', value: report.refunds, tone: 'negative' }
          ]}
        />
      </Grid>
    </Grid>
  );
}

function RegisterReportFilters({
  stores,
  registers,
  users,
  filters,
  onChange
}: {
  stores: Store[];
  registers: Register[];
  users: UserAdmin[];
  filters: FilterState;
  onChange: (next: FilterState) => void;
}) {
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2 }}>
      <Grid container spacing={2}>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Store"
            value={filters.storeId}
            onChange={(event) => onChange({ ...filters, storeId: event.target.value, registerId: '' })}
            fullWidth
          >
            <MenuItem value="">All stores</MenuItem>
            {stores.map((store) => (
              <MenuItem key={store.id} value={store.id}>{storeLabel(store)}</MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Register"
            value={filters.registerId}
            onChange={(event) => onChange({ ...filters, registerId: event.target.value })}
            fullWidth
          >
            <MenuItem value="">All registers</MenuItem>
            {registers.map((register) => (
              <MenuItem key={register.id} value={register.id}>{registerLabel(register)}</MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Cashier"
            value={filters.cashierId}
            onChange={(event) => onChange({ ...filters, cashierId: event.target.value })}
            fullWidth
          >
            <MenuItem value="">All cashiers</MenuItem>
            {users.map((user) => (
              <MenuItem key={user.id} value={user.id}>{userLabel(user)}</MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            select
            label="Status"
            value={filters.status}
            onChange={(event) => onChange({ ...filters, status: event.target.value as RegisterSessionStatus | '' })}
            fullWidth
          >
            <MenuItem value="">All statuses</MenuItem>
            {statuses.map((status) => (
              <MenuItem key={status} value={status}>{label(status)}</MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            label="Date from"
            type="date"
            value={filters.dateFrom}
            InputLabelProps={{ shrink: true }}
            onChange={(event) => onChange({ ...filters, dateFrom: event.target.value })}
            fullWidth
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField
            label="Date to"
            type="date"
            value={filters.dateTo}
            InputLabelProps={{ shrink: true }}
            onChange={(event) => onChange({ ...filters, dateTo: event.target.value })}
            fullWidth
          />
        </Grid>
      </Grid>
    </Paper>
  );
}

function RegisterReportTable({ rows }: { rows: RegisterReportRow[] }) {
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
      <TableContainer>
        <Table aria-label="Register report sessions">
          <TableHead>
            <TableRow>
              <TableCell>Opened</TableCell>
              <TableCell>Register</TableCell>
              <TableCell>Cashier</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Opening cash</TableCell>
              <TableCell align="right">Retail cash</TableCell>
              <TableCell align="right">Lottery cash</TableCell>
              <TableCell align="right">Refunds</TableCell>
              <TableCell align="right">Cash movements</TableCell>
              <TableCell align="right">Expected</TableCell>
              <TableCell align="right">Counted</TableCell>
              <TableCell align="right">Variance</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.registerSessionId}>
                <TableCell>
                  <Typography>{new Date(row.openedAt).toLocaleString()}</Typography>
                  {row.closedAt ? <Typography variant="body2" color="text.secondary">Closed {new Date(row.closedAt).toLocaleString()}</Typography> : null}
                </TableCell>
                <TableCell>
                  <Typography fontWeight={600}>{row.registerName} ({row.registerCode})</Typography>
                  <Typography variant="body2" color="text.secondary">{row.storeName} ({row.storeCode})</Typography>
                </TableCell>
                <TableCell>
                  <Typography>{row.cashierDisplayName}</Typography>
                  <Typography variant="body2" color="text.secondary">{row.cashierEmail}</Typography>
                </TableCell>
                <TableCell><Chip label={label(row.status)} size="small" /></TableCell>
                <TableCell align="right">{money(row.openingCash, row.currencyCode)}</TableCell>
                <TableCell align="right">{money(row.retailCash, row.currencyCode)}</TableCell>
                <TableCell align="right">{money(row.lotteryCash, row.currencyCode)}</TableCell>
                <TableCell align="right">{money(row.refunds, row.currencyCode)}</TableCell>
                <TableCell align="right">{money(row.cashMovements, row.currencyCode)}</TableCell>
                <TableCell align="right">{money(row.expectedCash, row.currencyCode)}</TableCell>
                <TableCell align="right">{row.countedCash === null ? 'Not counted' : money(row.countedCash, row.currencyCode)}</TableCell>
                <TableCell align="right">
                  <Typography color={(row.variance ?? 0) === 0 ? 'text.primary' : 'warning.main'} fontWeight={600}>
                    {row.variance === null ? 'Open' : money(row.variance, row.currencyCode)}
                  </Typography>
                </TableCell>
              </TableRow>
            ))}
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={12}>
                  <Typography color="text.secondary">No register sessions found.</Typography>
                </TableCell>
              </TableRow>
            ) : null}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
}

function exportReport(report: RegisterReport) {
  downloadCsv('register-report.csv', [
    ['Opened', 'Closed', 'Store', 'Register', 'Cashier', 'Status', 'Opening Cash', 'Retail Cash', 'Lottery Cash', 'Refunds', 'Cash Movements', 'Expected Cash', 'Counted Cash', 'Variance'],
    ...report.rows.map((row) => [
      row.openedAt,
      row.closedAt ?? '',
      `${row.storeName} (${row.storeCode})`,
      `${row.registerName} (${row.registerCode})`,
      row.cashierDisplayName,
      row.status,
      row.openingCash.toFixed(2),
      row.retailCash.toFixed(2),
      row.lotteryCash.toFixed(2),
      row.refunds.toFixed(2),
      row.cashMovements.toFixed(2),
      row.expectedCash.toFixed(2),
      row.countedCash?.toFixed(2) ?? '',
      row.variance?.toFixed(2) ?? ''
    ])
  ]);
}

export function RegisterReportsPage() {
  const { currentUser, session, getValidAccessToken } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const canView = canViewRegisterReports(roles);
  const [filters, setFilters] = React.useState(defaultFilters);
  const params = React.useMemo(() => cleanParams(filters), [filters]);

  const stores = useQuery({
    queryKey: ['stores', 'register-reports'],
    queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled: canView
  });
  const registers = useQuery({
    queryKey: ['registers', 'register-reports', filters.storeId],
    queryFn: async () => listRegisters(await getValidAccessToken(), { page: 0, size: 100, active: true, storeId: filters.storeId || undefined }),
    enabled: canView
  });
  const users = useQuery({
    queryKey: ['users', 'register-reports'],
    queryFn: async () => listUsers(await getValidAccessToken(), { page: 0, size: 100, enabled: true }),
    enabled: canView
  });
  const report = useQuery({
    queryKey: ['register-report', params],
    queryFn: async () => getRegisterReport(await getValidAccessToken(), params),
    enabled: canView
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const loading = stores.isLoading || registers.isLoading || users.isLoading || report.isLoading;
  const error = stores.error ?? registers.error ?? users.error ?? report.error;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} alignItems={{ xs: 'stretch', lg: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">Register reports</Typography>
          <Typography color="text.secondary">Cash reconciliation across register sessions.</Typography>
        </Box>
        <Tooltip title="Refresh register reports">
          <IconButton
            aria-label="Refresh register reports"
            onClick={() => {
              void stores.refetch();
              void registers.refetch();
              void users.refetch();
              void report.refetch();
            }}
          >
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        <Button
          variant="outlined"
          startIcon={<DownloadIcon />}
          disabled={!report.data}
          onClick={() => report.data ? exportReport(report.data) : undefined}
        >
          Export CSV
        </Button>
      </Stack>

      <RegisterReportFilters
        stores={stores.data?.content ?? []}
        registers={registers.data?.content ?? []}
        users={users.data?.content ?? []}
        filters={filters}
        onChange={setFilters}
      />

      {loading ? (
        <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }}>
          <CircularProgress aria-label="Loading register report" />
          <Typography color="text.secondary">Loading register report</Typography>
        </Stack>
      ) : null}
      {error ? <Alert severity="error">{errorMessage(error)}</Alert> : null}

      {!loading && !error && report.data ? (
        <>
          <SummaryCards report={report.data} />
          <Charts report={report.data} />
          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2 }}>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <AssessmentOutlinedIcon color="primary" />
              <Box>
                <Typography variant="h6">Session details</Typography>
                <Typography color="text.secondary">
                  Generated {new Date(report.data.generatedAt).toLocaleString()}
                </Typography>
              </Box>
            </Stack>
          </Paper>
          <RegisterReportTable rows={report.data.rows} />
        </>
      ) : null}
    </Stack>
  );
}
