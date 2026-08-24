import ApprovalOutlinedIcon from '@mui/icons-material/ApprovalOutlined';
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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  authorizeLotteryPayout,
  completeLotteryCashPayout,
  createLotteryPayout,
  getCurrentRegisterSession,
  getFeatureResolution,
  getLotteryPayoutAvailableCash,
  getLotteryPayoutPolicy,
  listDevices,
  listLotteryOperators,
  listRegisters,
  listStores,
  validateLotteryPayout,
  type LotteryPayoutCreatePayload
} from '../../api/client';
import type {
  Device,
  LotteryOperator,
  LotteryPayout,
  LotteryPayoutCashAvailability,
  LotteryPayoutMethod,
  LotteryPayoutPolicy,
  LotteryVerificationState,
  Register,
  RegisterSession,
  Store,
  UserRole
} from '../../api/types';
import { getApplicationDeviceIdentifier } from '../../app/deviceIdentity';
import { useSession } from '../../app/session';

const payoutMethods: LotteryPayoutMethod[] = [
  'CASH',
  'STORE_CREDIT',
  'OPERATOR_VOUCHER',
  'CHEQUE_REFERRAL',
  'OPERATOR_CLAIM_REFERRAL',
  'OTHER'
];
const verificationStates: Exclude<LotteryVerificationState, 'NOT_REQUIRED' | 'PENDING'>[] = ['VERIFIED', 'FAILED'];
const ticketTypes = ['Draw ticket', 'Instant ticket', 'Sports wager', 'Breakopen', 'Online credit', 'Other'];

const payoutSchema = z.object({
  operatorId: z.string().trim().min(1, 'Operator is required'),
  validationReference: z.string().trim().min(1, 'Validation reference is required').max(180),
  payoutReference: z.string().trim().min(1, 'Payout reference is required').max(180),
  ticketType: z.string().trim().min(1, 'Ticket type is required'),
  amount: z.coerce.number().positive('Prize amount must be greater than zero').multipleOf(0.01, 'Prize amount may include no more than 2 decimals'),
  ticketValidationState: z.enum(['VERIFIED', 'FAILED']),
  ageVerificationState: z.enum(['VERIFIED', 'FAILED']),
  identificationVerificationState: z.enum(['VERIFIED', 'FAILED']),
  payoutMethod: z.enum(['CASH', 'STORE_CREDIT', 'OPERATOR_VOUCHER', 'CHEQUE_REFERRAL', 'OPERATOR_CLAIM_REFERRAL', 'OTHER']),
  notes: z.string().max(1000, 'Notes must be 1000 characters or fewer').optional()
});

type LotteryPayoutFormValues = z.infer<typeof payoutSchema>;

const emptyValues: LotteryPayoutFormValues = {
  operatorId: '',
  validationReference: '',
  payoutReference: '',
  ticketType: 'Draw ticket',
  amount: 0,
  ticketValidationState: 'VERIFIED',
  ageVerificationState: 'VERIFIED',
  identificationVerificationState: 'VERIFIED',
  payoutMethod: 'CASH',
  notes: ''
};

type ApprovalRequirement = 'CASHIER' | 'MANAGER' | 'REFERRAL';

function canRecordLotteryPayout(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
}

function canApproveManagerRequirement(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase());
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Lottery payout request failed';
}

function money(value: number, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(Number.isFinite(value) ? value : 0);
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function idempotencyKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `lottery-payout-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function approvalRequirement(policy: LotteryPayoutPolicy | undefined, amount: number, method: LotteryPayoutMethod): ApprovalRequirement {
  if (!policy || amount <= 0) {
    return 'CASHIER';
  }
  if (
    amount >= policy.operatorReferralThreshold ||
    (method === 'CASH' && amount > policy.maximumCashPayout) ||
    method === 'CHEQUE_REFERRAL' ||
    method === 'OPERATOR_CLAIM_REFERRAL'
  ) {
    return 'REFERRAL';
  }
  return amount > policy.cashierApprovalLimit ? 'MANAGER' : 'CASHIER';
}

function requirementLabel(requirement: ApprovalRequirement) {
  if (requirement === 'REFERRAL') {
    return 'Operator referral required';
  }
  if (requirement === 'MANAGER') {
    return 'Manager approval required';
  }
  return 'Cashier limit approval';
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

function CashSummary({
  availability,
  amount,
  policy,
  method,
  currencyCode
}: {
  availability?: LotteryPayoutCashAvailability;
  amount: number;
  policy?: LotteryPayoutPolicy;
  method: LotteryPayoutMethod;
  currencyCode: string;
}) {
  const available = availability?.availablePayoutCash ?? 0;
  const remaining = method === 'CASH' ? available - amount : available;
  const requirement = approvalRequirement(policy, amount, method);
  const insufficient = method === 'CASH' && amount > 0 && Boolean(availability) && remaining < 0;

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <PaidOutlinedIcon color="primary" />
          <Typography variant="h6">Cash availability</Typography>
        </Stack>
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6} md={4}>
            <Typography variant="body2" color="text.secondary">Expected drawer cash</Typography>
            <Typography fontWeight={700}>{money(availability?.expectedDrawerCash ?? 0, currencyCode)}</Typography>
          </Grid>
          <Grid item xs={12} sm={6} md={4}>
            <Typography variant="body2" color="text.secondary">Protected float</Typography>
            <Typography fontWeight={700}>{money(availability?.protectedRegisterFloat ?? 0, currencyCode)}</Typography>
          </Grid>
          <Grid item xs={12} sm={6} md={4}>
            <Typography variant="body2" color="text.secondary">Available payout cash</Typography>
            <Typography fontWeight={700}>{money(available, currencyCode)}</Typography>
          </Grid>
          <Grid item xs={12} sm={6} md={4}>
            <Typography variant="body2" color="text.secondary">Requested payout</Typography>
            <Typography fontWeight={700}>{money(amount, currencyCode)}</Typography>
          </Grid>
          <Grid item xs={12} sm={6} md={4}>
            <Typography variant="body2" color="text.secondary">Remaining cash</Typography>
            <Typography fontWeight={700} color={insufficient ? 'error.main' : 'text.primary'}>{money(remaining, currencyCode)}</Typography>
          </Grid>
          <Grid item xs={12} sm={6} md={4}>
            <Typography variant="body2" color="text.secondary">Required approval</Typography>
            <Typography fontWeight={700}>{requirementLabel(requirement)}</Typography>
          </Grid>
        </Grid>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Chip
            icon={<ApprovalOutlinedIcon />}
            label={`Referral requirement: ${requirement === 'REFERRAL' ? 'Required' : 'Not required'}`}
            color={requirement === 'REFERRAL' ? 'warning' : 'default'}
          />
          <Chip label={`Approval status: ${requirementLabel(requirement)}`} color={requirement === 'MANAGER' ? 'warning' : 'default'} />
        </Stack>
        {insufficient ? <Alert severity="error">Available payout cash is insufficient for this cash payout.</Alert> : null}
      </Stack>
    </Paper>
  );
}

function PayoutResult({ payout, onNew }: { payout: LotteryPayout; onNew: () => void }) {
  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <PaidOutlinedIcon color={payout.status === 'PAID' ? 'success' : 'warning'} />
          <Typography variant="h6">Lottery payout {label(payout.status)}</Typography>
        </Stack>
        <Grid container spacing={2}>
          <Grid item xs={12} md={3}>
            <Typography variant="body2" color="text.secondary">Operator</Typography>
            <Typography fontWeight={700}>{payout.operatorName}</Typography>
          </Grid>
          <Grid item xs={12} md={3}>
            <Typography variant="body2" color="text.secondary">Payout reference</Typography>
            <Typography fontWeight={700}>{payout.ticketNumber}</Typography>
          </Grid>
          <Grid item xs={12} md={3}>
            <Typography variant="body2" color="text.secondary">Payout method</Typography>
            <Typography fontWeight={700}>{label(payout.payoutMethod)}</Typography>
          </Grid>
          <Grid item xs={12} md={3}>
            <Typography variant="body2" color="text.secondary">Prize amount</Typography>
            <Typography fontWeight={700}>{money(payout.amount, payout.currencyCode)}</Typography>
          </Grid>
        </Grid>
        <Typography color="text.secondary">
          Approval status: {payout.approvals.length > 0 ? label(payout.approvals[payout.approvals.length - 1].approvalType) : label(payout.status)}
        </Typography>
        <Button variant="outlined" startIcon={<RefreshIcon />} onClick={onNew} sx={{ alignSelf: 'flex-start' }}>
          Start another payout
        </Button>
      </Stack>
    </Paper>
  );
}

function LotteryPayoutForm({
  operators,
  session,
  store,
  register,
  device,
  availability,
  policy,
  roles,
  disabled,
  loading,
  error,
  onOperatorChanged,
  onSubmit
}: {
  operators: LotteryOperator[];
  session: RegisterSession;
  store?: Store;
  register?: Register;
  device?: Device;
  availability?: LotteryPayoutCashAvailability;
  policy?: LotteryPayoutPolicy;
  roles: UserRole[];
  disabled: boolean;
  loading: boolean;
  error?: string;
  onOperatorChanged: (operatorId: string) => void;
  onSubmit: (values: LotteryPayoutFormValues, requirement: ApprovalRequirement) => void;
}) {
  const form = useForm<LotteryPayoutFormValues>({
    resolver: zodResolver(payoutSchema),
    defaultValues: emptyValues
  });
  const operatorId = form.watch('operatorId');
  const amount = Number(form.watch('amount') || 0);
  const method = form.watch('payoutMethod');
  const currencyCode = availability?.currencyCode ?? store?.currencyCode ?? 'USD';
  const requirement = approvalRequirement(policy, amount, method);
  const remaining = method === 'CASH' ? (availability?.availablePayoutCash ?? 0) - amount : availability?.availablePayoutCash ?? 0;
  const insufficientCash = method === 'CASH' && amount > 0 && Boolean(availability) && remaining < 0;
  const managerBlocked = requirement === 'MANAGER' && !canApproveManagerRequirement(roles);

  React.useEffect(() => {
    onOperatorChanged(operatorId);
  }, [operatorId, onOperatorChanged]);

  return (
    <Stack component="form" spacing={3} onSubmit={form.handleSubmit((values) => onSubmit(values, requirement))}>
      {error ? <Alert severity="error">{error}</Alert> : null}
      <IdentityStrip session={session} store={store} register={register} device={device} />
      <CashSummary availability={availability} amount={amount} policy={policy} method={method} currencyCode={currencyCode} />

      {managerBlocked ? (
        <Alert severity="warning">This payout requires manager approval. Cashier accounts can validate it but cannot approve or complete it.</Alert>
      ) : null}
      {requirement === 'REFERRAL' ? (
        <Alert severity="info">This payout must be referred to the operator and cannot be completed from the drawer.</Alert>
      ) : null}

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
            name="ticketType"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} select label="Ticket type" disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                {ticketTypes.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} md={3}>
          <Controller
            name="payoutMethod"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} select label="Payout method" disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                {payoutMethods.map((methodOption) => <MenuItem key={methodOption} value={methodOption}>{label(methodOption)}</MenuItem>)}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <Controller
            name="payoutReference"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} label="Payout reference" disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )}
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <Controller
            name="validationReference"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} label="Validation reference" disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
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
                label="Prize amount"
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
            name="ticketValidationState"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} select label="Validation" disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                {verificationStates.map((state) => <MenuItem key={state} value={state}>{label(state)}</MenuItem>)}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <Controller
            name="ageVerificationState"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} select label="Age verification" disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                {verificationStates.map((state) => <MenuItem key={state} value={state}>{label(state)}</MenuItem>)}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <Controller
            name="identificationVerificationState"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} select label="Identification verification" disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                {verificationStates.map((state) => <MenuItem key={state} value={state}>{label(state)}</MenuItem>)}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12}>
          <Controller
            name="notes"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} label="Notes" disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth multiline minRows={3} />
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
          disabled={disabled || loading || operators.length === 0 || !availability || insufficientCash}
        >
          {loading ? 'Submitting' : 'Submit payout'}
        </Button>
        <Chip label={`Session ${session.status}`} color={session.status === 'OPEN' ? 'success' : 'default'} />
        <Chip label={`Approval status: ${requirementLabel(requirement)}`} color={requirement === 'MANAGER' ? 'warning' : 'default'} />
      </Stack>
    </Stack>
  );
}

export function LotteryPayoutPage() {
  const { currentUser, session: authSession, getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const roles = currentUser?.roles ?? authSession?.roles ?? [];
  const canRecord = canRecordLotteryPayout(roles);
  const browserDeviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);
  const [selectedOperatorId, setSelectedOperatorId] = React.useState('');
  const [lastPayout, setLastPayout] = React.useState<LotteryPayout | null>(null);
  const [workflowNotice, setWorkflowNotice] = React.useState<string | null>(null);

  const current = useQuery({
    queryKey: ['register-session-current', browserDeviceIdentifier, 'lottery-payout'],
    queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier: browserDeviceIdentifier }),
    enabled: canRecord
  });

  const stores = useQuery({
    queryKey: ['stores', 'lottery-payout'],
    queryFn: async () => listStores(await getValidAccessToken(), { size: 100 }),
    enabled: canRecord && Boolean(current.data)
  });

  const registers = useQuery({
    queryKey: ['registers', 'lottery-payout'],
    queryFn: async () => listRegisters(await getValidAccessToken(), { size: 100 }),
    enabled: canRecord && Boolean(current.data)
  });

  const devices = useQuery({
    queryKey: ['devices', 'lottery-payout'],
    queryFn: async () => listDevices(await getValidAccessToken(), { size: 100 }),
    enabled: canRecord && Boolean(current.data)
  });

  const featureResolution = useQuery({
    queryKey: ['features', 'resolution', 'lottery-payout', current.data?.storeId, current.data?.registerId],
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
    queryKey: ['lottery-operators', 'payout'],
    queryFn: async () => listLotteryOperators(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: canRecord && featureEnabled === true
  });

  const availability = useQuery({
    queryKey: ['lottery-payout-available-cash', current.data?.id, selectedOperatorId],
    queryFn: async () => {
      if (!current.data || !selectedOperatorId) {
        throw new Error('Select an operator before checking payout cash.');
      }
      return getLotteryPayoutAvailableCash(await getValidAccessToken(), {
        registerSessionId: current.data.id,
        operatorId: selectedOperatorId
      });
    },
    enabled: canRecord && featureEnabled === true && Boolean(current.data?.id) && Boolean(selectedOperatorId)
  });

  const policy = useQuery({
    queryKey: ['lottery-payout-policy', availability.data?.policyId],
    queryFn: async () => getLotteryPayoutPolicy(await getValidAccessToken(), availability.data!.policyId),
    enabled: Boolean(availability.data?.policyId)
  });

  const payoutMutation = useMutation({
    mutationFn: async ({ values, requirement }: { values: LotteryPayoutFormValues; requirement: ApprovalRequirement }) => {
      const currentSession = current.data;
      if (!currentSession) {
        throw new Error('Open a register session before recording lottery payouts on this device.');
      }
      if (values.payoutMethod === 'CASH' && availability.data && values.amount > availability.data.availablePayoutCash) {
        throw new Error('Available payout cash is insufficient for this cash payout.');
      }

      const notes = [`Ticket type: ${values.ticketType}`, optionalText(values.notes)].filter(Boolean).join('\n');
      const payload: LotteryPayoutCreatePayload = {
        operatorId: values.operatorId,
        storeId: currentSession.storeId,
        registerId: currentSession.registerId,
        deviceId: currentSession.deviceId!,
        registerSessionId: currentSession.id,
        ticketNumber: values.payoutReference.trim(),
        amount: values.amount,
        payoutMethod: values.payoutMethod,
        notes: optionalText(notes)
      };
      const created = await createLotteryPayout(await getValidAccessToken(), payload);
      const validated = await validateLotteryPayout(await getValidAccessToken(), created.id, {
        version: created.version,
        ticketValidationState: values.ticketValidationState,
        ageVerificationState: values.ageVerificationState,
        identificationVerificationState: values.identificationVerificationState,
        validationReference: values.validationReference.trim()
      });
      if (validated.status === 'REFERRED_TO_OPERATOR' || requirement === 'REFERRAL') {
        return { payout: validated, notice: 'Operator referral required. The drawer payout was not completed.' };
      }
      if (requirement === 'MANAGER' && !canApproveManagerRequirement(roles)) {
        return { payout: validated, notice: 'Manager approval required. The payout was validated but not approved or completed.' };
      }
      const authorized = await authorizeLotteryPayout(await getValidAccessToken(), validated.id, {
        version: validated.version,
        approvalNotes: requirement === 'MANAGER' ? 'Manager approved in payout workflow' : 'Cashier limit approval'
      });
      if (values.payoutMethod !== 'CASH') {
        return { payout: authorized, notice: 'Payout authorized. Non-cash completion is not available in this workflow yet.' };
      }
      const paid = await completeLotteryCashPayout(await getValidAccessToken(), authorized.id, idempotencyKey());
      return { payout: paid, notice: null };
    },
    onSuccess: async (result) => {
      setLastPayout(result.payout);
      setWorkflowNotice(result.notice);
      await queryClient.invalidateQueries({ queryKey: ['lottery-payout-available-cash'] });
    }
  });

  if (!canRecord) {
    return <Alert severity="error">This account cannot record lottery payouts.</Alert>;
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
          <Typography variant="h5" component="h1">Lottery payout</Typography>
        </Stack>
        <Typography color="text.secondary">Validate and complete eligible lottery payouts against the active register context.</Typography>
      </Box>

      {loading ? <LoadingPanel label="Loading lottery payout context" /> : null}
      {current.error ? <Alert severity="error">{errorMessage(current.error)}</Alert> : null}
      {featureResolution.error ? <Alert severity="error">{errorMessage(featureResolution.error)}</Alert> : null}

      {!loading && !current.data ? (
        <Alert severity="warning">Open a register session on this device before recording lottery payouts.</Alert>
      ) : null}

      {!loading && current.data && featureEnabled === false ? (
        <Alert severity="warning">Lottery sales is disabled for this store/register.</Alert>
      ) : null}

      {workflowNotice ? <Alert severity="info">{workflowNotice}</Alert> : null}
      {lastPayout ? <PayoutResult payout={lastPayout} onNew={() => {
        setLastPayout(null);
        setWorkflowNotice(null);
      }} /> : null}

      {!lastPayout && current.data && featureEnabled === true ? (
        <Paper variant="outlined" sx={{ p: 3 }}>
          <LotteryPayoutForm
            operators={activeOperators}
            session={current.data}
            store={store}
            register={register}
            device={device}
            availability={availability.data}
            policy={policy.data}
            roles={roles}
            disabled={payoutMutation.isPending || operators.isLoading}
            loading={payoutMutation.isPending}
            error={payoutMutation.error ? errorMessage(payoutMutation.error) : undefined}
            onOperatorChanged={setSelectedOperatorId}
            onSubmit={(values, requirement) => payoutMutation.mutate({ values, requirement })}
          />
          {operators.error ? <Alert severity="error" sx={{ mt: 2 }}>{errorMessage(operators.error)}</Alert> : null}
          {availability.error ? <Alert severity="error" sx={{ mt: 2 }}>{errorMessage(availability.error)}</Alert> : null}
          {!operators.isLoading && activeOperators.length === 0 ? (
            <Alert severity="info" sx={{ mt: 2 }}>No active lottery operators are available.</Alert>
          ) : null}
        </Paper>
      ) : null}
    </Stack>
  );
}
