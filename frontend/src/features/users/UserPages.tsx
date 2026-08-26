import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import KeyIcon from '@mui/icons-material/Key';
import RefreshIcon from '@mui/icons-material/Refresh';
import SaveIcon from '@mui/icons-material/Save';
import SearchIcon from '@mui/icons-material/Search';
import {
  Alert,
  Box,
  Button,
  Checkbox,
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
import { merchantUserKeys } from './merchantUserKeys';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import { PASSWORD_POLICY_HELP, passwordValueSchema, validPassword } from '../auth/passwordPolicy';
import {
  createUser,
  disableUser,
  getUser,
  listAssignableStores,
  listRegisters,
  listRoles,
  listUsers,
  reactivateUser,
  resetUserPassword,
  updateUser,
  updateUserRoles,
  type UserAdminCreatePayload,
  type UserAdminSearchParams,
  type UserAdminUpdatePayload
} from '../../api/client';
import type { AssignedStore, Register, RoleAdmin, Store, UserAdmin, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

const employeeRoleOptions = ['STORE_MANAGER', 'CASHIER'] satisfies UserRole[];
type EmployeeUserRole = typeof employeeRoleOptions[number];
const tenantRoleOptions = ['STORE_MANAGER', 'MANAGER', 'CASHIER'] as const;
type TenantUserRole = typeof tenantRoleOptions[number];

function isTenantUserRole(role: UserRole): role is TenantUserRole {
  return (tenantRoleOptions as readonly UserRole[]).includes(role);
}

function isEmployeeRole(role: UserRole): role is EmployeeUserRole {
  return (employeeRoleOptions as readonly UserRole[]).includes(role);
}

function normalizeEmployeeRole(role: TenantUserRole): EmployeeUserRole {
  return role === 'MANAGER' ? 'STORE_MANAGER' : role;
}

type UserFilterForm = {
  search: string;
  role: '' | UserRole;
  storeId: string;
  status: '';
  enabled: '' | 'true' | 'false';
};

const userSchema = z.object({
  email: z.string().trim().email('Enter a valid email').max(320, 'Email must be 320 characters or fewer'),
  displayName: z.string().trim().min(1, 'Display name is required').max(160, 'Display name must be 160 characters or fewer'),
  password: passwordValueSchema.optional(),
  enabled: z.boolean(),
  locked: z.boolean(),
  roles: z.array(z.enum(['STORE_MANAGER', 'CASHIER'])).min(1, 'Select one role').max(1, 'Select one role'),
  storeIds: z.array(z.string().uuid()).min(1, 'Select at least one assigned store'),
  registerIds: z.array(z.string().uuid())
});

type UserFormValues = z.infer<typeof userSchema>;

const emptyUserForm: UserFormValues = {
  email: '',
  displayName: '',
  password: '',
  enabled: true,
  locked: false,
  roles: ['CASHIER'],
  storeIds: [],
  registerIds: []
};

function canViewUsers(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function canManageUsers(roles: UserRole[]) {
  return roles.includes('OWNER') || roles.includes('TENANT_OWNER');
}

function useUserPermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  return {
    canView: canViewUsers(roles),
    canManage: canManageUsers(roles)
  };
}

function userFormValues(user: UserAdmin): UserFormValues {
  return {
    email: user.email,
    displayName: user.displayName,
    password: '',
    enabled: user.enabled,
    locked: user.locked,
    roles: user.roles.filter(isTenantUserRole).map(normalizeEmployeeRole),
    storeIds: user.storeIds,
    registerIds: user.registerIds
  };
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function storeLabel(store?: Store) {
  return store ? `${store.name} (${store.code})` : 'Unknown store';
}

function assignmentSummary(user: UserAdmin) {
  const active = (user.storeAssignments ?? []).filter((assignment) => assignment.active);
  if (active.length === 0) {
    return 'No active stores';
  }
  return active.map((assignment) => `${assignment.storeName} (${assignment.assignmentRole})`).join(', ');
}

function createdByLabel(user: UserAdmin) {
  if (!user.createdByUserId || !user.createdByRole) {
    return 'Historical';
  }
  return `${user.createdByRole} ${user.createdByUserId.slice(0, 8)}`;
}

function assignableStoreToStore(store: AssignedStore): Store {
  return {
    id: store.storeId,
    code: store.storeCode,
    name: store.storeName,
    legalName: null,
    countryCode: '',
    administrativeAreaCode: store.administrativeDivisionCode,
    address: '',
    phone: null,
    email: null,
    currencyCode: '',
    locale: '',
    timezone: '',
    pricesIncludeTax: false,
    negativeStockAllowed: false,
    active: true,
    createdAt: '',
    updatedAt: '',
    version: 0
  };
}

function registerLabel(register?: Register, storeMap?: Map<string, Store>) {
  if (!register) {
    return 'Unknown register';
  }
  const store = storeMap?.get(register.storeId);
  return `${register.name} (${register.code})${store ? `, ${store.name}` : ''}`;
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }}>
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function UserStatusChip({ user }: { user: UserAdmin }) {
  if (!user.enabled) {
    return <Chip label="Inactive" size="small" />;
  }
  if (user.locked) {
    return <Chip label="Locked" color="warning" size="small" />;
  }
  return <Chip label="Active" color="success" size="small" />;
}

function RolesChips({ roles }: { roles: UserRole[] }) {
  return (
    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
      {roles.map((role) => <Chip key={role} label={role} size="small" />)}
    </Stack>
  );
}

function useAssignmentOptions(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  const stores = useQuery({
    queryKey: merchantUserKeys.assignableStores('form'),
    queryFn: async () => listAssignableStores(await getValidAccessToken()),
    enabled
  });
  const registers = useQuery({
    queryKey: ['registers', 'user-assignment-options'],
    queryFn: async () => listRegisters(await getValidAccessToken(), { page: 0, size: 100 }),
    enabled
  });
  const roles = useQuery({
    queryKey: ['roles'],
    queryFn: async () => listRoles(await getValidAccessToken()),
    enabled
  });
  return { stores, registers, roles };
}

function toggleItem(items: string[], item: string) {
  return items.includes(item) ? items.filter((value) => value !== item) : [...items, item];
}

function AssignmentControls({
  values,
  stores,
  registers,
  roles,
  disabled,
  onChange
}: {
  values: UserFormValues;
  stores: Store[];
  registers: Register[];
  roles: RoleAdmin[];
  disabled?: boolean;
  onChange: (values: Partial<UserFormValues>) => void;
}) {
  const storeMap = React.useMemo(() => new Map(stores.map((store) => [store.id, store])), [stores]);
  const visibleRegisters = values.storeIds.length === 0
    ? registers
    : registers.filter((register) => values.storeIds.includes(register.storeId));

  return (
    <Grid container spacing={3}>
      <Grid item xs={12} md={4}>
        <Typography variant="subtitle2" gutterBottom>Roles</Typography>
        <Stack spacing={0.5}>
          {(roles.length > 0 ? roles.map((role) => role.name).filter(isEmployeeRole) : employeeRoleOptions).map((role) => (
            <FormControlLabel
              key={role}
              control={(
                <Checkbox
                  checked={values.roles.includes(role)}
                  disabled={disabled}
                  onChange={() => onChange({ roles: [role] })}
                />
              )}
              label={role}
            />
          ))}
        </Stack>
      </Grid>
      <Grid item xs={12} md={4}>
        <Typography variant="subtitle2" gutterBottom>Stores</Typography>
        <Stack spacing={0.5}>
          {stores.length === 0 ? <Typography color="text.secondary">No stores available</Typography> : null}
          {stores.map((store) => (
            <FormControlLabel
              key={store.id}
              control={(
                <Checkbox
                  checked={values.storeIds.includes(store.id)}
                  disabled={disabled}
                  onChange={() => {
                    const storeIds = toggleItem(values.storeIds, store.id);
                    const registerIds = values.registerIds.filter((registerId) => {
                      const register = registers.find((candidate) => candidate.id === registerId);
                      return !register || storeIds.length === 0 || storeIds.includes(register.storeId);
                    });
                    onChange({ storeIds, registerIds });
                  }}
                />
              )}
              label={storeLabel(store)}
            />
          ))}
        </Stack>
      </Grid>
      <Grid item xs={12} md={4}>
        <Typography variant="subtitle2" gutterBottom>Registers</Typography>
        <Stack spacing={0.5}>
          {visibleRegisters.length === 0 ? <Typography color="text.secondary">No registers available</Typography> : null}
          {visibleRegisters.map((register) => (
            <FormControlLabel
              key={register.id}
              control={(
                <Checkbox
                  checked={values.registerIds.includes(register.id)}
                  disabled={disabled}
                  onChange={() => onChange({ registerIds: toggleItem(values.registerIds, register.id) })}
                />
              )}
              label={registerLabel(register, storeMap)}
            />
          ))}
        </Stack>
      </Grid>
    </Grid>
  );
}

function UserForm({
  defaultValues,
  stores,
  registers,
  roles,
  mode,
  loading,
  error,
  disabled,
  onSubmit
}: {
  defaultValues: UserFormValues;
  stores: Store[];
  registers: Register[];
  roles: RoleAdmin[];
  mode: 'create' | 'edit';
  loading: boolean;
  error?: string;
  disabled?: boolean;
  onSubmit: (values: UserFormValues) => void;
}) {
  const form = useForm<UserFormValues>({
    resolver: zodResolver(mode === 'create'
      ? userSchema.extend({ password: passwordValueSchema })
      : userSchema),
    defaultValues,
    values: defaultValues
  });
  const values = form.watch();

  return (
    <Stack component="form" spacing={3} onSubmit={form.handleSubmit(onSubmit)}>
      {error ? <Alert severity="error">{error}</Alert> : null}
      {disabled ? <Alert severity="info">This account can view users but cannot change user settings.</Alert> : null}

      <Grid container spacing={2}>
        <Grid item xs={12} sm={6}>
          <Controller
            name="email"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} label="Email" disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )}
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <Controller
            name="displayName"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} label="Display name" disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )}
          />
        </Grid>
        {mode === 'create' ? (
          <Grid item xs={12} sm={6}>
            <Controller
              name="password"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField
                  {...field}
                  type="password"
                  label="Initial password"
                  disabled={disabled}
                  error={Boolean(fieldState.error)}
                  helperText={fieldState.error?.message}
                  fullWidth
                />
              )}
            />
          </Grid>
        ) : null}
      </Grid>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <Controller
          name="enabled"
          control={form.control}
          render={({ field }) => (
            <FormControlLabel
              control={<Switch checked={field.value} disabled={disabled || mode === 'edit'} onChange={(_, checked) => field.onChange(checked)} />}
              label="Active"
            />
          )}
        />
        <Controller
          name="locked"
          control={form.control}
          render={({ field }) => (
            <FormControlLabel
              control={<Switch checked={field.value} disabled={disabled} onChange={(_, checked) => field.onChange(checked)} />}
              label="Locked"
            />
          )}
        />
      </Stack>

      <AssignmentControls
        values={values}
        stores={stores}
        registers={registers}
        roles={roles}
        disabled={disabled}
        onChange={(nextValues) => {
          Object.entries(nextValues).forEach(([key, value]) => {
            form.setValue(key as keyof UserFormValues, value as never, { shouldDirty: true, shouldValidate: true });
          });
        }}
      />
      {form.formState.errors.roles ? <Alert severity="error">{form.formState.errors.roles.message}</Alert> : null}
      {form.formState.errors.storeIds ? <Alert severity="error">{form.formState.errors.storeIds.message}</Alert> : null}

      {!disabled ? (
        <Button type="submit" variant="contained" startIcon={<SaveIcon />} disabled={loading} sx={{ alignSelf: 'flex-start' }}>
          {mode === 'create' ? 'Create user' : 'Save changes'}
        </Button>
      ) : null}
    </Stack>
  );
}

export function UsersPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useUserPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState<UserFilterForm>({ search: '', role: '', storeId: '', status: '', enabled: '' });
  const [appliedFilters, setAppliedFilters] = React.useState<UserFilterForm>(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const storeOptions = useQuery({
    queryKey: merchantUserKeys.assignableStores('filters'),
    queryFn: async () => listAssignableStores(await getValidAccessToken()),
    enabled: canView
  });

  const params = React.useMemo<UserAdminSearchParams>(() => ({
    search: optionalText(appliedFilters.search),
    role: appliedFilters.role,
    storeId: optionalText(appliedFilters.storeId),
    status: optionalText(appliedFilters.status),
    enabled: appliedFilters.enabled === '' ? '' : appliedFilters.enabled === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const users = useQuery({
    queryKey: merchantUserKeys.list(params),
    queryFn: async () => listUsers(await getValidAccessToken(), params),
    enabled: canView
  });

  const statusMutation = useMutation({
    mutationFn: async (user: UserAdmin) => user.enabled
      ? disableUser(await getValidAccessToken(), user.id, user.version)
      : reactivateUser(await getValidAccessToken(), user.id, user.version),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: merchantUserKeys.all });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">Employees</Typography>
          <Typography color="text.secondary">Tenant-scoped managers, cashiers, and store assignments.</Typography>
        </Box>
        <Tooltip title="Refresh users">
          <IconButton aria-label="Refresh users" onClick={() => void users.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        {canManage ? (
          <Button component={Link} to="/users/new" variant="contained" startIcon={<AddIcon />}>
            New user
          </Button>
        ) : null}
      </Stack>

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
          <TextField label="Name or email" value={filters.search} onChange={(event) => setFilters((value) => ({ ...value, search: event.target.value }))} />
          <TextField
            select
            label="Store"
            value={filters.storeId}
            onChange={(event) => setFilters((value) => ({ ...value, storeId: event.target.value }))}
            sx={{ minWidth: 190 }}
          >
            <MenuItem value="">Any</MenuItem>
            {(storeOptions.data ?? []).map((store) => <MenuItem key={store.storeId} value={store.storeId}>{`${store.storeName} (${store.storeCode})`}</MenuItem>)}
          </TextField>
          <TextField
            select
            label="Role"
            value={filters.role}
            onChange={(event) => setFilters((value) => ({ ...value, role: event.target.value as UserFilterForm['role'] }))}
            sx={{ minWidth: 150 }}
          >
            <MenuItem value="">Any</MenuItem>
            {employeeRoleOptions.map((role) => <MenuItem key={role} value={role}>{role}</MenuItem>)}
          </TextField>
          <TextField
            select
            label="Status"
            value={filters.enabled}
            onChange={(event) => setFilters((value) => ({ ...value, enabled: event.target.value as UserFilterForm['enabled'] }))}
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
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Employee list</Typography>
          <Chip label={`${users.data?.totalElements ?? 0} employees`} size="small" />
        </Stack>
        <Divider />
        {users.isLoading ? <LoadingPanel label="Loading users" /> : null}
        {users.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(users.error)}</Alert> : null}
        {!users.isLoading && !users.isError ? (
          <>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>User</TableCell>
                  <TableCell>Roles</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Assigned stores</TableCell>
                  <TableCell>Created By</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(users.data?.content ?? []).map((user) => (
                  <TableRow key={user.id} hover>
                    <TableCell>
                      <Typography component={Link} to={`/users/${user.id}`} sx={{ color: 'primary.main', textDecoration: 'none', fontWeight: 600 }}>
                        {user.displayName}
                      </Typography>
                      <Typography color="text.secondary" variant="body2">{user.email}</Typography>
                    </TableCell>
                    <TableCell><RolesChips roles={user.roles} /></TableCell>
                    <TableCell><UserStatusChip user={user} /></TableCell>
                    <TableCell>{assignmentSummary(user)}</TableCell>
                    <TableCell>{createdByLabel(user)}</TableCell>
                    <TableCell align="right">
                      {canManage ? (
                        <Tooltip title={user.enabled ? 'Deactivate user' : 'Activate user'}>
                          <IconButton
                            aria-label={`${user.enabled ? 'Deactivate' : 'Activate'} ${user.displayName}`}
                            onClick={() => statusMutation.mutate(user)}
                            disabled={statusMutation.isPending}
                          >
                            {user.enabled ? <BlockIcon /> : <CheckCircleIcon />}
                          </IconButton>
                        </Tooltip>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))}
                {(users.data?.content ?? []).length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6}>
                      <Typography color="text.secondary" align="center" sx={{ py: 4 }}>No users found.</Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={users.data?.totalElements ?? 0}
              page={page}
              rowsPerPage={size}
              onPageChange={(_, nextPage) => setPage(nextPage)}
              onRowsPerPageChange={(event) => {
                setSize(Number(event.target.value));
                setPage(0);
              }}
            />
          </>
        ) : null}
      </TableContainer>
    </Stack>
  );
}

export function NewUserPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useUserPermissions();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const options = useAssignmentOptions(canView);
  const mutation = useMutation({
    mutationFn: async (values: UserFormValues) => {
      const payload: UserAdminCreatePayload = {
        email: values.email.trim().toLowerCase(),
        displayName: values.displayName.trim(),
        password: values.password ?? '',
        roles: values.roles,
        storeIds: values.storeIds,
        registerIds: values.registerIds,
        enabled: values.enabled,
        locked: values.locked
      };
      return createUser(await getValidAccessToken(), payload);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: merchantUserKeys.all });
      navigate('/users');
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const loadingOptions = options.stores.isLoading || options.registers.isLoading || options.roles.isLoading;
  return (
    <Stack spacing={3}>
      <Button component={Link} to="/users" startIcon={<ArrowBackIcon />} sx={{ alignSelf: 'flex-start' }}>Users</Button>
      <Box>
        <Typography variant="h5" component="h1">New user</Typography>
        <Typography color="text.secondary">Create an account and assign access.</Typography>
      </Box>
      {loadingOptions ? <LoadingPanel label="Loading access options" /> : (
        <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
          <UserForm
            defaultValues={emptyUserForm}
            stores={(options.stores.data ?? []).map(assignableStoreToStore)}
            registers={options.registers.data?.content ?? []}
            roles={options.roles.data ?? []}
            mode="create"
            loading={mutation.isPending}
            error={mutation.isError ? errorMessage(mutation.error) : undefined}
            disabled={!canManage}
            onSubmit={(values) => mutation.mutate(values)}
          />
        </Paper>
      )}
    </Stack>
  );
}

export function UserDetailPage() {
  const { id } = useParams();
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useUserPermissions();
  const queryClient = useQueryClient();
  const options = useAssignmentOptions(canView);
  const [savedMessage, setSavedMessage] = React.useState('');
  const [newPassword, setNewPassword] = React.useState('');

  const user = useQuery({
    queryKey: merchantUserKeys.detail(id),
    queryFn: async () => getUser(await getValidAccessToken(), id ?? ''),
    enabled: canView && Boolean(id)
  });

  const updateMutation = useMutation({
    mutationFn: async (values: UserFormValues) => {
      if (!user.data || !id) {
        throw new Error('User is not loaded');
      }
      const token = await getValidAccessToken();
      const payload: UserAdminUpdatePayload = {
        email: values.email.trim().toLowerCase(),
        displayName: values.displayName.trim(),
        locked: values.locked,
        storeIds: values.storeIds,
        registerIds: values.registerIds,
        version: user.data.version
      };
      const updated = await updateUser(token, id, payload);
      return updateUserRoles(token, id, {
        roles: values.roles,
        storeIds: values.storeIds,
        registerIds: values.registerIds,
        version: updated.version
      });
    },
    onSuccess: async (updated) => {
      setSavedMessage('User saved.');
      await queryClient.setQueryData(['users', id], updated);
      await queryClient.invalidateQueries({ queryKey: merchantUserKeys.all });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async () => {
      if (!user.data || !id) {
        throw new Error('User is not loaded');
      }
      return user.data.enabled
        ? disableUser(await getValidAccessToken(), id, user.data.version)
        : reactivateUser(await getValidAccessToken(), id, user.data.version);
    },
    onSuccess: async (updated) => {
      setSavedMessage(updated.enabled ? 'User activated.' : 'User deactivated.');
      await queryClient.setQueryData(['users', id], updated);
      await queryClient.invalidateQueries({ queryKey: merchantUserKeys.all });
    }
  });

  const resetMutation = useMutation({
    mutationFn: async () => {
      if (!user.data || !id) {
        throw new Error('User is not loaded');
      }
      return resetUserPassword(await getValidAccessToken(), id, {
        newPassword,
        version: user.data.version
      });
    },
    onSuccess: async (updated) => {
      setNewPassword('');
      setSavedMessage('Password reset.');
      await queryClient.setQueryData(['users', id], updated);
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }
  if (user.isLoading || options.stores.isLoading || options.registers.isLoading || options.roles.isLoading) {
    return <LoadingPanel label="Loading user" />;
  }
  if (user.isError) {
    return <Alert severity="error">{errorMessage(user.error)}</Alert>;
  }
  if (!user.data) {
    return <Alert severity="error">User not found</Alert>;
  }

  return (
    <Stack spacing={3}>
      <Button component={Link} to="/users" startIcon={<ArrowBackIcon />} sx={{ alignSelf: 'flex-start' }}>Users</Button>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">{user.data.displayName}</Typography>
          <Typography color="text.secondary">{user.data.email}</Typography>
        </Box>
        <UserStatusChip user={user.data} />
        {canManage ? (
          <Button
            variant="outlined"
            color={user.data.enabled ? 'error' : 'primary'}
            startIcon={user.data.enabled ? <BlockIcon /> : <CheckCircleIcon />}
            disabled={statusMutation.isPending}
            onClick={() => statusMutation.mutate()}
          >
            {user.data.enabled ? 'Deactivate' : 'Activate'}
          </Button>
        ) : null}
      </Stack>

      {savedMessage ? <Alert severity="success" onClose={() => setSavedMessage('')}>{savedMessage}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      {resetMutation.isError ? <Alert severity="error">{errorMessage(resetMutation.error)}</Alert> : null}

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <UserForm
          defaultValues={userFormValues(user.data)}
          stores={(options.stores.data ?? []).map(assignableStoreToStore)}
          registers={options.registers.data?.content ?? []}
          roles={options.roles.data ?? []}
          mode="edit"
          loading={updateMutation.isPending}
          error={updateMutation.isError ? errorMessage(updateMutation.error) : undefined}
          disabled={!canManage}
          onSubmit={(values) => updateMutation.mutate(values)}
        />
      </Paper>

      {canManage ? (
        <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
          <Stack spacing={2} sx={{ maxWidth: 520 }}>
            <Typography variant="h6" component="h2">Password reset</Typography>
            <TextField
              type="password"
              label="New password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              error={newPassword.length > 0 && !validPassword(newPassword)}
              helperText={newPassword.length > 0 && !validPassword(newPassword) ? PASSWORD_POLICY_HELP : undefined}
            />
            <Button
              variant="outlined"
              startIcon={<KeyIcon />}
              disabled={resetMutation.isPending || !validPassword(newPassword)}
              onClick={() => resetMutation.mutate()}
              sx={{ alignSelf: 'flex-start' }}
            >
              Reset password
            </Button>
          </Stack>
        </Paper>
      ) : null}
    </Stack>
  );
}

export function RolesPage() {
  const { getValidAccessToken } = useSession();
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const canView = canViewUsers(roles);
  const roleQuery = useQuery({
    queryKey: ['roles'],
    queryFn: async () => listRoles(await getValidAccessToken()),
    enabled: canView
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h5" component="h1">Roles</Typography>
        <Typography color="text.secondary">System roles and their granted permissions.</Typography>
      </Box>
      {roleQuery.isLoading ? <LoadingPanel label="Loading roles" /> : null}
      {roleQuery.isError ? <Alert severity="error">{errorMessage(roleQuery.error)}</Alert> : null}
      <Grid container spacing={2}>
        {(roleQuery.data ?? []).map((role) => (
          <Grid item xs={12} md={4} key={role.id}>
            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3, height: '100%' }}>
              <Stack spacing={2}>
                <Box>
                  <Typography variant="h6">{role.name}</Typography>
                  <Typography color="text.secondary">{role.description}</Typography>
                </Box>
                <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                  {role.permissions.map((permission) => (
                    <Chip key={permission} label={permission} size="small" />
                  ))}
                </Stack>
              </Stack>
            </Paper>
          </Grid>
        ))}
      </Grid>
    </Stack>
  );
}
