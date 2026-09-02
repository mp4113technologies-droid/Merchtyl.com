import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import LockOpenOutlinedIcon from '@mui/icons-material/LockOpenOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import PointOfSaleOutlinedIcon from '@mui/icons-material/PointOfSaleOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  MenuItem,
  Paper,
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
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import {
  cancelRegisterSessionClosing,
  closeRegisterSession,
  createCashMovement,
  forceCloseRegisterSession,
  getCurrentRegisterSession,
  listCashMovements,
  listDevices,
  listRegisters,
  listRegisterSessions,
  listStores,
  openRegisterSession,
  overrideRegisterSession,
  startRegisterSessionClosing
} from '../../api/client';
import { registerSessionKeys } from './registerSessionKeys';
import { ApiClientError } from '../../api/client';
import type { CashLedgerBreakdown, CashLedgerDirection, CashLedgerSourceType, CashMovement, CashMovementType, Device, Register, RegisterSession, Store, UserRole } from '../../api/types';
import { getApplicationDeviceIdentifier } from '../../app/deviceIdentity';
import { useSession } from '../../app/session';

export function registerDeviceEnforcementEnabled() {
  return import.meta.env.VITE_REGISTER_DEVICE_ENFORCEMENT_ENABLED === 'true';
}

type RegisterSessionFormValues = {
  storeId: string;
  registerId: string;
  deviceId?: string;
  openingCash: number;
};

const cashMovementTypes: CashMovementType[] = [
  'CASH_IN',
  'CASH_OUT',
  'SAFE_DROP',
  'FLOAT_ADD',
  'FLOAT_REMOVE',
  'EXPENSE',
  'BANK_DEPOSIT',
  'CORRECTION'
];

const cashMovementSchema = z.object({
  type: z.enum(cashMovementTypes as [CashMovementType, ...CashMovementType[]]),
  direction: z.enum(['IN', 'OUT']).optional(),
  amount: z.coerce.number().positive('Amount must be greater than 0'),
  reason: z.string().trim().min(1, 'Reason is required'),
  notes: z.string().optional(),
  approvalNotes: z.string().optional()
}).superRefine((value, context) => {
  if (value.type === 'CORRECTION' && !value.direction) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['direction'],
      message: 'Direction is required for corrections'
    });
  }
});

type CashMovementFormValues = z.infer<typeof cashMovementSchema>;

const closeSchema = z.object({
  countedCash: z.coerce.number().min(0, 'Counted cash cannot be negative'),
  forceCloseReason: z.string().optional()
});

type CloseFormValues = z.infer<typeof closeSchema>;

function canUseRegisterSessions(roles: UserRole[], permissions: string[]) {
  if (permissions.length > 0) {
    return permissions.includes('REGISTER_SESSION_OPEN')
      || permissions.includes('REGISTER_SESSION_VIEW')
      || permissions.includes('REGISTER_SESSION_OPERATE');
  }
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER'
    || role === 'STORE_MANAGER' || role === 'CASHIER' || role === 'KITCHEN');
}

function useRegisterSessionPermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const permissions = currentUser?.permissions ?? [];
  return {
    canUse: canUseRegisterSessions(roles, permissions),
    canForceClose: roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER'),
    canOverride: roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER')
  };
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 260 }}>
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
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

function money(value: number, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(value);
}

function movementLabel(value: CashMovementType) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function sourceLabel(value: CashLedgerSourceType) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function reconciliationFor(session: RegisterSession): CashLedgerBreakdown {
  return session.reconciliation ?? {
    openingCash: session.openingCash,
    retailCashReceived: Math.max(0, session.expectedCash - session.openingCash),
    retailChange: 0,
    retailRefunds: 0,
    lotteryCashSales: 0,
    lotteryPayouts: 0,
    payoutReversals: 0,
    lotterySaleCancellations: 0,
    otherCashIn: 0,
    otherCashOut: 0,
    totalIn: Math.max(0, session.expectedCash - session.openingCash),
    totalOut: 0,
    expectedCash: session.expectedCash,
    sourceBreakdown: []
  };
}

function ReconciliationBreakdown({ session, currencyCode = 'USD' }: { session: RegisterSession; currencyCode?: string }) {
  const reconciliation = reconciliationFor(session);
  const formulaRows = [
    { label: 'Opening cash', sign: '+', amount: reconciliation.openingCash },
    { label: 'Retail cash received', sign: '+', amount: reconciliation.retailCashReceived },
    { label: 'Retail change', sign: '-', amount: reconciliation.retailChange },
    { label: 'Retail refunds', sign: '-', amount: reconciliation.retailRefunds },
    { label: 'Lottery cash sales', sign: '+', amount: reconciliation.lotteryCashSales },
    { label: 'Lottery payouts', sign: '-', amount: reconciliation.lotteryPayouts },
    { label: 'Payout reversals', sign: '+', amount: reconciliation.payoutReversals },
    { label: 'Lottery sale cancellations', sign: '-', amount: reconciliation.lotterySaleCancellations },
    { label: 'Other cash in', sign: '+', amount: reconciliation.otherCashIn },
    { label: 'Other cash out', sign: '-', amount: reconciliation.otherCashOut }
  ];
  return (
    <Stack spacing={2}>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}>
          <Typography variant="body2" color="text.secondary">Opening cash</Typography>
          <Typography fontWeight={700}>{money(reconciliation.openingCash, currencyCode)}</Typography>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Typography variant="body2" color="text.secondary">Cash in</Typography>
          <Typography fontWeight={700}>{money(reconciliation.totalIn, currencyCode)}</Typography>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Typography variant="body2" color="text.secondary">Cash out</Typography>
          <Typography fontWeight={700}>{money(reconciliation.totalOut, currencyCode)}</Typography>
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <Typography variant="body2" color="text.secondary">Expected cash</Typography>
          <Typography fontWeight={700}>{money(reconciliation.expectedCash, currencyCode)}</Typography>
        </Grid>
        {session.countedCash !== null ? (
          <>
            <Grid item xs={12} sm={6} md={3}>
              <Typography variant="body2" color="text.secondary">Counted cash</Typography>
              <Typography fontWeight={700}>{money(session.countedCash, currencyCode)}</Typography>
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <Typography variant="body2" color="text.secondary">Difference</Typography>
              <Typography fontWeight={700} color={(session.differenceCash ?? 0) === 0 ? 'success.main' : 'warning.main'}>
                {money(session.differenceCash ?? 0, currencyCode)}
              </Typography>
            </Grid>
          </>
        ) : null}
      </Grid>
      <Table size="small" aria-label="Register reconciliation formula">
        <TableHead>
          <TableRow>
            <TableCell>Category</TableCell>
            <TableCell align="center">Effect</TableCell>
            <TableCell align="right">Amount</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {formulaRows.map((item) => (
            <TableRow key={item.label}>
              <TableCell>{item.label}</TableCell>
              <TableCell align="center">{item.sign}</TableCell>
              <TableCell align="right">{money(item.amount, currencyCode)}</TableCell>
            </TableRow>
          ))}
          <TableRow>
            <TableCell>
              <Typography fontWeight={700}>Expected closing cash</Typography>
            </TableCell>
            <TableCell align="center">=</TableCell>
            <TableCell align="right">
              <Typography fontWeight={700}>{money(reconciliation.expectedCash, currencyCode)}</Typography>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
      {reconciliation.sourceBreakdown.length > 0 ? (
        <Table size="small" aria-label="Reconciliation source breakdown">
          <TableHead>
            <TableRow>
              <TableCell>Source</TableCell>
              <TableCell>Direction</TableCell>
              <TableCell align="right">Amount</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {reconciliation.sourceBreakdown.map((item) => (
              <TableRow key={`${item.sourceType}-${item.direction}`}>
                <TableCell>{sourceLabel(item.sourceType)}</TableCell>
                <TableCell>{item.direction}</TableCell>
                <TableCell align="right">{money(item.amount, currencyCode)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      ) : (
        <Typography variant="body2" color="text.secondary">No cash activity after opening float.</Typography>
      )}
    </Stack>
  );
}

function CurrentSessionSummary({
  session,
  stores,
  registers,
  devices
}: {
  session: RegisterSession;
  stores: Store[];
  registers: Register[];
  devices: Device[];
}) {
  const store = stores.find((item) => item.id === session.storeId);
  const register = registers.find((item) => item.id === session.registerId);
  const device = devices.find((item) => item.id === session.deviceId);

  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'flex-start', sm: 'center' }}>
          <PointOfSaleOutlinedIcon color="primary" sx={{ fontSize: 40 }} />
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="overline" color="text.secondary">Current session</Typography>
            <Typography variant="h5" component="h1">{registerLabel(register)}</Typography>
          </Box>
          <Chip label={session.status} color="success" />
        </Stack>
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6}>
            <Typography variant="body2" color="text.secondary">Store</Typography>
            <Typography fontWeight={700}>{storeLabel(store)}</Typography>
          </Grid>
          <Grid item xs={12} sm={6}>
            <Typography variant="body2" color="text.secondary">Device</Typography>
            <Typography fontWeight={700}>{session.deviceName ?? deviceLabel(device)}</Typography>
          </Grid>
          <Grid item xs={12} sm={6}>
            <Typography variant="body2" color="text.secondary">Current operator</Typography>
            <Typography fontWeight={700}>{session.assignedCashierDisplayName}</Typography>
            <Typography variant="body2" color="text.secondary">{session.assignedCashierEmail}</Typography>
          </Grid>
          <Grid item xs={12} sm={6}>
            <Typography variant="body2" color="text.secondary">Opened by</Typography>
            <Typography fontWeight={700}>{session.openedByDisplayName ?? 'Operator not recorded'}</Typography>
          </Grid>
          <Grid item xs={12} sm={6}>
            <Typography variant="body2" color="text.secondary">Opening cash</Typography>
            <Typography fontWeight={700}>{money(session.openingCash, store?.currencyCode)}</Typography>
          </Grid>
          <Grid item xs={12} sm={6}>
            <Typography variant="body2" color="text.secondary">Expected cash</Typography>
            <Typography fontWeight={700}>{money(session.expectedCash, store?.currencyCode)}</Typography>
          </Grid>
          <Grid item xs={12} sm={6}>
            <Typography variant="body2" color="text.secondary">Opened</Typography>
            <Typography fontWeight={700}>{new Date(session.openedAt).toLocaleString()}</Typography>
          </Grid>
          <Grid item xs={12} sm={6}>
            <Typography variant="body2" color="text.secondary">Version</Typography>
            <Typography fontWeight={700}>{session.version}</Typography>
          </Grid>
        </Grid>
        <ReconciliationBreakdown session={session} currencyCode={store?.currencyCode} />
      </Stack>
    </Paper>
  );
}

export function RegisterCurrentPage() {
  const { getValidAccessToken } = useSession();
  const { canUse } = useRegisterSessionPermissions();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [startClosingOpen, setStartClosingOpen] = React.useState(false);
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);

  const current = useQuery({
    queryKey: registerSessionKeys.current(browserDeviceIdentifier),
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier }),
    enabled: canUse
  });

  const stores = useQuery({
    queryKey: ['stores', 'register-session-current'],
    queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100 }),
    enabled: canUse && Boolean(current.data?.deviceId)
  });

  const registers = useQuery({
    queryKey: ['registers', 'register-session-current'],
    queryFn: async () => listRegisters(await getValidAccessToken(), { page: 0, size: 100 }),
    enabled: canUse && Boolean(current.data)
  });

  const devices = useQuery({
    queryKey: ['devices', 'register-session-current'],
    queryFn: async () => listDevices(await getValidAccessToken(), { page: 0, size: 100 }),
    enabled: canUse && Boolean(current.data)
  });

  const startClosing = useMutation({
    mutationFn: async () => {
      if (!current.data) throw new Error('No current register session');
      return startRegisterSessionClosing(await getValidAccessToken(), current.data.id, { version: current.data.version });
    },
    onSuccess: async () => {
      setStartClosingOpen(false);
      await queryClient.invalidateQueries({ queryKey: ['register-session-current'] });
      navigate('/register/close');
    }
  });

  if (!canUse) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 980 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">Current register</Typography>
          <Typography color="text.secondary">Open register session for this browser.</Typography>
        </Box>
        <Tooltip title="Refresh current session">
          <IconButton aria-label="Refresh current session" onClick={() => void current.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        <Button component={Link} to="/register/open" variant="contained" startIcon={<LockOpenOutlinedIcon />}>
          Open register
        </Button>
        <Button component={Link} to="/register/cash-movements" variant="outlined" startIcon={<PaymentsOutlinedIcon />}>
          Cash movements
        </Button>
        {current.data?.status === 'CLOSING' ? (
          <Button component={Link} to="/register/close" variant="outlined" startIcon={<ReceiptLongOutlinedIcon />}>Complete Closing</Button>
        ) : (
          <Button variant="outlined" startIcon={<ReceiptLongOutlinedIcon />} disabled={!current.data} onClick={() => setStartClosingOpen(true)}>Start Closing</Button>
        )}
      </Stack>

      {current.isLoading ? <LoadingPanel label="Loading current register" /> : null}
      {current.isError ? <Alert severity="error">{errorMessage(current.error)}</Alert> : null}
      {startClosing.isError ? <Alert severity="error">{errorMessage(startClosing.error)}</Alert> : null}
      {!current.isLoading && !current.isError && !current.data ? (
        <Alert severity="info" action={<Button component={Link} to="/register/open">Open</Button>}>
          No register session is open for this user.
        </Alert>
      ) : null}
      {stores.isError ? <Alert severity="error">{errorMessage(stores.error)}</Alert> : null}
      {registers.isError ? <Alert severity="error">{errorMessage(registers.error)}</Alert> : null}
      {devices.isError ? <Alert severity="error">{errorMessage(devices.error)}</Alert> : null}
      {current.data ? (
        <CurrentSessionSummary
          session={current.data}
          stores={stores.data?.content ?? []}
          registers={registers.data?.content ?? []}
          devices={devices.data?.content ?? []}
        />
      ) : null}
      <Dialog open={startClosingOpen} onClose={() => setStartClosingOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Start register closing?</DialogTitle>
        <DialogContent>
          <Typography>This will begin the cash reconciliation process. You can cancel before the register is finalized.</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setStartClosingOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={startClosing.isPending} onClick={() => startClosing.mutate()}>Start Closing</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

export function RegisterClosePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { getValidAccessToken } = useSession();
  const { canUse, canForceClose } = useRegisterSessionPermissions();
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);

  const current = useQuery({
    queryKey: registerSessionKeys.current(browserDeviceIdentifier),
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier }),
    enabled: canUse
  });

  const form = useForm<CloseFormValues>({
    resolver: zodResolver(closeSchema),
    defaultValues: {
      countedCash: 0,
      forceCloseReason: ''
    }
  });

  React.useEffect(() => {
    if (current.data) {
      form.setValue('countedCash', current.data.expectedCash);
    }
  }, [current.data, form]);

  const closeMutation = useMutation({
    mutationFn: async (values: CloseFormValues) => {
      if (!current.data) {
        throw new Error('No current register session');
      }
      return closeRegisterSession(await getValidAccessToken(), current.data.id, {
        countedCash: values.countedCash,
        version: current.data.version
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['register-session-current'] });
      await queryClient.invalidateQueries({ queryKey: ['register-sessions'] });
      navigate('/register/history');
    }
  });

  const forceCloseMutation = useMutation({
    mutationFn: async (values: CloseFormValues) => {
      if (!current.data) {
        throw new Error('No current register session');
      }
      const reason = values.forceCloseReason?.trim();
      if (!reason) {
        throw new Error('Force-close reason is required');
      }
      return forceCloseRegisterSession(await getValidAccessToken(), current.data.id, {
        countedCash: values.countedCash,
        reason,
        version: current.data.version
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['register-session-current'] });
      await queryClient.invalidateQueries({ queryKey: ['register-sessions'] });
      navigate('/register/history');
    }
  });

  const cancelClosingMutation = useMutation({
    mutationFn: async () => {
      if (!current.data) throw new Error('No current register session');
      return cancelRegisterSessionClosing(await getValidAccessToken(), current.data.id, { version: current.data.version });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['register-session-current'] });
      await queryClient.invalidateQueries({ queryKey: ['register-sessions'] });
      navigate('/register/current');
    }
  });

  if (!canUse) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 980 }}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Tooltip title="Back to current register">
          <IconButton component={Link} to="/register/current" aria-label="Back to current register">
            <ArrowBackIcon />
          </IconButton>
        </Tooltip>
        <Box>
          <Typography variant="h5" component="h1">Close register</Typography>
          <Typography color="text.secondary">Count drawer cash and reconcile it against ledger activity.</Typography>
        </Box>
      </Stack>

      {current.isLoading ? <LoadingPanel label="Loading current register" /> : null}
      {current.isError ? <Alert severity="error">{errorMessage(current.error)}</Alert> : null}
      {!current.isLoading && !current.isError && !current.data ? (
        <Alert severity="info" action={<Button component={Link} to="/register/open">Open</Button>}>
          No register session is open for this device.
        </Alert>
      ) : null}
      {closeMutation.isError ? <Alert severity="error">{errorMessage(closeMutation.error)}</Alert> : null}
      {forceCloseMutation.isError ? <Alert severity="error">{errorMessage(forceCloseMutation.error)}</Alert> : null}
      {cancelClosingMutation.isError ? <Alert severity="error">{errorMessage(cancelClosingMutation.error)}</Alert> : null}
      {current.data?.status === 'OPEN' ? <Alert severity="info">Start closing from the current register screen before completing reconciliation.</Alert> : null}

      {current.data ? (
        <Grid container spacing={3}>
          <Grid item xs={12} md={5}>
            <Paper
              component="form"
              elevation={0}
              onSubmit={form.handleSubmit((values) => closeMutation.mutate(values))}
              sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}
            >
              <Stack spacing={2.5}>
                <Typography variant="h6">Cash count</Typography>
                <Controller
                  name="countedCash"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      label="Counted cash"
                      type="number"
                      inputProps={{ min: 0, step: '0.01' }}
                      error={Boolean(fieldState.error)}
                      helperText={fieldState.error?.message}
                      fullWidth
                    />
                  )}
                />
                <Controller
                  name="forceCloseReason"
                  control={form.control}
                  render={({ field }) => (
                    <TextField {...field} label="Force-close reason" multiline minRows={2} fullWidth disabled={!canForceClose} />
                  )}
                />
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                  <Button
                    type="submit"
                    variant="contained"
                    startIcon={<ReceiptLongOutlinedIcon />}
                    disabled={current.data.status !== 'CLOSING' || closeMutation.isPending || forceCloseMutation.isPending}
                  >
                    Complete Closing
                  </Button>
                  <Button type="button" variant="outlined" disabled={current.data.status !== 'CLOSING' || cancelClosingMutation.isPending || closeMutation.isPending}
                    onClick={() => cancelClosingMutation.mutate()}>
                    Cancel Closing
                  </Button>
                  {canForceClose ? (
                    <Button
                      type="button"
                      variant="outlined"
                      color="warning"
                      disabled={closeMutation.isPending || forceCloseMutation.isPending}
                      onClick={form.handleSubmit((values) => forceCloseMutation.mutate(values))}
                    >
                      Force close
                    </Button>
                  ) : null}
                </Stack>
              </Stack>
            </Paper>
          </Grid>
          <Grid item xs={12} md={7}>
            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
              <Stack spacing={2}>
                <Typography variant="h6">Reconciliation</Typography>
                <ReconciliationBreakdown session={current.data} />
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  );
}

export function RegisterHistoryPage() {
  const { getValidAccessToken } = useSession();
  const { canUse } = useRegisterSessionPermissions();

  const sessions = useQuery({
    queryKey: ['register-sessions', 'history'],
    queryFn: async () => listRegisterSessions(await getValidAccessToken(), { page: 0, size: 25 }),
    enabled: canUse
  });

  if (!canUse) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 1100 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">Register history</Typography>
          <Typography color="text.secondary">Recent register sessions and close reconciliation.</Typography>
        </Box>
        <Tooltip title="Refresh register history">
          <IconButton aria-label="Refresh register history" onClick={() => void sessions.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Stack>

      {sessions.isLoading ? <LoadingPanel label="Loading register history" /> : null}
      {sessions.isError ? <Alert severity="error">{errorMessage(sessions.error)}</Alert> : null}
      {sessions.data?.content.length === 0 ? <Alert severity="info">No register sessions found.</Alert> : null}
      {sessions.data?.content.map((session) => (
        <Paper key={session.id} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
          <Stack spacing={2}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems={{ xs: 'flex-start', sm: 'center' }}>
              <Box sx={{ flexGrow: 1 }}>
                <Typography variant="h6">{session.assignedCashierDisplayName ?? 'Operator not recorded'}</Typography>
                <Typography color="text.secondary">
                  Opened by {session.openedByDisplayName ?? 'Operator not recorded'}
                </Typography>
                <Typography color="text.secondary">Opened {new Date(session.openedAt).toLocaleString()}</Typography>
                <Typography color="text.secondary">Device: {session.deviceName ?? 'Not recorded'}</Typography>
                {session.closedAt ? (
                  <Typography color="text.secondary">Closed {new Date(session.closedAt).toLocaleString()}</Typography>
                ) : null}
              </Box>
              <Chip label={session.status} color={session.status === 'OPEN' ? 'success' : 'default'} />
            </Stack>
            <ReconciliationBreakdown session={session} />
            {session.forceCloseReason ? <Alert severity="warning">Force close: {session.forceCloseReason}</Alert> : null}
          </Stack>
        </Paper>
      ))}
    </Stack>
  );
}

function CashMovementHistory({ movements }: { movements: CashMovement[] }) {
  if (movements.length === 0) {
    return <Alert severity="info">No cash movements recorded for this session.</Alert>;
  }
  return (
    <Table size="small" aria-label="Cash movement history">
      <TableHead>
        <TableRow>
          <TableCell>Time</TableCell>
          <TableCell>Type</TableCell>
          <TableCell>Direction</TableCell>
          <TableCell align="right">Amount</TableCell>
          <TableCell>Reason</TableCell>
          <TableCell>Approved</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {movements.map((movement) => (
          <TableRow key={movement.id}>
            <TableCell>{new Date(movement.occurredAt).toLocaleString()}</TableCell>
            <TableCell>{movementLabel(movement.type)}</TableCell>
            <TableCell>
              <Chip
                size="small"
                label={movement.direction}
                color={movement.direction === 'IN' ? 'success' : 'default'}
              />
            </TableCell>
            <TableCell align="right">{money(movement.amount, movement.currencyCode)}</TableCell>
            <TableCell>{movement.reason}</TableCell>
            <TableCell>{movement.approvedAt ? new Date(movement.approvedAt).toLocaleString() : 'Not required'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function CashMovementPage() {
  const queryClient = useQueryClient();
  const { getValidAccessToken } = useSession();
  const { canUse } = useRegisterSessionPermissions();
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);

  const current = useQuery({
    queryKey: registerSessionKeys.current(browserDeviceIdentifier),
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier }),
    enabled: canUse
  });

  const movements = useQuery({
    queryKey: ['cash-movements', current.data?.id],
    queryFn: async () => listCashMovements(await getValidAccessToken(), {
      registerSessionId: current.data?.id,
      page: 0,
      size: 50
    }),
    enabled: canUse && Boolean(current.data?.id)
  });

  const form = useForm<CashMovementFormValues>({
    resolver: zodResolver(cashMovementSchema),
    defaultValues: {
      type: 'CASH_OUT',
      direction: undefined,
      amount: 0,
      reason: '',
      notes: '',
      approvalNotes: ''
    }
  });

  const selectedType = form.watch('type');
  React.useEffect(() => {
    if (selectedType !== 'CORRECTION') {
      form.setValue('direction', undefined);
    }
  }, [form, selectedType]);

  const mutation = useMutation({
    mutationFn: async (values: CashMovementFormValues) => {
      if (!current.data) {
        throw new Error('No current register session');
      }
      return createCashMovement(await getValidAccessToken(), {
        registerSessionId: current.data.id,
        type: values.type,
        direction: values.direction as CashLedgerDirection | undefined,
        amount: values.amount,
        reason: values.reason,
        notes: values.notes || undefined,
        occurredAt: new Date().toISOString(),
        approvalNotes: values.approvalNotes || undefined
      });
    },
    onSuccess: async () => {
      form.reset({
        type: 'CASH_OUT',
        direction: undefined,
        amount: 0,
        reason: '',
        notes: '',
        approvalNotes: ''
      });
      await queryClient.invalidateQueries({ queryKey: ['register-session-current'] });
      await queryClient.invalidateQueries({ queryKey: ['cash-movements'] });
    }
  });

  if (!canUse) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 1040 }}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Tooltip title="Back to current register">
          <IconButton component={Link} to="/register/current" aria-label="Back to current register">
            <ArrowBackIcon />
          </IconButton>
        </Tooltip>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">Cash movements</Typography>
          <Typography color="text.secondary">Record drawer cash changes for the current register session.</Typography>
        </Box>
        <Tooltip title="Refresh cash movements">
          <IconButton aria-label="Refresh cash movements" onClick={() => void movements.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Stack>

      {current.isLoading ? <LoadingPanel label="Loading current register" /> : null}
      {current.isError ? <Alert severity="error">{errorMessage(current.error)}</Alert> : null}
      {!current.isLoading && !current.isError && !current.data ? (
        <Alert severity="info" action={<Button component={Link} to="/register/open">Open</Button>}>
          No register session is open for this device.
        </Alert>
      ) : null}
      {mutation.isError ? <Alert severity="error">{errorMessage(mutation.error)}</Alert> : null}

      {current.data ? (
        <Grid container spacing={3}>
          <Grid item xs={12} md={5}>
            <Paper
              component="form"
              elevation={0}
              onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
              sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}
            >
              <Stack spacing={2.5}>
                <Typography variant="h6">New movement</Typography>
                <Controller
                  name="type"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      select
                      label="Type"
                      error={Boolean(fieldState.error)}
                      helperText={fieldState.error?.message}
                      fullWidth
                    >
                      {cashMovementTypes.map((type) => (
                        <MenuItem key={type} value={type}>{movementLabel(type)}</MenuItem>
                      ))}
                    </TextField>
                  )}
                />
                {selectedType === 'CORRECTION' ? (
                  <Controller
                    name="direction"
                    control={form.control}
                    render={({ field, fieldState }) => (
                      <TextField
                        {...field}
                        select
                        label="Direction"
                        error={Boolean(fieldState.error)}
                        helperText={fieldState.error?.message}
                        fullWidth
                      >
                        <MenuItem value="IN">In</MenuItem>
                        <MenuItem value="OUT">Out</MenuItem>
                      </TextField>
                    )}
                  />
                ) : null}
                <Controller
                  name="amount"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      label="Amount"
                      type="number"
                      inputProps={{ min: 0.01, step: '0.01' }}
                      error={Boolean(fieldState.error)}
                      helperText={fieldState.error?.message}
                      fullWidth
                    />
                  )}
                />
                <Controller
                  name="reason"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      label="Reason"
                      error={Boolean(fieldState.error)}
                      helperText={fieldState.error?.message}
                      fullWidth
                    />
                  )}
                />
                <Controller
                  name="notes"
                  control={form.control}
                  render={({ field }) => (
                    <TextField {...field} label="Notes" multiline minRows={2} fullWidth />
                  )}
                />
                <Controller
                  name="approvalNotes"
                  control={form.control}
                  render={({ field }) => (
                    <TextField {...field} label="Approval notes" multiline minRows={2} fullWidth />
                  )}
                />
                <Button
                  type="submit"
                  variant="contained"
                  startIcon={<PaymentsOutlinedIcon />}
                  disabled={mutation.isPending}
                  sx={{ alignSelf: 'flex-start' }}
                >
                  Record movement
                </Button>
              </Stack>
            </Paper>
          </Grid>
          <Grid item xs={12} md={7}>
            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">History</Typography>
                  <Typography color="text.secondary">
                    Expected cash: {money(current.data.expectedCash)}
                  </Typography>
                </Box>
                {movements.isLoading ? <LoadingPanel label="Loading cash movements" /> : null}
                {movements.isError ? <Alert severity="error">{errorMessage(movements.error)}</Alert> : null}
                {movements.data ? <CashMovementHistory movements={movements.data.content} /> : null}
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  );
}

export function RegisterOpenPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const { getValidAccessToken, currentUser } = useSession();
  const { canUse, canForceClose, canOverride } = useRegisterSessionPermissions();
  const [existingSession, setExistingSession] = React.useState<RegisterSession | null>(null);
  const [overrideReason, setOverrideReason] = React.useState('');
  const [forceClosingCash, setForceClosingCash] = React.useState(0);
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);
  const deviceEnforcementEnabled = registerDeviceEnforcementEnabled();
  const registerSessionSchema = React.useMemo(() => z.object({
    storeId: z.string().uuid('Select a store'),
    registerId: z.string().uuid('Select a register'),
    deviceId: deviceEnforcementEnabled ? z.string().uuid('Select a device') : z.string().optional(),
    openingCash: z.coerce.number().min(0, 'Opening cash cannot be negative')
  }), [deviceEnforcementEnabled]);

  const stores = useQuery({
    queryKey: ['stores', 'register-session-open'],
    queryFn: async () => listStores(await getValidAccessToken(), { active: true, page: 0, size: 100 }),
    enabled: canUse
  });

  const form = useForm<RegisterSessionFormValues>({
    resolver: zodResolver(registerSessionSchema),
    defaultValues: {
      storeId: searchParams.get('storeId') ?? '',
      registerId: searchParams.get('registerId') ?? '',
      deviceId: '',
      openingCash: 0
    }
  });
  const selectedStoreId = form.watch('storeId');
  const selectedRegisterId = form.watch('registerId');

  const registers = useQuery({
    queryKey: ['registers', 'register-session-open', selectedStoreId],
    queryFn: async () => listRegisters(await getValidAccessToken(), {
      storeId: selectedStoreId,
      active: true,
      page: 0,
      size: 100
    }),
    enabled: canUse && Boolean(selectedStoreId)
  });

  const devices = useQuery({
    queryKey: ['devices', 'register-session-open', selectedStoreId, selectedRegisterId],
    queryFn: async () => listDevices(await getValidAccessToken(), {
      storeId: selectedStoreId,
      registerId: selectedRegisterId,
      active: true,
      page: 0,
      size: 100
    }),
    enabled: deviceEnforcementEnabled && canUse && Boolean(selectedStoreId) && Boolean(selectedRegisterId)
  });

  const activeSessions = useQuery({
    queryKey: ['register-sessions', 'active-register', selectedRegisterId],
    queryFn: async () => listRegisterSessions(await getValidAccessToken(), {
      registerId: selectedRegisterId,
      status: 'OPEN',
      page: 0,
      size: 1
    }),
    enabled: canUse && Boolean(selectedRegisterId)
  });

  React.useEffect(() => {
    setExistingSession(activeSessions.data?.content[0] ?? null);
  }, [activeSessions.data?.content]);

  React.useEffect(() => {
    const firstStoreId = stores.data?.content[0]?.id;
    if (!form.getValues('storeId') && firstStoreId) {
      form.setValue('storeId', firstStoreId);
    }
  }, [form, stores.data?.content]);

  React.useEffect(() => {
    const firstRegisterId = registers.data?.content[0]?.id;
    if (!form.getValues('registerId') && firstRegisterId) {
      form.setValue('registerId', firstRegisterId);
    }
  }, [form, registers.data?.content]);

  React.useEffect(() => {
    if (!deviceEnforcementEnabled) return;
    const device = devices.data?.content.find((item) => item.deviceIdentifier === browserDeviceIdentifier)
      ?? devices.data?.content[0];
    if (!form.getValues('deviceId') && device) {
      form.setValue('deviceId', device.id);
    }
  }, [browserDeviceIdentifier, devices.data?.content, form]);

  const mutation = useMutation({
    mutationFn: async (values: RegisterSessionFormValues) => openRegisterSession(await getValidAccessToken(), {
      storeId: values.storeId,
      registerId: values.registerId,
      openingCash: values.openingCash,
      ...(deviceEnforcementEnabled && values.deviceId ? { deviceId: values.deviceId } : {})
    }),
    onSuccess: async (session) => {
      await queryClient.invalidateQueries({ queryKey: ['register-session-current'] });
      navigate(session.registerType === 'FOOD_SERVICE' ? '/pos/food' : '/pos');
    },
    onError: async (error, values) => {
      if (!(error instanceof ApiClientError) || error.status !== 409) return;
      const page = await listRegisterSessions(await getValidAccessToken(), {
        registerId: values.registerId,
        status: 'OPEN',
        page: 0,
        size: 1
      });
      setExistingSession(page.content[0] ?? null);
    }
  });

  const overrideMutation = useMutation({
    mutationFn: async () => {
      if (!existingSession) throw new Error('Open register session is required');
      return overrideRegisterSession(await getValidAccessToken(), existingSession.id, {
        reason: overrideReason,
        version: existingSession.version
      });
    },
    onSuccess: async (session) => {
      setExistingSession(null);
      await queryClient.invalidateQueries({ queryKey: ['register-session-current'] });
      await queryClient.invalidateQueries({ queryKey: ['register-sessions'] });
      navigate(session.registerType === 'FOOD_SERVICE' ? '/pos/food' : '/pos');
    }
  });

  const forceCloseMutation = useMutation({
    mutationFn: async () => {
      if (!existingSession) throw new Error('Open register session is required');
      return forceCloseRegisterSession(await getValidAccessToken(), existingSession.id, {
        countedCash: forceClosingCash,
        reason: overrideReason,
        version: existingSession.version
      });
    },
    onSuccess: async () => {
      setExistingSession(null);
      await queryClient.invalidateQueries({ queryKey: ['register-sessions'] });
    }
  });

  if (!canUse) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 900 }}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Tooltip title="Back to current register">
          <IconButton component={Link} to="/register/current" aria-label="Back to current register">
            <ArrowBackIcon />
          </IconButton>
        </Tooltip>
        <Box>
          <Typography variant="h5" component="h1">Open register</Typography>
          <Typography color="text.secondary">Assign this cashier to a store and register.</Typography>
        </Box>
      </Stack>

      {stores.isLoading ? <LoadingPanel label="Loading register setup" /> : null}
      {stores.isError ? <Alert severity="error">{errorMessage(stores.error)}</Alert> : null}
      {mutation.isError ? <Alert severity="error">{errorMessage(mutation.error)}</Alert> : null}

      {!stores.isLoading && !stores.isError ? (
        <Paper
          component="form"
          elevation={0}
          onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
          sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}
        >
          <Stack spacing={3}>
            {stores.data?.content.length === 0 ? <Alert severity="warning">No active stores are available.</Alert> : null}
            <Grid container spacing={2}>
              <Grid item xs={12}>
                <Controller
                  name="storeId"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      select
                      label="Store"
                      error={Boolean(fieldState.error)}
                      helperText={fieldState.error?.message}
                      fullWidth
                      onChange={(event) => {
                        field.onChange(event);
                        form.setValue('registerId', '');
                        form.setValue('deviceId', '');
                      }}
                    >
                      {(stores.data?.content ?? []).map((store) => (
                        <MenuItem key={store.id} value={store.id}>{storeLabel(store)}</MenuItem>
                      ))}
                    </TextField>
                  )}
                />
              </Grid>
              <Grid item xs={12} md={6}>
                <Controller
                  name="registerId"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      select
                      label="Register"
                      disabled={!selectedStoreId || registers.isLoading}
                      error={Boolean(fieldState.error)}
                      helperText={fieldState.error?.message}
                      fullWidth
                      onChange={(event) => {
                        field.onChange(event);
                        form.setValue('deviceId', '');
                        setExistingSession(null);
                      }}
                    >
                      {(registers.data?.content ?? []).map((register) => (
                        <MenuItem key={register.id} value={register.id}>{registerLabel(register)}</MenuItem>
                      ))}
                    </TextField>
                  )}
                />
              </Grid>
              {deviceEnforcementEnabled ? <Grid item xs={12} md={6}>
                <Controller
                  name="deviceId"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      select
                      label="Device"
                      required
                      disabled={!selectedRegisterId || devices.isLoading}
                      error={Boolean(fieldState.error)}
                      helperText={fieldState.error?.message ?? `Browser device: ${browserDeviceIdentifier}`}
                      fullWidth
                    >
                      {(devices.data?.content ?? []).map((device) => (
                        <MenuItem key={device.id} value={device.id}>{deviceLabel(device)}</MenuItem>
                      ))}
                    </TextField>
                  )}
                />
              </Grid> : <Grid item xs={12} md={6}>
                <Typography variant="body2" color="text.secondary">Device</Typography>
                <Typography>Not required for current browser deployment</Typography>
              </Grid>}
              {selectedRegisterId && activeSessions.isLoading ? (
                <Grid item xs={12}><LoadingPanel label="Checking register availability" /></Grid>
              ) : null}
              {!existingSession && !activeSessions.isLoading ? <Grid item xs={12} md={6}>
                <Controller
                  name="openingCash"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      label="Opening cash"
                      type="number"
                      inputProps={{ min: 0, step: '0.01' }}
                      error={Boolean(fieldState.error)}
                      helperText={fieldState.error?.message}
                      fullWidth
                    />
                  )}
                />
              </Grid> : null}
            </Grid>
            {registers.isError ? <Alert severity="error">{errorMessage(registers.error)}</Alert> : null}
            {deviceEnforcementEnabled && devices.isError ? <Alert severity="error">{errorMessage(devices.error)}</Alert> : null}
            {!existingSession && !activeSessions.isLoading ? <Button
              type="submit"
              variant="contained"
              startIcon={<LockOpenOutlinedIcon />}
              disabled={mutation.isPending || !selectedStoreId || !selectedRegisterId || (deviceEnforcementEnabled && !form.watch('deviceId'))}
              sx={{ alignSelf: 'flex-start' }}
            >
              Open register
            </Button> : null}
          </Stack>
        </Paper>
      ) : null}
      <Dialog open={Boolean(existingSession)} onClose={() => { setExistingSession(null); form.setValue('registerId', ''); }} fullWidth maxWidth="sm">
        <DialogTitle>Register already in use</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Alert severity="warning">
              This register is currently operated by {existingSession?.assignedCashierDisplayName ?? 'another user'}.
              Viewing it will not change the operator.
            </Alert>
            <Typography>Register: {existingSession?.registerId}</Typography>
            <Typography>Store: {existingSession?.storeId}</Typography>
            <Typography>Opened: {existingSession ? new Date(existingSession.openedAt).toLocaleString() : '—'}</Typography>
            <Typography>Opened by: {existingSession?.openedByDisplayName ?? 'Operator not recorded'}</Typography>
            <Typography>Opening cash: {existingSession ? money(existingSession.openingCash) : '—'}</Typography>
            {canOverride || canForceClose ? <TextField
              label="Reason"
              value={overrideReason}
              onChange={(event) => setOverrideReason(event.target.value)}
              required
              multiline
              minRows={2}
            /> : null}
            {canForceClose ? (
              <TextField
                label="Closing cash"
                type="number"
                value={forceClosingCash}
                onChange={(event) => setForceClosingCash(Number(event.target.value))}
                inputProps={{ min: 0, step: '0.01' }}
              />
            ) : null}
            {overrideMutation.isError ? <Alert severity="error">{errorMessage(overrideMutation.error)}</Alert> : null}
            {forceCloseMutation.isError ? <Alert severity="error">{errorMessage(forceCloseMutation.error)}</Alert> : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setExistingSession(null); form.setValue('registerId', ''); }}>Cancel</Button>
          <Button onClick={() => navigate('/register/history')}>View Session</Button>
          {existingSession?.assignedCashierId === currentUser?.userId ? (
            <Button variant="contained" onClick={() => navigate(existingSession?.registerType === 'FOOD_SERVICE' ? '/pos/food' : '/pos')}>Resume Register</Button>
          ) : null}
          {canForceClose ? (
            <Button color="error" disabled={!overrideReason.trim() || forceCloseMutation.isPending} onClick={() => forceCloseMutation.mutate()}>
              Force Close Register
            </Button>
          ) : null}
          {canOverride ? (
            <Button variant="contained" disabled={!overrideReason.trim() || overrideMutation.isPending} onClick={() => overrideMutation.mutate()}>
              Override Session
            </Button>
          ) : null}
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
