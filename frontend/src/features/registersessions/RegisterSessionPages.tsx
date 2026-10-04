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
  Skeleton,
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
  getBusinessDayOperationalState,
  getCurrentRegisterSession,
  getRegisterAvailability,
  listCashMovements,
  listDevices,
  listRegisters,
  listRegisterSessions,
  listStores,
  openRegisterSession,
  openBusinessDay,
  previewRegisterTillSettlement,
  restoreRegisterTill,
  reverseCashPayout,
  overrideRegisterSession,
  startRegisterSessionClosing
} from '../../api/client';
import { registerSessionKeys } from './registerSessionKeys';
import { ReconciliationBreakdown } from './RegisterReconciliation';
import { ApiClientError } from '../../api/client';
import type { CashLedgerDirection, CashMovement, CashMovementType, Device, Register, RegisterSession, RegisterTillSettlement, Store, UserRole } from '../../api/types';
import { getApplicationDeviceIdentifier } from '../../app/deviceIdentity';
import { useSession } from '../../app/session';
import { resolveBusinessDayAccess } from '../eod/businessDayAccess';
import { useBusinessDayBoundaryRefresh } from '../eod/useBusinessDayBoundaryRefresh';
import { posRouteForRegisterType } from '../pos/posRouting';

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
  retainedCashOverride: z.string().optional(),
  retentionOverrideReason: z.string().optional(),
  forceCloseReason: z.string().optional(),
  varianceExplanation: z.string().optional()
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

function openingSourceLabel(source: RegisterSession['openingSource']) {
  if (source === 'STORE_DEFAULT') return 'Store Default Till Amount';
  if (source === 'REGISTER_OVERRIDE') return 'Register Default Till Amount';
  if (source === 'MANUAL_ENTRY') return 'Verified Manual Opening Cash';
  return 'Historical source not recorded';
}

function SessionMetricCard({ label, value, emphasis = false }: { label: string; value: string; emphasis?: boolean }) {
  return <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2, minHeight: 104, height: '100%' }}>
    <Stack spacing={0.75} justifyContent="center" sx={{ height: '100%' }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h6" fontWeight={700} color={emphasis ? 'success.main' : 'text.primary'}>{value}</Typography>
    </Stack>
  </Paper>;
}

function SalesClassificationSection({ session, currencyCode }: { session: RegisterSession; currencyCode?: string }) {
  const classification = session.salesClassification;
  const metrics = [
    ['Taxable Sales', classification?.taxableSales],
    ['Non-Taxable Sales', classification?.nonTaxableSales],
    ['General Tax Collected', classification?.generalTaxCollected ?? classification?.taxCollected],
    ['Vape Tax Collected', classification?.vapeTaxCollected ?? 0],
    ['Total Tax Collected', classification?.totalTaxCollected ?? classification?.taxCollected],
    ['Merchandise Net Sales', classification?.merchandiseNetSales]
  ] as const;
  return <Paper component="section" aria-labelledby="sales-classification-heading" elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: { xs: 2, sm: 2.5 } }}>
    <Stack spacing={2}>
      <Typography id="sales-classification-heading" variant="h6" component="h2" fontWeight={600}>Sales Classification</Typography>
      <Grid container spacing={2} data-testid="sales-classification-grid">
        {metrics.map(([label, amount], index) => <Grid item xs={12} sm={6} lg={3} key={label} data-sales-classification-card={label}>
          <SessionMetricCard label={label} value={amount === undefined ? 'Unavailable' : money(amount, currencyCode)} emphasis={index === metrics.length - 1 && amount !== undefined} />
        </Grid>)}
      </Grid>
    </Stack>
  </Paper>;
}

function CurrentSessionLoading() {
  return <Paper role="status" aria-label="Loading current register" elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
    <Stack spacing={3}>
      <Skeleton variant="text" width="35%" height={44} />
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: { xs: 2, sm: 2.5 } }}>
        <Stack spacing={2}>
          <Skeleton variant="text" width={180} height={32} />
          <Grid container spacing={2} data-testid="sales-classification-loading-grid">
            {[0, 1, 2, 3].map((key) => <Grid item xs={12} sm={6} lg={3} key={key}><Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2, minHeight: 104 }}><Skeleton width="60%" /><Skeleton width="75%" height={34} /></Paper></Grid>)}
          </Grid>
        </Stack>
      </Paper>
    </Stack>
  </Paper>;
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
            <Typography variant="body2" color="text.secondary">Opened</Typography>
            <Typography fontWeight={700}>{new Date(session.openedAt).toLocaleString()}</Typography>
          </Grid>
          <Grid item xs={12} sm={6}>
            <Typography variant="body2" color="text.secondary">Version</Typography>
            <Typography fontWeight={700}>{session.version}</Typography>
          </Grid>
        </Grid>
        <SalesClassificationSection session={session} currencyCode={store?.currencyCode} />
        <ReconciliationBreakdown session={session} currencyCode={store?.currencyCode} contained />
      </Stack>
    </Paper>
  );
}

export function RegisterCurrentPage() {
  const { currentUser, getValidAccessToken } = useSession();
  const { canUse } = useRegisterSessionPermissions();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [startClosingOpen, setStartClosingOpen] = React.useState(false);
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);

  const current = useQuery({
    queryKey: registerSessionKeys.current(browserDeviceIdentifier, currentUser?.userId),
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

      {current.isLoading ? <CurrentSessionLoading /> : null}
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
  const { currentUser, getValidAccessToken } = useSession();
  const { canUse, canForceClose } = useRegisterSessionPermissions();
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);
  const [settlement, setSettlement] = React.useState<RegisterTillSettlement | null>(null);

  const current = useQuery({
    queryKey: registerSessionKeys.current(browserDeviceIdentifier, currentUser?.userId),
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier }),
    enabled: canUse
  });

  const form = useForm<CloseFormValues>({
    resolver: zodResolver(closeSchema),
    defaultValues: {
      countedCash: 0,
      retainedCashOverride: '',
      retentionOverrideReason: '',
      forceCloseReason: '',
      varianceExplanation: ''
    }
  });

  const countedCash = form.watch('countedCash');
  const retainedCashOverride = form.watch('retainedCashOverride');
  const retentionOverrideReason = form.watch('retentionOverrideReason');
  React.useEffect(() => { setSettlement(null); }, [countedCash, retainedCashOverride, retentionOverrideReason]);

  const previewMutation = useMutation({
    mutationFn: async (values: CloseFormValues) => {
      if (!current.data) throw new Error('No current register session');
      return previewRegisterTillSettlement(await getValidAccessToken(), current.data.id, {
        countedCash: values.countedCash,
        ...(values.retainedCashOverride ? { retainedCash: Number(values.retainedCashOverride), overrideReason: values.retentionOverrideReason } : {}),
        version: current.data.version
      });
    },
    onSuccess: setSettlement
  });

  const closeMutation = useMutation({
    mutationFn: async (values: CloseFormValues) => {
      if (!current.data) {
        throw new Error('No current register session');
      }
      const varianceExplanation = values.varianceExplanation?.trim();
      if (settlement?.variance !== 0 && !varianceExplanation) {
        throw new Error('Variance explanation is required when counted cash does not match expected cash');
      }
      return closeRegisterSession(await getValidAccessToken(), current.data.id, {
        countedCash: values.countedCash,
        retainedCash: settlement?.cashToLeave,
        ...(settlement?.override ? { overrideReason: values.retentionOverrideReason } : {}),
        ...(varianceExplanation ? { varianceExplanation } : {}),
        version: current.data.version
      });
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['register-session-current'] }),
        queryClient.invalidateQueries({ queryKey: ['register-sessions'] }),
        queryClient.invalidateQueries({ queryKey: ['register-session-availability'] }),
        queryClient.invalidateQueries({ queryKey: ['registers'] }),
        queryClient.invalidateQueries({ queryKey: ['business-day'] })
      ]);
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
      const varianceExplanation = values.varianceExplanation?.trim();
      if (settlement?.variance !== 0 && !varianceExplanation) {
        throw new Error('Variance explanation is required when counted cash does not match expected cash');
      }
      return forceCloseRegisterSession(await getValidAccessToken(), current.data.id, {
        countedCash: values.countedCash,
        retainedCash: settlement?.cashToLeave,
        ...(settlement?.override ? { overrideReason: values.retentionOverrideReason } : {}),
        reason,
        ...(varianceExplanation ? { varianceExplanation } : {}),
        version: current.data.version
      });
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['register-session-current'] }),
        queryClient.invalidateQueries({ queryKey: ['register-sessions'] }),
        queryClient.invalidateQueries({ queryKey: ['register-session-availability'] }),
        queryClient.invalidateQueries({ queryKey: ['registers'] }),
        queryClient.invalidateQueries({ queryKey: ['business-day'] })
      ]);
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
      {previewMutation.isError ? <Alert severity="error">{errorMessage(previewMutation.error)}</Alert> : null}
      {forceCloseMutation.isError ? <Alert severity="error">{errorMessage(forceCloseMutation.error)}</Alert> : null}
      {cancelClosingMutation.isError ? <Alert severity="error">{errorMessage(cancelClosingMutation.error)}</Alert> : null}
      {current.data?.status === 'OPEN' ? <Alert severity="info">Start closing from the current register screen before completing reconciliation.</Alert> : null}

      {current.data ? (
        <Grid container spacing={3}>
          <Grid item xs={12} md={5}>
            <Paper
              component="form"
              elevation={0}
              onSubmit={form.handleSubmit((values) => settlement ? closeMutation.mutate(values) : previewMutation.mutate(values))}
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
                {settlement && settlement.variance !== 0 ? (
                  <Controller
                    name="varianceExplanation"
                    control={form.control}
                    render={({ field }) => (
                      <TextField {...field} label="Variance explanation" required multiline minRows={2} fullWidth
                        helperText="Explain why counted cash does not match expected cash." />
                    )}
                  />
                ) : null}
                {canForceClose ? <Paper variant="outlined" sx={{ p: 2 }}><Stack spacing={1.5}>
                  <Typography variant="subtitle2">Manager retained-float override</Typography>
                  <Controller name="retainedCashOverride" control={form.control} render={({ field }) => (
                    <TextField {...field} label="Actual retained cash (optional)" type="number" inputProps={{ min: 0, step: '0.01' }} />
                  )} />
                  <Controller name="retentionOverrideReason" control={form.control} render={({ field }) => (
                    <TextField {...field} label="Retention override reason" required={Boolean(retainedCashOverride)} disabled={!retainedCashOverride} />
                  )} />
                </Stack></Paper> : null}
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                  <Button
                    type="submit"
                    variant="contained"
                    startIcon={<ReceiptLongOutlinedIcon />}
                    disabled={current.data.status !== 'CLOSING' || closeMutation.isPending || previewMutation.isPending
                      || forceCloseMutation.isPending
                      || Boolean(settlement && settlement.variance !== 0 && !form.watch('varianceExplanation')?.trim())}
                  >
                    {settlement ? 'Confirm Cash Bag & Close Shift' : 'Review Shift Cash Settlement'}
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
                      disabled={!settlement || closeMutation.isPending || forceCloseMutation.isPending
                        || (settlement.variance !== 0 && !form.watch('varianceExplanation')?.trim())}
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
                {settlement ? <>
                  <Grid container spacing={2}>
                    <Grid item xs={6}><Typography color="text.secondary">Expected Cash</Typography><Typography variant="h6">{money(settlement.expectedCash)}</Typography></Grid>
                    <Grid item xs={6}><Typography color="text.secondary">Variance</Typography><Typography variant="h6" color={settlement.variance === 0 ? 'success.main' : 'warning.main'}>{money(settlement.variance)} — {settlement.variance === 0 ? 'BALANCED' : settlement.variance < 0 ? 'SHORT' : 'OVER'}</Typography></Grid>
                  </Grid>
                  <Box sx={{ borderTop: '1px solid', borderColor: 'divider', pt: 2 }}>
                    <Typography variant="overline">Shift Cash Settlement</Typography>
                    <Typography color="text.secondary">Default Till Amount</Typography>
                    <Typography variant="h6">{settlement.targetTillFloat == null ? 'Not configured' : money(settlement.targetTillFloat)}</Typography>
                    <Typography color="text.secondary" sx={{ mt: 1 }}>Cash Remaining in Till</Typography>
                    <Typography variant="h5" fontWeight={700}>{money(settlement.cashToLeave)}</Typography>
                    <Typography color="error.main" sx={{ mt: 1, fontWeight: 700 }}>CASH TO BAG</Typography>
                    <Typography variant="h4" fontWeight={800} color="error.main">{money(settlement.cashToRemove)}</Typography>
                    {settlement.amountNeededToRestoreFloat > 0 ? <Alert severity="warning" sx={{ mt: 2 }}>Amount Needed to Restore Float: {money(settlement.amountNeededToRestoreFloat)}. Record any top-up separately as Cash Paid In.</Alert> : null}
                  </Box>
                </> : <Alert severity="info">Enter the counted cash, then review the backend-calculated settlement. Expected cash remains hidden until review.</Alert>}
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
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6} md={4}><SessionMetricCard label="Session Opening Balance" value={money(session.openingCash, session.currencyCode)} /></Grid>
              <Grid item xs={12} sm={6} md={4}><SessionMetricCard label="Opening Source" value={openingSourceLabel(session.openingSource)} /></Grid>
              <Grid item xs={12} sm={6} md={4}><SessionMetricCard label="Expected Cash at Close" value={money(session.expectedCashAtClose ?? session.expectedCash, session.currencyCode)} /></Grid>
              <Grid item xs={12} sm={6} md={4}><SessionMetricCard label="Counted Cash" value={session.countedCash == null ? 'Not counted' : money(session.countedCash, session.currencyCode)} /></Grid>
              <Grid item xs={12} sm={6} md={4}><SessionMetricCard label="Variance" value={session.differenceCash == null ? 'Not reconciled' : money(session.differenceCash, session.currencyCode)} /></Grid>
              <Grid item xs={12} sm={6} md={4}><SessionMetricCard label="Default Till Amount" value={session.targetFloatAtClose == null ? 'Not configured' : money(session.targetFloatAtClose, session.currencyCode)} /></Grid>
              <Grid item xs={12} sm={6} md={4}><SessionMetricCard label="Cash to Bag" value={session.cashRemoved == null ? 'Not settled' : money(session.cashRemoved, session.currencyCode)} /></Grid>
              <Grid item xs={12} sm={6} md={4}><SessionMetricCard label="Cash Remaining in Till" value={session.cashRetained == null ? 'Not settled' : money(session.cashRetained, session.currencyCode)} /></Grid>
              <Grid item xs={12} sm={6} md={4}><SessionMetricCard label="Closed By" value={session.closedByDisplayName ?? 'Not recorded'} /></Grid>
            </Grid>
            <Box component="details" sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2 }}>
              <Typography component="summary" fontWeight={700} sx={{ cursor: 'pointer' }}>Session cash activity</Typography>
              <Box sx={{ pt: 2, overflowX: 'auto' }}><ReconciliationBreakdown session={session} currencyCode={session.currencyCode} /></Box>
            </Box>
            {session.retentionOverrideReason ? <Alert severity="info">Retention override by {session.retentionOverrideByDisplayName ?? 'authorized manager'}: {session.retentionOverrideReason}</Alert> : null}
            {session.forceCloseReason ? <Alert severity="warning">Force close: {session.forceCloseReason}</Alert> : null}
            {session.varianceExplanation ? <Alert severity="info">Variance explanation: {session.varianceExplanation}</Alert> : null}
          </Stack>
        </Paper>
      ))}
    </Stack>
  );
}

function CashMovementHistory({ movements, onReverse }: { movements: CashMovement[]; onReverse?: (movement: CashMovement) => void }) {
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
          <TableCell>Employee</TableCell>
          <TableCell>Reason</TableCell>
          <TableCell>Note</TableCell>
          <TableCell>Approved</TableCell>
          {onReverse ? <TableCell align="right">Action</TableCell> : null}
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
            <TableCell>{movement.createdByName || '—'}</TableCell>
            <TableCell>{movement.reason}</TableCell>
            <TableCell>{movement.notes || '—'}</TableCell>
            <TableCell>{movement.approvedAt ? new Date(movement.approvedAt).toLocaleString() : 'Not required'}</TableCell>
            {onReverse ? <TableCell align="right">{movement.type === 'PAYOUT' && !movements.some((candidate) => candidate.reversedMovementId === movement.id)
              ? <Button size="small" color="warning" onClick={() => onReverse(movement)}>Reverse</Button>
              : null}</TableCell> : null}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function CashMovementPage() {
  const queryClient = useQueryClient();
  const { getValidAccessToken, currentUser } = useSession();
  const [reversing, setReversing] = React.useState<CashMovement | null>(null);
  const [reversalReason, setReversalReason] = React.useState('');
  const { canUse } = useRegisterSessionPermissions();
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);

  const current = useQuery({
    queryKey: registerSessionKeys.current(browserDeviceIdentifier, currentUser?.userId),
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

  const reversalMutation = useMutation({
    mutationFn: async () => {
      if (!reversing) throw new Error('No payout selected');
      return reverseCashPayout(await getValidAccessToken(), reversing.id, reversalReason.trim());
    },
    onSuccess: async () => {
      setReversing(null);
      setReversalReason('');
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
                {movements.data ? <CashMovementHistory movements={movements.data.content}
                  onReverse={currentUser?.permissions?.includes('CASH_MOVEMENT_APPROVE') ? setReversing : undefined} /> : null}
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      ) : null}
      <Dialog open={reversing !== null} onClose={reversalMutation.isPending ? undefined : () => setReversing(null)} fullWidth maxWidth="xs">
        <DialogTitle>Reverse Cash Payout</DialogTitle>
        <DialogContent sx={{ pt: '8px !important' }}><Stack spacing={2}>
          <Alert severity="warning">This records an equal cash-in reversal. The original payout remains in the audit history.</Alert>
          <Typography>Amount: {money(reversing?.amount ?? 0, reversing?.currencyCode)}</Typography>
          <TextField autoFocus required label="Reversal reason" value={reversalReason}
            onChange={(event) => setReversalReason(event.target.value)} multiline minRows={2} />
          {reversalMutation.isError ? <Alert severity="error">{errorMessage(reversalMutation.error)}</Alert> : null}
        </Stack></DialogContent>
        <DialogActions><Button disabled={reversalMutation.isPending} onClick={() => setReversing(null)}>Cancel</Button>
          <Button variant="contained" color="warning" disabled={!reversalReason.trim() || reversalMutation.isPending}
            onClick={() => reversalMutation.mutate()}>Record Reversal</Button></DialogActions>
      </Dialog>
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
  const [forceCloseReason, setForceCloseReason] = React.useState('');
  const [forceVarianceExplanation, setForceVarianceExplanation] = React.useState('');
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
  const businessDayAccess = resolveBusinessDayAccess(currentUser?.roles ?? [], currentUser?.permissions);
  const businessDay = useQuery({
    queryKey: ['business-day', 'operational-state', selectedStoreId],
    queryFn: async () => getBusinessDayOperationalState(await getValidAccessToken(), selectedStoreId),
    enabled: canUse && businessDayAccess.canView && Boolean(selectedStoreId),
    refetchOnMount: 'always',
    refetchOnWindowFocus: 'always'
  });
  useBusinessDayBoundaryRefresh(businessDay.data?.nextBusinessDateAt, businessDay.refetch);
  const startBusinessDay = useMutation({
    mutationFn: async () => openBusinessDay(await getValidAccessToken(), { storeId: selectedStoreId }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['business-day', 'operational-state', selectedStoreId] }),
        queryClient.invalidateQueries({ queryKey: ['register-session'] })
      ]);
    }
  });

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
  const selectedRegister = registers.data?.content.find((register) => register.id === selectedRegisterId);

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
  const availability = useQuery({
    queryKey: ['register-session-availability', selectedRegisterId],
    queryFn: async () => getRegisterAvailability(await getValidAccessToken(), selectedRegisterId),
    enabled: canUse && Boolean(selectedRegisterId)
  });
  const unavailableToCurrentUser = availability.data?.state === 'IN_USE' && !existingSession;
  const restoreRequired = availability.data?.state === 'RESTORE_REQUIRED';
  const canRestoreTill = currentUser?.permissions?.includes('CASH_MOVEMENT_CREATE') ?? false;

  React.useEffect(() => {
    if (availability.data?.state === 'AVAILABLE' && availability.data.openingCash != null) {
      form.setValue('openingCash', availability.data.openingCash, { shouldValidate: true });
    }
  }, [availability.data, form]);

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
      ...(availability.data?.openingCashSource === 'MANUAL_ENTRY' ? { openingCash: values.openingCash } : {}),
      ...(deviceEnforcementEnabled && values.deviceId ? { deviceId: values.deviceId } : {})
    }),
    onSuccess: async (session) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['register-session-current'] }),
        queryClient.invalidateQueries({ queryKey: ['register-sessions'] }),
        queryClient.invalidateQueries({ queryKey: ['register-session-availability'] }),
        queryClient.invalidateQueries({ queryKey: ['registers'] }),
        queryClient.invalidateQueries({ queryKey: ['business-day'] })
      ]);
      navigate(posRouteForRegisterType(session.registerType) ?? '/store-menu');
    },
    onError: async (error, values) => {
      if (!(error instanceof ApiClientError) || error.status !== 409) return;
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['register-session-availability', values.registerId] }),
        queryClient.invalidateQueries({ queryKey: ['register-sessions'] }),
        queryClient.invalidateQueries({ queryKey: ['register-session-current'] })
      ]);
      if (canOverride || canForceClose) {
        const page = await listRegisterSessions(await getValidAccessToken(), {
          registerId: values.registerId,
          status: 'OPEN',
          page: 0,
          size: 1
        });
        setExistingSession(page.content[0] ?? null);
      }
    }
  });

  const restoreTillMutation = useMutation({
    mutationFn: async () => {
      if (!selectedRegisterId || !availability.data?.restoreRequiredAmount) {
        throw new Error('Till restore amount is unavailable');
      }
      return restoreRegisterTill(await getValidAccessToken(), {
        registerId: selectedRegisterId,
        amount: availability.data.restoreRequiredAmount,
        operationId: crypto.randomUUID()
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['register-session-availability', selectedRegisterId] });
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
      navigate(posRouteForRegisterType(session.registerType) ?? '/store-menu');
    }
  });

  const forceCloseMutation = useMutation({
    mutationFn: async () => {
      if (!existingSession) throw new Error('Open register session is required');
      return forceCloseRegisterSession(await getValidAccessToken(), existingSession.id, {
        countedCash: forceClosingCash,
        reason: forceCloseReason.trim(),
        ...(forceVarianceExplanation.trim() ? { varianceExplanation: forceVarianceExplanation.trim() } : {}),
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

  const forceCloseVariance = existingSession
    ? Math.round((forceClosingCash - existingSession.expectedCash) * 100) / 100
    : 0;

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
      {mutation.isError && (!(mutation.error instanceof ApiClientError) || mutation.error.code !== 'TILL_RESTORE_REQUIRED')
        ? <Alert severity="error">{errorMessage(mutation.error)}</Alert> : null}
      {restoreTillMutation.isError ? <Alert severity="error">{errorMessage(restoreTillMutation.error)}</Alert> : null}
      {startBusinessDay.isError ? <Alert severity="error">{errorMessage(startBusinessDay.error)}</Alert> : null}

      {businessDay.data?.state === 'PREVIOUS_DAY_STILL_OPEN' ? (
        <Alert severity="warning">The previous business day is still open. Ask a Manager or Owner to close it before starting today's business day.</Alert>
      ) : null}
      {businessDay.data?.state === 'CLOSED_TODAY' ? (
        <Alert severity="warning">Today's business day has been closed. Ask a Manager or Owner to reopen it.</Alert>
      ) : null}
      {(businessDay.data?.state === 'NO_BUSINESS_DAY_TODAY' || businessDay.data?.state === 'HISTORICAL_CLOSED') && businessDayAccess.canOpen ? (
        <Alert severity="info" action={(
          <Button color="inherit" disabled={startBusinessDay.isPending} onClick={() => startBusinessDay.mutate()}>
            {startBusinessDay.isPending ? 'Starting…' : 'Start Business Day'}
          </Button>
        )}>
          Business Day Not Started
        </Alert>
      ) : null}

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
              {unavailableToCurrentUser ? <Grid item xs={12}>
                <Alert severity="warning">Register unavailable. This register is currently open in another session. Please choose another register or ask a manager for assistance.</Alert>
              </Grid> : null}
              {restoreRequired ? <Grid item xs={12}>
                <Alert severity="warning">
                  <Typography fontWeight={700}>Till restoration required</Typography>
                  <Typography>This register currently has {money(availability.data?.currentTillAmount ?? 0, stores.data?.content.find((store) => store.id === selectedStoreId)?.currencyCode)}.</Typography>
                  <Typography>The configured till amount is {money(availability.data?.targetTillAmount ?? 0, stores.data?.content.find((store) => store.id === selectedStoreId)?.currencyCode)}.</Typography>
                  <Typography>Add {money(availability.data?.restoreRequiredAmount ?? 0, stores.data?.content.find((store) => store.id === selectedStoreId)?.currencyCode)} before starting this shift.</Typography>
                  {canRestoreTill ? <Button sx={{ mt: 1 }} variant="contained" color="warning"
                    disabled={restoreTillMutation.isPending} onClick={() => restoreTillMutation.mutate()}>
                    Restore Till
                  </Button> : null}
                </Alert>
              </Grid> : null}
              {selectedRegisterId && !existingSession && !unavailableToCurrentUser && !restoreRequired ? <Grid item xs={12} md={6}>
                <Controller
                  name="openingCash"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      label="Actual Opening Cash"
                      type="number"
                      inputProps={{ min: 0, step: '0.01' }}
                      disabled={activeSessions.isLoading || availability.isLoading || availability.data?.openingCashSource !== 'MANUAL_ENTRY'}
                      error={Boolean(fieldState.error)}
                      helperText={fieldState.error?.message ?? (availability.data?.openingCashSource === 'REGISTER_OVERRIDE'
                        ? 'Source: Register Default Till Amount. This amount is configured for this physical register.'
                        : availability.data?.openingCashSource === 'STORE_DEFAULT'
                        ? 'Source: Store Default Till Amount. This amount is configured for the store.'
                        : selectedRegister?.effectiveTillFloat == null
                        ? 'Configured till float: Not configured. Count and enter the cash physically present.'
                        : `Configured till float: ${money(selectedRegister.effectiveTillFloat, selectedStoreId ? stores.data?.content.find((store) => store.id === selectedStoreId)?.currencyCode : undefined)}. Verify and enter the cash physically present.`)}
                      fullWidth
                    />
                  )}
                />
              </Grid> : null}
            </Grid>
            {registers.isError ? <Alert severity="error">{errorMessage(registers.error)}</Alert> : null}
            {deviceEnforcementEnabled && devices.isError ? <Alert severity="error">{errorMessage(devices.error)}</Alert> : null}
            {!existingSession && !unavailableToCurrentUser && !restoreRequired && !activeSessions.isLoading && !availability.isLoading ? <Button
              type="submit"
              variant="contained"
              startIcon={<LockOpenOutlinedIcon />}
              disabled={mutation.isPending || (businessDayAccess.canView && (businessDay.isLoading || businessDay.data?.state !== 'OPEN')) || !selectedStoreId || !selectedRegisterId || (deviceEnforcementEnabled && !form.watch('deviceId'))}
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
            {canOverride ? <TextField
              label="Override reason"
              value={overrideReason}
              onChange={(event) => setOverrideReason(event.target.value)}
              required
              multiline
              minRows={2}
            /> : null}
            {canForceClose ? (
              <Stack spacing={2}>
                <TextField
                  label="Counted cash"
                  type="number"
                  value={forceClosingCash}
                  onChange={(event) => setForceClosingCash(Number(event.target.value))}
                  inputProps={{ min: 0, step: '0.01' }}
                  required
                />
                <Typography>Expected cash: {money(existingSession?.expectedCash ?? 0)}</Typography>
                <Typography color={forceCloseVariance === 0 ? 'success.main' : 'warning.main'}>
                  Variance: {money(forceCloseVariance)}{forceCloseVariance < 0 ? ' — SHORT' : forceCloseVariance > 0 ? ' — OVER' : ''}
                </Typography>
                <TextField
                  label="Force-close reason"
                  value={forceCloseReason}
                  onChange={(event) => setForceCloseReason(event.target.value)}
                  required
                  multiline
                  minRows={2}
                  helperText="Explain why this Register Session must be force closed."
                />
                {forceCloseVariance !== 0 ? <TextField
                  label="Variance explanation"
                  value={forceVarianceExplanation}
                  onChange={(event) => setForceVarianceExplanation(event.target.value)}
                  required
                  multiline
                  minRows={2}
                  helperText="Explain why counted cash does not match expected cash."
                /> : null}
              </Stack>
            ) : null}
            {overrideMutation.isError ? <Alert severity="error">{errorMessage(overrideMutation.error)}</Alert> : null}
            {forceCloseMutation.isError ? <Alert severity="error">{errorMessage(forceCloseMutation.error)}</Alert> : null}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setExistingSession(null); form.setValue('registerId', ''); }}>Cancel</Button>
          <Button onClick={() => navigate('/register/history')}>View Session</Button>
          {existingSession?.assignedCashierId === currentUser?.userId ? (
            <Button variant="contained" disabled={!posRouteForRegisterType(existingSession?.registerType)} onClick={() => navigate(posRouteForRegisterType(existingSession?.registerType) ?? '/store-menu')}>Resume Register</Button>
          ) : null}
          {canForceClose ? (
            <Button color="error" disabled={!forceCloseReason.trim() || (forceCloseVariance !== 0 && !forceVarianceExplanation.trim()) || forceCloseMutation.isPending} onClick={() => forceCloseMutation.mutate()}>
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
