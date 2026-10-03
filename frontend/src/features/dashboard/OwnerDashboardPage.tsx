import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import ConfirmationNumberOutlinedIcon from '@mui/icons-material/ConfirmationNumberOutlined';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import LockOpenOutlinedIcon from '@mui/icons-material/LockOpenOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import RequestQuoteOutlinedIcon from '@mui/icons-material/RequestQuoteOutlined';
import {
  Alert,
  Box,
  Card,
  CardContent,
  CircularProgress,
  Grid,
  IconButton,
  Paper,
  Stack,
  Tooltip,
  Typography,
  useTheme
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis
} from 'recharts';
import {
  getInventoryReport,
  getLotterySalesReport,
  getSalesReport,
  listStores,
  listRegisterSessions
} from '../../api/client';
import type { LotterySalesReport, SalesReport, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

function canViewOwnerDashboard(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function money(value: number, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(Number(value ?? 0));
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Dashboard request failed';
}

function MetricCard({
  title,
  value,
  detail,
  icon,
  tone = 'default'
}: {
  title: string;
  value: string;
  detail: string;
  icon: React.ReactNode;
  tone?: 'default' | 'warning' | 'positive';
}) {
  const color = tone === 'warning' ? 'warning.main' : tone === 'positive' ? 'success.main' : 'primary.main';
  return (
    <Card variant="outlined" sx={{ borderRadius: 2, height: '100%' }}>
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
          <Box>
            <Typography variant="body2" color="text.secondary">{title}</Typography>
            <Typography variant="h5" component="p" sx={{ mt: 0.75 }}>{value}</Typography>
          </Box>
          <Box sx={{ color, display: 'flex' }}>{icon}</Box>
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{detail}</Typography>
      </CardContent>
    </Card>
  );
}

function DashboardChart({
  title,
  children
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2, height: 320 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>{title}</Typography>
      {children}
    </Paper>
  );
}

function paymentChartRows(report?: SalesReport) {
  const rows = report?.paymentBreakdown ?? [];
  return rows.length === 0
    ? [{ method: 'No payments', collected: 0, refunded: 0, net: 0 }]
    : rows.map((row) => ({
      method: label(row.method),
      collected: row.collected,
      refunded: row.refunded,
      net: row.net
    }));
}

function storeLocalDate(timezone?: string) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: timezone,
    year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(new Date());
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day}`;
}

function lotteryChartRows(report: LotterySalesReport | undefined, timezone?: string) {
  const hourly = new Map<number, { hour: string; sold: number; wins: number; net: number }>();
  for (const activity of report?.activities ?? []) {
    const hour = Number(new Intl.DateTimeFormat('en-US', {
      timeZone: timezone,
      hour: '2-digit', hourCycle: 'h23'
    }).format(new Date(activity.occurredAt)));
    const row = hourly.get(hour) ?? { hour: `${String(hour).padStart(2, '0')}:00`, sold: 0, wins: 0, net: 0 };
    if (activity.type === 'SOLD') row.sold += activity.amount;
    else row.wins += activity.amount;
    row.net = row.sold - row.wins;
    hourly.set(hour, row);
  }
  if (hourly.size === 0) return [];
  const hours = [...hourly.keys()];
  const first = Math.min(...hours);
  const last = Math.max(...hours);
  return Array.from({ length: last - first + 1 }, (_, index) => {
    const hour = first + index;
    return hourly.get(hour) ?? { hour: `${String(hour).padStart(2, '0')}:00`, sold: 0, wins: 0, net: 0 };
  });
}

export function OwnerDashboardPage() {
  const muiTheme = useTheme();
  const { currentUser, session, getValidAccessToken } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const canView = canViewOwnerDashboard(roles);
  const stores = useQuery({
    queryKey: ['dashboard', 'stores'],
    queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100 }),
    enabled: canView
  });
  const requestedStoreId = window.localStorage.getItem('merchtyl.activeStoreId');
  const activeStore = stores.data?.content.find((store) => store.id === requestedStoreId)
    ?? stores.data?.content[0];
  const today = storeLocalDate(activeStore?.timezone);

  const sales = useQuery({
    queryKey: ['dashboard', 'sales', activeStore?.id, today],
    enabled: canView && stores.isSuccess,
    queryFn: async () => getSalesReport(await getValidAccessToken(), {
      storeId: activeStore?.id, dateFrom: today, dateTo: today
    })
  });
  const lottery = useQuery({
    queryKey: ['dashboard', 'lottery', activeStore?.id, today],
    queryFn: async () => getLotterySalesReport(await getValidAccessToken(), {
      storeId: activeStore?.id, dateFrom: today, dateTo: today
    }),
    enabled: canView && stores.isSuccess
  });
  const inventory = useQuery({
    queryKey: ['dashboard', 'inventory-alerts', activeStore?.id],
    queryFn: async () => getInventoryReport(await getValidAccessToken(), {
      storeId: activeStore?.id, lowStockThreshold: 5
    }),
    enabled: canView && stores.isSuccess
  });
  const openRegisters = useQuery({
    queryKey: ['dashboard', 'open-registers', activeStore?.id],
    queryFn: async () => listRegisterSessions(await getValidAccessToken(), {
      storeId: activeStore?.id, status: 'OPEN', page: 0, size: 100
    }),
    enabled: canView && stores.isSuccess
  });

  if (!canView) {
    return (
      <Stack spacing={3}>
        <Typography variant="h5" component="h1">Workspace</Typography>
        <Typography color="text.secondary">Signed in as {currentUser?.displayName ?? session?.displayName}</Typography>
      </Stack>
    );
  }

  const loading = stores.isLoading || sales.isLoading || lottery.isLoading || inventory.isLoading || openRegisters.isLoading;
  const error = stores.error ?? sales.error ?? lottery.error ?? inventory.error ?? openRegisters.error;
  const salesData = sales.data;
  const lotteryData = lottery.data;
  const inventoryData = inventory.data;
  const openRegisterCount = openRegisters.data?.totalElements ?? 0;
  const inventoryAlertCount = (inventoryData?.lowStockCount ?? 0) + (inventoryData?.negativeStockCount ?? 0);
  const currencyCode = activeStore?.currencyCode ?? lotteryData?.currencyCode ?? 'CAD';
  const lotteryTrend = lotteryChartRows(lotteryData, activeStore?.timezone);

  const refresh = () => {
    void sales.refetch();
    void lottery.refetch();
    void inventory.refetch();
    void openRegisters.refetch();
    void stores.refetch();
  };

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">Owner dashboard</Typography>
          <Typography color="text.secondary">Today&apos;s operating snapshot.</Typography>
        </Box>
        <Tooltip title="Refresh dashboard">
          <IconButton aria-label="Refresh dashboard" onClick={refresh}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Stack>

      {loading ? (
        <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 220 }}>
          <CircularProgress aria-label="Loading owner dashboard" />
          <Typography color="text.secondary">Loading owner dashboard</Typography>
        </Stack>
      ) : null}
      {error ? <Alert severity="error">{errorMessage(error)}</Alert> : null}

      {!loading ? (
        <>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard
                title="Today's sales"
                value={money(salesData?.netSales ?? 0, currencyCode)}
                detail={`${salesData?.saleCount ?? 0} sales, ${money(salesData?.payments ?? 0, currencyCode)} collected`}
                icon={<AssessmentOutlinedIcon />}
                tone="positive"
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard
                title="Today's lottery"
                value={money(lotteryData?.netLottery ?? 0, currencyCode)}
                detail={`Net Lottery · Sold ${money(lotteryData?.totalLotterySold ?? 0, currencyCode)} • Wins ${money(lotteryData?.lotteryWins ?? 0, currencyCode)}`}
                icon={<ConfirmationNumberOutlinedIcon />}
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard
                title="Inventory alerts"
                value={String(inventoryAlertCount)}
                detail={`${inventoryData?.lowStockCount ?? 0} low stock, ${inventoryData?.negativeStockCount ?? 0} negative`}
                icon={<Inventory2OutlinedIcon />}
                tone={inventoryAlertCount > 0 ? 'warning' : 'positive'}
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard
                title="Open registers"
                value={String(openRegisterCount)}
                detail="Currently open register sessions"
                icon={<LockOpenOutlinedIcon />}
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard
                title="Refunds"
                value={money(salesData?.refunds ?? 0, currencyCode)}
                detail={`${salesData?.refundCount ?? 0} refunds today`}
                icon={<ReceiptLongOutlinedIcon />}
                tone={(salesData?.refunds ?? 0) > 0 ? 'warning' : 'positive'}
              />
            </Grid>
            <Grid item xs={12} sm={6} lg={4}>
              <MetricCard
                title="Tax collected"
                value={money(salesData?.taxes ?? 0, currencyCode)}
                detail="Collected on completed sales"
                icon={<RequestQuoteOutlinedIcon />}
              />
            </Grid>
          </Grid>

          <Grid container spacing={2}>
            <Grid item xs={12} lg={6}>
              <DashboardChart title="Payment mix">
                <ResponsiveContainer width="100%" height="88%">
                  <BarChart data={paymentChartRows(salesData)} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="method" />
                    <YAxis />
                    <RechartsTooltip formatter={(value) => money(Number(value), currencyCode)} />
                    <Legend />
                    <Bar dataKey="collected" name="Collected" fill={muiTheme.palette.primary.main} radius={[4, 4, 0, 0]} />
                    <Bar dataKey="refunded" name="Refunded" fill={muiTheme.palette.warning.main} radius={[4, 4, 0, 0]} />
                    <Bar dataKey="net" name="Net" fill={muiTheme.palette.success.main} radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </DashboardChart>
            </Grid>
            <Grid item xs={12} lg={6}>
              <DashboardChart title="Lottery trend">
                {lotteryTrend.length === 0 ? <Stack alignItems="center" justifyContent="center" sx={{ height: '82%' }}>
                  <Typography color="text.secondary">No lottery activity today</Typography>
                </Stack> : <ResponsiveContainer width="100%" height="88%">
                  <LineChart data={lotteryTrend} margin={{ top: 8, right: 12, left: 0, bottom: 8 }}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="hour" />
                    <YAxis />
                    <RechartsTooltip formatter={(value) => money(Number(value), currencyCode)} />
                    <Legend />
                    <Line type="monotone" dataKey="sold" name="Sold" stroke={muiTheme.palette.primary.main} strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="wins" name="Wins" stroke={muiTheme.palette.warning.main} strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="net" name="Net" stroke={muiTheme.palette.success.main} strokeWidth={2} dot={false} />
                  </LineChart>
                </ResponsiveContainer>}
              </DashboardChart>
            </Grid>
          </Grid>
        </>
      ) : null}
    </Stack>
  );
}
