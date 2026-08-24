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
  createSupplier,
  getSupplier,
  listSuppliers,
  updateSupplier,
  updateSupplierStatus,
  type SupplierPayload,
  type SupplierSearchParams,
  type SupplierUpdatePayload
} from '../../api/client';
import type { Supplier, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

type SupplierFilterForm = {
  code: string;
  name: string;
  contactName: string;
  email: string;
  active: '' | 'true' | 'false';
};

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const supplierSchema = z.object({
  code: z.string().trim().min(1, 'Code is required').max(64, 'Code must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  contactName: z.string().max(180, 'Contact name must be 180 characters or fewer').optional(),
  phone: z.string().max(40, 'Phone must be 40 characters or fewer').optional(),
  email: z.string().trim().max(320, 'Email must be 320 characters or fewer')
    .refine((value) => value === '' || emailPattern.test(value), 'Enter a valid email')
    .optional(),
  address: z.string().max(1000, 'Address must be 1000 characters or fewer').optional(),
  notes: z.string().max(2000, 'Notes must be 2000 characters or fewer').optional(),
  active: z.boolean()
});

type SupplierFormValues = z.infer<typeof supplierSchema>;
type SupplierTextFieldName = Exclude<keyof SupplierFormValues, 'active'>;

const emptySupplierForm: SupplierFormValues = {
  code: '',
  name: '',
  contactName: '',
  phone: '',
  email: '',
  address: '',
  notes: '',
  active: true
};

function canViewSuppliers(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
}

function canManageSuppliers(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function useSupplierPermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  return {
    canView: canViewSuppliers(roles),
    canManage: canManageSuppliers(roles)
  };
}

function supplierFormValues(supplier: Supplier): SupplierFormValues {
  return {
    code: supplier.code,
    name: supplier.name,
    contactName: supplier.contactName ?? '',
    phone: supplier.phone ?? '',
    email: supplier.email ?? '',
    address: supplier.address ?? '',
    notes: supplier.notes ?? '',
    active: supplier.active
  };
}

function cleanPayload(values: SupplierFormValues): SupplierPayload {
  return {
    code: values.code.trim().toUpperCase(),
    name: values.name.trim(),
    contactName: optionalText(values.contactName),
    phone: optionalText(values.phone),
    email: optionalText(values.email)?.toLowerCase(),
    address: optionalText(values.address),
    notes: optionalText(values.notes),
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

function SupplierStatusChip({ active }: { active: boolean }) {
  return <Chip label={active ? 'Active' : 'Inactive'} color={active ? 'success' : 'default'} size="small" />;
}

function SupplierForm({
  defaultValues,
  submitLabel,
  loading,
  error,
  disabled,
  onSubmit
}: {
  defaultValues: SupplierFormValues;
  submitLabel: string;
  loading: boolean;
  error?: string;
  disabled?: boolean;
  onSubmit: (values: SupplierFormValues) => void;
}) {
  const form = useForm<SupplierFormValues>({
    resolver: zodResolver(supplierSchema),
    defaultValues,
    values: defaultValues
  });

  return (
    <Stack component="form" spacing={3} onSubmit={form.handleSubmit(onSubmit)}>
      {error ? <Alert severity="error">{error}</Alert> : null}
      {disabled ? <Alert severity="info">This account can view suppliers but cannot change supplier records.</Alert> : null}

      <Grid container spacing={2}>
        <Grid item xs={12} sm={4}>
          <TextInput control={form.control} name="code" label="Code" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={8}>
          <TextInput control={form.control} name="name" label="Name" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6}>
          <TextInput control={form.control} name="contactName" label="Contact name" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6}>
          <TextInput control={form.control} name="phone" label="Phone" disabled={disabled} />
        </Grid>
        <Grid item xs={12}>
          <TextInput control={form.control} name="email" label="Email" disabled={disabled} />
        </Grid>
        <Grid item xs={12}>
          <TextInput control={form.control} name="address" label="Address" multiline minRows={3} disabled={disabled} />
        </Grid>
        <Grid item xs={12}>
          <TextInput control={form.control} name="notes" label="Notes" multiline minRows={4} disabled={disabled} />
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
  control: Control<SupplierFormValues>;
  name: SupplierTextFieldName;
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

export function SuppliersPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useSupplierPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState<SupplierFilterForm>({
    code: '',
    name: '',
    contactName: '',
    email: '',
    active: ''
  });
  const [appliedFilters, setAppliedFilters] = React.useState<SupplierFilterForm>(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);

  const params = React.useMemo<SupplierSearchParams>(() => ({
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    contactName: optionalText(appliedFilters.contactName),
    email: optionalText(appliedFilters.email),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const suppliers = useQuery({
    queryKey: ['suppliers', params],
    queryFn: async () => listSuppliers(await getValidAccessToken(), params),
    enabled: canView
  });

  const statusMutation = useMutation({
    mutationFn: async (supplier: Supplier) => updateSupplierStatus(await getValidAccessToken(), supplier.id, {
      active: !supplier.active,
      version: supplier.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">Suppliers</Typography>
          <Typography color="text.secondary">Vendor records used for product purchasing and replenishment.</Typography>
        </Box>
        <Tooltip title="Refresh suppliers">
          <IconButton aria-label="Refresh suppliers" onClick={() => void suppliers.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        {canManage ? (
          <Button component={Link} to="/suppliers/new" variant="contained" startIcon={<AddIcon />}>
            New supplier
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
          <TextField label="Code" value={filters.code} onChange={(event) => setFilters((value) => ({ ...value, code: event.target.value }))} />
          <TextField label="Name" value={filters.name} onChange={(event) => setFilters((value) => ({ ...value, name: event.target.value }))} />
          <TextField
            label="Contact"
            value={filters.contactName}
            onChange={(event) => setFilters((value) => ({ ...value, contactName: event.target.value }))}
          />
          <TextField label="Email" value={filters.email} onChange={(event) => setFilters((value) => ({ ...value, email: event.target.value }))} />
          <TextField
            select
            label="Status"
            value={filters.active}
            onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as SupplierFilterForm['active'] }))}
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
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Supplier list</Typography>
          <Chip label={`${suppliers.data?.totalElements ?? 0} suppliers`} size="small" />
        </Stack>
        <Divider />
        {suppliers.isLoading ? <LoadingPanel label="Loading suppliers" /> : null}
        {suppliers.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(suppliers.error)}</Alert> : null}
        {!suppliers.isLoading && !suppliers.isError ? (
          <>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Supplier</TableCell>
                  <TableCell>Contact</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(suppliers.data?.content ?? []).map((supplier) => (
                  <TableRow key={supplier.id} hover>
                    <TableCell>
                      <Button component={Link} to={`/suppliers/${supplier.id}`} sx={{ px: 0, justifyContent: 'flex-start', textAlign: 'left' }}>
                        <Stack alignItems="flex-start">
                          <Typography fontWeight={700}>{supplier.name}</Typography>
                          <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{supplier.code}</Typography>
                        </Stack>
                      </Button>
                    </TableCell>
                    <TableCell>{supplier.contactName ?? supplier.phone ?? 'No contact set'}</TableCell>
                    <TableCell>{supplier.email ?? 'No email set'}</TableCell>
                    <TableCell><SupplierStatusChip active={supplier.active} /></TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Tooltip title="Open supplier">
                          <IconButton component={Link} to={`/suppliers/${supplier.id}`} aria-label={`Open ${supplier.name}`}>
                            <EditIcon />
                          </IconButton>
                        </Tooltip>
                        {canManage ? (
                          <Tooltip title={supplier.active ? 'Deactivate supplier' : 'Activate supplier'}>
                            <span>
                              <IconButton
                                aria-label={supplier.active ? `Deactivate ${supplier.name}` : `Activate ${supplier.name}`}
                                onClick={() => statusMutation.mutate(supplier)}
                                disabled={statusMutation.isPending}
                              >
                                {supplier.active ? <BlockIcon /> : <CheckCircleIcon />}
                              </IconButton>
                            </span>
                          </Tooltip>
                        ) : null}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
                {(suppliers.data?.content.length ?? 0) === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography color="text.secondary" textAlign="center" sx={{ py: 5 }}>
                        No suppliers match the current filters.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={suppliers.data?.totalElements ?? 0}
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

export function NewSupplierPage() {
  const navigate = useNavigate();
  const { getValidAccessToken } = useSession();
  const { canManage } = useSupplierPermissions();

  const mutation = useMutation({
    mutationFn: async (values: SupplierFormValues) => createSupplier(await getValidAccessToken(), cleanPayload(values)),
    onSuccess: (supplier) => navigate(`/suppliers/${supplier.id}`)
  });

  if (!canManage) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 900 }}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Tooltip title="Back to suppliers">
          <IconButton component={Link} to="/suppliers" aria-label="Back to suppliers">
            <ArrowBackIcon />
          </IconButton>
        </Tooltip>
        <Box>
          <Typography variant="h5" component="h1">New supplier</Typography>
          <Typography color="text.secondary">Create a vendor profile and contact record.</Typography>
        </Box>
      </Stack>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <SupplierForm
          defaultValues={emptySupplierForm}
          submitLabel="Create supplier"
          loading={mutation.isPending}
          error={mutation.isError ? errorMessage(mutation.error) : undefined}
          onSubmit={(values) => mutation.mutate(values)}
        />
      </Paper>
    </Stack>
  );
}

export function SupplierDetailPage() {
  const { id } = useParams();
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const { canView, canManage } = useSupplierPermissions();

  const supplier = useQuery({
    queryKey: ['supplier', id],
    queryFn: async () => getSupplier(await getValidAccessToken(), id ?? ''),
    enabled: canView && Boolean(id)
  });

  const updateMutation = useMutation({
    mutationFn: async (values: SupplierFormValues) => {
      if (!supplier.data || !id) {
        throw new Error('Supplier is not loaded');
      }
      const payload: SupplierUpdatePayload = {
        ...cleanPayload(values),
        version: supplier.data.version
      };
      return updateSupplier(await getValidAccessToken(), id, payload);
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['supplier', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async () => {
      if (!supplier.data || !id) {
        throw new Error('Supplier is not loaded');
      }
      return updateSupplierStatus(await getValidAccessToken(), id, {
        active: !supplier.data.active,
        version: supplier.data.version
      });
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['supplier', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  if (supplier.isLoading) {
    return <LoadingPanel label="Loading supplier" />;
  }

  if (supplier.isError) {
    return (
      <Stack spacing={2} sx={{ maxWidth: 720 }}>
        <Button component={Link} to="/suppliers" startIcon={<ArrowBackIcon />} sx={{ alignSelf: 'flex-start' }}>
          Suppliers
        </Button>
        <Alert severity="error">{errorMessage(supplier.error)}</Alert>
      </Stack>
    );
  }

  if (!supplier.data) {
    return <Alert severity="error">Supplier was not found.</Alert>;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 980 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Button component={Link} to="/suppliers" startIcon={<ArrowBackIcon />} sx={{ alignSelf: { xs: 'flex-start', sm: 'center' } }}>
          Suppliers
        </Button>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">{supplier.data.name}</Typography>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <Typography color="text.secondary" sx={{ fontFamily: 'monospace' }}>{supplier.data.code}</Typography>
            {supplier.data.email ? <Typography color="text.secondary">{supplier.data.email}</Typography> : null}
            <SupplierStatusChip active={supplier.data.active} />
          </Stack>
        </Box>
        {canManage ? (
          <Button
            variant="outlined"
            startIcon={supplier.data.active ? <BlockIcon /> : <CheckCircleIcon />}
            onClick={() => statusMutation.mutate()}
            disabled={statusMutation.isPending || updateMutation.isPending}
          >
            {supplier.data.active ? 'Deactivate' : 'Activate'}
          </Button>
        ) : null}
      </Stack>

      {updateMutation.isSuccess ? <Alert severity="success">Supplier saved.</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6" component="h2">Supplier settings</Typography>
            <Typography color="text.secondary">Version {supplier.data.version}</Typography>
          </Box>
          <SupplierForm
            defaultValues={supplierFormValues(supplier.data)}
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
