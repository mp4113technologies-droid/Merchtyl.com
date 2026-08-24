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
import { Controller, useFieldArray, useForm } from 'react-hook-form';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import {
  createStockCount,
  getStockCount,
  listInventoryBalances,
  listProducts,
  listStockCounts,
  listStores,
  updateStockCountLines,
  type StockCountPayload
} from '../../api/client';
import type { Product, StockCount, StockCountStatus, Store, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

const countLineSchema = z.object({
  productId: z.string().trim().min(1, 'Product is required'),
  countedQuantity: z.coerce.number().min(0, 'Actual count must be zero or greater')
});

const countSchema = z.object({
  storeId: z.string().trim().min(1, 'Store is required'),
  reference: z.string().trim().min(1, 'Reference is required').max(255, 'Reference must be 255 characters or fewer'),
  notes: z.string().max(2000, 'Notes must be 2000 characters or fewer').optional(),
  lines: z.array(countLineSchema).min(1, 'Add at least one count line')
});

type CountFormValues = z.infer<typeof countSchema>;

const emptyCountForm: CountFormValues = {
  storeId: '',
  reference: '',
  notes: '',
  lines: [{ productId: '', countedQuantity: 0 }]
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
    canView: permissions.size ? permissions.has('INVENTORY_COUNT_VIEW') || permissions.has('INVENTORY_COUNT_UPDATE') || permissions.has('INVENTORY_MANAGE') : canManageInventory(roles),
    canManage: permissions.size ? permissions.has('INVENTORY_COUNT_UPDATE') || permissions.has('INVENTORY_MANAGE') : canManageInventory(roles)
  };
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function cleanPayload(values: CountFormValues): StockCountPayload {
  return {
    storeId: values.storeId,
    reference: values.reference.trim(),
    notes: optionalText(values.notes),
    lines: values.lines.map((line) => ({ productId: line.productId, countedQuantity: Number(line.countedQuantity) }))
  };
}

function errorMessage(error: unknown) {
  const message = error instanceof Error ? error.message : 'Request failed';
  if (/PSQLException|HibernateException|SQLState|constraint|DataIntegrityViolationException/i.test(message)) {
    return "We couldn't update the stock count. Please try again.";
  }
  return message;
}

function formatDate(value: string | null) {
  if (!value) {
    return 'Not set';
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value));
}

function formatQuantity(value: number | null) {
  if (value === null) {
    return 'Not counted';
  }
  return new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4
  }).format(value);
}

function countDifference(actual: string | number | undefined, current: number) {
  if (actual === '' || actual == null || Number.isNaN(Number(actual))) return null;
  return Number(actual) - current;
}

function storeLabel(store?: Store) {
  return store ? `${store.name} (${store.code})` : 'Unknown store';
}

function productLabel(product?: Product) {
  return product ? `${product.name} (${product.sku})` : 'Unknown product';
}

function statusColor(status: StockCountStatus): 'default' | 'info' | 'success' {
  if (status === 'POSTED') {
    return 'success';
  }
  if (status === 'IN_REVIEW') {
    return 'info';
  }
  return 'default';
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }}>
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

export function StockCountsPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useInventoryPermissions();
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);

  const counts = useQuery({
    queryKey: ['stock-counts', page, size],
    queryFn: async () => listStockCounts(await getValidAccessToken(), { page, size }),
    enabled: canView
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">Stock counts</Typography>
          <Typography color="text.secondary">Save physical counts directly to inventory</Typography>
        </Box>
        <Tooltip title="Refresh counts">
          <IconButton aria-label="Refresh counts" onClick={() => void counts.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        {canManage ? (
          <Button component={Link} to="/inventory/counts/new" variant="contained" startIcon={<AddIcon />}>
            New count
          </Button>
        ) : null}
      </Stack>

      {counts.isLoading ? <LoadingPanel label="Loading stock counts" /> : null}
      {counts.error ? <Alert severity="error">{errorMessage(counts.error)}</Alert> : null}

      {counts.data ? (
        <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
          <TableContainer>
            <Table aria-label="Stock counts">
              <TableHead>
                <TableRow>
                  <TableCell>Reference</TableCell>
                  <TableCell>Store</TableCell>
                  <TableCell align="right">Lines</TableCell>
                  <TableCell>Counted</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {counts.data.content.map((count) => (
                  <TableRow key={count.id} hover component={Link} to={`/inventory/counts/${count.id}`} sx={{ textDecoration: 'none' }}>
                    <TableCell>
                      <Typography fontWeight={600}>{count.reference}</Typography>
                      {count.notes ? <Typography variant="body2" color="text.secondary">{count.notes}</Typography> : null}
                    </TableCell>
                    <TableCell>{count.storeId}</TableCell>
                    <TableCell align="right">{count.lines.length}</TableCell>
                    <TableCell>{formatDate(count.updatedAt)}</TableCell>
                  </TableRow>
                ))}
                {counts.data.content.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={4}>
                      <Typography color="text.secondary">No stock counts found.</Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={counts.data.totalElements}
            page={counts.data.page}
            rowsPerPage={counts.data.size}
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

export function NewStockCountPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useInventoryPermissions();

  const stores = useQuery({
    queryKey: ['stores', 'stock-count-options'],
    queryFn: async () => listStores(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled: canManage
  });
  const products = useQuery({
    queryKey: ['products', 'stock-count-options'],
    queryFn: async () => listProducts(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled: canManage
  });

  const mutation = useMutation({
    mutationFn: async (values: CountFormValues) => createStockCount(await getValidAccessToken(), cleanPayload(values)),
    onSuccess: async (count) => {
      await queryClient.invalidateQueries({ queryKey: ['stock-counts'] });
      navigate(`/inventory/counts/${count.id}`, { state: { success: 'Stock count updated successfully.' } });
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
        <Button component={Link} to="/inventory/counts" startIcon={<ArrowBackIcon />}>
          Back
        </Button>
        <Box>
          <Typography variant="h5" component="h1">New stock count</Typography>
          <Typography color="text.secondary">Enter actual quantities and save them directly</Typography>
        </Box>
      </Stack>

      {loadingOptions ? <LoadingPanel label="Loading count options" /> : null}
      {optionError ? <Alert severity="error">{errorMessage(optionError)}</Alert> : null}

      {!loadingOptions && !optionError ? (
        <CountForm
          stores={stores.data?.content ?? []}
          products={trackedProducts}
          loading={mutation.isPending}
          error={mutation.error ? errorMessage(mutation.error) : undefined}
          onSubmit={(values) => mutation.mutate(values)}
        />
      ) : null}
    </Stack>
  );
}

function CountForm({
  stores,
  products,
  loading,
  error,
  onSubmit
}: {
  stores: Store[];
  products: Product[];
  loading: boolean;
  error?: string;
  onSubmit: (values: CountFormValues) => void;
}) {
  const form = useForm<CountFormValues>({
    resolver: zodResolver(countSchema),
    defaultValues: emptyCountForm
  });
  const lines = useFieldArray({ control: form.control, name: 'lines' });
  const { getValidAccessToken } = useSession();
  const selectedStoreId = form.watch('storeId');
  const balances = useQuery({
    queryKey: ['inventory', 'stock-count', selectedStoreId],
    queryFn: async () => listInventoryBalances(await getValidAccessToken(), { storeId: selectedStoreId, page: 0, size: 100 }),
    enabled: Boolean(selectedStoreId)
  });
  const balanceByProduct = new Map((balances.data?.content ?? []).map((balance) => [balance.productId, balance.quantityOnHand]));

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
            <Controller
              name="reference"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField {...field} label="Reference" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
              )}
            />
          </Grid>
          <Grid item xs={12}>
            <Controller
              name="notes"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField {...field} value={field.value ?? ''} label="Notes" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} multiline minRows={3} fullWidth />
              )}
            />
          </Grid>
        </Grid>
      </Paper>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <Stack spacing={2}>
          <Stack direction="row" spacing={2} alignItems="center">
            <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Lines</Typography>
            <Button type="button" variant="outlined" startIcon={<AddIcon />} onClick={() => lines.append({ productId: '', countedQuantity: 0 })}>
              Add line
            </Button>
          </Stack>
          {lines.fields.map((line, index) => (
            <Paper key={line.id} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 2 }}>
              <Grid container spacing={2} alignItems="flex-start">
                <Grid item xs={12} sm={6}>
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
                <Grid item xs={6} sm={2}>
                  <Typography variant="overline" color="text.secondary">Current Stock</Typography>
                  <Typography>{formatQuantity(balanceByProduct.get(form.watch(`lines.${index}.productId`)) ?? 0)}</Typography>
                </Grid>
                <Grid item xs={6} sm={3}>
                  <Controller name={`lines.${index}.countedQuantity`} control={form.control} render={({ field, fieldState }) => (
                    <TextField {...field} type="number" label="Actual Count" inputProps={{ min: 0, step: '0.0001' }}
                      error={Boolean(fieldState.error)} helperText={fieldState.error?.message ?? `Difference: ${formatQuantity(countDifference(field.value, balanceByProduct.get(form.watch(`lines.${index}.productId`)) ?? 0))}`} fullWidth />
                  )} />
                </Grid>
                <Grid item xs={12} sm={1}>
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
        Save Count
      </Button>
    </Stack>
  );
}

export function StockCountDetailPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useInventoryPermissions();
  const location = useLocation();
  const [lineValues, setLineValues] = React.useState<Record<string, string>>({});
  const [success, setSuccess] = React.useState<string | null>((location.state as { success?: string } | null)?.success ?? null);

  const count = useQuery({
    queryKey: ['stock-count', id],
    queryFn: async () => getStockCount(await getValidAccessToken(), id ?? ''),
    enabled: canView && Boolean(id)
  });
  const products = useQuery({
    queryKey: ['products', 'stock-count-detail'],
    queryFn: async () => listProducts(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled: canView
  });

  React.useEffect(() => {
    if (count.data) {
      setLineValues(Object.fromEntries(count.data.lines.map((line) => [line.id, (line.countedQuantity ?? line.expectedQuantity).toString()])));
    }
  }, [count.data]);

  const saveLines = useMutation({
    mutationFn: async (current: StockCount) => updateStockCountLines(await getValidAccessToken(), current.id, {
      lines: current.lines.map((line) => ({
        lineId: line.id,
        countedQuantity: Number(lineValues[line.id])
      }))
    }),
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['stock-counts'] }),
        queryClient.invalidateQueries({ queryKey: ['inventory'] }),
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['pos-products'] })
      ]);
      queryClient.setQueryData(['stock-count', updated.id], updated);
      setSuccess('Stock count updated successfully.');
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const productById = new Map((products.data?.content ?? []).map((product) => [product.id, product]));
  const actionError = saveLines.error;
  const current = count.data;
  const editable = canManage && Boolean(current);
  const canSave = editable && current!.lines.every((line) => lineValues[line.id] !== '' && !Number.isNaN(Number(lineValues[line.id])));

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Button component={Link} to="/inventory/counts" startIcon={<ArrowBackIcon />} sx={{ alignSelf: { sm: 'flex-start' } }}>
          Back
        </Button>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">{current?.reference ?? 'Stock count'}</Typography>
          {current ? <Typography color="text.secondary">Created {formatDate(current.createdAt)}</Typography> : null}
        </Box>
      </Stack>

      {count.isLoading ? <LoadingPanel label="Loading stock count" /> : null}
      {count.error ? <Alert severity="error">{errorMessage(count.error)}</Alert> : null}
      {actionError ? <Alert severity="error">{errorMessage(actionError)}</Alert> : null}
      {success ? <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert> : null}

      {current ? (
        <>
          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <Typography variant="overline" color="text.secondary">Store</Typography>
                <Typography>{current.storeId}</Typography>
              </Grid>
              {current.notes ? (
                <Grid item xs={12}>
                  <Typography variant="overline" color="text.secondary">Notes</Typography>
                  <Typography>{current.notes}</Typography>
                </Grid>
              ) : null}
            </Grid>
          </Paper>

          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
            <TableContainer>
              <Table aria-label="Stock count lines">
                <TableHead>
                  <TableRow>
                    <TableCell>Product</TableCell>
                    <TableCell align="right">Current</TableCell>
                    <TableCell align="right">Actual</TableCell>
                    <TableCell align="right">Difference</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {current.lines.map((line) => (
                    <TableRow key={line.id}>
                      <TableCell>{productLabel(productById.get(line.productId))}</TableCell>
                      <TableCell align="right">{formatQuantity(line.expectedQuantity)}</TableCell>
                      <TableCell align="right">
                        {editable ? (
                          <TextField
                            type="number"
                            size="small"
                            value={lineValues[line.id] ?? ''}
                            inputProps={{ min: 0, step: '0.0001', 'aria-label': `Counted quantity for ${productLabel(productById.get(line.productId))}` }}
                            onChange={(event) => setLineValues((values) => ({ ...values, [line.id]: event.target.value }))}
                            sx={{ maxWidth: 140 }}
                          />
                        ) : formatQuantity(line.countedQuantity)}
                      </TableCell>
                      <TableCell align="right">
                        <Typography color={(countDifference(lineValues[line.id], line.expectedQuantity) ?? 0) < 0 ? 'error.main' : 'success.main'} fontWeight={600}>
                          {(countDifference(lineValues[line.id], line.expectedQuantity) ?? 0) > 0 ? '+' : ''}{formatQuantity(countDifference(lineValues[line.id], line.expectedQuantity))}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>

          {canManage ? (
            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
              <Stack spacing={2}>
                <Button variant="contained" startIcon={<SaveIcon />} disabled={!canSave || saveLines.isPending} onClick={() => saveLines.mutate(current)} sx={{ alignSelf: 'flex-start' }}>
                  Save Count
                </Button>
              </Stack>
            </Paper>
          ) : null}
        </>
      ) : null}
    </Stack>
  );
}
