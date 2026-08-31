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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { Controller, useForm, type Control } from 'react-hook-form';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { z } from 'zod';
import {
  createStore,
  getStoreDefaults,
  getMerchantStorePricingPreview,
  getStore,
  listReferenceAdministrativeDivisions,
  listReferenceCountries,
  listReferenceCountryCurrencies,
  listReferenceDivisionTaxRegions,
  listReferenceDivisionTimezones,
  listStores,
  updateStore,
  updateStoreStatus,
  type StorePayload,
  type StoreSearchParams,
  type StoreUpdatePayload
} from '../../api/client';
import type {
  AdministrativeDivisionReference,
  CountryReference,
  CurrencyReference,
  Store,
  StoreDefaults,
  TaxRegionReference,
  TimezoneReference,
  UserRole,
  StoreCapability
} from '../../api/types';
import { compactFilterBarSx } from '../../app/responsive';
import { useSession } from '../../app/session';

type StoreFilterForm = {
  code: string;
  name: string;
  countryCode: string;
  currencyCode: string;
  active: '' | 'true' | 'false';
};

const storeSchema = z.object({
  code: z.string().trim().min(1, 'Store code is required').max(64, 'Store code must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  legalName: z.string().max(255, 'Legal name must be 255 characters or fewer').optional(),
  countryCode: z.string().trim().regex(/^[A-Za-z]{2}$/, 'Country is required'),
  administrativeAreaCode: z.string().max(32, 'Area code must be 32 characters or fewer')
    .regex(/^[A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens')
    .optional(),
  address: z.string().trim().min(1, 'Address is required').max(1000, 'Address must be 1000 characters or fewer'),
  phone: z.string().max(40, 'Phone must be 40 characters or fewer').optional(),
  email: z.string().trim().email('Enter a valid email').max(320, 'Email must be 320 characters or fewer').or(z.literal('')),
  currencyCode: z.string().trim().regex(/^[A-Za-z]{3}$/, 'Use a 3-letter currency code'),
  locale: z.string().trim().min(1, 'Locale is required').max(35, 'Locale must be 35 characters or fewer'),
  timezone: z.string().trim().min(1, 'Timezone is required').max(64, 'Timezone must be 64 characters or fewer'),
  taxRegionCode: z.string().trim().min(1, 'Tax region is required').max(64, 'Tax region must be 64 characters or fewer'),
  pricesIncludeTax: z.boolean(),
  negativeStockAllowed: z.boolean(),
  active: z.boolean()
  ,capabilities: z.array(z.enum(['RETAIL', 'FOOD_SERVICE'])).min(1, 'Select at least one operation'),
  kitchenDisplayName: z.string().max(180, 'Kitchen name must be 180 characters or fewer').optional()
});

type StoreFormValues = z.infer<typeof storeSchema>;
type StoreTextFieldName = Exclude<keyof StoreFormValues, 'pricesIncludeTax' | 'negativeStockAllowed' | 'active' | 'capabilities'>;

const emptyStoreForm: StoreFormValues = {
  code: '',
  name: '',
  legalName: '',
  countryCode: '',
  administrativeAreaCode: '',
  address: '',
  phone: '',
  email: '',
  currencyCode: '',
  locale: 'en-US',
  timezone: '',
  taxRegionCode: '',
  pricesIncludeTax: false,
  negativeStockAllowed: false,
  active: true,
  capabilities: ['RETAIL'],
  kitchenDisplayName: ''
};

function canViewStores(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER' || role === 'CASHIER');
}

function canManageStores(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function storeFormValues(store: Store): StoreFormValues {
  return {
    code: store.code,
    name: store.name,
    legalName: store.legalName ?? '',
    countryCode: store.countryCode,
    administrativeAreaCode: store.administrativeAreaCode ?? '',
    address: store.address,
    phone: store.phone ?? '',
    email: store.email ?? '',
    currencyCode: store.currencyCode,
    locale: store.locale,
    timezone: store.timezone,
    taxRegionCode: store.taxRegionCode ?? '',
    pricesIncludeTax: store.pricesIncludeTax,
    negativeStockAllowed: store.negativeStockAllowed,
    active: store.active,
    capabilities: store.capabilities ?? ['RETAIL'],
    kitchenDisplayName: store.kitchenDisplayName ?? ''
  };
}

function storeDefaultsForm(defaults?: StoreDefaults): StoreFormValues {
  return {
    ...emptyStoreForm,
    countryCode: defaults?.countryCode ?? '',
    administrativeAreaCode: defaults?.administrativeDivisionCode ?? '',
    currencyCode: defaults?.currencyCode ?? '',
    locale: defaults?.locale ?? 'en-US',
    timezone: defaults?.timezone ?? '',
    taxRegionCode: defaults?.taxRegionCode ?? '',
    capabilities: defaults?.capabilities ?? ['RETAIL'],
    kitchenDisplayName: defaults?.kitchenDisplayName ?? ''
  };
}

function cleanPayload(values: StoreFormValues): StorePayload {
  return {
    code: values.code.trim(),
    name: values.name.trim(),
    legalName: optionalText(values.legalName),
    countryCode: values.countryCode.trim().toUpperCase(),
    administrativeAreaCode: optionalText(values.administrativeAreaCode)?.toUpperCase(),
    administrativeDivisionCode: optionalText(values.administrativeAreaCode)?.toUpperCase(),
    address: values.address.trim(),
    phone: optionalText(values.phone),
    email: optionalText(values.email)?.toLowerCase(),
    currencyCode: values.currencyCode.trim().toUpperCase(),
    locale: values.locale.trim().replace('_', '-'),
    timezone: values.timezone.trim(),
    taxRegionCode: optionalText(values.taxRegionCode)?.toUpperCase(),
    pricesIncludeTax: values.pricesIncludeTax,
    negativeStockAllowed: values.negativeStockAllowed,
    active: values.active,
    capabilities: values.capabilities,
    kitchenDisplayName: values.capabilities.includes('FOOD_SERVICE') ? optionalText(values.kitchenDisplayName) : undefined
  };
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function useStorePermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  return {
    canView: canViewStores(roles),
    canManage: canManageStores(roles),
    canCurrencyOverride: roles.includes('OWNER') || roles.includes('TENANT_OWNER')
  };
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }} role="status" aria-live="polite">
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function StoreStatusChip({ active }: { active: boolean }) {
  return (
    <Chip
      label={active ? 'Active' : 'Inactive'}
      color={active ? 'success' : 'default'}
      size="small"
    />
  );
}

function StoreForm({
  defaultValues,
  submitLabel,
  loading,
  error,
  disabled,
  showChangeWarnings,
  canCurrencyOverride,
  onSubmit
}: {
  defaultValues: StoreFormValues;
  submitLabel: string;
  loading: boolean;
  error?: string;
  disabled?: boolean;
  showChangeWarnings?: boolean;
  canCurrencyOverride?: boolean;
  onSubmit: (values: StoreFormValues) => void;
}) {
  const { getValidAccessToken } = useSession();
  const form = useForm<StoreFormValues>({
    resolver: zodResolver(storeSchema),
    defaultValues,
    values: defaultValues
  });
  const countryCode = form.watch('countryCode');
  const administrativeAreaCode = form.watch('administrativeAreaCode');
  const currencyCode = form.watch('currencyCode');
  const timezone = form.watch('timezone');
  const taxRegionCode = form.watch('taxRegionCode');
  const capabilities = form.watch('capabilities');
  const pricingPreview = useQuery({queryKey:['store-pricing-preview',capabilities.includes('FOOD_SERVICE')],queryFn:async()=>getMerchantStorePricingPreview(await getValidAccessToken(),capabilities.includes('FOOD_SERVICE'))});

  const countries = useQuery({
    queryKey: ['reference', 'countries'],
    queryFn: async () => listReferenceCountries(await getValidAccessToken())
  });
  const divisions = useQuery({
    queryKey: ['reference', 'administrative-divisions', countryCode],
    queryFn: async () => listReferenceAdministrativeDivisions(await getValidAccessToken(), countryCode),
    enabled: Boolean(countryCode)
  });
  const currencies = useQuery({
    queryKey: ['reference', 'currencies', countryCode],
    queryFn: async () => listReferenceCountryCurrencies(await getValidAccessToken(), countryCode),
    enabled: Boolean(countryCode)
  });

  const selectedCountry = countries.data?.find((country) => country.alpha2Code === countryCode);
  const selectedDivision = divisions.data?.find((division) => division.code === administrativeAreaCode);

  const timezones = useQuery({
    queryKey: ['reference', 'timezones', selectedDivision?.id],
    queryFn: async () => listReferenceDivisionTimezones(await getValidAccessToken(), selectedDivision?.id ?? ''),
    enabled: Boolean(selectedDivision?.id)
  });
  const taxRegions = useQuery({
    queryKey: ['reference', 'tax-regions', selectedDivision?.id],
    queryFn: async () => listReferenceDivisionTaxRegions(await getValidAccessToken(), selectedDivision?.id ?? ''),
    enabled: Boolean(selectedDivision?.id)
  });

  React.useEffect(() => {
    if (!currencyCode && currencies.data?.[0]) {
      form.setValue('currencyCode', currencies.data[0].code, { shouldDirty: true, shouldValidate: true });
    }
  }, [currencies.data, currencyCode, form]);

  React.useEffect(() => {
    if (!timezone) {
      const defaultTimezone = timezones.data?.find((item) => item.defaultForDivision) ?? timezones.data?.[0];
      if (defaultTimezone) {
        form.setValue('timezone', defaultTimezone.ianaName, { shouldDirty: true, shouldValidate: true });
      }
    }
  }, [form, timezone, timezones.data]);

  React.useEffect(() => {
    if (!taxRegionCode) {
      const defaultTaxRegion = taxRegions.data?.find((item) => item.defaultForDivision) ?? taxRegions.data?.[0];
      if (defaultTaxRegion) {
        form.setValue('taxRegionCode', defaultTaxRegion.code, { shouldDirty: true, shouldValidate: true });
      }
    }
  }, [form, taxRegionCode, taxRegions.data]);

  const referenceError = countries.error ?? divisions.error ?? currencies.error ?? timezones.error ?? taxRegions.error;
  const divisionLabel = selectedCountry?.alpha2Code === 'CA'
    ? 'Province / Territory'
    : selectedCountry?.alpha2Code === 'US'
      ? 'State'
      : 'Province / Territory / State';
  const changingCountry = showChangeWarnings && defaultValues.countryCode && countryCode !== defaultValues.countryCode;
  const changingCurrency = showChangeWarnings && defaultValues.currencyCode && currencyCode !== defaultValues.currencyCode;
  const changingTimezone = showChangeWarnings && defaultValues.timezone && timezone !== defaultValues.timezone;
  const changingTaxRegion = showChangeWarnings && defaultValues.taxRegionCode && taxRegionCode !== defaultValues.taxRegionCode;

  return (
    <Stack component="form" spacing={3} noValidate aria-busy={loading} onSubmit={form.handleSubmit(onSubmit)}>
      {error ? <Alert severity="error">{error}</Alert> : null}
      {referenceError ? <Alert severity="error">{errorMessage(referenceError)}</Alert> : null}
      {disabled ? <Alert severity="info">This account can view stores but cannot change store settings.</Alert> : null}
      {changingCountry ? <Alert severity="warning">Changing country clears incompatible province/state, timezone, and tax-region selections.</Alert> : null}
      {changingCurrency ? <Alert severity="warning">Changing currency affects future transactions only and may require elevated approval when transactions exist.</Alert> : null}
      {changingTimezone ? <Alert severity="warning">Changing timezone affects future reporting boundaries and does not rewrite historical timestamps.</Alert> : null}
      {changingTaxRegion ? <Alert severity="warning">Changing tax region affects future tax selection and does not rewrite historical sales or reports.</Alert> : null}

      <Grid container spacing={2}>
        <Grid item xs={12} sm={4}>
          <TextInput control={form.control} name="code" label="Code" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={8}>
          <TextInput control={form.control} name="name" label="Name" disabled={disabled} />
        </Grid>
        <Grid item xs={12}>
          <TextInput control={form.control} name="legalName" label="Legal name" disabled={disabled} />
        </Grid>
        <Grid item xs={12}>
          <TextInput control={form.control} name="address" label="Address" multiline minRows={3} disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={4}>
          <SelectInput
            control={form.control}
            name="countryCode"
            label="Country"
            disabled={disabled || countries.isLoading}
            loading={countries.isLoading}
            options={(countries.data ?? []).map(countryOption)}
            onValueChange={() => {
              form.setValue('administrativeAreaCode', '', { shouldDirty: true, shouldValidate: true });
              form.setValue('currencyCode', '', { shouldDirty: true, shouldValidate: true });
              form.setValue('timezone', '', { shouldDirty: true, shouldValidate: true });
              form.setValue('taxRegionCode', '', { shouldDirty: true, shouldValidate: true });
            }}
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <SelectInput
            control={form.control}
            name="administrativeAreaCode"
            label={divisionLabel}
            disabled={disabled || !countryCode || divisions.isLoading}
            loading={divisions.isLoading}
            emptyLabel={countryCode ? 'Select a value' : 'Select country first'}
            options={(divisions.data ?? []).map(divisionOption)}
            onValueChange={() => {
              form.setValue('timezone', '', { shouldDirty: true, shouldValidate: true });
              form.setValue('taxRegionCode', '', { shouldDirty: true, shouldValidate: true });
            }}
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <SelectInput
            control={form.control}
            name="currencyCode"
            label="Currency"
            disabled={disabled || !countryCode || currencies.isLoading || !canCurrencyOverride}
            loading={currencies.isLoading}
            options={(currencies.data ?? []).map(currencyOption)}
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <TextInput control={form.control} name="locale" label="Locale" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6}>
          <SelectInput
            control={form.control}
            name="timezone"
            label="Timezone"
            disabled={disabled || !selectedDivision || timezones.isLoading}
            loading={timezones.isLoading}
            emptyLabel={selectedDivision ? 'Select a timezone' : `Select ${divisionLabel.toLowerCase()} first`}
            options={(timezones.data ?? []).map(timezoneOption)}
          />
        </Grid>
        <Grid item xs={12}>
          <SelectInput
            control={form.control}
            name="taxRegionCode"
            label="Tax Region"
            disabled={disabled || !selectedDivision || taxRegions.isLoading}
            loading={taxRegions.isLoading}
            emptyLabel={selectedDivision ? 'Select a tax region' : `Select ${divisionLabel.toLowerCase()} first`}
            options={(taxRegions.data ?? []).map(taxRegionOption)}
          />
        </Grid>
        <Grid item xs={12} sm={6}>
          <TextInput control={form.control} name="phone" label="Phone" disabled={disabled} />
        </Grid>
        <Grid item xs={12} sm={6}>
          <TextInput control={form.control} name="email" label="Email" disabled={disabled} />
        </Grid>
      </Grid>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="h6">Store Operations</Typography>
        <Typography color="text.secondary" sx={{ mb: 1 }}>What does this location operate?</Typography>
        <Controller name="capabilities" control={form.control} render={({ field, fieldState }) => (
          <Stack>
            {([['RETAIL', 'Convenience / Retail Store', 'Barcode scanning, retail inventory and standard POS'], ['FOOD_SERVICE', 'Kitchen / Food Service', 'Food menu, tile POS and kitchen operations']] as const).map(([value, label, description]) => (
              <FormControlLabel key={value} control={<Checkbox checked={field.value.includes(value)} disabled={disabled} onChange={(_, checked) => field.onChange(checked ? [...field.value, value] : field.value.filter((item: StoreCapability) => item !== value))} />} label={<Box><Typography>{label}</Typography><Typography variant="body2" color="text.secondary">{description}</Typography></Box>} />
            ))}
            {fieldState.error ? <Typography color="error" variant="caption">{fieldState.error.message}</Typography> : null}
          </Stack>
        )} />
        {capabilities.includes('FOOD_SERVICE') ? <Box sx={{ mt: 2 }}><TextInput control={form.control} name="kitchenDisplayName" label="Kitchen / Food Service Name" disabled={disabled} /></Box> : null}
        {pricingPreview.data ? <Alert severity={capabilities.includes('FOOD_SERVICE')?'warning':'info'} sx={{mt:2}}>{capabilities.includes('FOOD_SERVICE')?'This location includes the Food Service add-on. ':''}Additional Store: {new Intl.NumberFormat(undefined,{style:'currency',currency:pricingPreview.data.currency}).format(pricingPreview.data.additionalStoreMonthlyPrice)}/month. {pricingPreview.data.capabilityCharges.map(charge=>`${charge.description}: +${new Intl.NumberFormat(undefined,{style:'currency',currency:pricingPreview.data.currency}).format(charge.monthlyPricePerStore)} / ${charge.billingUnit.replace('PER_','').toLowerCase()} / month. `)}Estimated Monthly Subscription: {new Intl.NumberFormat(undefined,{style:'currency',currency:pricingPreview.data.currency}).format(pricingPreview.data.estimatedMonthlySubscription)}.</Alert>:null}
      </Paper>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <Controller
          name="pricesIncludeTax"
          control={form.control}
          render={({ field }) => (
            <FormControlLabel
              control={<Switch checked={field.value} disabled={disabled} onChange={(_, checked) => field.onChange(checked)} />}
              label="Prices include tax"
            />
          )}
        />
        <Controller
          name="negativeStockAllowed"
          control={form.control}
          render={({ field }) => (
            <FormControlLabel
              control={<Switch checked={field.value} disabled={disabled} onChange={(_, checked) => field.onChange(checked)} />}
              label="Negative stock"
            />
          )}
        />
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
      </Stack>

      {!disabled ? (
        <Button
          type="submit"
          variant="contained"
          startIcon={loading ? <CircularProgress color="inherit" size={18} /> : <SaveIcon />}
          disabled={loading}
          aria-busy={loading}
          sx={{ alignSelf: 'flex-start' }}
        >
          {loading ? 'Saving' : submitLabel}
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
  control: Control<StoreFormValues>;
  name: StoreTextFieldName;
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

type SelectOption = {
  value: string;
  label: string;
};

function SelectInput({
  control,
  name,
  label,
  disabled,
  loading,
  options,
  emptyLabel = 'Select a value',
  onValueChange
}: {
  control: Control<StoreFormValues>;
  name: StoreTextFieldName;
  label: string;
  disabled?: boolean;
  loading?: boolean;
  options: SelectOption[];
  emptyLabel?: string;
  onValueChange?: (value: string) => void;
}) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState }) => (
        <TextField
          {...field}
          value={field.value ?? ''}
          select
          label={label}
          disabled={disabled}
          error={Boolean(fieldState.error)}
          helperText={fieldState.error?.message ?? (loading ? 'Loading options' : options.length === 0 ? emptyLabel : undefined)}
          fullWidth
          onChange={(event) => {
            field.onChange(event);
            onValueChange?.(event.target.value);
          }}
        >
          <MenuItem value="">{loading ? 'Loading...' : emptyLabel}</MenuItem>
          {options.map((option) => (
            <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
          ))}
        </TextField>
      )}
    />
  );
}

function countryOption(country: CountryReference): SelectOption {
  return { value: country.alpha2Code, label: `${country.name} (${country.alpha2Code})` };
}

function divisionOption(division: AdministrativeDivisionReference): SelectOption {
  return { value: division.code, label: `${division.name} (${division.code})` };
}

function currencyOption(currency: CurrencyReference): SelectOption {
  return { value: currency.code, label: `${currency.code} - ${currency.name} (${currency.symbol})` };
}

function timezoneOption(timezone: TimezoneReference): SelectOption {
  return { value: timezone.ianaName, label: `${timezone.ianaName} - ${timezone.displayName}` };
}

function taxRegionOption(region: TaxRegionReference): SelectOption {
  return { value: region.code, label: `${region.code} - ${region.name}` };
}

export function StoresPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useStorePermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState<StoreFilterForm>({
    code: '',
    name: '',
    countryCode: '',
    currencyCode: '',
    active: ''
  });
  const [appliedFilters, setAppliedFilters] = React.useState<StoreFilterForm>(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);

  const params = React.useMemo<StoreSearchParams>(() => ({
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    countryCode: optionalText(appliedFilters.countryCode),
    currencyCode: optionalText(appliedFilters.currencyCode),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const stores = useQuery({
    queryKey: ['stores', params],
    queryFn: async () => listStores(await getValidAccessToken(), params),
    enabled: canView
  });

  const statusMutation = useMutation({
    mutationFn: async (store: Store) => updateStoreStatus(await getValidAccessToken(), store.id, {
      active: !store.active,
      version: store.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['stores'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const pendingStatusStoreId = statusMutation.isPending ? statusMutation.variables?.id : undefined;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">Stores</Typography>
          <Typography color="text.secondary">Locations, operating defaults, and availability.</Typography>
        </Box>
        <Tooltip title="Refresh stores">
          <IconButton aria-label="Refresh stores" onClick={() => void stores.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
        {canManage ? (
          <Button component={Link} to="/stores/new" variant="contained" startIcon={<AddIcon />}>
            New store
          </Button>
        ) : null}
      </Stack>

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack
          component="form"
          sx={compactFilterBarSx}
          noValidate
          aria-label="Store filters"
          onSubmit={(event) => {
            event.preventDefault();
            setPage(0);
            setAppliedFilters(filters);
          }}
        >
          <TextField label="Code" value={filters.code} onChange={(event) => setFilters((value) => ({ ...value, code: event.target.value }))} fullWidth />
          <TextField label="Name" value={filters.name} onChange={(event) => setFilters((value) => ({ ...value, name: event.target.value }))} fullWidth />
          <TextField label="Country" value={filters.countryCode} onChange={(event) => setFilters((value) => ({ ...value, countryCode: event.target.value }))} fullWidth />
          <TextField label="Currency" value={filters.currencyCode} onChange={(event) => setFilters((value) => ({ ...value, currencyCode: event.target.value }))} fullWidth />
          <TextField
            select
            label="Status"
            value={filters.active}
            onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as StoreFilterForm['active'] }))}
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

      <TableContainer component={Paper} elevation={0} aria-busy={stores.isFetching} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, overflowX: 'auto' }}>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ p: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Store directory</Typography>
          <Chip label={`${stores.data?.totalElements ?? 0} stores`} size="small" />
        </Stack>
        <Divider />
        {stores.isLoading ? <LoadingPanel label="Loading stores" /> : null}
        {stores.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(stores.error)}</Alert> : null}
        {!stores.isLoading && !stores.isError ? (
          <>
            <Table aria-label="Stores" sx={{ minWidth: 680 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Store</TableCell>
                  <TableCell>Region</TableCell>
                  <TableCell>Currency</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(stores.data?.content ?? []).map((store) => (
                  <TableRow key={store.id} hover>
                    <TableCell>
                      <Button component={Link} to={`/stores/${store.id}`} sx={{ px: 0, justifyContent: 'flex-start', textAlign: 'left' }}>
                        <Stack alignItems="flex-start">
                          <Typography fontWeight={700}>{store.name}</Typography>
                          <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{store.code}</Typography>
                        </Stack>
                      </Button>
                    </TableCell>
                    <TableCell>{[store.administrativeAreaCode, store.countryCode].filter(Boolean).join(', ')}</TableCell>
                    <TableCell>{store.currencyCode}</TableCell>
                    <TableCell><StoreStatusChip active={store.active} /></TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Tooltip title="Open store">
                          <IconButton component={Link} to={`/stores/${store.id}`} aria-label={`Open ${store.name}`}>
                            <EditIcon />
                          </IconButton>
                        </Tooltip>
                        {canManage ? (
                          <Tooltip title={store.active ? 'Deactivate store' : 'Activate store'}>
                            <span>
                              <IconButton
                                aria-label={store.active ? `Deactivate ${store.name}` : `Activate ${store.name}`}
                                onClick={() => statusMutation.mutate(store)}
                                disabled={statusMutation.isPending}
                                aria-busy={pendingStatusStoreId === store.id}
                              >
                                {pendingStatusStoreId === store.id ? <CircularProgress color="inherit" size={20} /> : store.active ? <BlockIcon /> : <CheckCircleIcon />}
                              </IconButton>
                            </span>
                          </Tooltip>
                        ) : null}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
                {(stores.data?.content.length ?? 0) === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Typography color="text.secondary" textAlign="center" sx={{ py: 5 }}>
                        No stores match the current filters.
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : null}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={stores.data?.totalElements ?? 0}
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

      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
    </Stack>
  );
}

export function NewStorePage() {
  const navigate = useNavigate();
  const { getValidAccessToken } = useSession();
  const { canManage, canCurrencyOverride } = useStorePermissions();
  const defaults = useQuery({
    queryKey: ['store-defaults'],
    queryFn: async () => getStoreDefaults(await getValidAccessToken()),
    enabled: canManage
  });

  const mutation = useMutation({
    mutationFn: async (values: StoreFormValues) => createStore(await getValidAccessToken(), cleanPayload(values)),
    onSuccess: (store) => navigate(`/stores/${store.id}`)
  });

  if (!canManage) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 980 }}>
      <Stack direction="row" spacing={2} alignItems="center">
        <Tooltip title="Back to stores">
          <IconButton component={Link} to="/stores" aria-label="Back to stores">
            <ArrowBackIcon />
          </IconButton>
        </Tooltip>
        <Box>
          <Typography variant="h5" component="h1">New store</Typography>
          <Typography color="text.secondary">Create a store location and operating defaults.</Typography>
        </Box>
      </Stack>

      {defaults.isLoading ? <CircularProgress aria-label="Loading store defaults" /> : null}
      {defaults.isError ? <Alert severity="warning">{errorMessage(defaults.error)}</Alert> : null}
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        {!defaults.isLoading ? (
          <StoreForm
            defaultValues={storeDefaultsForm(defaults.data)}
            submitLabel="Create store"
            loading={mutation.isPending}
            error={mutation.isError ? errorMessage(mutation.error) : undefined}
            canCurrencyOverride={canCurrencyOverride}
            onSubmit={(values) => mutation.mutate(values)}
          />
        ) : null}
      </Paper>
    </Stack>
  );
}

export function StoreDetailPage() {
  const { id } = useParams();
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const { canView, canManage, canCurrencyOverride } = useStorePermissions();

  const store = useQuery({
    queryKey: ['store', id],
    queryFn: async () => getStore(await getValidAccessToken(), id ?? ''),
    enabled: canView && Boolean(id)
  });

  const updateMutation = useMutation({
    mutationFn: async (values: StoreFormValues) => {
      if (!store.data || !id) {
        throw new Error('Store is not loaded');
      }
      const payload: StoreUpdatePayload = {
        ...cleanPayload(values),
        version: store.data.version
      };
      return updateStore(await getValidAccessToken(), id, payload);
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['store', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['stores'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async () => {
      if (!store.data || !id) {
        throw new Error('Store is not loaded');
      }
      return updateStoreStatus(await getValidAccessToken(), id, {
        active: !store.data.active,
        version: store.data.version
      });
    },
    onSuccess: async (updated) => {
      queryClient.setQueryData(['store', updated.id], updated);
      await queryClient.invalidateQueries({ queryKey: ['stores'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  if (store.isLoading) {
    return <LoadingPanel label="Loading store" />;
  }

  if (store.isError) {
    return (
      <Stack spacing={2} sx={{ maxWidth: 720 }}>
        <Button component={Link} to="/stores" startIcon={<ArrowBackIcon />} sx={{ alignSelf: 'flex-start' }}>
          Stores
        </Button>
        <Alert severity="error">{errorMessage(store.error)}</Alert>
      </Stack>
    );
  }

  if (!store.data) {
    return <Alert severity="error">Store was not found.</Alert>;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 1080 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Button component={Link} to="/stores" startIcon={<ArrowBackIcon />} sx={{ alignSelf: { xs: 'flex-start', sm: 'center' } }}>
          Stores
        </Button>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">{store.data.name}</Typography>
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography color="text.secondary" sx={{ fontFamily: 'monospace' }}>{store.data.code}</Typography>
            <StoreStatusChip active={store.data.active} />
          </Stack>
        </Box>
        {canManage ? (
          <Button
            variant="outlined"
            startIcon={store.data.active ? <BlockIcon /> : <CheckCircleIcon />}
            onClick={() => statusMutation.mutate()}
            disabled={statusMutation.isPending || updateMutation.isPending}
          >
            {store.data.active ? 'Deactivate' : 'Activate'}
          </Button>
        ) : null}
      </Stack>

      {updateMutation.isSuccess ? <Alert severity="success">Store saved.</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6">Operations</Typography>
            <Typography>{storeTypeLabel(store.data.capabilities)}</Typography>
            <Typography color="text.secondary">Retail: {(store.data.capabilities ?? ['RETAIL']).includes('RETAIL') ? 'Enabled' : 'Disabled'}</Typography>
            <Typography color="text.secondary">Kitchen / Food Service: {store.data.foodServiceEnabled ? 'Enabled' : 'Disabled'}</Typography>
            {store.data.foodServiceEnabled ? <><Typography color="text.secondary">Kitchen Name: {store.data.kitchenDisplayName}</Typography><Typography color="text.secondary">Restaurant POS: Enabled</Typography><Typography color="text.secondary">Kitchen Users: {store.data.kitchenUsersCount}</Typography></> : null}
          </Box>
          <Divider />
          <Box>
            <Typography variant="h6" component="h2">Store settings</Typography>
            <Typography color="text.secondary">Version {store.data.version}</Typography>
          </Box>
          <StoreForm
            defaultValues={storeFormValues(store.data)}
            submitLabel="Save changes"
            loading={updateMutation.isPending}
            disabled={!canManage || statusMutation.isPending}
            showChangeWarnings
            canCurrencyOverride={canCurrencyOverride}
            error={updateMutation.isError ? errorMessage(updateMutation.error) : undefined}
            onSubmit={(values) => updateMutation.mutate(values)}
          />
        </Stack>
      </Paper>
    </Stack>
  );
}

export function storeTypeLabel(capabilities: StoreCapability[] = ['RETAIL']) {
  if (capabilities.includes('RETAIL') && capabilities.includes('FOOD_SERVICE')) return 'Convenience Store + Kitchen';
  if (capabilities.includes('FOOD_SERVICE')) return 'Restaurant / Food Service';
  return 'Convenience Store';
}
