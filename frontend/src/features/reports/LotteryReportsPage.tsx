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
  getLotteryReport,
  listLotteryOperators,
  listRegisters,
  listStores,
  listUsers,
  type LotteryReportParams
} from '../../api/client';
import type {
  LotteryOperator,
  LotteryPayout,
  LotteryPayoutReversal,
  LotteryReport,
  LotteryReportApprovalRow,
  LotteryReportCommissionRow,
  LotterySale,
  LotterySettlement,
  Register,
  Store,
  UserAdmin,
  UserRole
} from '../../api/types';
import { useSession } from '../../app/session';

type FilterState = {
  operatorId: string;
  storeId: string;
  registerId: string;
  cashierId: string;
  dateFrom: string;
  dateTo: string;
};

const today = new Date().toISOString().slice(0, 10);
const monthStart = `${today.slice(0, 8)}01`;

const defaultFilters: FilterState = {
  operatorId: '',
  storeId: '',
  registerId: '',
  cashierId: '',
  dateFrom: monthStart,
  dateTo: today
};

function canViewLotteryReports(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Lottery report request failed';
}

function money(value: number, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(Number(value ?? 0));
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function dateTime(value: string) {
  return new Date(value).toLocaleString();
}

function cleanParams(filters: FilterState): LotteryReportParams {
  return {
    operatorId: filters.operatorId || undefined,
    storeId: filters.storeId || undefined,
    registerId: filters.registerId || undefined,
    cashierId: filters.cashierId || undefined,
    dateFrom: filters.dateFrom || undefined,
    dateTo: filters.dateTo || undefined
  };
}

function currencyFor(report?: LotteryReport) {
  return report?.saleRows[0]?.currencyCode
    ?? report?.payoutRows[0]?.currencyCode
    ?? report?.settlementRows[0]?.currencyCode
    ?? 'USD';
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

function reportCsvRows(report: LotteryReport) {
  return [
    ['Lottery report'],
    ['Metric', 'Value'],
    ['Sales', report.sales],
    ['Payouts', report.payouts],
    ['Approvals', report.approvals],
    ['Reversals', report.reversals],
    ['Referrals', report.referrals],
    ['Commission', report.commission],
    ['Calculated settlement', report.calculatedSettlement],
    ['Settlement', report.settlement],
    ['Variance', report.variance],
    [],
    ['Sales'],
    ['Occurred', 'Operator', 'Store', 'Register', 'Cashier', 'Game', 'Payment', 'Status', 'Amount', 'Reference'],
    ...report.saleRows.map((row) => [
      row.occurredAt,
      row.operatorName,
      row.storeName,
      row.registerName,
      row.cashierDisplayName,
      row.gameType,
      row.paymentMethod,
      row.status,
      row.amount,
      row.operatorReference ?? row.ticketReference ?? ''
    ]),
    [],
    ['Payouts'],
    ['Occurred', 'Ticket', 'Operator', 'Store', 'Register', 'Cashier', 'Method', 'Status', 'Amount'],
    ...report.payoutRows.map((row) => [
      row.occurredAt,
      row.ticketNumber,
      row.operatorName,
      row.storeName,
      row.registerName,
      row.cashierDisplayName,
      row.payoutMethod,
      row.status,
      row.amount
    ]),
    [],
    ['Approvals'],
    ['Approved', 'Ticket', 'Type', 'Approved by', 'Payout amount', 'Threshold', 'Notes'],
    ...report.approvalRows.map((row) => [
      row.approvedAt,
      row.ticketNumber,
      row.approvalType,
      row.approvedByDisplayName,
      row.payoutAmount,
      row.thresholdAmount,
      row.notes ?? ''
    ]),
    [],
    ['Reversals'],
    ['Reversed', 'Original payout', 'Reversed by', 'Amount', 'Reason'],
    ...report.reversalRows.map((row) => [
      row.reversedAt,
      row.originalPayoutId,
      row.reversedByDisplayName,
      row.amount,
      row.reason
    ]),
    [],
    ['Referrals'],
    ['Occurred', 'Ticket', 'Operator', 'Store', 'Status', 'Amount'],
    ...report.referralRows.map((row) => [
      row.occurredAt,
      row.ticketNumber,
      row.operatorName,
      row.storeName,
      row.status,
      row.amount
    ]),
    [],
    ['Commission'],
    ['Period start', 'Period end', 'Operator', 'Store', 'Gross sales', 'Payouts', 'Commission', 'Expected settlement', 'Status'],
    ...report.commissionRows.map((row) => [
      row.periodStart,
      row.periodEnd,
      row.operatorName,
      row.storeName,
      row.grossSales,
      row.totalPayouts,
      row.commission,
      row.expectedSettlement,
      row.status
    ]),
    [],
    ['Settlement'],
    ['Period start', 'Period end', 'Operator', 'Store', 'Expected settlement', 'Status'],
    ...report.settlementRows.map((row) => [
      row.periodStart,
      row.periodEnd,
      row.operatorName,
      row.storeName,
      row.expectedSettlement,
      row.status
    ])
  ];
}

function MetricCard({ title, value, detail, tone = 'default' }: { title: string; value: string; detail?: string; tone?: 'default' | 'warning' | 'positive' }) {
  const color = tone === 'warning' ? 'warning.main' : tone === 'positive' ? 'success.main' : 'text.primary';
  return (
    <Card variant="outlined" sx={{ borderRadius: 2, height: '100%' }}>
      <CardContent>
        <Typography variant="body2" color="text.secondary">{title}</Typography>
        <Typography variant="h5" component="p" color={color} sx={{ mt: 0.75 }}>{value}</Typography>
        {detail ? <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>{detail}</Typography> : null}
      </CardContent>
    </Card>
  );
}

function SummaryCards({ report }: { report: LotteryReport }) {
  const currencyCode = currencyFor(report);
  const metrics = [
    { title: 'Sales', value: money(report.sales, currencyCode), detail: `${report.saleCount} sales` },
    { title: 'Payouts', value: money(report.payouts, currencyCode), detail: `${report.payoutCount} payout records` },
    { title: 'Approvals', value: money(report.approvals, currencyCode), detail: `${report.approvalCount} approvals` },
    { title: 'Reversals', value: money(report.reversals, currencyCode), detail: `${report.reversalCount} reversals` },
    { title: 'Referrals', value: money(report.referrals, currencyCode), detail: `${report.referralCount} referrals` },
    { title: 'Commission', value: money(report.commission, currencyCode), detail: 'From settlement rows' },
    { title: 'Settlement', value: money(report.settlement, currencyCode), detail: `Calculated ${money(report.calculatedSettlement, currencyCode)}` },
    { title: 'Variance', value: money(report.variance, currencyCode), detail: 'Settlement minus calculated', tone: report.variance === 0 ? 'positive' as const : 'warning' as const }
  ];

  return (
    <Grid container spacing={2}>
      {metrics.map((metric) => (
        <Grid item xs={12} sm={6} lg={3} key={metric.title}>
          <MetricCard title={metric.title} value={metric.value} detail={metric.detail} tone={metric.tone} />
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

function Charts({ report }: { report: LotteryReport }) {
  const currencyCode = currencyFor(report);
  const dailyRows = report.chartRows.slice(-8).map((row) => ({
    label: row.date,
    value: row.sales - row.payouts + row.reversals - row.referrals + row.settlement,
    tone: row.sales - row.payouts + row.reversals - row.referrals + row.settlement < 0 ? 'negative' as const : 'positive' as const
  }));
  return (
    <Grid container spacing={2}>
      <Grid item xs={12} lg={6}>
        <BarChart
          title="Activity totals"
          currencyCode={currencyCode}
          rows={[
            { label: 'Sales', value: report.sales, tone: 'positive' },
            { label: 'Payouts', value: report.payouts, tone: 'negative' },
            { label: 'Reversals', value: report.reversals, tone: 'positive' },
            { label: 'Referrals', value: report.referrals, tone: 'neutral' },
            { label: 'Commission', value: report.commission, tone: 'negative' }
          ]}
        />
      </Grid>
      <Grid item xs={12} lg={6}>
        <BarChart
          title="Daily net"
          currencyCode={currencyCode}
          rows={dailyRows.length === 0 ? [{ label: 'No activity', value: 0, tone: 'neutral' }] : dailyRows}
        />
      </Grid>
    </Grid>
  );
}

function LotteryReportFilters({
  operators,
  stores,
  registers,
  users,
  filters,
  onChange
}: {
  operators: LotteryOperator[];
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
          <TextField select label="Operator" value={filters.operatorId} onChange={(event) => onChange({ ...filters, operatorId: event.target.value })} fullWidth>
            <MenuItem value="">All operators</MenuItem>
            {operators.map((operator) => <MenuItem key={operator.id} value={operator.id}>{operator.name} ({operator.code})</MenuItem>)}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField select label="Store" value={filters.storeId} onChange={(event) => onChange({ ...filters, storeId: event.target.value, registerId: '' })} fullWidth>
            <MenuItem value="">All stores</MenuItem>
            {stores.map((store) => <MenuItem key={store.id} value={store.id}>{store.name} ({store.code})</MenuItem>)}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField select label="Register" value={filters.registerId} onChange={(event) => onChange({ ...filters, registerId: event.target.value })} fullWidth>
            <MenuItem value="">All registers</MenuItem>
            {registers.map((register) => <MenuItem key={register.id} value={register.id}>{register.name} ({register.code})</MenuItem>)}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField select label="Cashier" value={filters.cashierId} onChange={(event) => onChange({ ...filters, cashierId: event.target.value })} fullWidth>
            <MenuItem value="">All cashiers</MenuItem>
            {users.map((user) => <MenuItem key={user.id} value={user.id}>{user.displayName} ({user.email})</MenuItem>)}
          </TextField>
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField label="Date from" type="date" value={filters.dateFrom} InputLabelProps={{ shrink: true }} onChange={(event) => onChange({ ...filters, dateFrom: event.target.value })} fullWidth />
        </Grid>
        <Grid item xs={12} md={4}>
          <TextField label="Date to" type="date" value={filters.dateTo} InputLabelProps={{ shrink: true }} onChange={(event) => onChange({ ...filters, dateTo: event.target.value })} fullWidth />
        </Grid>
      </Grid>
    </Paper>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
      <Box sx={{ p: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
        <Typography variant="h6">{title}</Typography>
      </Box>
      {children}
    </Paper>
  );
}

function SalesTable({ rows }: { rows: LotterySale[] }) {
  return (
    <TableContainer>
      <Table aria-label="Lottery sales report">
        <TableHead>
          <TableRow>
            <TableCell>Occurred</TableCell>
            <TableCell>Operator</TableCell>
            <TableCell>Register</TableCell>
            <TableCell>Cashier</TableCell>
            <TableCell>Game</TableCell>
            <TableCell>Status</TableCell>
            <TableCell align="right">Amount</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.id}>
              <TableCell>{dateTime(row.occurredAt)}</TableCell>
              <TableCell>{row.operatorName}</TableCell>
              <TableCell>{row.registerName} ({row.registerCode})</TableCell>
              <TableCell>{row.cashierDisplayName}</TableCell>
              <TableCell>{label(row.gameType)}</TableCell>
              <TableCell><Chip size="small" label={label(row.status)} /></TableCell>
              <TableCell align="right">{money(row.amount, row.currencyCode)}</TableCell>
            </TableRow>
          ))}
          {rows.length === 0 ? <EmptyRow colSpan={7} label="No lottery sales found." /> : null}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function PayoutsTable({ rows }: { rows: LotteryPayout[] }) {
  return (
    <TableContainer>
      <Table aria-label="Lottery payouts report">
        <TableHead>
          <TableRow>
            <TableCell>Occurred</TableCell>
            <TableCell>Ticket</TableCell>
            <TableCell>Operator</TableCell>
            <TableCell>Register</TableCell>
            <TableCell>Method</TableCell>
            <TableCell>Status</TableCell>
            <TableCell align="right">Amount</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.id}>
              <TableCell>{dateTime(row.occurredAt)}</TableCell>
              <TableCell>{row.ticketNumber}</TableCell>
              <TableCell>{row.operatorName}</TableCell>
              <TableCell>{row.registerName} ({row.registerCode})</TableCell>
              <TableCell>{label(row.payoutMethod)}</TableCell>
              <TableCell><Chip size="small" label={label(row.status)} /></TableCell>
              <TableCell align="right">{money(row.amount, row.currencyCode)}</TableCell>
            </TableRow>
          ))}
          {rows.length === 0 ? <EmptyRow colSpan={7} label="No lottery payouts found." /> : null}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function ApprovalsTable({ rows }: { rows: LotteryReportApprovalRow[] }) {
  return (
    <TableContainer>
      <Table aria-label="Lottery approvals report">
        <TableHead>
          <TableRow>
            <TableCell>Approved</TableCell>
            <TableCell>Ticket</TableCell>
            <TableCell>Type</TableCell>
            <TableCell>Approved by</TableCell>
            <TableCell align="right">Payout</TableCell>
            <TableCell align="right">Threshold</TableCell>
            <TableCell>Notes</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.id}>
              <TableCell>{dateTime(row.approvedAt)}</TableCell>
              <TableCell>{row.ticketNumber}</TableCell>
              <TableCell><Chip size="small" label={label(row.approvalType)} /></TableCell>
              <TableCell>{row.approvedByDisplayName}</TableCell>
              <TableCell align="right">{money(row.payoutAmount)}</TableCell>
              <TableCell align="right">{money(row.thresholdAmount)}</TableCell>
              <TableCell>{row.notes ?? 'None'}</TableCell>
            </TableRow>
          ))}
          {rows.length === 0 ? <EmptyRow colSpan={7} label="No lottery approvals found." /> : null}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function ReversalsTable({ rows }: { rows: LotteryPayoutReversal[] }) {
  return (
    <TableContainer>
      <Table aria-label="Lottery reversals report">
        <TableHead>
          <TableRow>
            <TableCell>Reversed</TableCell>
            <TableCell>Original payout</TableCell>
            <TableCell>Reversed by</TableCell>
            <TableCell>Reason</TableCell>
            <TableCell align="right">Amount</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.id}>
              <TableCell>{dateTime(row.reversedAt)}</TableCell>
              <TableCell>{row.originalPayoutId}</TableCell>
              <TableCell>{row.reversedByDisplayName}</TableCell>
              <TableCell>{row.reason}</TableCell>
              <TableCell align="right">{money(row.amount, row.currencyCode)}</TableCell>
            </TableRow>
          ))}
          {rows.length === 0 ? <EmptyRow colSpan={5} label="No lottery reversals found." /> : null}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function CommissionTable({ rows }: { rows: LotteryReportCommissionRow[] }) {
  return (
    <TableContainer>
      <Table aria-label="Lottery commission report">
        <TableHead>
          <TableRow>
            <TableCell>Period</TableCell>
            <TableCell>Operator</TableCell>
            <TableCell>Store</TableCell>
            <TableCell>Status</TableCell>
            <TableCell align="right">Gross sales</TableCell>
            <TableCell align="right">Payouts</TableCell>
            <TableCell align="right">Commission</TableCell>
            <TableCell align="right">Expected settlement</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.settlementId}>
              <TableCell>{row.periodStart} to {row.periodEnd}</TableCell>
              <TableCell>{row.operatorName}</TableCell>
              <TableCell>{row.storeName}</TableCell>
              <TableCell><Chip size="small" label={label(row.status)} /></TableCell>
              <TableCell align="right">{money(row.grossSales)}</TableCell>
              <TableCell align="right">{money(row.totalPayouts)}</TableCell>
              <TableCell align="right">{money(row.commission)}</TableCell>
              <TableCell align="right">{money(row.expectedSettlement)}</TableCell>
            </TableRow>
          ))}
          {rows.length === 0 ? <EmptyRow colSpan={8} label="No lottery commission rows found." /> : null}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function SettlementTable({ rows, report }: { rows: LotterySettlement[]; report: LotteryReport }) {
  return (
    <TableContainer>
      <Table aria-label="Lottery settlement report">
        <TableHead>
          <TableRow>
            <TableCell>Period</TableCell>
            <TableCell>Operator</TableCell>
            <TableCell>Store</TableCell>
            <TableCell>Status</TableCell>
            <TableCell align="right">Expected settlement</TableCell>
            <TableCell align="right">Report variance</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.id}>
              <TableCell>{row.periodStart} to {row.periodEnd}</TableCell>
              <TableCell>{row.operatorName}</TableCell>
              <TableCell>{row.storeName}</TableCell>
              <TableCell><Chip size="small" label={label(row.status)} /></TableCell>
              <TableCell align="right">{money(row.expectedSettlement, row.currencyCode)}</TableCell>
              <TableCell align="right">{money(report.variance, row.currencyCode)}</TableCell>
            </TableRow>
          ))}
          {rows.length === 0 ? <EmptyRow colSpan={6} label="No lottery settlements found." /> : null}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function EmptyRow({ colSpan, label: emptyLabel }: { colSpan: number; label: string }) {
  return (
    <TableRow>
      <TableCell colSpan={colSpan}>
        <Typography color="text.secondary">{emptyLabel}</Typography>
      </TableCell>
    </TableRow>
  );
}

export function LotteryReportsPage() {
  const { currentUser, session, getValidAccessToken } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const canView = canViewLotteryReports(roles);
  const [filters, setFilters] = React.useState(defaultFilters);
  const params = React.useMemo(() => cleanParams(filters), [filters]);

  const operators = useQuery({
    queryKey: ['lottery-operators', 'lottery-reports'],
    queryFn: async () => listLotteryOperators(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled: canView
  });
  const stores = useQuery({
    queryKey: ['stores', 'lottery-reports'],
    queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled: canView
  });
  const registers = useQuery({
    queryKey: ['registers', 'lottery-reports', filters.storeId],
    queryFn: async () => listRegisters(await getValidAccessToken(), { page: 0, size: 100, active: true, storeId: filters.storeId || undefined }),
    enabled: canView
  });
  const users = useQuery({
    queryKey: ['users', 'lottery-reports'],
    queryFn: async () => listUsers(await getValidAccessToken(), { page: 0, size: 100, enabled: true }),
    enabled: canView
  });
  const report = useQuery({
    queryKey: ['lottery-report', params],
    queryFn: async () => getLotteryReport(await getValidAccessToken(), params),
    enabled: canView
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const loading = operators.isLoading || stores.isLoading || registers.isLoading || users.isLoading || report.isLoading;
  const error = operators.error ?? stores.error ?? registers.error ?? users.error ?? report.error;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} alignItems={{ xs: 'stretch', lg: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <AssessmentOutlinedIcon color="primary" />
            <Typography variant="h5" component="h1">Lottery reports</Typography>
          </Stack>
          <Typography color="text.secondary">Sales, payouts, approvals, reversals, referrals, commission, settlement, and variance.</Typography>
        </Box>
        <Tooltip title="Refresh lottery reports">
          <IconButton
            aria-label="Refresh lottery reports"
            onClick={() => {
              void operators.refetch();
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
          onClick={() => report.data ? downloadCsv('lottery-report.csv', reportCsvRows(report.data)) : undefined}
        >
          Export CSV
        </Button>
      </Stack>

      <LotteryReportFilters
        operators={operators.data?.content ?? []}
        stores={stores.data?.content ?? []}
        registers={registers.data?.content ?? []}
        users={users.data?.content ?? []}
        filters={filters}
        onChange={setFilters}
      />

      {loading ? (
        <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }}>
          <CircularProgress aria-label="Loading lottery report" />
          <Typography color="text.secondary">Loading lottery report</Typography>
        </Stack>
      ) : null}
      {error ? <Alert severity="error">{errorMessage(error)}</Alert> : null}

      {!loading && !error && report.data ? (
        <>
          <SummaryCards report={report.data} />
          <Charts report={report.data} />
          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2 }}>
            <Typography variant="body2" color="text.secondary">
              Generated {dateTime(report.data.generatedAt)}
            </Typography>
          </Paper>
          <Section title="Sales"><SalesTable rows={report.data.saleRows} /></Section>
          <Section title="Payouts"><PayoutsTable rows={report.data.payoutRows} /></Section>
          <Section title="Approvals"><ApprovalsTable rows={report.data.approvalRows} /></Section>
          <Section title="Reversals"><ReversalsTable rows={report.data.reversalRows} /></Section>
          <Section title="Referrals"><PayoutsTable rows={report.data.referralRows} /></Section>
          <Section title="Commission"><CommissionTable rows={report.data.commissionRows} /></Section>
          <Section title="Settlement and variance"><SettlementTable rows={report.data.settlementRows} report={report.data} /></Section>
        </>
      ) : null}
    </Stack>
  );
}
