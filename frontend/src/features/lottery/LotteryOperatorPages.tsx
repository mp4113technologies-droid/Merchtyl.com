import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
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
  createLotteryOperator,
  getFeatureResolution,
  getLotteryOperator,
  listLotteryOperators,
  listTaxJurisdictions,
  updateLotteryOperator,
  updateLotteryOperatorStatus,
  type LotteryOperatorPayload,
  type LotteryOperatorSearchParams,
  type LotteryOperatorUpdatePayload
} from '../../api/client';
import type { LotteryOperator, SettlementFrequency, TaxJurisdiction, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

type LotteryOperatorFilterForm = {
  code: string;
  name: string;
  jurisdictionId: string;
  settlementFrequency: SettlementFrequency | '';
  active: '' | 'true' | 'false';
};

const settlementFrequencies: SettlementFrequency[] = ['DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY'];

const lotteryOperatorSchema = z.object({
  code: z.string().trim().min(1, 'Code is required').max(64, 'Code must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  jurisdictionId: z.string().trim().min(1, 'Jurisdiction is required'),
  supportContact: z.string().max(1000, 'Support contact must be 1000 characters or fewer').optional(),
  settlementFrequency: z.enum(['DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY']),
  active: z.boolean()
});

type LotteryOperatorFormValues = z.infer<typeof lotteryOperatorSchema>;
type LotteryOperatorTextFieldName = Exclude<keyof LotteryOperatorFormValues, 'active' | 'jurisdictionId' | 'settlementFrequency'>;

const emptyLotteryOperatorForm: LotteryOperatorFormValues = {
  code: '',
  name: '',
  jurisdictionId: '',
  supportContact: '',
  settlementFrequency: 'WEEKLY',
  active: true
};

function canViewLotteryOperators(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function canManageLotteryOperators(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function useLotteryOperatorPermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  return {
    canView: canViewLotteryOperators(roles),
    canManage: canManageLotteryOperators(roles)
  };
}

function useLotteryFeatureEnabled(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['features', 'resolution', 'lottery-operators'],
    queryFn: async () => getFeatureResolution(await getValidAccessToken()),
    enabled,
    select: (resolutions) => resolutions.find((resolution) => resolution.definition.code === 'LOTTERY_SALES')?.enabled
  });
}

function lotteryOperatorFormValues(operator: LotteryOperator): LotteryOperatorFormValues {
  return {
    code: operator.code,
    name: operator.name,
    jurisdictionId: operator.jurisdictionId,
    supportContact: operator.supportContact ?? '',
    settlementFrequency: operator.settlementFrequency,
    active: operator.active
  };
}

function cleanPayload(values: LotteryOperatorFormValues): LotteryOperatorPayload {
  return {
    code: values.code.trim().toUpperCase(),
    name: values.name.trim(),
    jurisdictionId: values.jurisdictionId,
    supportContact: optionalText(values.supportContact),
    settlementFrequency: values.settlementFrequency,
    active: values.active
  };
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Lottery operator request failed';
}

function formatFrequency(frequency: SettlementFrequency) {
  return frequency.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase());
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }}>
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function LotteryStatusChip({ active }: { active: boolean }) {
  return <Chip label={active ? 'Active' : 'Inactive'} color={active ? 'success' : 'default'} size="small" />;
}

function FeatureDisabledAlert() {
  return (
    <Alert severity="warning">
      Lottery sales is disabled. Enable LOTTERY_SALES in feature settings before managing lottery operators.
    </Alert>
  );
}

function LotteryOperatorForm({
  defaultValues,
  jurisdictions,
  submitLabel,
  loading,
  error,
  disabled,
  onSubmit
}: {
  defaultValues: LotteryOperatorFormValues;
  jurisdictions: TaxJurisdiction[];
  submitLabel: string;
  loading: boolean;
  error?: string;
  disabled?: boolean;
  onSubmit: (values: LotteryOperatorFormValues) => void;
}) {
  const form = useForm<LotteryOperatorFormValues>({
    resolver: zodResolver(lotteryOperatorSchema),
    defaultValues,
    values: defaultValues
  });

  return (
    <Stack component="form" spacing={3} onSubmit={form.handleSubmit(onSubmit)}>
      {error ? <Alert severity="error">{error}</Alert> : null}
      {disabled ? <Alert severity="info">This account or feature state can view operators but cannot change lottery operator records.</Alert> : null}

      <Grid container spacing={2}>
        <Grid item xs={12} sm={4}>
          <TextInput control={form.control} name="code" label="Code" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={8}>
          <TextInput control={form.control} name="name" label="Name" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6}>
          <Controller
            name="jurisdictionId"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                select
                label="Jurisdiction"
                disabled={disabled}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message}
                fullWidth
              >
                {jurisdictions.map((jurisdiction) => (
                  <MenuItem key={jurisdiction.id} value={jurisdiction.id}>
                    {jurisdiction.name} ({jurisdiction.code})
                  </MenuItem>
                ))}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <Controller
            name="settlementFrequency"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                select
                label="Settlement frequency"
                disabled={disabled}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message}
                fullWidth
              >
                {settlementFrequencies.map((frequency) => (
                  <MenuItem key={frequency} value={frequency}>{formatFrequency(frequency)}</MenuItem>
                ))}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12}>
          <TextInput control={form.control} name="supportContact" label="Support contact" multiline minRows={4} disabled={disabled} />
        </Grid>
      </Grid>

      <Controller
        name="active"
        control={form.control}
        render={({ field }) => (
          <FormControlLabel
            control={<Switch checked={field.value} disabled={disabled} onChange={(_, checked) => field.onChange(checked)} />}
            label="Active"
          />
        )}
      />

      {!disabled ? (
        <Button type="submit" variant="contained" startIcon={<SaveIcon />} disabled={loading} sx={{ alignSelf: 'flex-start' }}>
          {submitLabel}
        </Button>
      ) : null}
    </Stack>
  );
}

function TextInput({
  control,
  name,
  label,
  disabled,
  multiline,
  minRows
}: {
  control: Control<LotteryOperatorFormValues>;
  name: LotteryOperatorTextFieldName;
  label: string;
  disabled?: boolean;
  multiline?: boolean;
  minRows?: number;
}) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          {...field}
          value={field.value ?? ''}
          label={label}
          disabled={disabled}
          error={Boolean(fieldState.error)}
          helperText={fieldState.error?.message}
          multiline={multiline}
          minRows={minRows}
          fullWidth
        />
      )}
    />
  );
}

function useActiveJurisdictions(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['tax-jurisdictions', 'lottery-operator-form'],
    queryFn: async () => listTaxJurisdictions(await getValidAccessToken(), { active: true, size: 100 }),
    enabled
  });
}

export function LotteryOperatorsPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useLotteryOperatorPermissions();
  const queryClient = useQueryClient();
  const featureQuery = useLotteryFeatureEnabled(canView);
  const lotteryFeatureDisabled = featureQuery.data === false;
  const [filters, setFilters] = React.useState<LotteryOperatorFilterForm>({
    code: '',
    name: '',
    jurisdictionId: '',
    settlementFrequency: '',
    active: ''
  });
  const [appliedFilters, setAppliedFilters] = React.useState<LotteryOperatorFilterForm>(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);

  const jurisdictionsQuery = useActiveJurisdictions(canView);

  const params = React.useMemo<LotteryOperatorSearchParams>(() => ({
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    jurisdictionId: optionalText(appliedFilters.jurisdictionId),
    settlementFrequency: appliedFilters.settlementFrequency,
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const operators = useQuery({
    queryKey: ['lottery-operators', params],
    queryFn: async () => listLotteryOperators(await getValidAccessToken(), params),
    enabled: canView && featureQuery.isSuccess && !lotteryFeatureDisabled
  });

  const statusMutation = useMutation({
    mutationFn: async (operator: LotteryOperator) => updateLotteryOperatorStatus(await getValidAccessToken(), operator.id, {
      active: !operator.active,
      version: operator.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['lottery-operators'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">Lottery operators</Typography>
          <Typography color="text.secondary">Manage lottery operator records by jurisdiction and settlement cadence.</Typography>
        </Box>
        <Tooltip title="Refresh lottery operators">
          <IconButton aria-label="Refresh lottery operators" onClick={() => void operators.refetch()} disabled={lotteryFeatureDisabled}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        {canManage && !lotteryFeatureDisabled ? (
          <Button component={Link} to="/lottery/operators/new" variant="contained" startIcon={<AddIcon />}>
            New operator
          </Button>
        ) : null}
      </Stack>

      {lotteryFeatureDisabled ? <FeatureDisabledAlert /> : null}
      {featureQuery.isError ? <Alert severity="error">{errorMessage(featureQuery.error)}</Alert> : null}

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
          <TextField label="Code" value={filters.code} onChange={(event) => setFilters((value) => ({ ...value, code: event.target.value }))} />
          <TextField label="Name" value={filters.name} onChange={(event) => setFilters((value) => ({ ...value, name: event.target.value }))} />
          <TextField
            select
            label="Jurisdiction"
            value={filters.jurisdictionId}
            onChange={(event) => setFilters((value) => ({ ...value, jurisdictionId: event.target.value }))}
            sx={{ minWidth: 210 }}
          >
            <MenuItem value="">Any</MenuItem>
            {(jurisdictionsQuery.data?.content ?? []).map((jurisdiction) => (
              <MenuItem key={jurisdiction.id} value={jurisdiction.id}>{jurisdiction.name}</MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Frequency"
            value={filters.settlementFrequency}
            onChange={(event) => setFilters((value) => ({ ...value, settlementFrequency: event.target.value as LotteryOperatorFilterForm['settlementFrequency'] }))}
            sx={{ minWidth: 170 }}
          >
            <MenuItem value="">Any</MenuItem>
            {settlementFrequencies.map((frequency) => (
              <MenuItem key={frequency} value={frequency}>{formatFrequency(frequency)}</MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Status"
            value={filters.active}
            onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as LotteryOperatorFilterForm['active'] }))}
            sx={{ minWidth: 150 }}
          >
            <MenuItem value="">Any</MenuItem>
            <MenuItem value="true">Active</MenuItem>
            <MenuItem value="false">Inactive</MenuItem>
          </TextField>
          <Button type="submit" variant="outlined" startIcon={<SearchIcon />} sx={{ minWidth: 112 }}>
            Search
          </Button>
        </Stack>
      </Paper>

      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}

      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ p: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Operator list</Typography>
          <Chip label={`${operators.data?.totalElements ?? 0} operators`} size="small" />
        </Stack>
        <Divider />
        {operators.isLoading ? <LoadingPanel label="Loading lottery operators" /> : null}
        {operators.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(operators.error)}</Alert> : null}
        {!operators.isLoading && !operators.isError && !lotteryFeatureDisabled ? (
          <>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Operator</TableCell>
                  <TableCell>Jurisdiction</TableCell>
                  <TableCell>Settlement</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(operators.data?.content ?? []).map((operator) => (
                  <TableRow key={operator.id} hover>
                    <TableCell>
                      <Button component={Link} to={`/lottery/operators/${operator.id}`} sx={{ px: 0, justifyContent: 'flex-start', textAlign: 'left' }}>
                        <Stack alignItems="flex-start">
                          <Typography fontWeight={700}>{operator.name}</Typography>
                          <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{operator.code}</Typography>
                        </Stack>
                      </Button>
                    </TableCell>
                    <TableCell>{operator.jurisdictionName} ({operator.jurisdictionCode})</TableCell>
                    <TableCell>{formatFrequency(operator.settlementFrequency)}</TableCell>
                    <TableCell><LotteryStatusChip active={operator.active} /></TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Tooltip title="Open lottery operator">
                          <IconButton component={Link} to={`/lottery/operators/${operator.id}`} aria-label={`Open ${operator.name}`}>
                            <EditIcon />
                          </IconButton>
                        </Tooltip>
                        {canManage ? (
                          <Tooltip title={operator.active ? 'Deactivate lottery operator' : 'Activate lottery operator'}>
                            <span>
                              <IconButton
                                aria-label={operator.active ? `Deactivate ${operator.name}` : `Activate ${operator.name}`}
                                onClick={() => statusMutation.mutate(operator)}
                                disabled={statusMutation.isPending || lotteryFeatureDisabled}
                              >
                                {operator.active ? <BlockIcon /> : <CheckCircleIcon />}
                              </IconButton>
                            </span>
                          </Tooltip>
                        ) : null}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
                {(operators.data?.content.length ?? 0) === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography color="text.secondary" textAlign="center" sx={{ py: 5 }}>
                        No lottery operators match the current filters.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={operators.data?.totalElements ?? 0}
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

export function NewLotteryOperatorPage() {
  const navigate = useNavigate();
  const { getValidAccessToken } = useSession();
  const { canManage } = useLotteryOperatorPermissions();
  const featureQuery = useLotteryFeatureEnabled(canManage);
  const lotteryFeatureDisabled = featureQuery.data === false;
  const jurisdictionsQuery = useActiveJurisdictions(canManage && !lotteryFeatureDisabled);

  const mutation = useMutation({
    mutationFn: async (values: LotteryOperatorFormValues) => createLotteryOperator(await getValidAccessToken(), cleanPayload(values)),
    onSuccess: (operator) => navigate(`/lottery/operators/${operator.id}`)
  });

  if (!canManage) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 900 }}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Tooltip title="Back to lottery operators">
          <IconButton component={Link} to="/lottery/operators" aria-label="Back to lottery operators">
            <ArrowBackIcon />
          </IconButton>
        </Tooltip>
        <Box>
          <Typography variant="h5" component="h1">New lottery operator</Typography>
          <Typography color="text.secondary">Create an operator record tied to a tax jurisdiction.</Typography>
        </Box>
      </Stack>

      {lotteryFeatureDisabled ? <FeatureDisabledAlert /> : null}
      {jurisdictionsQuery.isError ? <Alert severity="error">{errorMessage(jurisdictionsQuery.error)}</Alert> : null}

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <LotteryOperatorForm
          defaultValues={emptyLotteryOperatorForm}
          jurisdictions={jurisdictionsQuery.data?.content ?? []}
          submitLabel="Create operator"
          loading={mutation.isPending}
          disabled={lotteryFeatureDisabled || jurisdictionsQuery.isLoading}
          error={mutation.isError ? errorMessage(mutation.error) : undefined}
          onSubmit={(values) => mutation.mutate(values)}
        />
      </Paper>
    </Stack>
  );
}

export function LotteryOperatorDetailPage() {
  const { id } = useParams();
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const { canView, canManage } = useLotteryOperatorPermissions();
  const featureQuery = useLotteryFeatureEnabled(canView);
  const lotteryFeatureDisabled = featureQuery.data === false;
  const jurisdictionsQuery = useActiveJurisdictions(canView && !lotteryFeatureDisabled);

  const operator = useQuery({
    queryKey: ['lottery-operator', id],
    queryFn: async () => getLotteryOperator(await getValidAccessToken(), id ?? ''),
    enabled: canView && Boolean(id) && featureQuery.isSuccess && !lotteryFeatureDisabled
  });

  const updateMutation = useMutation({
    mutationFn: async (values: LotteryOperatorFormValues) => {
      if (!operator.data || !id) {
        throw new Error('Lottery operator is not loaded');
      }
      const payload: LotteryOperatorUpdatePayload = {
        ...cleanPayload(values),
        version: operator.data.version
      };
      return updateLotteryOperator(await getValidAccessToken(), id, payload);
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['lottery-operator', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['lottery-operators'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async () => {
      if (!operator.data || !id) {
        throw new Error('Lottery operator is not loaded');
      }
      return updateLotteryOperatorStatus(await getValidAccessToken(), id, {
        active: !operator.data.active,
        version: operator.data.version
      });
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['lottery-operator', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['lottery-operators'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  if (lotteryFeatureDisabled) {
    return (
      <Stack spacing={2} sx={{ maxWidth: 720 }}>
        <Button component={Link} to="/lottery/operators" startIcon={<ArrowBackIcon />} sx={{ alignSelf: 'flex-start' }}>
          Lottery operators
        </Button>
        <FeatureDisabledAlert />
      </Stack>
    );
  }

  if (operator.isLoading) {
    return <LoadingPanel label="Loading lottery operator" />;
  }

  if (operator.isError) {
    return (
      <Stack spacing={2} sx={{ maxWidth: 720 }}>
        <Button component={Link} to="/lottery/operators" startIcon={<ArrowBackIcon />} sx={{ alignSelf: 'flex-start' }}>
          Lottery operators
        </Button>
        <Alert severity="error">{errorMessage(operator.error)}</Alert>
      </Stack>
    );
  }

  if (!operator.data) {
    return <Alert severity="error">Lottery operator was not found.</Alert>;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 980 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Button component={Link} to="/lottery/operators" startIcon={<ArrowBackIcon />} sx={{ alignSelf: { xs: 'flex-start', sm: 'center' } }}>
          Lottery operators
        </Button>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">{operator.data.name}</Typography>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <Typography color="text.secondary" sx={{ fontFamily: 'monospace' }}>{operator.data.code}</Typography>
            <Typography color="text.secondary">{operator.data.jurisdictionName}</Typography>
            <LotteryStatusChip active={operator.data.active} />
          </Stack>
        </Box>
        {canManage ? (
          <Button
            variant="outlined"
            startIcon={operator.data.active ? <BlockIcon /> : <CheckCircleIcon />}
            onClick={() => statusMutation.mutate()}
            disabled={statusMutation.isPending || updateMutation.isPending}
          >
            {operator.data.active ? 'Deactivate' : 'Activate'}
          </Button>
        ) : null}
      </Stack>

      {updateMutation.isSuccess ? <Alert severity="success">Lottery operator saved.</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      {jurisdictionsQuery.isError ? <Alert severity="error">{errorMessage(jurisdictionsQuery.error)}</Alert> : null}

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6" component="h2">Operator settings</Typography>
            <Typography color="text.secondary">Version {operator.data.version}</Typography>
          </Box>
          <LotteryOperatorForm
            defaultValues={lotteryOperatorFormValues(operator.data)}
            jurisdictions={jurisdictionsQuery.data?.content ?? []}
            submitLabel="Save changes"
            loading={updateMutation.isPending}
            disabled={!canManage || statusMutation.isPending || jurisdictionsQuery.isLoading}
            error={updateMutation.isError ? errorMessage(updateMutation.error) : undefined}
            onSubmit={(values) => updateMutation.mutate(values)}
          />
        </Stack>
      </Paper>
    </Stack>
  );
}
