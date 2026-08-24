import AddIcon from '@mui/icons-material/Add';
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
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
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
import { Controller, useForm } from 'react-hook-form';
import { Navigate } from 'react-router-dom';
import { z } from 'zod';
import {
  catalogueReferenceApi,
  type CatalogueReferencePayload,
  type CatalogueReferenceSearchParams,
  type CatalogueReferenceUpdatePayload
} from '../../api/client';
import type { CatalogueReference, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

type ReferenceKind = 'categories' | 'brands' | 'units';

type ReferenceConfig = {
  kind: ReferenceKind;
  title: string;
  subtitle: string;
  singular: string;
  newLabel: string;
};

const configs: Record<ReferenceKind, ReferenceConfig> = {
  categories: {
    kind: 'categories',
    title: 'Categories',
    subtitle: 'Product category codes and names.',
    singular: 'category',
    newLabel: 'New category'
  },
  brands: {
    kind: 'brands',
    title: 'Brands',
    subtitle: 'Product brand codes and names.',
    singular: 'brand',
    newLabel: 'New brand'
  },
  units: {
    kind: 'units',
    title: 'Units of measure',
    subtitle: 'Units used for product quantities and measurements.',
    singular: 'unit',
    newLabel: 'New unit'
  }
};

const referenceSchema = z.object({
  code: z.string().trim().min(1, 'Code is required').max(64, 'Code must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  description: z.string().max(1000, 'Description must be 1000 characters or fewer').optional(),
  active: z.boolean()
});

type ReferenceFormValues = z.infer<typeof referenceSchema>;

type ReferenceFilterForm = {
  code: string;
  name: string;
  active: '' | 'true' | 'false';
};

const emptyReferenceForm: ReferenceFormValues = {
  code: '',
  name: '',
  description: '',
  active: true
};

function canViewCatalogue(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
}

function canManageCatalogue(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function useCataloguePermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  return {
    canView: canViewCatalogue(roles),
    canManage: canManageCatalogue(roles)
  };
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function cleanPayload(values: ReferenceFormValues): CatalogueReferencePayload {
  return {
    code: values.code.trim().toUpperCase(),
    name: values.name.trim(),
    description: optionalText(values.description),
    active: values.active
  };
}

function formValues(reference?: CatalogueReference | null): ReferenceFormValues {
  if (!reference) {
    return emptyReferenceForm;
  }
  return {
    code: reference.code,
    name: reference.name,
    description: reference.description ?? '',
    active: reference.active
  };
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 240 }}>
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function ReferenceStatusChip({ active }: { active: boolean }) {
  return <Chip label={active ? 'Active' : 'Inactive'} color={active ? 'success' : 'default'} size="small" />;
}

function ReferenceDialog({
  config,
  open,
  reference,
  loading,
  error,
  onClose,
  onSubmit
}: {
  config: ReferenceConfig;
  open: boolean;
  reference: CatalogueReference | null;
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: ReferenceFormValues) => void;
}) {
  const form = useForm<ReferenceFormValues>({
    resolver: zodResolver(referenceSchema),
    defaultValues: formValues(reference),
    values: formValues(reference)
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{reference ? `Edit ${config.singular}` : config.newLabel}</DialogTitle>
      <DialogContent>
        <Stack component="form" id={`${config.kind}-form`} spacing={2} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Controller
            name="code"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} label="Code" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )}
          />
          <Controller
            name="name"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} label="Name" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )}
          />
          <Controller
            name="description"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField
                {...field}
                value={field.value ?? ''}
                label="Description"
                multiline
                minRows={3}
                error={Boolean(fieldState.error)}
                helperText={fieldState.error?.message}
                fullWidth
              />
            )}
          />
          <Controller
            name="active"
            control={form.control}
            render={({ field }) => (
              <FormControlLabel
                control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />}
                label="Active"
              />
            )}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form={`${config.kind}-form`} variant="contained" startIcon={<SaveIcon />} disabled={loading}>
          {reference ? 'Save changes' : 'Create'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function CatalogueReferencePage({ config }: { config: ReferenceConfig }) {
  const api = catalogueReferenceApi[config.kind];
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useCataloguePermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState<ReferenceFilterForm>({ code: '', name: '', active: '' });
  const [appliedFilters, setAppliedFilters] = React.useState<ReferenceFilterForm>(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<CatalogueReference | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);

  const params = React.useMemo<CatalogueReferenceSearchParams>(() => ({
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const references = useQuery({
    queryKey: [config.kind, params],
    queryFn: async () => api.list(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: ReferenceFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: CatalogueReferenceUpdatePayload = {
          ...cleanPayload(values),
          version: editing.version
        };
        return api.update(token, editing.id, payload);
      }
      return api.create(token, cleanPayload(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: [config.kind] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (reference: CatalogueReference) => api.updateStatus(await getValidAccessToken(), reference.id, {
      active: !reference.active,
      version: reference.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: [config.kind] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">{config.title}</Typography>
          <Typography color="text.secondary">{config.subtitle}</Typography>
        </Box>
        <Tooltip title={`Refresh ${config.title.toLowerCase()}`}>
          <IconButton aria-label={`Refresh ${config.title.toLowerCase()}`} onClick={() => void references.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        {canManage ? (
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => {
              setEditing(null);
              setDialogOpen(true);
            }}
          >
            {config.newLabel}
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
            select
            label="Status"
            value={filters.active}
            onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as ReferenceFilterForm['active'] }))}
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

      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}

      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ p: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>{config.title} list</Typography>
          <Chip label={`${references.data?.totalElements ?? 0} records`} size="small" />
        </Stack>
        <Divider />
        {references.isLoading ? <LoadingPanel label={`Loading ${config.title.toLowerCase()}`} /> : null}
        {references.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(references.error)}</Alert> : null}
        {!references.isLoading && !references.isError ? (
          <>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Code</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Description</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(references.data?.content ?? []).map((reference) => (
                  <TableRow key={reference.id} hover>
                    <TableCell sx={{ fontWeight: 700 }}>{reference.code}</TableCell>
                    <TableCell>{reference.name}</TableCell>
                    <TableCell>{reference.description ?? '-'}</TableCell>
                    <TableCell><ReferenceStatusChip active={reference.active} /></TableCell>
                    <TableCell align="right">
                      {canManage ? (
                        <>
                          <Tooltip title={`Edit ${reference.name}`}>
                            <IconButton
                              aria-label={`Edit ${reference.name}`}
                              onClick={() => {
                                setEditing(reference);
                                setDialogOpen(true);
                              }}
                            >
                              <EditIcon />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title={reference.active ? `Deactivate ${reference.name}` : `Activate ${reference.name}`}>
                            <IconButton
                              aria-label={`${reference.active ? 'Deactivate' : 'Activate'} ${reference.name}`}
                              disabled={statusMutation.isPending}
                              onClick={() => statusMutation.mutate(reference)}
                            >
                              {reference.active ? <BlockIcon /> : <CheckCircleIcon />}
                            </IconButton>
                          </Tooltip>
                        </>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))}
                {(references.data?.content ?? []).length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography color="text.secondary" align="center" sx={{ py: 4 }}>No records found.</Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={references.data?.totalElements ?? 0}
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

      <ReferenceDialog
        config={config}
        open={dialogOpen}
        reference={editing}
        loading={saveMutation.isPending}
        error={saveMutation.isError ? errorMessage(saveMutation.error) : undefined}
        onClose={() => {
          if (!saveMutation.isPending) {
            setDialogOpen(false);
            setEditing(null);
          }
        }}
        onSubmit={(values) => saveMutation.mutate(values)}
      />
    </Stack>
  );
}

export function CategoriesPage() {
  return <CatalogueReferencePage config={configs.categories} />;
}

export function BrandsPage() {
  return <CatalogueReferencePage config={configs.brands} />;
}

export function UnitsPage() {
  return <CatalogueReferencePage config={configs.units} />;
}
