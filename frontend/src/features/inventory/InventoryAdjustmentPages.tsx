import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteIcon from '@mui/icons-material/Delete';
import RefreshIcon from '@mui/icons-material/Refresh';
import SaveIcon from '@mui/icons-material/Save';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Grid,
  IconButton,
  MenuItem,
  Paper,
  Stack,
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
import { Controller, useFieldArray, useForm, type Control, type FieldPath } from 'react-hook-form';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import {
  createStockAdjustment,
  listProducts,
  listStockAdjustments,
  listStores,
  type StockAdjustmentPayload
} from '../../api/client';
import type { Product, StockAdjustment, StockAdjustmentType, Store, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

const adjustmentTypes = ['INCREASE', 'DECREASE', 'DAMAGED', 'EXPIRED'] as const;

const adjustmentLineSchema = z.object({
  productId: z.string().trim().min(1, 'Product is required'),
  adjustmentType: z.enum(adjustmentTypes),
  quantity: z.coerce.number().positive('Quantity must be greater than zero')
});

const adjustmentSchema = z.object({
  storeId: z.string().trim().min(1, 'Store is required'),
  reason: z.string().trim().min(1, 'Reason is required').max(255, 'Reason must be 255 characters or fewer'),
  notes: z.string().max(2000, 'Notes must be 2000 characters or fewer').optional(),
  approvalNotes: z.string().max(1000, 'Approval notes must be 1000 characters or fewer').optional(),
  lines: z.array(adjustmentLineSchema).min(1, 'Add at least one adjustment line')
});

type AdjustmentFormValues = z.infer<typeof adjustmentSchema>;
type AdjustmentTextFieldName = FieldPath<AdjustmentFormValues>;

const emptyAdjustmentForm: AdjustmentFormValues = {
  storeId: '',
  reason: '',
  notes: '',
  approvalNotes: '',
  lines: [{ productId: '', adjustmentType: 'INCREASE', quantity: 1 }]
};

function canViewInventory(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
}

function canManageInventory(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function useInventoryPermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const permissions = new Set(currentUser?.permissions ?? []);
  return {
    canView: permissions.size ? permissions.has('INVENTORY_VIEW') : canViewInventory(roles),
    canManage: permissions.size
      ? permissions.has('INVENTORY_RECEIVE') || permissions.has('INVENTORY_ADJUST')
      : canManageInventory(roles)
  };
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function cleanPayload(values: AdjustmentFormValues): StockAdjustmentPayload {
  return {
    storeId: values.storeId,
    reason: values.reason.trim(),
    notes: optionalText(values.notes),
    approvalNotes: optionalText(values.approvalNotes),
    lines: values.lines.map((line) => ({
      productId: line.productId,
      adjustmentType: line.adjustmentType as StockAdjustmentType,
      quantity: Number(line.quantity)
    }))
  };
}

function errorMessage(error: unknown) {
  const message = error instanceof Error ? error.message : 'Request failed';
  if (message.includes('PRODUCT_STORE_ACCESS_DENIED') || message.includes('INVENTORY_ACCESS_DENIED')) {
    return 'You do not have permission to update inventory for this store.';
  }
  return message;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value));
}

function formatQuantity(value: number) {
  return new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4
  }).format(value);
}

function storeLabel(store?: Store) {
  return store ? `${store.name} (${store.code})` : 'Unknown store';
}

function productLabel(product?: Product) {
  return product ? `${product.name} (${product.sku})` : 'Unknown product';
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }}>
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function ApprovalChip({ adjustment }: { adjustment: StockAdjustment }) {
  return <Chip label={adjustment.approvalStatus} color="success" size="small" />;
}

export function InventoryAdjustmentsPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useInventoryPermissions();
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);

  const adjustments = useQuery({
    queryKey: ['inventory-adjustments', page, size],
    queryFn: async () => listStockAdjustments(await getValidAccessToken(), { page, size }),
    enabled: canView
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">Inventory adjustments</Typography>
          <Typography color="text.secondary">Approved stock corrections and shrinkage records</Typography>
        </Box>
        <Tooltip title="Refresh adjustments">
          <IconButton aria-label="Refresh adjustments" onClick={() => void adjustments.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        {canManage ? (
          <Button component={Link} to="/inventory/adjustments/new" variant="contained" startIcon={<AddIcon />}>
            New adjustment
          </Button>
        ) : null}
      </Stack>

      {adjustments.isLoading ? <LoadingPanel label="Loading adjustments" /> : null}
      {adjustments.error ? <Alert severity="error">{errorMessage(adjustments.error)}</Alert> : null}

      {adjustments.data ? (
        <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
          <TableContainer>
            <Table aria-label="Inventory adjustments">
              <TableHead>
                <TableRow>
                  <TableCell>Reason</TableCell>
                  <TableCell>Store</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Lines</TableCell>
                  <TableCell>Created</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {adjustments.data.content.map((adjustment) => (
                  <TableRow key={adjustment.id}>
                    <TableCell>
                      <Typography fontWeight={600}>{adjustment.reason}</Typography>
                      {adjustment.notes ? <Typography variant="body2" color="text.secondary">{adjustment.notes}</Typography> : null}
                    </TableCell>
                    <TableCell>{adjustment.storeId}</TableCell>
                    <TableCell><ApprovalChip adjustment={adjustment} /></TableCell>
                    <TableCell align="right">{adjustment.lines.length}</TableCell>
                    <TableCell>{formatDate(adjustment.createdAt)}</TableCell>
                  </TableRow>
                ))}
                {adjustments.data.content.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography color="text.secondary">No inventory adjustments found.</Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={adjustments.data.totalElements}
            page={adjustments.data.page}
            rowsPerPage={adjustments.data.size}
            rowsPerPageOptions={[10, 20, 50]}
            onPageChange={(_, nextPage) => setPage(nextPage)}
            onRowsPerPageChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
          />
        </Paper>
      ) : null}
    </Stack>
  );
}

export function NewInventoryAdjustmentPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useInventoryPermissions();
  const [success, setSuccess] = React.useState<string | null>(null);

  const stores = useQuery({
    queryKey: ['stores', 'inventory-adjustment-options'],
    queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled: canManage
  });
  const products = useQuery({
    queryKey: ['products', 'inventory-adjustment-options'],
    queryFn: async () => listProducts(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled: canManage
  });

  const mutation = useMutation({
    mutationFn: async (values: AdjustmentFormValues) => createStockAdjustment(await getValidAccessToken(), cleanPayload(values)),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['inventory-adjustments'] }),
        queryClient.invalidateQueries({ queryKey: ['inventory'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['pos-products'] })
      ]);
      setSuccess('Stock updated successfully.');
    }
  });

  if (!canView || !canManage) {
    return <Navigate to="/unauthorized" replace />;
  }

  const trackedProducts = products.data?.content.filter((product) => product.inventoryTrackingEnabled) ?? [];
  const loadingOptions = stores.isLoading || products.isLoading;
  const optionError = stores.error ?? products.error;

  return (
    <Stack spacing={3}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Button component={Link} to="/inventory/adjustments" startIcon={<ArrowBackIcon />}>
          Back
        </Button>
        <Box>
          <Typography variant="h5" component="h1">New inventory adjustment</Typography>
          <Typography color="text.secondary">Post quantity corrections immediately for one authorized store</Typography>
        </Box>
      </Stack>

      {loadingOptions ? <LoadingPanel label="Loading adjustment options" /> : null}
      {optionError ? <Alert severity="error">{errorMessage(optionError)}</Alert> : null}
      {success ? <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert> : null}

      {!loadingOptions && !optionError ? (
        <AdjustmentForm
          stores={stores.data?.content ?? []}
          products={trackedProducts}
          defaultValues={emptyAdjustmentForm}
          loading={mutation.isPending}
          error={mutation.error ? errorMessage(mutation.error) : undefined}
          onSubmit={(values) => mutation.mutate(values)}
        />
      ) : null}
    </Stack>
  );
}

function AdjustmentForm({
  stores,
  products,
  defaultValues,
  loading,
  error,
  onSubmit
}: {
  stores: Store[];
  products: Product[];
  defaultValues: AdjustmentFormValues;
  loading: boolean;
  error?: string;
  onSubmit: (values: AdjustmentFormValues) => void;
}) {
  const form = useForm<AdjustmentFormValues>({
    resolver: zodResolver(adjustmentSchema),
    defaultValues
  });
  const lines = useFieldArray({ control: form.control, name: 'lines' });

  return (
    <Stack component="form" spacing={3} onSubmit={form.handleSubmit(onSubmit)}>
      {error ? <Alert severity="error">{error}</Alert> : null}
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6}>
            <Controller
              name="storeId"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField {...field} select label="Store" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                  {stores.map((store) => <MenuItem key={store.id} value={store.id}>{storeLabel(store)}</MenuItem>)}
                </TextField>
              )}
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextInput control={form.control} name="reason" label="Reason" />
          </Grid>
          <Grid item xs={12}>
            <TextInput control={form.control} name="notes" label="Notes" multiline minRows={3} />
          </Grid>
          <Grid item xs={12}>
            <TextInput control={form.control} name="approvalNotes" label="Approval notes" />
          </Grid>
        </Grid>
      </Paper>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <Stack spacing={2}>
          <Stack direction="row" spacing={2} alignItems="center">
            <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Lines</Typography>
            <Button
              type="button"
              variant="outlined"
              startIcon={<AddIcon />}
              onClick={() => lines.append({ productId: '', adjustmentType: 'INCREASE', quantity: 1 })}
            >
              Add line
            </Button>
          </Stack>
          {lines.fields.map((line, index) => (
            <Paper key={line.id} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 2 }}>
              <Grid container spacing={2} alignItems="flex-start">
                <Grid item xs={12} md={5}>
                  <Controller
                    name={`lines.${index}.productId`}
                    control={form.control}
                    render={({ field, fieldState }) => (
                      <TextField {...field} select label="Product" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                        {products.map((product) => <MenuItem key={product.id} value={product.id}>{productLabel(product)}</MenuItem>)}
                      </TextField>
                    )}
                  />
                </Grid>
                <Grid item xs={12} sm={5} md={3}>
                  <Controller
                    name={`lines.${index}.adjustmentType`}
                    control={form.control}
                    render={({ field, fieldState }) => (
                      <TextField {...field} select label="Type" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                        {adjustmentTypes.map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
                      </TextField>
                    )}
                  />
                </Grid>
                <Grid item xs={8} sm={5} md={3}>
                  <TextInput control={form.control} name={`lines.${index}.quantity`} label="Quantity" type="number" />
                </Grid>
                <Grid item xs={4} sm={2} md={1}>
                  <Tooltip title="Remove line">
                    <IconButton aria-label={`Remove line ${index + 1}`} color="error" onClick={() => lines.remove(index)} disabled={lines.fields.length === 1}>
                      <DeleteIcon />
                    </IconButton>
                  </Tooltip>
                </Grid>
              </Grid>
            </Paper>
          ))}
          {products.length === 0 ? <Alert severity="info">No active tracked products are available.</Alert> : null}
        </Stack>
      </Paper>

      <Button type="submit" variant="contained" startIcon={<SaveIcon />} disabled={loading || products.length === 0} sx={{ alignSelf: 'flex-start' }}>
        Create adjustment
      </Button>
    </Stack>
  );
}

function TextInput({
  control,
  name,
  label,
  multiline,
  minRows,
  type
}: {
  control: Control<AdjustmentFormValues>;
  name: AdjustmentTextFieldName;
  label: string;
  multiline?: boolean;
  minRows?: number;
  type?: 'number';
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
          type={type}
          inputProps={type === 'number' ? { step: '0.0001', min: 0.0001 } : undefined}
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

export function InventoryAdjustmentDelta({ adjustment }: { adjustment: StockAdjustment }) {
  const total = adjustment.lines.reduce((sum, line) => sum + line.quantityDelta, 0);
  return (
    <Typography color={total < 0 ? 'error.main' : 'success.main'} fontWeight={600}>
      {total > 0 ? '+' : ''}{formatQuantity(total)}
    </Typography>
  );
}
