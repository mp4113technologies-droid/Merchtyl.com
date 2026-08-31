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
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { z } from 'zod';
import {
  createRegister,
  getRegister,
  listRegisters,
  listStores,
  updateRegister,
  updateRegisterStatus,
  type RegisterPayload,
  type RegisterSearchParams,
  type RegisterUpdatePayload
} from '../../api/client';
import type { Register, Store, UserRole } from '../../api/types';
import { compactFilterBarSx } from '../../app/responsive';
import { useSession } from '../../app/session';

type RegisterFilterForm = {
  storeId: string;
  code: string;
  name: string;
  active: '' | 'true' | 'false';
};

const registerSchema = z.object({
  storeId: z.string().uuid('Select a store'),
  code: z.string().trim().min(1, 'Register code is required').max(64, 'Register code must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  locationDescription: z.string().max(1000, 'Location description must be 1000 characters or fewer').optional(),
  active: z.boolean()
});

type RegisterFormValues = z.infer<typeof registerSchema>;
type RegisterTextFieldName = Exclude<keyof RegisterFormValues, 'storeId' | 'active'>;

const emptyRegisterForm: RegisterFormValues = {
  storeId: '',
  code: '',
  name: '',
  locationDescription: '',
  active: true
};

function canViewRegisters(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
}

function canManageRegisters(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function useRegisterPermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  return {
    canView: canViewRegisters(roles),
    canManage: canManageRegisters(roles)
  };
}

function registerFormValues(register: Register): RegisterFormValues {
  return {
    storeId: register.storeId,
    code: register.code,
    name: register.name,
    locationDescription: register.locationDescription ?? '',
    active: register.active
  };
}

function cleanPayload(values: RegisterFormValues): RegisterPayload {
  return {
    storeId: values.storeId,
    code: values.code.trim(),
    name: values.name.trim(),
    locationDescription: optionalText(values.locationDescription),
    active: values.active
  };
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }}>
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function RegisterStatusChip({ active }: { active: boolean }) {
  return (
    <Chip
      label={active ? 'Active' : 'Inactive'}
      color={active ? 'success' : 'default'}
      size="small"
    />
  );
}

function storeLabel(store?: Store) {
  if (!store) {
    return 'Unknown store';
  }
  return `${store.name} (${store.code})`;
}

function useStoreOptions(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['stores', 'register-options'],
    queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100 }),
    enabled
  });
}

function RegisterForm({
  stores,
  defaultValues,
  submitLabel,
  loading,
  error,
  disabled,
  onSubmit
}: {
  stores: Store[];
  defaultValues: RegisterFormValues;
  submitLabel: string;
  loading: boolean;
  error?: string;
  disabled?: boolean;
  onSubmit: (values: RegisterFormValues) => void;
}) {
  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues,
    values: defaultValues
  });

  return (
    <Stack component="form" spacing={3} onSubmit={form.handleSubmit(onSubmit)}>
      {error ? <Alert severity="error">{error}</Alert> : null}
      {disabled ? <Alert severity="info">This account can view registers but cannot change register settings.</Alert> : null}
      {stores.length === 0 ? <Alert severity="warning">Create a store before adding registers.</Alert> : null}

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
                disabled={disabled || stores.length === 0}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message}
                fullWidth
              >
                {stores.map((store) => (
                  <MenuItem key={store.id} value={store.id}>{storeLabel(store)}</MenuItem>
                ))}
              </TextField>
            )}
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <TextInput control={form.control} name="code" label="Code" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={8}>
          <TextInput control={form.control} name="name" label="Name" disabled={disabled} />
        </Grid>
        <Grid item xs={12}>
          <TextInput
            control={form.control}
            name="locationDescription"
            label="Location description"
            multiline
            minRows={3}
            disabled={disabled}
          />
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
        <Button
          type="submit"
          variant="contained"
          startIcon={<SaveIcon />}
          disabled={loading || stores.length === 0}
          sx={{ alignSelf: 'flex-start' }}
        >
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
  control: Control<RegisterFormValues>;
  name: RegisterTextFieldName;
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

export function RegistersPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useRegisterPermissions();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const initialStoreId = searchParams.get('storeId') ?? '';
  const [filters, setFilters] = React.useState<RegisterFilterForm>({
    storeId: initialStoreId,
    code: '',
    name: '',
    active: ''
  });
  const [appliedFilters, setAppliedFilters] = React.useState<RegisterFilterForm>(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const stores = useStoreOptions(canView);

  const storeMap = React.useMemo(() => new Map((stores.data?.content ?? []).map((store) => [store.id, store])), [stores.data?.content]);
  const params = React.useMemo<RegisterSearchParams>(() => ({
    storeId: optionalText(appliedFilters.storeId),
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const registers = useQuery({
    queryKey: ['registers', params],
    queryFn: async () => listRegisters(await getValidAccessToken(), params),
    enabled: canView
  });

  const statusMutation = useMutation({
    mutationFn: async (register: Register) => updateRegisterStatus(await getValidAccessToken(), register.id, {
      active: !register.active,
      version: register.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['registers'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">Registers</Typography>
          <Typography color="text.secondary">Checkout stations assigned to store locations.</Typography>
        </Box>
        <Tooltip title="Refresh registers">
          <IconButton aria-label="Refresh registers" onClick={() => void registers.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        {canManage ? (
          <Button component={Link} to="/registers/new" variant="contained" startIcon={<AddIcon />}>
            New register
          </Button>
        ) : null}
      </Stack>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack
          component="form"
          sx={compactFilterBarSx}
          onSubmit={(event) => {
            event.preventDefault();
            setPage(0);
            setAppliedFilters(filters);
          }}
        >
          <TextField
            select
            label="Store"
            value={filters.storeId}
            onChange={(event) => setFilters((value) => ({ ...value, storeId: event.target.value }))}
          >
            <MenuItem value="">All stores</MenuItem>
            {(stores.data?.content ?? []).map((store) => (
              <MenuItem key={store.id} value={store.id}>{storeLabel(store)}</MenuItem>
            ))}
          </TextField>
          <TextField label="Code" value={filters.code} onChange={(event) => setFilters((value) => ({ ...value, code: event.target.value }))} />
          <TextField label="Name" value={filters.name} onChange={(event) => setFilters((value) => ({ ...value, name: event.target.value }))} />
          <TextField
            select
            label="Status"
            value={filters.active}
            onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as RegisterFilterForm['active'] }))}
          >
            <MenuItem value="">Any</MenuItem>
            <MenuItem value="true">Active</MenuItem>
            <MenuItem value="false">Inactive</MenuItem>
          </TextField>
          <Button type="submit" variant="outlined" startIcon={<SearchIcon />}>
            Search
          </Button>
        </Stack>
      </Paper>

      {stores.isError ? <Alert severity="error">{errorMessage(stores.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}

      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ p: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Register list</Typography>
          <Chip label={`${registers.data?.totalElements ?? 0} registers`} size="small" />
        </Stack>
        <Divider />
        {registers.isLoading ? <LoadingPanel label="Loading registers" /> : null}
        {registers.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(registers.error)}</Alert> : null}
        {!registers.isLoading && !registers.isError ? (
          <>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Register</TableCell>
                  <TableCell>Store</TableCell>
                  <TableCell>Location</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(registers.data?.content ?? []).map((register) => (
                  <TableRow key={register.id} hover>
                    <TableCell>
                      <Button component={Link} to={`/registers/${register.id}`} sx={{ px: 0, justifyContent: 'flex-start', textAlign: 'left' }}>
                        <Stack alignItems="flex-start">
                          <Typography fontWeight={700}>{register.name}</Typography>
                          <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{register.code}</Typography>
                        </Stack>
                      </Button>
                    </TableCell>
                    <TableCell>{storeLabel(storeMap.get(register.storeId))}</TableCell>
                    <TableCell>{register.locationDescription ?? 'No location set'}</TableCell>
                    <TableCell><RegisterStatusChip active={register.active} /></TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Tooltip title="Open register">
                          <IconButton component={Link} to={`/registers/${register.id}`} aria-label={`Open ${register.name}`}>
                            <EditIcon />
                          </IconButton>
                        </Tooltip>
                        {canManage ? (
                          <Tooltip title={register.active ? 'Deactivate register' : 'Activate register'}>
                            <span>
                              <IconButton
                                aria-label={register.active ? `Deactivate ${register.name}` : `Activate ${register.name}`}
                                onClick={() => statusMutation.mutate(register)}
                                disabled={statusMutation.isPending}
                              >
                                {register.active ? <BlockIcon /> : <CheckCircleIcon />}
                              </IconButton>
                            </span>
                          </Tooltip>
                        ) : null}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
                {(registers.data?.content.length ?? 0) === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography color="text.secondary" textAlign="center" sx={{ py: 5 }}>
                        No registers match the current filters.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={registers.data?.totalElements ?? 0}
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

export function NewRegisterPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { getValidAccessToken } = useSession();
  const { canManage } = useRegisterPermissions();
  const stores = useStoreOptions(canManage);
  const defaultValues = React.useMemo<RegisterFormValues>(() => ({
    ...emptyRegisterForm,
    storeId: searchParams.get('storeId') ?? stores.data?.content[0]?.id ?? ''
  }), [searchParams, stores.data?.content]);

  const mutation = useMutation({
    mutationFn: async (values: RegisterFormValues) => createRegister(await getValidAccessToken(), cleanPayload(values)),
    onSuccess: (register) => navigate(`/registers/${register.id}`)
  });

  if (!canManage) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 900 }}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Tooltip title="Back to registers">
          <IconButton component={Link} to="/registers" aria-label="Back to registers">
            <ArrowBackIcon />
          </IconButton>
        </Tooltip>
        <Box>
          <Typography variant="h5" component="h1">New register</Typography>
          <Typography color="text.secondary">Create a checkout station for a store.</Typography>
        </Box>
      </Stack>

      {stores.isLoading ? <LoadingPanel label="Loading stores" /> : null}
      {stores.isError ? <Alert severity="error">{errorMessage(stores.error)}</Alert> : null}
      {!stores.isLoading && !stores.isError ? (
        <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
          <RegisterForm
            stores={stores.data?.content ?? []}
            defaultValues={defaultValues}
            submitLabel="Create register"
            loading={mutation.isPending}
            error={mutation.isError ? errorMessage(mutation.error) : undefined}
            onSubmit={(values) => mutation.mutate(values)}
          />
        </Paper>
      ) : null}
    </Stack>
  );
}

export function RegisterDetailPage() {
  const { id } = useParams();
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const { canView, canManage } = useRegisterPermissions();
  const stores = useStoreOptions(canView);

  const register = useQuery({
    queryKey: ['register', id],
    queryFn: async () => getRegister(await getValidAccessToken(), id ?? ''),
    enabled: canView && Boolean(id)
  });

  const storeMap = React.useMemo(() => new Map((stores.data?.content ?? []).map((store) => [store.id, store])), [stores.data?.content]);

  const updateMutation = useMutation({
    mutationFn: async (values: RegisterFormValues) => {
      if (!register.data || !id) {
        throw new Error('Register is not loaded');
      }
      const payload: RegisterUpdatePayload = {
        ...cleanPayload(values),
        version: register.data.version
      };
      return updateRegister(await getValidAccessToken(), id, payload);
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['register', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['registers'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async () => {
      if (!register.data || !id) {
        throw new Error('Register is not loaded');
      }
      return updateRegisterStatus(await getValidAccessToken(), id, {
        active: !register.data.active,
        version: register.data.version
      });
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['register', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['registers'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  if (register.isLoading || stores.isLoading) {
    return <LoadingPanel label="Loading register" />;
  }

  if (register.isError) {
    return (
      <Stack spacing={2} sx={{ maxWidth: 720 }}>
        <Button component={Link} to="/registers" startIcon={<ArrowBackIcon />} sx={{ alignSelf: 'flex-start' }}>
          Registers
        </Button>
        <Alert severity="error">{errorMessage(register.error)}</Alert>
      </Stack>
    );
  }

  if (!register.data) {
    return <Alert severity="error">Register was not found.</Alert>;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 980 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Button component={Link} to="/registers" startIcon={<ArrowBackIcon />} sx={{ alignSelf: { xs: 'flex-start', sm: 'center' } }}>
          Registers
        </Button>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">{register.data.name}</Typography>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <Typography color="text.secondary" sx={{ fontFamily: 'monospace' }}>{register.data.code}</Typography>
            <Typography color="text.secondary">{storeLabel(storeMap.get(register.data.storeId))}</Typography>
            <RegisterStatusChip active={register.data.active} />
          </Stack>
        </Box>
        {canManage ? (
          <Button
            variant="outlined"
            startIcon={register.data.active ? <BlockIcon /> : <CheckCircleIcon />}
            onClick={() => statusMutation.mutate()}
            disabled={statusMutation.isPending || updateMutation.isPending}
          >
            {register.data.active ? 'Deactivate' : 'Activate'}
          </Button>
        ) : null}
      </Stack>

      {stores.isError ? <Alert severity="error">{errorMessage(stores.error)}</Alert> : null}
      {updateMutation.isSuccess ? <Alert severity="success">Register saved.</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6" component="h2">Register settings</Typography>
            <Typography color="text.secondary">Version {register.data.version}</Typography>
          </Box>
          <RegisterForm
            stores={stores.data?.content ?? []}
            defaultValues={registerFormValues(register.data)}
            submitLabel="Save changes"
            loading={updateMutation.isPending}
            disabled={!canManage || statusMutation.isPending}
            error={updateMutation.isError ? errorMessage(updateMutation.error) : undefined}
            onSubmit={(values) => updateMutation.mutate(values)}
          />
        </Stack>
      </Paper>
    </Stack>
  );
}
