import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchIcon from '@mui/icons-material/Search';
import {
  Alert,
  Box,
  Button,
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
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import * as React from 'react';
import { Navigate } from 'react-router-dom';
import {
  getFeatureResolution,
  listLotteryOperators,
  listLotterySales,
  listRegisters,
  listStores,
  listUsers,
  type LotterySaleSearchParams
} from '../../api/client';
import type {
  LotteryGameType,
  LotterySaleStatus,
  PaymentMethod,
  Register,
  UserAdmin,
  UserRole
} from '../../api/types';
import { useSession } from '../../app/session';

const gameTypes: LotteryGameType[] = [
  'DRAW_TICKET',
  'INSTANT_TICKET',
  'SPORTS_WAGER',
  'BREAKOPEN',
  'ONLINE_CREDIT',
  'OTHER'
];
const saleStatuses: LotterySaleStatus[] = ['RECORDED', 'CANCELLED'];
const paymentMethods: PaymentMethod[] = ['CASH', 'DEBIT', 'CREDIT', 'GIFT_CARD', 'STORE_CREDIT', 'OTHER'];

type FilterState = {
  search: string;
  operatorId: string;
  storeId: string;
  registerId: string;
  cashierId: string;
  gameType: LotteryGameType | '';
  status: LotterySaleStatus | '';
  paymentMethod: PaymentMethod | '';
  occurredFrom: string;
  occurredTo: string;
};

const emptyFilters: FilterState = {
  search: '',
  operatorId: '',
  storeId: '',
  registerId: '',
  cashierId: '',
  gameType: '',
  status: '',
  paymentMethod: '',
  occurredFrom: '',
  occurredTo: ''
};

function canViewLotteryHistory(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function money(value: number, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(value);
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Lottery history request failed';
}

function statusColor(status: LotterySaleStatus) {
  return status === 'RECORDED' ? 'success' : 'default';
}

function startOfDate(value: string) {
  return value ? new Date(`${value}T00:00:00.000Z`).toISOString() : undefined;
}

function endOfDate(value: string) {
  return value ? new Date(`${value}T23:59:59.999Z`).toISOString() : undefined;
}

function cleanParams(filters: FilterState, page: number): LotterySaleSearchParams {
  return {
    search: filters.search.trim() || undefined,
    operatorId: filters.operatorId || undefined,
    storeId: filters.storeId || undefined,
    registerId: filters.registerId || undefined,
    cashierId: filters.cashierId || undefined,
    gameType: filters.gameType,
    status: filters.status,
    paymentMethod: filters.paymentMethod,
    occurredFrom: startOfDate(filters.occurredFrom),
    occurredTo: endOfDate(filters.occurredTo),
    page,
    size: 10
  };
}

function useLotteryFeatureEnabled(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['features', 'resolution', 'lottery-history'],
    queryFn: async () => getFeatureResolution(await getValidAccessToken()),
    enabled,
    select: (resolutions) => resolutions.find((resolution) => resolution.definition.code === 'LOTTERY_SALES')?.enabled
  });
}

export function LotteryHistoryPage() {
  const { currentUser, session, getValidAccessToken } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const allowed = canViewLotteryHistory(roles);
  const [page, setPage] = React.useState(0);
  const [draftFilters, setDraftFilters] = React.useState<FilterState>(emptyFilters);
  const [filters, setFilters] = React.useState<FilterState>(emptyFilters);
  const featureEnabled = useLotteryFeatureEnabled(allowed);

  const operators = useQuery({
    queryKey: ['lottery-operators', 'history'],
    queryFn: async () => listLotteryOperators(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: allowed && featureEnabled.data !== false
  });

  const stores = useQuery({
    queryKey: ['stores', 'lottery-history'],
    queryFn: async () => listStores(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: allowed && featureEnabled.data !== false
  });

  const registers = useQuery({
    queryKey: ['registers', 'lottery-history', draftFilters.storeId],
    queryFn: async () => listRegisters(await getValidAccessToken(), { storeId: draftFilters.storeId || undefined, active: true, size: 100 }),
    enabled: allowed && featureEnabled.data !== false
  });

  const cashiers = useQuery({
    queryKey: ['users', 'lottery-history'],
    queryFn: async () => listUsers(await getValidAccessToken(), { enabled: true, size: 100 }),
    enabled: allowed && featureEnabled.data !== false
  });

  const sales = useQuery({
    queryKey: ['lottery-sales', 'history', filters, page],
    queryFn: async () => listLotterySales(await getValidAccessToken(), cleanParams(filters, page)),
    enabled: allowed && featureEnabled.data !== false
  });

  if (!allowed) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 1280 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <HistoryOutlinedIcon color="primary" />
            <Typography variant="h5" component="h1">Lottery history</Typography>
          </Stack>
        </Box>
        <Tooltip title="Refresh history">
          <IconButton aria-label="Refresh history" onClick={() => void sales.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Stack>

      {featureEnabled.data === false ? <Alert severity="warning">Lottery sales is disabled.</Alert> : null}
      {sales.isError ? <Alert severity="error">{errorMessage(sales.error)}</Alert> : null}

      <Paper
        component="form"
        elevation={0}
        onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setFilters(draftFilters);
        }}
        sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}
      >
        <Grid container spacing={2}>
          <Grid item xs={12} md={4}>
            <TextField
              label="Search"
              value={draftFilters.search}
              onChange={(event) => setDraftFilters((current) => ({ ...current, search: event.target.value }))}
              fullWidth
            />
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField
              select
              label="Operator"
              value={draftFilters.operatorId}
              onChange={(event) => setDraftFilters((current) => ({ ...current, operatorId: event.target.value }))}
              fullWidth
            >
              <MenuItem value="">All operators</MenuItem>
              {(operators.data?.content ?? []).map((operator) => <MenuItem key={operator.id} value={operator.id}>{operator.name}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField
              select
              label="Cashier"
              value={draftFilters.cashierId}
              onChange={(event) => setDraftFilters((current) => ({ ...current, cashierId: event.target.value }))}
              fullWidth
            >
              <MenuItem value="">All cashiers</MenuItem>
              {(cashiers.data?.content ?? []).map((cashier: UserAdmin) => <MenuItem key={cashier.id} value={cashier.id}>{cashier.displayName}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField
              select
              label="Store"
              value={draftFilters.storeId}
              onChange={(event) => setDraftFilters((current) => ({
                ...current,
                storeId: event.target.value,
                registerId: ''
              }))}
              fullWidth
            >
              <MenuItem value="">All stores</MenuItem>
              {(stores.data?.content ?? []).map((store) => <MenuItem key={store.id} value={store.id}>{store.name}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
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
          <Grid item xs={12} md={4}>
            <TextField
              select
              label="Game type"
              value={draftFilters.gameType}
              onChange={(event) => setDraftFilters((current) => ({ ...current, gameType: event.target.value as LotteryGameType | '' }))}
              fullWidth
            >
              <MenuItem value="">All game types</MenuItem>
              {gameTypes.map((type) => <MenuItem key={type} value={type}>{label(type)}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField
              select
              label="Status"
              value={draftFilters.status}
              onChange={(event) => setDraftFilters((current) => ({ ...current, status: event.target.value as LotterySaleStatus | '' }))}
              fullWidth
            >
              <MenuItem value="">All statuses</MenuItem>
              {saleStatuses.map((status) => <MenuItem key={status} value={status}>{label(status)}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField
              select
              label="Payment"
              value={draftFilters.paymentMethod}
              onChange={(event) => setDraftFilters((current) => ({ ...current, paymentMethod: event.target.value as PaymentMethod | '' }))}
              fullWidth
            >
              <MenuItem value="">All payments</MenuItem>
              {paymentMethods.map((method) => <MenuItem key={method} value={method}>{label(method)}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={2}>
            <TextField
              label="From"
              type="date"
              value={draftFilters.occurredFrom}
              onChange={(event) => setDraftFilters((current) => ({ ...current, occurredFrom: event.target.value }))}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
          </Grid>
          <Grid item xs={12} md={2}>
            <TextField
              label="To"
              type="date"
              value={draftFilters.occurredTo}
              onChange={(event) => setDraftFilters((current) => ({ ...current, occurredTo: event.target.value }))}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
          </Grid>
          <Grid item xs={12}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              <Button type="submit" variant="contained" startIcon={<SearchIcon />}>
                Apply filters
              </Button>
              <Button
                type="button"
                onClick={() => {
                  setDraftFilters(emptyFilters);
                  setFilters(emptyFilters);
                  setPage(0);
                }}
              >
                Reset
              </Button>
            </Stack>
          </Grid>
        </Grid>
      </Paper>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        {sales.isLoading ? (
          <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 320 }}>
            <CircularProgress aria-label="Loading lottery history" />
            <Typography color="text.secondary">Loading lottery history</Typography>
          </Stack>
        ) : (
          <>
            <TableContainer>
              <Table aria-label="Lottery history">
                <TableHead>
                  <TableRow>
                    <TableCell>Occurred</TableCell>
                    <TableCell>Operator</TableCell>
                    <TableCell>Ticket</TableCell>
                    <TableCell>Game</TableCell>
                    <TableCell>Cashier</TableCell>
                    <TableCell>Register</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Amount</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(sales.data?.content ?? []).map((sale) => (
                    <TableRow key={sale.id} hover>
                      <TableCell>
                        <Typography fontWeight={700}>{new Date(sale.occurredAt).toLocaleString()}</Typography>
                        <Typography variant="body2" color="text.secondary">{sale.paymentMethod}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography>{sale.operatorName}</Typography>
                        <Typography variant="body2" color="text.secondary">{sale.operatorReference ?? sale.operatorCode}</Typography>
                      </TableCell>
                      <TableCell>{sale.ticketReference ?? sale.id}</TableCell>
                      <TableCell>{label(sale.gameType)}</TableCell>
                      <TableCell>{sale.cashierDisplayName}</TableCell>
                      <TableCell>
                        <Typography>{sale.registerName}</Typography>
                        <Typography variant="body2" color="text.secondary">{sale.storeName}</Typography>
                      </TableCell>
                      <TableCell><Chip size="small" label={label(sale.status)} color={statusColor(sale.status)} /></TableCell>
                      <TableCell align="right">{money(sale.amount, sale.currencyCode)}</TableCell>
                    </TableRow>
                  ))}
                  {sales.data?.content.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8}>
                        <Alert severity="info">No lottery history entries found.</Alert>
                      </TableCell>
                    </TableRow>
                  ) : null}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component="div"
              count={sales.data?.totalElements ?? 0}
              page={page}
              onPageChange={(_, nextPage) => setPage(nextPage)}
              rowsPerPage={10}
              rowsPerPageOptions={[10]}
            />
          </>
        )}
      </Paper>
    </Stack>
  );
}
