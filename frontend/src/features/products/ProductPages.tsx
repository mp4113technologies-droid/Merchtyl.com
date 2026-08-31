import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
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
  FormGroup,
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
import { Controller, useFieldArray, useForm, useWatch, type Control, type FieldPath } from 'react-hook-form';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import {
  catalogueReferenceApi,
  createProduct,
  getProduct,
  listProducts,
  listAssignedStores,
  listTaxCategories,
  updateProduct,
  updateProductStatus,
  type ProductPayload,
  type ProductSearchParams,
  type ProductUpdatePayload
} from '../../api/client';
import type { CatalogueReference, Product, ProductCapability, SellableType, TaxCategory, UserRole } from '../../api/types';
import { compactFilterBarSx } from '../../app/responsive';
import { useSession } from '../../app/session';

type ProductFilterForm = {
  name: string;
  sku: string;
  barcode: string;
  categoryId: string;
  brandId: string;
  active: '' | 'true' | 'false';
};

const sellableTypes = [
  'STANDARD_PRODUCT',
  'WEIGHTED_PRODUCT',
  'SERVICE',
  'FOOD_ITEM',
  'LOTTERY_PRODUCT',
  'GIFT_CARD',
  'STORE_CREDIT',
  'BUNDLE',
  'DIGITAL_PRODUCT'
] as const;

const productCapabilities = [
  'RETAIL',
  'FOOD_SERVICE',
  'TRACK_INVENTORY',
  'ALLOW_DECIMAL_QUANTITY',
  'ALLOW_DISCOUNT',
  'ALLOW_RETURN',
  'ALLOW_REFUND',
  'ALLOW_PRICE_OVERRIDE',
  'REQUIRE_AGE_VERIFICATION',
  'REQUIRE_SERIAL_NUMBER',
  'REQUIRE_EXTERNAL_REFERENCE',
  'REQUIRE_CUSTOMER',
  'SEND_TO_KITCHEN',
  'EXCLUDE_FROM_LOYALTY',
  'RESTRICT_PAYMENT_METHOD',
  'NON_REFUNDABLE'
] as const;

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const variantSchema = z.object({
  id: z.string().regex(uuidPattern).optional(),
  sku: z.string().trim().min(1, 'Variant SKU is required').max(64, 'Variant SKU must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Variant name is required').max(180, 'Variant name must be 180 characters or fewer'),
  description: z.string().max(1000, 'Variant description must be 1000 characters or fewer').optional(),
  cost: z.coerce.number().min(0, 'Variant cost must be zero or greater'),
  price: z.coerce.number().min(0, 'Variant price must be zero or greater'),
  active: z.boolean()
});

const barcodeSchema = z.object({
  id: z.string().regex(uuidPattern).optional(),
  barcode: z.string().trim().min(1, 'Barcode is required').max(128, 'Barcode must be 128 characters or fewer'),
  variantId: z.string().optional(),
  variantSku: z.string().max(64, 'Variant SKU must be 64 characters or fewer').optional(),
  primaryBarcode: z.boolean(),
  active: z.boolean()
});

const productSchema = z.object({
  sku: z.string().trim().min(1, 'SKU is required').max(64, 'SKU must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  description: z.string().max(1000, 'Description must be 1000 characters or fewer').optional(),
  sellableType: z.enum(sellableTypes),
  unitOfMeasureId: z.string().optional(),
  categoryId: z.string().optional(),
  brandId: z.string().optional(),
  cost: z.coerce.number().min(0, 'Cost must be zero or greater'),
  price: z.coerce.number().min(0, 'Price must be zero or greater'),
  active: z.boolean(),
  inventoryTrackingEnabled: z.boolean(),
  decimalQuantityAllowed: z.boolean(),
  imageUrl: z.string().max(1000, 'Image URL must be 1000 characters or fewer').optional(),
  taxCategoryId: z.string().trim()
    .min(1, 'Select a tax category')
    .regex(uuidPattern, 'Select a valid tax category'),
  variants: z.array(variantSchema),
  barcodes: z.array(barcodeSchema),
  capabilities: z.array(z.enum(productCapabilities)),
  minimumAge: z.number().int().min(1, 'Minimum age must be at least 1').max(99, 'Minimum age must be 99 or less').optional()
}).superRefine((values, context) => {
  if (values.capabilities.includes('REQUIRE_AGE_VERIFICATION') && values.minimumAge == null) {
    context.addIssue({ code: 'custom', path: ['minimumAge'], message: 'Enter the required minimum age' });
  }
  const skus = new Set<string>();
  [values.sku, ...values.variants.map((variant) => variant.sku)].forEach((sku) => {
    const normalized = sku.trim().toUpperCase();
    if (skus.has(normalized)) {
      context.addIssue({ code: 'custom', path: ['sku'], message: 'Product and variant SKUs must be unique' });
    }
    skus.add(normalized);
  });
  const variantSkus = new Set(values.variants.map((variant) => variant.sku.trim().toUpperCase()));
  const barcodes = new Set<string>();
  values.barcodes.forEach((barcode, index) => {
    const normalized = barcode.barcode.trim().toUpperCase();
    if (barcodes.has(normalized)) {
      context.addIssue({ code: 'custom', path: ['barcodes', index, 'barcode'], message: 'Barcodes must be unique' });
    }
    barcodes.add(normalized);
    const variantSku = barcode.variantSku?.trim().toUpperCase();
    if (variantSku && !variantSkus.has(variantSku)) {
      context.addIssue({ code: 'custom', path: ['barcodes', index, 'variantSku'], message: 'Choose an existing variant SKU' });
    }
  });
});

type ProductFormValues = z.infer<typeof productSchema>;
type ProductTextFieldName = FieldPath<ProductFormValues>;

const emptyProductForm: ProductFormValues = {
  sku: '',
  name: '',
  description: '',
  sellableType: 'STANDARD_PRODUCT',
  unitOfMeasureId: '',
  categoryId: '',
  brandId: '',
  cost: 0,
  price: 0,
  active: true,
  inventoryTrackingEnabled: true,
  decimalQuantityAllowed: false,
  imageUrl: '',
  taxCategoryId: '',
  variants: [],
  barcodes: [],
  capabilities: ['TRACK_INVENTORY'],
  minimumAge: undefined
};

function canViewProducts(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
}

function canManageProducts(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function useProductPermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const permissions = new Set(currentUser?.permissions ?? []);
  return {
    canView: permissions.size ? permissions.has('PRODUCT_VIEW') : canViewProducts(roles),
    canCreate: permissions.size ? permissions.has('PRODUCT_CREATE') : canManageProducts(roles),
    canUpdate: permissions.size ? permissions.has('PRODUCT_UPDATE') : canManageProducts(roles),
    canDeactivate: permissions.size ? permissions.has('PRODUCT_DEACTIVATE') : canManageProducts(roles),
    canPriceUpdate: permissions.size ? permissions.has('PRODUCT_PRICE_UPDATE') : canManageProducts(roles),
    canCostView: permissions.size ? permissions.has('PRODUCT_COST_VIEW') : roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER')
  };
}

function useReferenceOptions(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  const categories = useQuery({
    queryKey: ['categories', 'product-options'],
    queryFn: async () => catalogueReferenceApi.categories.list(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled
  });
  const brands = useQuery({
    queryKey: ['brands', 'product-options'],
    queryFn: async () => catalogueReferenceApi.brands.list(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled
  });
  const units = useQuery({
    queryKey: ['units', 'product-options'],
    queryFn: async () => catalogueReferenceApi.units.list(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled
  });
  const taxCategories = useQuery({
    queryKey: ['tax-categories', 'product-options'],
    queryFn: async () => listTaxCategories(await getValidAccessToken(), { page: 0, size: 100, active: true }),
    enabled
  });
  return { categories, brands, units, taxCategories };
}

function referenceLabel(reference?: CatalogueReference) {
  return reference ? `${reference.name} (${reference.code})` : 'None';
}

function productFormValues(product: Product): ProductFormValues {
  return {
    sku: product.sku,
    name: product.name,
    description: product.description ?? '',
    sellableType: product.sellableType,
    unitOfMeasureId: product.unitOfMeasureId ?? '',
    categoryId: product.categoryId ?? '',
    brandId: product.brandId ?? '',
    cost: product.cost,
    price: product.price,
    active: product.active,
    inventoryTrackingEnabled: product.inventoryTrackingEnabled,
    decimalQuantityAllowed: product.decimalQuantityAllowed,
    imageUrl: product.imageUrl ?? '',
    taxCategoryId: product.taxCategoryId ?? '',
    variants: product.variants.map((variant) => ({
      id: variant.id,
      sku: variant.sku,
      name: variant.name,
      description: variant.description ?? '',
      cost: variant.cost,
      price: variant.price,
      active: variant.active
    })),
    barcodes: product.barcodes.map((barcode) => ({
      id: barcode.id,
      barcode: barcode.barcode,
      variantId: barcode.variantId ?? undefined,
      variantSku: barcode.variantSku ?? '',
      primaryBarcode: barcode.primaryBarcode,
      active: barcode.active
    })),
    capabilities: product.capabilities,
    minimumAge: product.minimumAge ?? undefined
  };
}

function cleanPayload(values: ProductFormValues): ProductPayload {
  const capabilities = new Set<ProductCapability>(values.capabilities as ProductCapability[]);
  if (values.inventoryTrackingEnabled) {
    capabilities.add('TRACK_INVENTORY');
  } else {
    capabilities.delete('TRACK_INVENTORY');
  }
  if (values.decimalQuantityAllowed) {
    capabilities.add('ALLOW_DECIMAL_QUANTITY');
  } else {
    capabilities.delete('ALLOW_DECIMAL_QUANTITY');
  }
  return {
    sku: values.sku.trim().toUpperCase(),
    name: values.name.trim(),
    description: optionalText(values.description),
    sellableType: values.sellableType,
    unitOfMeasureId: optionalText(values.unitOfMeasureId),
    cost: Number(values.cost),
    price: Number(values.price),
    categoryId: optionalText(values.categoryId),
    brandId: optionalText(values.brandId),
    active: values.active,
    inventoryTrackingEnabled: values.inventoryTrackingEnabled,
    decimalQuantityAllowed: values.decimalQuantityAllowed,
    imageUrl: optionalText(values.imageUrl),
    taxCategoryId: optionalText(values.taxCategoryId),
    variants: values.variants.map((variant) => ({
      id: variant.id,
      sku: variant.sku.trim().toUpperCase(),
      name: variant.name.trim(),
      description: optionalText(variant.description),
      cost: Number(variant.cost),
      price: Number(variant.price),
      active: variant.active
    })),
    barcodes: values.barcodes.map((barcode) => ({
      id: barcode.id,
      barcode: barcode.barcode.trim(),
      variantId: barcode.variantId && uuidPattern.test(barcode.variantId) ? barcode.variantId : undefined,
      variantSku: optionalText(barcode.variantSku)?.toUpperCase(),
      primaryBarcode: barcode.primaryBarcode,
      active: barcode.active
    })),
    capabilities: Array.from(capabilities),
    minimumAge: capabilities.has('REQUIRE_AGE_VERIFICATION') ? values.minimumAge : undefined
  };
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function formatMoney(value: number) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(value);
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }} role="status" aria-live="polite">
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function ProductStatusChip({ active }: { active: boolean }) {
  return <Chip label={active ? 'Active' : 'Inactive'} color={active ? 'success' : 'default'} size="small" />;
}

const productSelectMenuProps = {
  PaperProps: {
    sx: { maxHeight: 'min(320px, calc(100dvh - 96px))', maxWidth: 'calc(100vw - 32px)' }
  }
};

function ProductForm({
  categories,
  brands,
  units,
  taxCategories,
  taxCategoriesLoading,
  taxCategoriesError,
  retryTaxCategories,
  defaultValues,
  submitLabel,
  loading,
  error,
  disabled,
  onSubmit
}: {
  categories: CatalogueReference[];
  brands: CatalogueReference[];
  units: CatalogueReference[];
  taxCategories: TaxCategory[];
  taxCategoriesLoading: boolean;
  taxCategoriesError?: string;
  retryTaxCategories: () => void;
  defaultValues: ProductFormValues;
  submitLabel: string;
  loading: boolean;
  error?: string;
  disabled?: boolean;
  onSubmit: (values: ProductFormValues) => void;
}) {
  const form = useForm<ProductFormValues>({
    resolver: zodResolver(productSchema),
    defaultValues
  });
  const variants = useFieldArray({ control: form.control, name: 'variants', keyName: 'fieldKey' });
  const barcodes = useFieldArray({ control: form.control, name: 'barcodes', keyName: 'fieldKey' });
  const watchedVariants = useWatch({ control: form.control, name: 'variants' }) ?? [];
  const watchedBarcodes = useWatch({ control: form.control, name: 'barcodes' }) ?? [];
  const previousVariantSkus = React.useRef(new Map<string, string>());
  const variantOptions = watchedVariants.reduce<Array<{ clientId: string; value: string; id?: string; sku: string; label: string }>>((options, variant, index) => {
    const sku = variant.sku.trim().toUpperCase();
    if (!sku || options.some((option) => option.sku === sku)) return options;
    const name = variant.name.trim();
    const clientId = variants.fields[index]?.fieldKey ?? `variant-${index}`;
    options.push({ clientId, value: variant.id ?? sku, id: variant.id, sku, label: name ? `${name} — ${sku}` : sku });
    return options;
  }, []);

  React.useEffect(() => {
    const nextSkus = new Map<string, string>();
    watchedVariants.forEach((variant, index) => {
      const clientId = variants.fields[index]?.fieldKey;
      if (!clientId) return;
      const nextSku = variant.sku.trim().toUpperCase();
      const previousSku = previousVariantSkus.current.get(clientId);
      nextSkus.set(clientId, nextSku);
      if (previousSku && nextSku && previousSku !== nextSku) {
        watchedBarcodes.forEach((barcode, barcodeIndex) => {
          if (!barcode.variantId && barcode.variantSku?.trim().toUpperCase() === previousSku) {
            form.setValue(`barcodes.${barcodeIndex}.variantSku`, nextSku, { shouldDirty: true, shouldValidate: true });
            form.setValue(`barcodes.${barcodeIndex}.variantId`, nextSku, { shouldDirty: true });
          } else if (barcode.variantId === previousSku) {
            form.setValue(`barcodes.${barcodeIndex}.variantSku`, nextSku, { shouldDirty: true, shouldValidate: true });
            form.setValue(`barcodes.${barcodeIndex}.variantId`, nextSku, { shouldDirty: true });
          }
        });
      }
    });
    previousVariantSkus.current = nextSkus;
  }, [form, variants.fields, watchedBarcodes, watchedVariants]);

  const removeVariant = (index: number) => {
    const variant = watchedVariants[index];
    if (!variant) return;
    const sku = variant.sku.trim().toUpperCase();
    const linkedBarcodeIndexes = watchedBarcodes
      .map((barcode, barcodeIndex) => barcode.variantId === variant.id || (!barcode.variantId && barcode.variantSku?.trim().toUpperCase() === sku) ? barcodeIndex : -1)
      .filter((barcodeIndex) => barcodeIndex >= 0);
    if (linkedBarcodeIndexes.length > 0) {
      const label = variant.name.trim() || sku;
      const confirmed = window.confirm(`Variant ${label} has ${linkedBarcodeIndexes.length} barcode assigned. Removing this variant will also remove its barcode assignment.`);
      if (!confirmed) return;
      linkedBarcodeIndexes.reverse().forEach((barcodeIndex) => barcodes.remove(barcodeIndex));
    }
    variants.remove(index);
  };

  return (
    <Stack
      component="form"
      data-testid="product-form"
      spacing={{ xs: 2, lg: 3 }}
      noValidate
      aria-busy={loading}
      onSubmit={form.handleSubmit(onSubmit)}
      sx={{
        width: '100%',
        maxWidth: '100%',
        minWidth: 0,
        '& .MuiGrid-item': { minWidth: 0 },
        '& .MuiFormControl-root': { minWidth: 0, maxWidth: '100%' },
        '& .MuiInputBase-root': { minWidth: 0, maxWidth: '100%' }
      }}
    >
      {error ? <Alert severity="error">{error}</Alert> : null}
      {disabled ? <Alert severity="info">This account can view products but cannot change product records.</Alert> : null}

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: { xs: 2, lg: 3 }, minWidth: 0 }}>
        <Stack spacing={{ xs: 1.5, lg: 2 }}>
          <Typography variant="h6" component="h2">Product details</Typography>
          <Grid container spacing={{ xs: 1.5, lg: 2 }}>
            <Grid item xs={12} md={4}>
              <TextInput control={form.control} name="sku" label="SKU" disabled={disabled} />
            </Grid>
            <Grid item xs={12} md={8}>
              <TextInput control={form.control} name="name" label="Name" disabled={disabled} />
            </Grid>
            <Grid item xs={12}>
              <TextInput control={form.control} name="description" label="Description" multiline minRows={2} disabled={disabled} />
            </Grid>
            <Grid item xs={12} md={6} xl={4}>
              <Controller
                name="sellableType"
                control={form.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} select label="Sellable type" disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth SelectProps={{ MenuProps: productSelectMenuProps }}>
                    {sellableTypes.map((type) => <MenuItem key={type} value={type}>{type.replaceAll('_', ' ')}</MenuItem>)}
                  </TextField>
                )}
              />
            </Grid>
            <Grid item xs={12} md={6} xl={4}>
              <ReferenceSelect control={form.control} name="categoryId" label="Category" options={categories} disabled={disabled} />
            </Grid>
            <Grid item xs={12} md={6} xl={4}>
              <ReferenceSelect control={form.control} name="brandId" label="Brand" options={brands} disabled={disabled} />
            </Grid>
            <Grid item xs={12} md={6} xl={4}>
              <ReferenceSelect control={form.control} name="unitOfMeasureId" label="Unit" options={units} disabled={disabled} showCode={false} />
            </Grid>
            <Grid item xs={12} md={6} xl={4}>
              <TextInput control={form.control} name="cost" label="Cost" type="number" disabled={disabled} />
            </Grid>
            <Grid item xs={12} md={6} xl={4}>
              <TextInput control={form.control} name="price" label="Price" type="number" disabled={disabled} />
            </Grid>
            <Grid item xs={12} md={6}>
              <TextInput control={form.control} name="imageUrl" label="Product image URL" disabled={disabled} />
            </Grid>
            <Grid item xs={12} md={6}>
              <Controller
                name="taxCategoryId"
                control={form.control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    select
                    label="Tax Category"
                    required
                    disabled={disabled || taxCategoriesLoading || Boolean(taxCategoriesError) || taxCategories.length === 0}
                    error={Boolean(fieldState.error) || Boolean(taxCategoriesError)}
                    helperText={taxCategoriesLoading
                      ? 'Loading tax categories...'
                      : taxCategoriesError
                        ? 'Unable to load tax categories.'
                        : taxCategories.length === 0
                          ? 'No tax categories are configured.'
                          : fieldState.error?.message}
                    fullWidth
                    SelectProps={{ MenuProps: productSelectMenuProps }}
                  >
                    <MenuItem value="">Select Tax Category</MenuItem>
                    {taxCategories.map((category) => <MenuItem key={category.id} value={category.id} sx={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}>{category.name}</MenuItem>)}
                  </TextField>
                )}
              />
              {taxCategoriesError ? <Button size="small" onClick={retryTaxCategories}>Retry</Button> : null}
            </Grid>
          </Grid>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <SwitchInput control={form.control} name="active" label="Active" disabled={disabled} />
            <SwitchInput control={form.control} name="inventoryTrackingEnabled" label="Track inventory" disabled={disabled} />
            <SwitchInput control={form.control} name="decimalQuantityAllowed" label="Decimal quantity" disabled={disabled} />
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
            <Controller
              name="capabilities"
              control={form.control}
              render={({ field }) => {
                const checked = field.value.includes('REQUIRE_AGE_VERIFICATION');
                return <FormControlLabel control={<Checkbox checked={checked} disabled={disabled} onChange={(_, nextChecked) => {
                  field.onChange(nextChecked
                    ? [...field.value, 'REQUIRE_AGE_VERIFICATION']
                    : field.value.filter((value) => value !== 'REQUIRE_AGE_VERIFICATION'));
                  if (!nextChecked) form.setValue('minimumAge', undefined);
                }} />} label="Age Restricted" />;
              }}
            />
            {form.watch('capabilities').includes('REQUIRE_AGE_VERIFICATION') ? (
              <Controller name="minimumAge" control={form.control} render={({ field, fieldState }) => (
                <TextField {...field} value={field.value ?? ''} onChange={(event) => field.onChange(event.target.value === '' ? undefined : Number(event.target.value))}
                  type="number" label="Minimum Age" inputProps={{ min: 1, max: 99 }} disabled={disabled}
                  error={Boolean(fieldState.error)} helperText={fieldState.error?.message} />
              )} />
            ) : null}
          </Stack>
        </Stack>
      </Paper>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: { xs: 2, lg: 3 }, minWidth: 0 }}>
        <Stack spacing={{ xs: 1.5, lg: 2 }}>
          <Typography variant="h6" component="h2">Capabilities</Typography>
          <Controller
            name="capabilities"
            control={form.control}
            render={({ field }) => (
              <FormGroup sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 1fr))', xl: 'repeat(3, minmax(0, 1fr))' }, gap: 0.5, minWidth: 0 }}>
                {productCapabilities.filter((capability) => capability !== 'REQUIRE_AGE_VERIFICATION').map((capability) => {
                  const checked = field.value.includes(capability);
                  return (
                    <FormControlLabel
                      key={capability}
                      sx={{ minWidth: 0, m: 0, '& .MuiFormControlLabel-label': { overflowWrap: 'anywhere' } }}
                      control={(
                        <Checkbox
                          checked={checked}
                          disabled={disabled}
                          onChange={(_, nextChecked) => {
                            field.onChange(nextChecked
                              ? [...field.value, capability]
                              : field.value.filter((value) => value !== capability));
                          }}
                        />
                      )}
                      label={capability.replaceAll('_', ' ')}
                    />
                  );
                })}
              </FormGroup>
            )}
          />
        </Stack>
      </Paper>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: { xs: 2, lg: 3 }, minWidth: 0 }}>
        <Stack spacing={{ xs: 1.5, lg: 2 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
            <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Variants</Typography>
            {!disabled ? (
              <Button
                type="button"
                variant="outlined"
                startIcon={<AddIcon />}
                onClick={() => variants.append({ sku: '', name: '', description: '', cost: 0, price: 0, active: true })}
              >
                Add variant
              </Button>
            ) : null}
          </Stack>
          {variants.fields.length === 0 ? <Typography color="text.secondary">No variants configured.</Typography> : null}
          {variants.fields.map((variant, index) => (
            <Paper key={variant.fieldKey} data-testid="product-variant-card" elevation={0} sx={{ width: '100%', maxWidth: '100%', minWidth: 0, border: '1px solid', borderColor: 'divider', borderRadius: 1, p: { xs: 1.5, sm: 2 } }}>
              <Grid container spacing={{ xs: 1.5, lg: 2 }} alignItems="flex-start">
                <Grid item xs={12} md={6} xl={3}>
                  <TextInput control={form.control} name={`variants.${index}.sku`} label="Variant SKU" disabled={disabled} />
                </Grid>
                <Grid item xs={12} md={6} xl={5}>
                  <TextInput control={form.control} name={`variants.${index}.name`} label="Variant name" disabled={disabled} />
                </Grid>
                <Grid item xs={12} sm={6} xl={2}>
                  <TextInput control={form.control} name={`variants.${index}.cost`} label="Cost" type="number" disabled={disabled} />
                </Grid>
                <Grid item xs={12} sm={6} xl={2}>
                  <TextInput control={form.control} name={`variants.${index}.price`} label="Price" type="number" disabled={disabled} />
                </Grid>
                <Grid item xs={12}>
                  <TextInput control={form.control} name={`variants.${index}.description`} label="Variant description" disabled={disabled} />
                </Grid>
                <Grid item xs={12}>
                  <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                    <SwitchInput control={form.control} name={`variants.${index}.active`} label="Active" disabled={disabled} />
                    {!disabled ? (
                      <Button type="button" color="error" startIcon={<DeleteIcon />} onClick={() => removeVariant(index)}>
                        Remove variant
                      </Button>
                    ) : null}
                  </Stack>
                </Grid>
              </Grid>
            </Paper>
          ))}
        </Stack>
      </Paper>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: { xs: 2, lg: 3 }, minWidth: 0 }}>
        <Stack spacing={{ xs: 1.5, lg: 2 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
            <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Barcodes</Typography>
            {!disabled ? (
              <Button
                type="button"
                variant="outlined"
                startIcon={<AddIcon />}
                onClick={() => barcodes.append({ barcode: '', variantId: undefined, variantSku: '', primaryBarcode: barcodes.fields.length === 0, active: true })}
              >
                Add barcode
              </Button>
            ) : null}
          </Stack>
          {barcodes.fields.length === 0 ? <Typography color="text.secondary">No barcodes configured.</Typography> : null}
          {barcodes.fields.map((barcode, index) => (
            <Paper key={barcode.fieldKey} data-testid="product-barcode-card" elevation={0} sx={{ width: '100%', maxWidth: '100%', minWidth: 0, border: '1px solid', borderColor: 'divider', borderRadius: 1, p: { xs: 1.5, sm: 2 } }}>
              <Grid container spacing={{ xs: 1.5, lg: 2 }} alignItems="flex-start">
                <Grid item xs={12} md={6} xl={5}>
                  <TextInput control={form.control} name={`barcodes.${index}.barcode`} label="Barcode" disabled={disabled} />
                </Grid>
                <Grid item xs={12} md={6} xl={4} sx={{ minWidth: 0 }}>
                  <Controller
                    name={`barcodes.${index}.variantId`}
                    control={form.control}
                    render={({ field, fieldState }) => (
                      <TextField
                        {...field}
                        value={field.value ?? ''}
                        onChange={(event) => {
                          const selected = String(event.target.value);
                          const option = variantOptions.find((variant) => variant.value === selected);
                          field.onChange(option?.value ?? undefined);
                          form.setValue(`barcodes.${index}.variantSku`, option?.sku ?? '', { shouldDirty: true, shouldValidate: true });
                        }}
                        select
                        label="Assign To Variant"
                        disabled={disabled}
                        error={Boolean(fieldState.error)}
                        helperText={fieldState.error?.message}
                        fullWidth
                        SelectProps={{
                          displayEmpty: true,
                          MenuProps: productSelectMenuProps,
                          renderValue: (value) => (
                            <Box component="span" sx={{ display: 'block', maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                              {value === '' ? 'Base Variant' : variantOptions.find((variant) => variant.value === value)?.label ?? 'Select variant'}
                            </Box>
                          )
                        }}
                      >
                        <MenuItem value="">Base Variant</MenuItem>
                        {variantOptions.map((variant) => <MenuItem key={variant.clientId} value={variant.value} sx={{ maxWidth: '100%', whiteSpace: 'normal', overflowWrap: 'anywhere' }}>{variant.label}</MenuItem>)}
                      </TextField>
                    )}
                  />
                </Grid>
                <Grid item xs={12} xl={3}>
                  <Stack spacing={1}>
                    <SwitchInput control={form.control} name={`barcodes.${index}.primaryBarcode`} label="Primary" disabled={disabled} />
                    <SwitchInput control={form.control} name={`barcodes.${index}.active`} label="Active" disabled={disabled} />
                  </Stack>
                </Grid>
                {!disabled ? (
                  <Grid item xs={12}>
                    <Button type="button" color="error" startIcon={<DeleteIcon />} onClick={() => barcodes.remove(index)}>
                      Remove barcode
                    </Button>
                  </Grid>
                ) : null}
              </Grid>
            </Paper>
          ))}
        </Stack>
      </Paper>

      {!disabled ? (
        <Paper
          data-testid="product-action-bar"
          elevation={4}
          square
          sx={{ position: 'sticky', bottom: 0, zIndex: 2, mx: { xs: -2, sm: 0 }, px: { xs: 2, sm: 1.5 }, py: 1.25, borderTop: '1px solid', borderColor: 'divider', bgcolor: 'background.paper' }}
        >
          <Stack direction="row" spacing={1} justifyContent="flex-end" useFlexGap flexWrap="wrap">
            <Button component={Link} to="/products" variant="outlined">Cancel</Button>
            <Button
              type="submit"
              variant="contained"
              startIcon={loading ? <CircularProgress color="inherit" size={18} /> : <SaveIcon />}
              disabled={loading}
              aria-busy={loading}
            >
              {loading ? 'Saving' : submitLabel}
            </Button>
          </Stack>
        </Paper>
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
  minRows,
  type
}: {
  control: Control<ProductFormValues>;
  name: ProductTextFieldName;
  label: string;
  disabled?: boolean;
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
          inputProps={type === 'number' ? { step: '0.0001', min: 0 } : undefined}
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

function ReferenceSelect({
  control,
  name,
  label,
  options,
  disabled,
  showCode = true
}: {
  control: Control<ProductFormValues>;
  name: 'categoryId' | 'brandId' | 'unitOfMeasureId';
  label: string;
  options: CatalogueReference[];
  disabled?: boolean;
  showCode?: boolean;
}) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField {...field} select label={label} disabled={disabled} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth SelectProps={{ MenuProps: productSelectMenuProps }}>
          <MenuItem value="">None</MenuItem>
          {options.map((option) => <MenuItem key={option.id} value={option.id} sx={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}>{showCode ? referenceLabel(option) : option.name}</MenuItem>)}
        </TextField>
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
  control: Control<ProductFormValues>;
  name: FieldPath<ProductFormValues>;
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

export function ProductsPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canCreate, canDeactivate } = useProductPermissions();
  const queryClient = useQueryClient();
  const references = useReferenceOptions(canView);
  const [filters, setFilters] = React.useState<ProductFilterForm>({
    name: '',
    sku: '',
    barcode: '',
    categoryId: '',
    brandId: '',
    active: ''
  });
  const [appliedFilters, setAppliedFilters] = React.useState<ProductFilterForm>(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);

  const categoryMap = React.useMemo(() => new Map((references.categories.data?.content ?? []).map((item) => [item.id, item])), [references.categories.data?.content]);
  const brandMap = React.useMemo(() => new Map((references.brands.data?.content ?? []).map((item) => [item.id, item])), [references.brands.data?.content]);

  const params = React.useMemo<ProductSearchParams>(() => ({
    name: optionalText(appliedFilters.name),
    sku: optionalText(appliedFilters.sku),
    barcode: optionalText(appliedFilters.barcode),
    categoryId: optionalText(appliedFilters.categoryId),
    brandId: optionalText(appliedFilters.brandId),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const products = useQuery({
    queryKey: ['products', params],
    queryFn: async () => listProducts(await getValidAccessToken(), params),
    enabled: canView
  });

  const statusMutation = useMutation({
    mutationFn: async (product: Product) => updateProductStatus(await getValidAccessToken(), product.id, {
      active: !product.active,
      version: product.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['products'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const pendingStatusProductId = statusMutation.isPending ? statusMutation.variables?.id : undefined;

  return (
    <Stack spacing={3} sx={{ width: '100%', maxWidth: '100%', minWidth: 0 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">Products</Typography>
          <Typography color="text.secondary">Sellable items, pricing, barcodes, and product behavior.</Typography>
        </Box>
        <Tooltip title="Refresh products">
          <IconButton aria-label="Refresh products" onClick={() => void products.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        {canCreate ? (
          <Button component={Link} to="/products/new" variant="contained" startIcon={<AddIcon />}>
            New product
          </Button>
        ) : null}
      </Stack>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack
          component="form"
          sx={compactFilterBarSx}
          noValidate
          aria-label="Product filters"
          onSubmit={(event) => {
            event.preventDefault();
            setPage(0);
            setAppliedFilters(filters);
          }}
        >
          <TextField label="Name" value={filters.name} onChange={(event) => setFilters((value) => ({ ...value, name: event.target.value }))} fullWidth />
          <TextField label="SKU" value={filters.sku} onChange={(event) => setFilters((value) => ({ ...value, sku: event.target.value }))} fullWidth />
          <TextField label="Barcode" value={filters.barcode} onChange={(event) => setFilters((value) => ({ ...value, barcode: event.target.value }))} fullWidth />
          <TextField select label="Category" value={filters.categoryId} onChange={(event) => setFilters((value) => ({ ...value, categoryId: event.target.value }))} fullWidth>
            <MenuItem value="">All categories</MenuItem>
            {(references.categories.data?.content ?? []).map((category) => <MenuItem key={category.id} value={category.id}>{referenceLabel(category)}</MenuItem>)}
          </TextField>
          <TextField select label="Brand" value={filters.brandId} onChange={(event) => setFilters((value) => ({ ...value, brandId: event.target.value }))} fullWidth>
            <MenuItem value="">All brands</MenuItem>
            {(references.brands.data?.content ?? []).map((brand) => <MenuItem key={brand.id} value={brand.id}>{referenceLabel(brand)}</MenuItem>)}
          </TextField>
          <TextField
            select
            label="Status"
            value={filters.active}
            onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as ProductFilterForm['active'] }))}
            fullWidth
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

      {references.categories.isError ? <Alert severity="error">{errorMessage(references.categories.error)}</Alert> : null}
      {references.brands.isError ? <Alert severity="error">{errorMessage(references.brands.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}

      <TableContainer component={Paper} elevation={0} aria-busy={products.isFetching} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflowX: 'auto' }}>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ p: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Product table</Typography>
          <Chip label={`${products.data?.totalElements ?? 0} products`} size="small" />
        </Stack>
        <Divider />
        {products.isLoading ? <LoadingPanel label="Loading products" /> : null}
        {products.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(products.error)}</Alert> : null}
        {!products.isLoading && !products.isError ? (
          <>
            <Table aria-label="Products" sx={{ minWidth: 760 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Product</TableCell>
                  <TableCell>Category</TableCell>
                  <TableCell>Brand</TableCell>
                  <TableCell align="right">Price</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(products.data?.content ?? []).map((product) => (
                  <TableRow key={product.id} hover>
                    <TableCell>
                      <Button component={Link} to={`/products/${product.id}`} sx={{ px: 0, justifyContent: 'flex-start', textAlign: 'left' }}>
                        <Stack alignItems="flex-start">
                          <Typography fontWeight={700}>{product.name}</Typography>
                          <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{product.sku}</Typography>
                          <Typography variant="body2" color="text.secondary">{product.sellableType.replaceAll('_', ' ')}</Typography>
                        </Stack>
                      </Button>
                    </TableCell>
                    <TableCell>{referenceLabel(categoryMap.get(product.categoryId ?? ''))}</TableCell>
                    <TableCell>{referenceLabel(brandMap.get(product.brandId ?? ''))}</TableCell>
                    <TableCell align="right">{formatMoney(product.price)}</TableCell>
                    <TableCell><ProductStatusChip active={product.active} /></TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Tooltip title="Open product">
                          <IconButton component={Link} to={`/products/${product.id}`} aria-label={`Open ${product.name}`}>
                            <EditIcon />
                          </IconButton>
                        </Tooltip>
                        {canDeactivate ? (
                          <Tooltip title={product.active ? 'Deactivate product' : 'Activate product'}>
                            <span>
                              <IconButton
                                aria-label={product.active ? `Deactivate ${product.name}` : `Activate ${product.name}`}
                                onClick={() => statusMutation.mutate(product)}
                                disabled={statusMutation.isPending}
                                aria-busy={pendingStatusProductId === product.id}
                              >
                                {pendingStatusProductId === product.id ? <CircularProgress color="inherit" size={20} /> : product.active ? <BlockIcon /> : <CheckCircleIcon />}
                              </IconButton>
                            </span>
                          </Tooltip>
                        ) : null}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
                {(products.data?.content.length ?? 0) === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6}>
                      <Typography color="text.secondary" textAlign="center" sx={{ py: 5 }}>
                        No products match the current filters.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={products.data?.totalElements ?? 0}
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

export function NewProductPage() {
  const navigate = useNavigate();
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const { canCreate } = useProductPermissions();
  const references = useReferenceOptions(canCreate);
  const stores = useQuery({
    queryKey: ['assigned-stores', 'product-create'],
    queryFn: async () => listAssignedStores(await getValidAccessToken()),
    enabled: canCreate
  });
  const [storeIds, setStoreIds] = React.useState<string[]>([]);
  React.useEffect(() => {
    if (stores.data?.length === 1) setStoreIds([stores.data[0].storeId]);
  }, [stores.data]);

  const mutation = useMutation({
    mutationFn: async (values: ProductFormValues) => createProduct(await getValidAccessToken(), { ...cleanPayload(values), storeIds }),
    onSuccess: async (product) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['inventory'] }),
        queryClient.invalidateQueries({ queryKey: ['pos-products'] })
      ]);
      navigate(`/products/${product.id}`);
    }
  });

  if (!canCreate) {
    return <Navigate to="/unauthorized" replace />;
  }

  const loadingOptions = references.categories.isLoading || references.brands.isLoading || references.units.isLoading;

  return (
    <Stack spacing={{ xs: 2, lg: 3 }} sx={{ width: '100%', maxWidth: '100%', minWidth: 0 }}>
      <Stack direction="row" spacing={{ xs: 1, sm: 2 }} alignItems="flex-start" sx={{ minWidth: 0 }}>
        <Tooltip title="Back to products">
          <IconButton component={Link} to="/products" aria-label="Back to products">
            <ArrowBackIcon />
          </IconButton>
        </Tooltip>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h5" component="h1">New product</Typography>
          <Typography color="text.secondary">Create a sellable item with variants, barcodes, pricing, and behavior.</Typography>
        </Box>
      </Stack>
      {loadingOptions ? <LoadingPanel label="Loading product options" /> : null}
      {references.categories.isError ? <Alert severity="error">{errorMessage(references.categories.error)}</Alert> : null}
      {references.brands.isError ? <Alert severity="error">{errorMessage(references.brands.error)}</Alert> : null}
      {references.units.isError ? <Alert severity="error">{errorMessage(references.units.error)}</Alert> : null}
      {stores.isError ? <Alert severity="error">{errorMessage(stores.error)}</Alert> : null}
      {!stores.isLoading ? (
        <Paper elevation={0} sx={{ width: '100%', maxWidth: '100%', border: '1px solid', borderColor: 'divider', borderRadius: 2, p: { xs: 1.5, lg: 3 }, minWidth: 0 }}>
          <Typography variant="h6" component="h2">Assigned stores</Typography>
          <FormGroup row sx={{ minWidth: 0 }}>
            {(stores.data ?? []).map((store) => (
              <FormControlLabel key={store.storeId} label={store.storeName} sx={{ minWidth: 0, '& .MuiFormControlLabel-label': { overflowWrap: 'anywhere' } }} control={(
                <Checkbox checked={storeIds.includes(store.storeId)} onChange={(_, checked) => setStoreIds((current) => checked
                  ? [...current, store.storeId]
                  : current.filter((id) => id !== store.storeId))} />
              )} />
            ))}
          </FormGroup>
          {storeIds.length === 0 ? <Typography color="error" variant="body2">Select at least one authorized store.</Typography> : null}
        </Paper>
      ) : null}
      {!loadingOptions ? (
        <ProductForm
          key="new-product"
          categories={references.categories.data?.content ?? []}
          brands={references.brands.data?.content ?? []}
          units={references.units.data?.content ?? []}
          taxCategories={references.taxCategories.data?.content ?? []}
          taxCategoriesLoading={references.taxCategories.isLoading}
          taxCategoriesError={references.taxCategories.isError ? errorMessage(references.taxCategories.error) : undefined}
          retryTaxCategories={() => void references.taxCategories.refetch()}
          defaultValues={{
            ...emptyProductForm,
            unitOfMeasureId: references.units.data?.content.find((unit) => unit.code === 'EA')?.id ?? ''
          }}
          submitLabel="Create product"
          loading={mutation.isPending}
          error={mutation.isError ? errorMessage(mutation.error) : undefined}
          onSubmit={(values) => { if (storeIds.length > 0) mutation.mutate(values); }}
        />
      ) : null}
    </Stack>
  );
}

export function ProductDetailPage() {
  const { id } = useParams();
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const { canView, canUpdate, canDeactivate } = useProductPermissions();
  const references = useReferenceOptions(canView);

  const product = useQuery({
    queryKey: ['product', id],
    queryFn: async () => getProduct(await getValidAccessToken(), id ?? ''),
    enabled: canView && Boolean(id)
  });

  const updateMutation = useMutation({
    mutationFn: async (values: ProductFormValues) => {
      if (!product.data || !id) {
        throw new Error('Product is not loaded');
      }
      const payload: ProductUpdatePayload = {
        ...cleanPayload(values),
        version: product.data.version
      };
      return updateProduct(await getValidAccessToken(), id, payload);
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['product', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['products'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async () => {
      if (!product.data || !id) {
        throw new Error('Product is not loaded');
      }
      return updateProductStatus(await getValidAccessToken(), id, {
        active: !product.data.active,
        version: product.data.version
      });
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['product', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['products'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  if (product.isLoading || references.categories.isLoading || references.brands.isLoading || references.units.isLoading) {
    return <LoadingPanel label="Loading product" />;
  }

  if (product.isError) {
    return (
      <Stack spacing={2} sx={{ maxWidth: 720 }}>
        <Button component={Link} to="/products" startIcon={<ArrowBackIcon />} sx={{ alignSelf: 'flex-start' }}>
          Products
        </Button>
        <Alert severity="error">{errorMessage(product.error)}</Alert>
      </Stack>
    );
  }

  if (!product.data) {
    return <Alert severity="error">Product was not found.</Alert>;
  }

  return (
    <Stack spacing={{ xs: 2, lg: 3 }} sx={{ width: '100%', maxWidth: '100%', minWidth: 0 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Button component={Link} to="/products" startIcon={<ArrowBackIcon />} sx={{ alignSelf: { xs: 'flex-start', sm: 'center' } }}>
          Products
        </Button>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">{product.data.name}</Typography>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <Typography color="text.secondary" sx={{ fontFamily: 'monospace' }}>{product.data.sku}</Typography>
            <Typography color="text.secondary">{product.data.sellableType.replaceAll('_', ' ')}</Typography>
            <ProductStatusChip active={product.data.active} />
          </Stack>
        </Box>
        {canDeactivate ? (
          <Button
            variant="outlined"
            startIcon={product.data.active ? <BlockIcon /> : <CheckCircleIcon />}
            onClick={() => statusMutation.mutate()}
            disabled={statusMutation.isPending || updateMutation.isPending}
          >
            {product.data.active ? 'Deactivate' : 'Activate'}
          </Button>
        ) : null}
      </Stack>

      {references.categories.isError ? <Alert severity="error">{errorMessage(references.categories.error)}</Alert> : null}
      {references.brands.isError ? <Alert severity="error">{errorMessage(references.brands.error)}</Alert> : null}
      {references.units.isError ? <Alert severity="error">{errorMessage(references.units.error)}</Alert> : null}
      {updateMutation.isSuccess ? <Alert severity="success">Product saved.</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}

      <ProductForm
        key={`${product.data.id}:${product.data.version}`}
        categories={references.categories.data?.content ?? []}
        brands={references.brands.data?.content ?? []}
        units={references.units.data?.content ?? []}
        taxCategories={references.taxCategories.data?.content ?? []}
        taxCategoriesLoading={references.taxCategories.isLoading}
        taxCategoriesError={references.taxCategories.isError ? errorMessage(references.taxCategories.error) : undefined}
        retryTaxCategories={() => void references.taxCategories.refetch()}
        defaultValues={productFormValues(product.data)}
        submitLabel="Save changes"
        loading={updateMutation.isPending}
        disabled={!canUpdate || statusMutation.isPending}
        error={updateMutation.isError ? errorMessage(updateMutation.error) : undefined}
        onSubmit={(values) => updateMutation.mutate(values)}
      />
    </Stack>
  );
}
