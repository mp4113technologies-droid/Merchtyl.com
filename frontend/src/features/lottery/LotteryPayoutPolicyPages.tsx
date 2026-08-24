import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import EditIcon from '@mui/icons-material/Edit';
import RefreshIcon from '@mui/icons-material/Refresh';
import SaveIcon from '@mui/icons-material/Save';
import SearchIcon from '@mui/icons-material/Search';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  Grid,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Switch,
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
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { Controller, useForm, type Control } from 'react-hook-form';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import {
  createLotteryPayoutPolicy,
  getFeatureResolution,
  getLotteryPayoutPolicy,
  listLotteryOperators,
  listLotteryPayoutPolicies,
  listStores,
  updateLotteryPayoutPolicy,
  updateLotteryPayoutPolicyStatus,
  type LotteryPayoutPolicyPayload,
  type LotteryPayoutPolicySearchParams,
  type LotteryPayoutPolicyUpdatePayload
} from '../../api/client';
import type { LotteryOperator, LotteryPayoutPolicy, LotteryPayoutPolicyStatus, Store, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

type PolicyFilterForm = {
  operatorId: string;
  storeId: string;
  status: LotteryPayoutPolicyStatus | '';
};

const policyStatuses: LotteryPayoutPolicyStatus[] = ['DRAFT', 'SCHEDULED', 'ACTIVE', 'RETIRED'];

const policySchema = z.object({
  operatorId: z.string().trim().min(1, 'Operator is required'),
  storeId: z.string().trim().min(1, 'Store is required'),
  maximumCashPayout: z.coerce.number().min(0, 'Maximum cash payout must be zero or greater'),
  cashierApprovalLimit: z.coerce.number().min(0, 'Cashier approval limit must be zero or greater'),
  managerApprovalThreshold: z.coerce.number().min(0, 'Manager approval threshold must be zero or greater'),
  operatorReferralThreshold: z.coerce.number().min(0, 'Operator referral threshold must be zero or greater'),
  protectedRegisterFloat: z.coerce.number().min(0, 'Protected register float must be zero or greater'),
  allowCashPayout: z.boolean(),
  allowStoreCredit: z.boolean(),
  requireTicketValidation: z.boolean(),
  requireAgeVerification: z.boolean(),
  requireCustomerIdentification: z.boolean(),
  allowAlternateRegister: z.boolean(),
  effectiveFrom: z.string().trim().min(1, 'Effective from is required'),
  effectiveTo: z.string().optional(),
  status: z.enum(['DRAFT', 'SCHEDULED', 'ACTIVE', 'RETIRED'])
}).superRefine((values, context) => {
  if (values.effectiveTo && values.effectiveTo < values.effectiveFrom) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ['effectiveTo'],
      message: 'Effective to must be on or after effective from'
    });
  }
});

type PolicyFormValues = z.infer<typeof policySchema>;
type MoneyFieldName =
  | 'maximumCashPayout'
  | 'cashierApprovalLimit'
  | 'managerApprovalThreshold'
  | 'operatorReferralThreshold'
  | 'protectedRegisterFloat';

const emptyPolicyForm: PolicyFormValues = {
  operatorId: '',
  storeId: '',
  maximumCashPayout: 0,
  cashierApprovalLimit: 0,
  managerApprovalThreshold: 0,
  operatorReferralThreshold: 0,
  protectedRegisterFloat: 0,
  allowCashPayout: true,
  allowStoreCredit: true,
  requireTicketValidation: true,
  requireAgeVerification: true,
  requireCustomerIdentification: true,
  allowAlternateRegister: false,
  effectiveFrom: new Date().toISOString().slice(0, 10),
  effectiveTo: '',
  status: 'DRAFT'
};

function canViewLotteryPolicies(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function useLotteryPolicyPermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  return {
    canView: canViewLotteryPolicies(roles),
    canManage: canViewLotteryPolicies(roles)
  };
}

function useLotteryFeatureEnabled(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['features', 'resolution', 'lottery-payout-policies'],
    queryFn: async () => getFeatureResolution(await getValidAccessToken()),
    enabled,
    select: (resolutions) => resolutions.find((resolution) => resolution.definition.code === 'LOTTERY_SALES')?.enabled
  });
}

function usePolicyReferences(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  const operators = useQuery({
    queryKey: ['lottery-operators', 'payout-policy-form'],
    queryFn: async () => listLotteryOperators(await getValidAccessToken(), { active: true, size: 100 }),
    enabled
  });
  const stores = useQuery({
    queryKey: ['stores', 'payout-policy-form'],
    queryFn: async () => listStores(await getValidAccessToken(), { active: true, size: 100 }),
    enabled
  });
  return { operators, stores };
}

function policyFormValues(policy: LotteryPayoutPolicy): PolicyFormValues {
  return {
    operatorId: policy.operatorId,
    storeId: policy.storeId,
    maximumCashPayout: policy.maximumCashPayout,
    cashierApprovalLimit: policy.cashierApprovalLimit,
    managerApprovalThreshold: policy.managerApprovalThreshold,
    operatorReferralThreshold: policy.operatorReferralThreshold,
    protectedRegisterFloat: policy.protectedRegisterFloat,
    allowCashPayout: policy.allowCashPayout,
    allowStoreCredit: policy.allowStoreCredit,
    requireTicketValidation: policy.requireTicketValidation,
    requireAgeVerification: policy.requireAgeVerification,
    requireCustomerIdentification: policy.requireCustomerIdentification,
    allowAlternateRegister: policy.allowAlternateRegister,
    effectiveFrom: policy.effectiveFrom,
    effectiveTo: policy.effectiveTo ?? '',
    status: policy.status
  };
}

function cleanPayload(values: PolicyFormValues, operators: LotteryOperator[]): LotteryPayoutPolicyPayload {
  const operator = operators.find((candidate) => candidate.id === values.operatorId);
  return {
    operatorId: values.operatorId,
    jurisdictionId: operator?.jurisdictionId ?? '',
    storeId: values.storeId,
    maximumCashPayout: values.maximumCashPayout,
    cashierApprovalLimit: values.cashierApprovalLimit,
    managerApprovalThreshold: values.managerApprovalThreshold,
    operatorReferralThreshold: values.operatorReferralThreshold,
    protectedRegisterFloat: values.protectedRegisterFloat,
    allowCashPayout: values.allowCashPayout,
    allowStoreCredit: values.allowStoreCredit,
    requireTicketValidation: values.requireTicketValidation,
    requireAgeVerification: values.requireAgeVerification,
    requireCustomerIdentification: values.requireCustomerIdentification,
    allowAlternateRegister: values.allowAlternateRegister,
    effectiveFrom: values.effectiveFrom,
    effectiveTo: optionalText(values.effectiveTo),
    status: values.status
  };
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Lottery payout policy request failed';
}

function formatStatus(status: LotteryPayoutPolicyStatus) {
  return status.charAt(0) + status.slice(1).toLowerCase();
}

function formatMoney(amount: number) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(amount);
}

function statusColor(status: LotteryPayoutPolicyStatus) {
  if (status === 'ACTIVE') {
    return 'success';
  }
  if (status === 'SCHEDULED') {
    return 'info';
  }
  if (status === 'RETIRED') {
    return 'default';
  }
  return 'warning';
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }}>
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function FeatureDisabledAlert() {
  return (
    <Alert severity="warning">
      Lottery sales is disabled. Enable LOTTERY_SALES in feature settings before managing payout policies.
    </Alert>
  );
}

function PolicyStatusChip({ status }: { status: LotteryPayoutPolicyStatus }) {
  return <Chip label={formatStatus(status)} color={statusColor(status)} size="small" />;
}

function PolicyForm({
  defaultValues,
  operators,
  stores,
  submitLabel,
  loading,
  error,
  disabled,
  onSubmit
}: {
  defaultValues: PolicyFormValues;
  operators: LotteryOperator[];
  stores: Store[];
  submitLabel: string;
  loading: boolean;
  error?: string;
  disabled?: boolean;
  onSubmit: (values: PolicyFormValues) => void;
}) {
  const form = useForm<PolicyFormValues>({
    resolver: zodResolver(policySchema),
    defaultValues,
    values: defaultValues
  });
  const selectedOperator = operators.find((operator) => operator.id === form.watch('operatorId'));

  return (
    <Stack component="form" spacing={3} onSubmit={form.handleSubmit(onSubmit)}>
      {error ? <Alert severity="error">{error}</Alert> : null}
      {disabled ? <Alert severity="info">This account or feature state can view payout policies but cannot change them.</Alert> : null}

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
                  <MenuItem key={operator.id} value={operator.id}>{operator.name} ({operator.code})</MenuItem>
                ))}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <Controller
            name="storeId"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                select
                label="Store"
                disabled={disabled}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message}
                fullWidth
              >
                {stores.map((store) => (
                  <MenuItem key={store.id} value={store.id}>{store.name} ({store.code})</MenuItem>
                ))}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} md={6}>
          <TextField label="Jurisdiction" value={selectedOperator ? `${selectedOperator.jurisdictionName} (${selectedOperator.jurisdictionCode})` : ''} fullWidth disabled />
        </Grid>
        <Grid item xs={12} md={6}>
          <Controller
            name="status"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                select
                label="Status"
                disabled={disabled}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message}
                fullWidth
              >
                {policyStatuses.map((status) => (
                  <MenuItem key={status} value={status}>{formatStatus(status)}</MenuItem>
                ))}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MoneyInput control={form.control} name="maximumCashPayout" label="Maximum cash payout" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MoneyInput control={form.control} name="cashierApprovalLimit" label="Cashier approval limit" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MoneyInput control={form.control} name="managerApprovalThreshold" label="Manager approval threshold" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MoneyInput control={form.control} name="operatorReferralThreshold" label="Operator referral threshold" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MoneyInput control={form.control} name="protectedRegisterFloat" label="Protected register float" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <DateInput control={form.control} name="effectiveFrom" label="Effective from" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <DateInput control={form.control} name="effectiveTo" label="Effective to" disabled={disabled} />
        </Grid>
      </Grid>

      <Grid container spacing={1}>
        <Grid item xs={12} sm={6} md={4}>
          <SwitchInput control={form.control} name="allowCashPayout" label="Allow cash payout" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <SwitchInput control={form.control} name="allowStoreCredit" label="Allow store credit" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <SwitchInput control={form.control} name="requireTicketValidation" label="Require ticket validation" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <SwitchInput control={form.control} name="requireAgeVerification" label="Require age verification" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <SwitchInput control={form.control} name="requireCustomerIdentification" label="Require customer identification" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <SwitchInput control={form.control} name="allowAlternateRegister" label="Allow alternate register" disabled={disabled} />
        </Grid>
      </Grid>

      {!disabled ? (
        <Button type="submit" variant="contained" startIcon={<SaveIcon />} disabled={loading} sx={{ alignSelf: 'flex-start' }}>
          {submitLabel}
        </Button>
      ) : null}
    </Stack>
  );
}

function MoneyInput({
  control,
  name,
  label,
  disabled
}: {
  control: Control<PolicyFormValues>;
  name: MoneyFieldName;
  label: string;
  disabled?: boolean;
}) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          {...field}
          type="number"
          label={label}
          disabled={disabled}
          error={Boolean(fieldState.error)}
          helperText={fieldState.error?.message}
          inputProps={{ min: 0, step: '0.01' }}
          fullWidth
        />
      )}
    />
  );
}

function DateInput({
  control,
  name,
  label,
  disabled
}: {
  control: Control<PolicyFormValues>;
  name: 'effectiveFrom' | 'effectiveTo';
  label: string;
  disabled?: boolean;
}) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          {...field}
          value={field.value ?? ''}
          type="date"
          label={label}
          disabled={disabled}
          error={Boolean(fieldState.error)}
          helperText={fieldState.error?.message}
          InputLabelProps={{ shrink: true }}
          fullWidth
        />
      )}
    />
  );
}

function SwitchInput({
  control,
  name,
  label,
  disabled
}: {
  control: Control<PolicyFormValues>;
  name: keyof Pick<PolicyFormValues,
    | 'allowCashPayout'
    | 'allowStoreCredit'
    | 'requireTicketValidation'
    | 'requireAgeVerification'
    | 'requireCustomerIdentification'
    | 'allowAlternateRegister'>;
  label: string;
  disabled?: boolean;
}) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <FormControlLabel
          control={<Switch checked={Boolean(field.value)} disabled={disabled} onChange={(_, checked) => field.onChange(checked)} />}
          label={label}
        />
      )}
    />
  );
}

export function LotteryPayoutPoliciesPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useLotteryPolicyPermissions();
  const queryClient = useQueryClient();
  const featureQuery = useLotteryFeatureEnabled(canView);
  const lotteryFeatureDisabled = featureQuery.data === false;
  const references = usePolicyReferences(canView && featureQuery.isSuccess && !lotteryFeatureDisabled);
  const [filters, setFilters] = React.useState<PolicyFilterForm>({ operatorId: '', storeId: '', status: '' });
  const [appliedFilters, setAppliedFilters] = React.useState<PolicyFilterForm>(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);

  const params = React.useMemo<LotteryPayoutPolicySearchParams>(() => ({
    operatorId: optionalText(appliedFilters.operatorId),
    storeId: optionalText(appliedFilters.storeId),
    status: appliedFilters.status,
    page,
    size
  }), [appliedFilters, page, size]);

  const policies = useQuery({
    queryKey: ['lottery-payout-policies', params],
    queryFn: async () => listLotteryPayoutPolicies(await getValidAccessToken(), params),
    enabled: canView && featureQuery.isSuccess && !lotteryFeatureDisabled
  });

  const statusMutation = useMutation({
    mutationFn: async (policy: LotteryPayoutPolicy) => updateLotteryPayoutPolicyStatus(await getValidAccessToken(), policy.id, {
      status: policy.status === 'RETIRED' ? 'DRAFT' : 'RETIRED',
      version: policy.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['lottery-payout-policies'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">Lottery payout policies</Typography>
          <Typography color="text.secondary">Configure effective-dated payout limits and validation requirements.</Typography>
        </Box>
        <Tooltip title="Refresh payout policies">
          <IconButton aria-label="Refresh payout policies" onClick={() => void policies.refetch()} disabled={lotteryFeatureDisabled}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        {canManage && !lotteryFeatureDisabled ? (
          <Button component={Link} to="/lottery/payout-policies/new" variant="contained" startIcon={<AddIcon />}>
            New policy
          </Button>
        ) : null}
      </Stack>

      {lotteryFeatureDisabled ? <FeatureDisabledAlert /> : null}
      {featureQuery.isError ? <Alert severity="error">{errorMessage(featureQuery.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack
          component="form"
          direction={{ xs: 'column', md: 'row' }}
          spacing={2}
          sx={{ p: 2 }}
          onSubmit={(event) => {
            event.preventDefault();
            setPage(0);
            setAppliedFilters(filters);
          }}
        >
          <TextField
            select
            label="Operator"
            value={filters.operatorId}
            onChange={(event) => setFilters((value) => ({ ...value, operatorId: event.target.value }))}
            sx={{ minWidth: 220 }}
          >
            <MenuItem value="">Any</MenuItem>
            {(references.operators.data?.content ?? []).map((operator) => (
              <MenuItem key={operator.id} value={operator.id}>{operator.name} ({operator.code})</MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Store"
            value={filters.storeId}
            onChange={(event) => setFilters((value) => ({ ...value, storeId: event.target.value }))}
            sx={{ minWidth: 200 }}
          >
            <MenuItem value="">Any</MenuItem>
            {(references.stores.data?.content ?? []).map((store) => (
              <MenuItem key={store.id} value={store.id}>{store.name} ({store.code})</MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Status"
            value={filters.status}
            onChange={(event) => setFilters((value) => ({ ...value, status: event.target.value as PolicyFilterForm['status'] }))}
            sx={{ minWidth: 150 }}
          >
            <MenuItem value="">Any</MenuItem>
            {policyStatuses.map((status) => <MenuItem key={status} value={status}>{formatStatus(status)}</MenuItem>)}
          </TextField>
          <Button type="submit" variant="outlined" startIcon={<SearchIcon />} sx={{ minWidth: 112 }}>
            Search
          </Button>
        </Stack>
      </Paper>

      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ p: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Policy list</Typography>
          <Chip label={`${policies.data?.totalElements ?? 0} policies`} size="small" />
        </Stack>
        <Divider />
        {policies.isLoading ? <LoadingPanel label="Loading payout policies" /> : null}
        {policies.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(policies.error)}</Alert> : null}
        {!policies.isLoading && !policies.isError && !lotteryFeatureDisabled ? (
          <>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Policy</TableCell>
                  <TableCell>Store</TableCell>
                  <TableCell>Effective period</TableCell>
                  <TableCell>Cash limit</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(policies.data?.content ?? []).map((policy) => (
                  <TableRow key={policy.id} hover>
                    <TableCell>
                      <Button component={Link} to={`/lottery/payout-policies/${policy.id}`} sx={{ px: 0, justifyContent: 'flex-start', textAlign: 'left' }}>
                        <Stack alignItems="flex-start">
                          <Typography fontWeight={700}>{policy.operatorName}</Typography>
                          <Typography variant="body2" color="text.secondary">{policy.jurisdictionName} ({policy.jurisdictionCode})</Typography>
                        </Stack>
                      </Button>
                    </TableCell>
                    <TableCell>{policy.storeName} ({policy.storeCode})</TableCell>
                    <TableCell>{policy.effectiveFrom} to {policy.effectiveTo ?? 'open'}</TableCell>
                    <TableCell>{formatMoney(policy.maximumCashPayout)}</TableCell>
                    <TableCell><PolicyStatusChip status={policy.status} /></TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Tooltip title="Open payout policy">
                          <IconButton component={Link} to={`/lottery/payout-policies/${policy.id}`} aria-label={`Open ${policy.operatorName} policy`}>
                            <EditIcon />
                          </IconButton>
                        </Tooltip>
                        {canManage ? (
                          <Button size="small" onClick={() => statusMutation.mutate(policy)} disabled={statusMutation.isPending}>
                            {policy.status === 'RETIRED' ? 'Draft' : 'Retire'}
                          </Button>
                        ) : null}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
                {(policies.data?.content.length ?? 0) === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6}>
                      <Typography color="text.secondary" textAlign="center" sx={{ py: 5 }}>
                        No payout policies match the current filters.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={policies.data?.totalElements ?? 0}
              page={page}
              onPageChange={(_, nextPage) => setPage(nextPage)}
              rowsPerPage={size}
              onRowsPerPageChange={(event) => {
                setSize(Number(event.target.value));
                setPage(0);
              }}
              rowsPerPageOptions={[5, 10, 20, 50]}
            />
          </>
        ) : null}
      </TableContainer>
    </Stack>
  );
}

export function NewLotteryPayoutPolicyPage() {
  const navigate = useNavigate();
  const { getValidAccessToken } = useSession();
  const { canManage } = useLotteryPolicyPermissions();
  const featureQuery = useLotteryFeatureEnabled(canManage);
  const lotteryFeatureDisabled = featureQuery.data === false;
  const references = usePolicyReferences(canManage && featureQuery.isSuccess && !lotteryFeatureDisabled);
  const operators = references.operators.data?.content ?? [];

  const mutation = useMutation({
    mutationFn: async (values: PolicyFormValues) => createLotteryPayoutPolicy(await getValidAccessToken(), cleanPayload(values, operators)),
    onSuccess: (policy) => navigate(`/lottery/payout-policies/${policy.id}`)
  });

  if (!canManage) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 1100 }}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Tooltip title="Back to payout policies">
          <IconButton component={Link} to="/lottery/payout-policies" aria-label="Back to payout policies">
            <ArrowBackIcon />
          </IconButton>
        </Tooltip>
        <Box>
          <Typography variant="h5" component="h1">New payout policy</Typography>
          <Typography color="text.secondary">Create an effective-dated lottery payout policy.</Typography>
        </Box>
      </Stack>

      {lotteryFeatureDisabled ? <FeatureDisabledAlert /> : null}
      {references.operators.isError ? <Alert severity="error">{errorMessage(references.operators.error)}</Alert> : null}
      {references.stores.isError ? <Alert severity="error">{errorMessage(references.stores.error)}</Alert> : null}

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <PolicyForm
          defaultValues={emptyPolicyForm}
          operators={operators}
          stores={references.stores.data?.content ?? []}
          submitLabel="Create policy"
          loading={mutation.isPending}
          disabled={lotteryFeatureDisabled || references.operators.isLoading || references.stores.isLoading}
          error={mutation.isError ? errorMessage(mutation.error) : undefined}
          onSubmit={(values) => mutation.mutate(values)}
        />
      </Paper>
    </Stack>
  );
}

export function LotteryPayoutPolicyDetailPage() {
  const { id } = useParams();
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const { canView, canManage } = useLotteryPolicyPermissions();
  const featureQuery = useLotteryFeatureEnabled(canView);
  const lotteryFeatureDisabled = featureQuery.data === false;
  const references = usePolicyReferences(canView && featureQuery.isSuccess && !lotteryFeatureDisabled);

  const policy = useQuery({
    queryKey: ['lottery-payout-policy', id],
    queryFn: async () => getLotteryPayoutPolicy(await getValidAccessToken(), id ?? ''),
    enabled: canView && Boolean(id) && featureQuery.isSuccess && !lotteryFeatureDisabled
  });

  const operators = React.useMemo(() => {
    const content = references.operators.data?.content ?? [];
    if (!policy.data || content.some((operator) => operator.id === policy.data.operatorId)) {
      return content;
    }
    return [...content, {
      id: policy.data.operatorId,
      code: policy.data.operatorCode,
      name: policy.data.operatorName,
      jurisdictionId: policy.data.jurisdictionId,
      jurisdictionCode: policy.data.jurisdictionCode,
      jurisdictionName: policy.data.jurisdictionName,
      supportContact: null,
      settlementFrequency: 'WEEKLY',
      active: true,
      createdAt: policy.data.createdAt,
      updatedAt: policy.data.updatedAt,
      version: 0
    } satisfies LotteryOperator];
  }, [policy.data, references.operators.data]);

  const updateMutation = useMutation({
    mutationFn: async (values: PolicyFormValues) => {
      if (!policy.data || !id) {
        throw new Error('Lottery payout policy is not loaded');
      }
      const payload: LotteryPayoutPolicyUpdatePayload = {
        ...cleanPayload(values, operators),
        version: policy.data.version
      };
      return updateLotteryPayoutPolicy(await getValidAccessToken(), id, payload);
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['lottery-payout-policy', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['lottery-payout-policies'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (status: LotteryPayoutPolicyStatus) => {
      if (!policy.data || !id) {
        throw new Error('Lottery payout policy is not loaded');
      }
      return updateLotteryPayoutPolicyStatus(await getValidAccessToken(), id, {
        status,
        version: policy.data.version
      });
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['lottery-payout-policy', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['lottery-payout-policies'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  if (lotteryFeatureDisabled) {
    return (
      <Stack spacing={2} sx={{ maxWidth: 720 }}>
        <Button component={Link} to="/lottery/payout-policies" startIcon={<ArrowBackIcon />} sx={{ alignSelf: 'flex-start' }}>
          Payout policies
        </Button>
        <FeatureDisabledAlert />
      </Stack>
    );
  }

  if (policy.isLoading) {
    return <LoadingPanel label="Loading payout policy" />;
  }

  if (policy.isError) {
    return (
      <Stack spacing={2} sx={{ maxWidth: 720 }}>
        <Button component={Link} to="/lottery/payout-policies" startIcon={<ArrowBackIcon />} sx={{ alignSelf: 'flex-start' }}>
          Payout policies
        </Button>
        <Alert severity="error">{errorMessage(policy.error)}</Alert>
      </Stack>
    );
  }

  if (!policy.data) {
    return <Alert severity="error">Lottery payout policy was not found.</Alert>;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 1100 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Button component={Link} to="/lottery/payout-policies" startIcon={<ArrowBackIcon />} sx={{ alignSelf: { xs: 'flex-start', sm: 'center' } }}>
          Payout policies
        </Button>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">{policy.data.operatorName} payout policy</Typography>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <Typography color="text.secondary">{policy.data.storeName}</Typography>
            <Typography color="text.secondary">{policy.data.effectiveFrom} to {policy.data.effectiveTo ?? 'open'}</Typography>
            <PolicyStatusChip status={policy.data.status} />
          </Stack>
        </Box>
        {canManage ? (
          <TextField
            select
            size="small"
            label="Set status"
            value={policy.data.status}
            disabled={statusMutation.isPending || updateMutation.isPending}
            onChange={(event) => statusMutation.mutate(event.target.value as LotteryPayoutPolicyStatus)}
            sx={{ minWidth: 160 }}
          >
            {policyStatuses.map((status) => <MenuItem key={status} value={status}>{formatStatus(status)}</MenuItem>)}
          </TextField>
        ) : null}
      </Stack>

      {updateMutation.isSuccess ? <Alert severity="success">Payout policy saved.</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      {references.operators.isError ? <Alert severity="error">{errorMessage(references.operators.error)}</Alert> : null}
      {references.stores.isError ? <Alert severity="error">{errorMessage(references.stores.error)}</Alert> : null}

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6" component="h2">Policy settings</Typography>
            <Typography color="text.secondary">Version {policy.data.version}</Typography>
          </Box>
          <PolicyForm
            defaultValues={policyFormValues(policy.data)}
            operators={operators}
            stores={references.stores.data?.content ?? []}
            submitLabel="Save changes"
            loading={updateMutation.isPending}
            disabled={!canManage || statusMutation.isPending || references.operators.isLoading || references.stores.isLoading}
            error={updateMutation.isError ? errorMessage(updateMutation.error) : undefined}
            onSubmit={(values) => updateMutation.mutate(values)}
          />
        </Stack>
      </Paper>
    </Stack>
  );
}
