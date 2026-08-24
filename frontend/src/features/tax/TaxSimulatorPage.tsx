import CalculateOutlinedIcon from '@mui/icons-material/CalculateOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  MenuItem,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Navigate } from 'react-router-dom';
import { z } from 'zod';
import {
  calculateTax,
  listProducts,
  listStores,
  listTaxCategories,
  listTaxJurisdictions,
  type TaxCalculationPayload
} from '../../api/client';
import type { Product, Store, TaxCalculation, TaxCategory, TaxJurisdiction, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

const simulatorSchema = z.object({
  transactionDate: z.string().min(1, 'Date is required'),
  storeId: z.string().min(1, 'Store is required'),
  supplyJurisdictionId: z.string().min(1, 'Jurisdiction is required'),
  productId: z.string().min(1, 'Product is required'),
  productTaxCategoryId: z.string().min(1, 'Tax category is required'),
  quantity: z.coerce.number().positive('Quantity must be greater than zero'),
  unitPrice: z.coerce.number().min(0, 'Price must be zero or greater'),
  discountAmount: z.coerce.number().min(0, 'Discount must be zero or greater'),
  customerExempt: z.boolean()
}).refine((values) => values.discountAmount <= values.quantity * values.unitPrice, {
  message: 'Discount cannot exceed line subtotal',
  path: ['discountAmount']
});

type SimulatorForm = z.infer<typeof simulatorSchema>;

function canViewTax(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function money(value: number | null | undefined, currencyCode = 'CAD') {
  const amount = value ?? 0;
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: currencyCode,
    currencyDisplay: 'narrowSymbol'
  }).format(amount);
}

function percentage(value: number) {
  return `${Number(value).toFixed(3).replace(/\.?0+$/, '')}%`;
}

function labelById<T extends { id: string }>(items: T[], id: string | null | undefined, label: (item: T) => string) {
  const match = items.find((item) => item.id === id);
  return match ? label(match) : id ?? 'None';
}

function ResultSummary({ result, stores, jurisdictions, products, categories }: {
  result: TaxCalculation;
  stores: Store[];
  jurisdictions: TaxJurisdiction[];
  products: Product[];
  categories: TaxCategory[];
}) {
  const currency = result.currencyCode || 'CAD';
  const matchedRules = result.ruleEvaluation.ruleMatches.filter((match) => match.matched);

  return (
    <Stack spacing={3}>
      <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
        <Stack spacing={2}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
            <Box>
              <Typography variant="h6">Result</Typography>
              <Typography color="text.secondary">
                {labelById(stores, result.storeId, (store) => `${store.code} - ${store.name}`)} · {labelById(jurisdictions, result.supplyJurisdictionId, (jurisdiction) => `${jurisdiction.code} - ${jurisdiction.name}`)}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip size="small" label={result.pricesIncludeTax ? 'Tax-inclusive' : 'Tax-exclusive'} />
              <Chip size="small" label={result.roundingStrategy.replace(/_/g, ' ')} />
              {result.zeroRated ? <Chip size="small" color="warning" label="Zero-rated" /> : null}
              {result.exempt ? <Chip size="small" color="warning" label="Exempt" /> : null}
              {result.outOfScope ? <Chip size="small" color="warning" label="Out of scope" /> : null}
            </Stack>
          </Stack>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            {[
              ['Product', labelById(products, result.productId, (product) => `${product.sku} - ${product.name}`)],
              ['Tax category', labelById(categories, result.productTaxCategoryId, (category) => `${category.code} - ${category.name}`)],
              ['Taxable amount', money(result.netAmount, currency)],
              ['Tax', money(result.taxAmount, currency)],
              ['Total', money(result.grossAmount, currency)]
            ].map(([label, value]) => (
              <Box key={label} sx={{ minWidth: 150, flex: 1 }}>
                <Typography variant="overline" color="text.secondary">{label}</Typography>
                <Typography variant="subtitle1">{value}</Typography>
              </Box>
            ))}
          </Stack>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
        <Stack spacing={1.5}>
          <Typography variant="h6">Components and Rates</Typography>
          <TableContainer>
            <Table size="small" aria-label="Component tax calculations">
              <TableHead>
                <TableRow>
                  <TableCell>Component</TableCell>
                  <TableCell>Rate</TableCell>
                  <TableCell align="right">Taxable</TableCell>
                  <TableCell align="right">Tax</TableCell>
                  <TableCell>Mode</TableCell>
                  <TableCell>Effective</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {result.components.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6}>No active effective components matched this scenario.</TableCell>
                  </TableRow>
                ) : result.components.map((component) => (
                  <TableRow key={`${component.taxComponentCode}-${component.taxRateId ?? 'rate'}`}>
                    <TableCell>
                      <Typography variant="body2">{component.taxComponentCode}</Typography>
                      <Typography variant="caption" color="text.secondary">{component.taxComponentName}</Typography>
                    </TableCell>
                    <TableCell>{percentage(component.percentageRate)}</TableCell>
                    <TableCell align="right">{money(component.taxableAmount, currency)}</TableCell>
                    <TableCell align="right">{money(component.taxAmount, currency)}</TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip size="small" label={component.includedInPrice ? 'Included' : 'Added'} />
                        {component.compoundOnPreviousTax ? <Chip size="small" label="Compound" /> : null}
                      </Stack>
                    </TableCell>
                    <TableCell>{component.effectiveFrom}{component.effectiveTo ? ` to ${component.effectiveTo}` : ''}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
        <Stack spacing={1.5}>
          <Typography variant="h6">Matched Rules</Typography>
          {matchedRules.length === 0 ? (
            <Typography color="text.secondary">No rules matched this scenario.</Typography>
          ) : matchedRules.map((rule) => (
            <Box key={rule.ruleId}>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} alignItems={{ sm: 'center' }}>
                <Typography variant="subtitle2">{rule.code}</Typography>
                <Chip size="small" label={`Priority ${rule.priority}`} />
              </Stack>
              <Typography variant="body2" color="text.secondary">{rule.explanation}</Typography>
            </Box>
          ))}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
        <Stack spacing={1}>
          <Typography variant="h6">Explanation</Typography>
          {result.explanations.map((explanation) => (
            <Typography key={explanation} variant="body2">{explanation}</Typography>
          ))}
        </Stack>
      </Paper>
    </Stack>
  );
}

export function TaxSimulatorPage() {
  const { session, currentUser } = useSession();
  const token = session?.accessToken ?? '';
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const allowed = canViewTax(roles);
  const form = useForm<SimulatorForm>({
    resolver: zodResolver(simulatorSchema),
    defaultValues: {
      transactionDate: today(),
      storeId: '',
      supplyJurisdictionId: '',
      productId: '',
      productTaxCategoryId: '',
      quantity: 1,
      unitPrice: 0,
      discountAmount: 0,
      customerExempt: false
    }
  });

  const storesQuery = useQuery({
    queryKey: ['tax-simulator', 'stores'],
    queryFn: () => listStores(token, { active: true, size: 100 }),
    enabled: Boolean(token) && allowed
  });
  const jurisdictionsQuery = useQuery({
    queryKey: ['tax-simulator', 'jurisdictions'],
    queryFn: () => listTaxJurisdictions(token, { active: true, size: 200 }),
    enabled: Boolean(token) && allowed
  });
  const productsQuery = useQuery({
    queryKey: ['tax-simulator', 'products'],
    queryFn: () => listProducts(token, { active: true, size: 100 }),
    enabled: Boolean(token) && allowed
  });
  const categoriesQuery = useQuery({
    queryKey: ['tax-simulator', 'categories'],
    queryFn: () => listTaxCategories(token, { active: true, size: 100 }),
    enabled: Boolean(token) && allowed
  });

  const stores = storesQuery.data?.content ?? [];
  const jurisdictions = jurisdictionsQuery.data?.content ?? [];
  const products = productsQuery.data?.content ?? [];
  const categories = categoriesQuery.data?.content ?? [];
  const productId = form.watch('productId');

  useEffect(() => {
    const product = products.find((item) => item.id === productId);
    if (!product) {
      return;
    }
    form.setValue('unitPrice', Number(product.price), { shouldValidate: true });
    if (product.taxCategoryId) {
      form.setValue('productTaxCategoryId', product.taxCategoryId, { shouldValidate: true });
    }
  }, [form, productId, products]);

  const calculateMutation = useMutation({
    mutationFn: (payload: TaxCalculationPayload) => calculateTax(token, payload)
  });

  if (!allowed) {
    return <Navigate to="/unauthorized" replace />;
  }

  const loadingOptions = storesQuery.isLoading || jurisdictionsQuery.isLoading || productsQuery.isLoading || categoriesQuery.isLoading;
  const optionError = storesQuery.error ?? jurisdictionsQuery.error ?? productsQuery.error ?? categoriesQuery.error;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Box>
          <Typography variant="h5" component="h1">Tax Simulator</Typography>
          <Typography color="text.secondary">Run a transaction scenario against the configured tax rules and rates.</Typography>
        </Box>
      </Stack>

      {optionError ? <Alert severity="error">{errorMessage(optionError)}</Alert> : null}

      <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
        <Stack
          component="form"
          aria-label="Tax simulator form"
          spacing={2}
          onSubmit={form.handleSubmit((values) => {
            calculateMutation.mutate({
              storeId: values.storeId,
              supplyJurisdictionId: values.supplyJurisdictionId,
              productId: values.productId,
              productTaxCategoryId: values.productTaxCategoryId,
              customerExempt: values.customerExempt,
              transactionDate: values.transactionDate,
              saleChannel: 'POS',
              unitPrice: values.unitPrice,
              quantity: values.quantity,
              discountAmount: values.discountAmount
            });
          })}
        >
          {loadingOptions ? (
            <Stack direction="row" spacing={1.5} alignItems="center">
              <CircularProgress size={20} />
              <Typography color="text.secondary">Loading simulator inputs</Typography>
            </Stack>
          ) : null}

          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, minmax(0, 1fr))' }, gap: 2 }}>
            <Controller
              name="transactionDate"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField
                  {...field}
                  label="Date"
                  type="date"
                  InputLabelProps={{ shrink: true }}
                  error={Boolean(fieldState.error)}
                  helperText={fieldState.error?.message}
                  fullWidth
                />
              )}
            />
            <Controller
              name="storeId"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField {...field} select label="Store" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                  {stores.map((store) => (
                    <MenuItem key={store.id} value={store.id}>{store.code} - {store.name}</MenuItem>
                  ))}
                </TextField>
              )}
            />
            <Controller
              name="supplyJurisdictionId"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField {...field} select label="Jurisdiction" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                  {jurisdictions.map((jurisdiction) => (
                    <MenuItem key={jurisdiction.id} value={jurisdiction.id}>{jurisdiction.code} - {jurisdiction.name}</MenuItem>
                  ))}
                </TextField>
              )}
            />
            <Controller
              name="productId"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField {...field} select label="Product" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                  {products.map((product) => (
                    <MenuItem key={product.id} value={product.id}>{product.sku} - {product.name}</MenuItem>
                  ))}
                </TextField>
              )}
            />
            <Controller
              name="productTaxCategoryId"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField {...field} select label="Tax category" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                  {categories.map((category) => (
                    <MenuItem key={category.id} value={category.id}>{category.code} - {category.name}</MenuItem>
                  ))}
                </TextField>
              )}
            />
            <Controller
              name="quantity"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField {...field} label="Quantity" type="number" inputProps={{ min: 0.0001, step: 0.001 }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
              )}
            />
            <Controller
              name="unitPrice"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField {...field} label="Price" type="number" inputProps={{ min: 0, step: 0.01 }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
              )}
            />
            <Controller
              name="discountAmount"
              control={form.control}
              render={({ field, fieldState }) => (
                <TextField {...field} label="Discount" type="number" inputProps={{ min: 0, step: 0.01 }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
              )}
            />
            <Controller
              name="customerExempt"
              control={form.control}
              render={({ field }) => (
                <FormControlLabel
                  sx={{ alignSelf: 'center' }}
                  control={<Switch checked={field.value} onChange={(event) => field.onChange(event.target.checked)} />}
                  label="Customer exempt"
                />
              )}
            />
          </Box>

          <Divider />

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
            <Button
              type="submit"
              variant="contained"
              startIcon={calculateMutation.isPending ? <CircularProgress color="inherit" size={18} /> : <CalculateOutlinedIcon />}
              disabled={loadingOptions || calculateMutation.isPending}
              sx={{ alignSelf: { xs: 'stretch', sm: 'flex-start' } }}
            >
              Run simulation
            </Button>
            {calculateMutation.error ? (
              <Alert icon={<ErrorOutlineIcon fontSize="inherit" />} severity="error" sx={{ flex: 1 }}>
                {errorMessage(calculateMutation.error)}
              </Alert>
            ) : null}
          </Stack>
        </Stack>
      </Paper>

      {calculateMutation.data ? (
        <ResultSummary
          result={calculateMutation.data}
          stores={stores}
          jurisdictions={jurisdictions}
          products={products}
          categories={categories}
        />
      ) : null}
    </Stack>
  );
}
