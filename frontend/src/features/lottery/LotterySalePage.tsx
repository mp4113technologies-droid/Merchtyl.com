import ConfirmationNumberOutlinedIcon from '@mui/icons-material/ConfirmationNumberOutlined';
import PaidOutlinedIcon from '@mui/icons-material/PaidOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import SaveIcon from '@mui/icons-material/Save';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography
} from '@mui/material';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import * as React from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  getCurrentRegisterSession,
  getFeatureResolution,
  listDevices,
  listLotteryOperators,
  listRegisters,
  listStores,
  recordLotterySale,
  type LotterySalePayload
} from '../../api/client';
import type { Device, LotteryGameType, LotteryOperator, PaymentMethod, Register, RegisterSession, Store, UserRole } from '../../api/types';
import { getApplicationDeviceIdentifier } from '../../app/deviceIdentity';
import { useSession } from '../../app/session';

const gameTypes: LotteryGameType[] = ['DRAW_TICKET', 'INSTANT_TICKET', 'SPORTS_WAGER', 'BREAKOPEN', 'ONLINE_CREDIT', 'OTHER'];
const paymentMethods: PaymentMethod[] = ['CASH', 'DEBIT', 'CREDIT', 'GIFT_CARD', 'STORE_CREDIT', 'OTHER'];

const lotterySaleSchema = z.object({
  operatorId: z.string().trim().min(1, 'Operator is required'),
  gameType: z.enum(['DRAW_TICKET', 'INSTANT_TICKET', 'SPORTS_WAGER', 'BREAKOPEN', 'ONLINE_CREDIT', 'OTHER']),
  amount: z.coerce.number().positive('Amount must be greater than zero').multipleOf(0.01, 'Amount may include no more than 2 decimals'),
  paymentMethod: z.enum(['CASH', 'DEBIT', 'CREDIT', 'GIFT_CARD', 'STORE_CREDIT', 'OTHER']),
  ticketReference: z.string().max(180, 'Ticket reference must be 180 characters or fewer').optional(),
  operatorReference: z.string().max(180, 'Operator reference must be 180 characters or fewer').optional()
});

type LotterySaleFormValues = z.infer<typeof lotterySaleSchema>;

const emptyValues: LotterySaleFormValues = {
  operatorId: '',
  gameType: 'DRAW_TICKET',
  amount: 0,
  paymentMethod: 'CASH',
  ticketReference: '',
  operatorReference: ''
};

function canRecordLotterySale(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Lottery sale request failed';
}

function money(value: number, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(value);
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase());
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function idempotencyKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `lottery-sale-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function LoadingPanel({ label: panelLabel }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 260 }}>
      <CircularProgress aria-label={panelLabel} />
      <Typography color="text.secondary">{panelLabel}</Typography>
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
          <Typography fontWeight={700}>{store ? `${store.name} (${store.code})` : 'Unknown store'}</Typography>
        </Grid>
        <Grid item xs={12} md={3}>
          <Typography variant="body2" color="text.secondary">Register</Typography>
          <Typography fontWeight={700}>{register ? `${register.name} (${register.code})` : 'Unknown register'}</Typography>
        </Grid>
        <Grid item xs={12} md={3}>
          <Typography variant="body2" color="text.secondary">Device</Typography>
          <Typography fontWeight={700}>{device ? `${device.displayName} (${device.deviceIdentifier})` : 'Unknown device'}</Typography>
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

function LotterySaleResult({
  sale,
  onNew
}: {
  sale: {
    operatorName: string;
    gameType: LotteryGameType;
    amount: number;
    currencyCode: string;
    paymentMethod: PaymentMethod;
    operationId: string;
    occurredAt: string;
  };
  onNew: () => void;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <PaidOutlinedIcon color="success" />
          <Typography variant="h6">Lottery sale recorded</Typography>
        </Stack>
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6} md={3}>
            <Typography variant="body2" color="text.secondary">Operator</Typography>
            <Typography fontWeight={700}>{sale.operatorName}</Typography>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Typography variant="body2" color="text.secondary">Game</Typography>
            <Typography fontWeight={700}>{label(sale.gameType)}</Typography>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Typography variant="body2" color="text.secondary">Payment</Typography>
            <Typography fontWeight={700}>{label(sale.paymentMethod)}</Typography>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Typography variant="body2" color="text.secondary">Amount</Typography>
            <Typography fontWeight={700}>{money(sale.amount, sale.currencyCode)}</Typography>
          </Grid>
        </Grid>
        <Box>
          <Typography variant="body2" color="text.secondary">Operation ID</Typography>
          <Typography sx={{ fontFamily: 'monospace', wordBreak: 'break-word' }}>{sale.operationId}</Typography>
        </Box>
        <Typography variant="body2" color="text.secondary">
          Recorded {new Date(sale.occurredAt).toLocaleString()}
        </Typography>
        <Button variant="outlined" startIcon={<RefreshIcon />} onClick={onNew} sx={{ alignSelf: 'flex-start' }}>
          Record another
        </Button>
      </Stack>
    </Paper>
  );
}

function LotterySaleForm({
  operators,
  session,
  store,
  register,
  device,
  disabled,
  onSubmit,
  loading,
  error
}: {
  operators: LotteryOperator[];
  session: RegisterSession;
  store?: Store;
  register?: Register;
  device?: Device;
  disabled: boolean;
  onSubmit: (values: LotterySaleFormValues) => void;
  loading: boolean;
  error?: string;
}) {
  const form = useForm<LotterySaleFormValues>({
    resolver: zodResolver(lotterySaleSchema),
    defaultValues: emptyValues
  });
  const amount = Number(form.watch('amount') || 0);
  const currencyCode = store?.currencyCode ?? 'USD';

  return (
    <Stack component="form" spacing={3} onSubmit={form.handleSubmit(onSubmit)}>
      {error ? <Alert severity="error">{error}</Alert> : null}
      <IdentityStrip session={session} store={store} register={register} device={device} />

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Controller
            name="operatorId"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                select
                label="Operator"
                disabled={disabled}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message}
                fullWidth
              >
                {operators.map((operator) => (
                  <MenuItem key={operator.id} value={operator.id}>
                    {operator.name} ({operator.code})
                  </MenuItem>
                ))}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} md={3}>
          <Controller
            name="gameType"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                select
                label="Game type"
                disabled={disabled}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message}
                fullWidth
              >
                {gameTypes.map((type) => <MenuItem key={type} value={type}>{label(type)}</MenuItem>)}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} md={3}>
          <Controller
            name="paymentMethod"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                select
                label="Payment method"
                disabled={disabled}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message}
                fullWidth
              >
                {paymentMethods.map((method) => <MenuItem key={method} value={method}>{label(method)}</MenuItem>)}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <Controller
            name="amount"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                type="number"
                inputProps={{ min: 0.01, step: 0.01 }}
                label="Amount"
                disabled={disabled}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message ?? money(amount, currencyCode)}
                fullWidth
              />
            )}
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <Controller
            name="ticketReference"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                label="Ticket reference"
                disabled={disabled}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message}
                fullWidth
              />
            )}
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <Controller
            name="operatorReference"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                label="Operator reference"
                disabled={disabled}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message}
                fullWidth
              />
            )}
          />
        </Grid>
      </Grid>

      <Divider />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Button
          type="submit"
          variant="contained"
          startIcon={<SaveIcon />}
          disabled={disabled || loading || operators.length === 0}
        >
          {loading ? 'Recording' : 'Record lottery sale'}
        </Button>
        <Chip label={`Session ${session.status}`} color={session.status === 'OPEN' ? 'success' : 'default'} />
      </Stack>
    </Stack>
  );
}

export function LotterySalePage() {
  const { currentUser, session: authSession, getValidAccessToken } = useSession();
  const roles = currentUser?.roles ?? authSession?.roles ?? [];
  const canRecord = canRecordLotterySale(roles);
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);
  const [recordedSale, setRecordedSale] = React.useState<Awaited<ReturnType<typeof recordLotterySale>> | null>(null);

  const current = useQuery({
    queryKey: ['register-session-current', browserDeviceIdentifier, 'lottery-sale'],
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier }),
    enabled: canRecord
  });

  const stores = useQuery({
    queryKey: ['stores', 'lottery-sale'],
    queryFn: async () => listStores(await getValidAccessToken(), { size: 100 }),
    enabled: canRecord && Boolean(current.data)
  });

  const registers = useQuery({
    queryKey: ['registers', 'lottery-sale'],
    queryFn: async () => listRegisters(await getValidAccessToken(), { size: 100 }),
    enabled: canRecord && Boolean(current.data)
  });

  const devices = useQuery({
    queryKey: ['devices', 'lottery-sale'],
    queryFn: async () => listDevices(await getValidAccessToken(), { size: 100 }),
    enabled: canRecord && Boolean(current.data)
  });

  const featureResolution = useQuery({
    queryKey: ['features', 'resolution', 'lottery-sale', current.data?.storeId, current.data?.registerId],
    queryFn: async () => getFeatureResolution(await getValidAccessToken(), {
      storeId: current.data?.storeId,
      registerId: current.data?.registerId
    }),
    enabled: canRecord && Boolean(current.data)
  });

  const featureEnabled = featureResolution.data
    ?.find((resolution) => resolution.definition.code === 'LOTTERY_SALES')
    ?.enabled;

  const operators = useQuery({
    queryKey: ['lottery-operators', 'sale-recording'],
    queryFn: async () => listLotteryOperators(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: canRecord && featureEnabled === true
  });

  const recordMutation = useMutation({
    mutationFn: async (values: LotterySaleFormValues) => {
      const currentSession = current.data;
      if (!currentSession) {
        throw new Error('Open a register session before recording lottery sales on this device.');
      }
      const payload: LotterySalePayload = {
        operatorId: values.operatorId,
        operatorReference: optionalText(values.operatorReference),
        ticketReference: optionalText(values.ticketReference),
        gameType: values.gameType,
        amount: values.amount,
        paymentMethod: values.paymentMethod,
        storeId: currentSession.storeId,
        registerId: currentSession.registerId,
        deviceId: currentSession.deviceId!,
        registerSessionId: currentSession.id
      };
      return recordLotterySale(await getValidAccessToken(), payload, idempotencyKey());
    },
    onSuccess: (sale) => setRecordedSale(sale)
  });

  if (!canRecord) {
    return <Alert severity="error">This account cannot record lottery sales.</Alert>;
  }

  const loading = current.isLoading || featureResolution.isLoading;
  const store = stores.data?.content.find((item) => item.id === current.data?.storeId);
  const register = registers.data?.content.find((item) => item.id === current.data?.registerId);
  const device = devices.data?.content.find((item) => item.id === current.data?.deviceId);
  const activeOperators = operators.data?.content ?? [];

  return (
    <Stack spacing={3}>
      <Box>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <ConfirmationNumberOutlinedIcon color="primary" />
          <Typography variant="h5" component="h1">Lottery sale</Typography>
        </Stack>
        <Typography color="text.secondary">Record lottery sales against the active register context.</Typography>
      </Box>

      {loading ? <LoadingPanel label="Loading lottery sale context" /> : null}
      {current.error ? <Alert severity="error">{errorMessage(current.error)}</Alert> : null}
      {featureResolution.error ? <Alert severity="error">{errorMessage(featureResolution.error)}</Alert> : null}

      {!loading && !current.data ? (
        <Alert severity="warning">Open a register session on this device before recording lottery sales.</Alert>
      ) : null}

      {!loading && current.data && featureEnabled === false ? (
        <Alert severity="warning">Lottery sales is disabled for this store/register.</Alert>
      ) : null}

      {recordedSale ? (
        <LotterySaleResult sale={recordedSale} onNew={() => setRecordedSale(null)} />
      ) : null}

      {!recordedSale && current.data && featureEnabled === true ? (
        <Paper variant="outlined" sx={{ p: 3 }}>
          <LotterySaleForm
            operators={activeOperators}
            session={current.data}
            store={store}
            register={register}
            device={device}
            disabled={recordMutation.isPending || operators.isLoading}
            loading={recordMutation.isPending}
            error={recordMutation.error ? errorMessage(recordMutation.error) : undefined}
            onSubmit={(values) => recordMutation.mutate(values)}
          />
          {operators.error ? <Alert severity="error" sx={{ mt: 2 }}>{errorMessage(operators.error)}</Alert> : null}
          {!operators.isLoading && activeOperators.length === 0 ? (
            <Alert severity="info" sx={{ mt: 2 }}>No active lottery operators are available.</Alert>
          ) : null}
        </Paper>
      ) : null}
    </Stack>
  );
}
