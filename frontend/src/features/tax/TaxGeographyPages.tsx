import AddIcon from '@mui/icons-material/Add';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import EditIcon from '@mui/icons-material/Edit';
import PublicOutlinedIcon from '@mui/icons-material/PublicOutlined';
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
  Tab,
  Tabs,
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
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import {
  createAdministrativeArea,
  createCountry,
  createProductTaxCategoryAssignment,
  createTaxComponent,
  createTaxCategory,
  createTaxGroup,
  createTaxGroupComponent,
  createTaxRate,
  createTaxRule,
  createTaxType,
  createTaxJurisdiction,
  listAdministrativeAreas,
  listCountries,
  listProducts,
  listProductTaxCategoryAssignments,
  listTaxCategories,
  listTaxComponents,
  listTaxGroups,
  listTaxGroupComponents,
  listTaxRates,
  listTaxRules,
  listTaxTypes,
  listTaxJurisdictions,
  type AdministrativeAreaPayload,
  type AdministrativeAreaSearchParams,
  type AdministrativeAreaUpdatePayload,
  type CountryPayload,
  type CountrySearchParams,
  type CountryUpdatePayload,
  type ProductTaxCategoryAssignmentPayload,
  type ProductTaxCategoryAssignmentSearchParams,
  type ProductTaxCategoryAssignmentUpdatePayload,
  type TaxCategoryPayload,
  type TaxCategorySearchParams,
  type TaxCategoryUpdatePayload,
  type TaxComponentPayload,
  type TaxComponentSearchParams,
  type TaxComponentUpdatePayload,
  type TaxGroupComponentPayload,
  type TaxGroupComponentSearchParams,
  type TaxGroupComponentUpdatePayload,
  type TaxGroupPayload,
  type TaxGroupSearchParams,
  type TaxGroupUpdatePayload,
  type TaxRatePayload,
  type TaxRateSearchParams,
  type TaxRateUpdatePayload,
  type TaxRuleActionPayload,
  type TaxRuleConditionPayload,
  type TaxRulePayload,
  type TaxRuleSearchParams,
  type TaxRuleUpdatePayload,
  type TaxTypePayload,
  type TaxTypeSearchParams,
  type TaxTypeUpdatePayload,
  type TaxJurisdictionPayload,
  type TaxJurisdictionSearchParams,
  type TaxJurisdictionUpdatePayload,
  updateAdministrativeArea,
  updateAdministrativeAreaStatus,
  updateCountry,
  updateCountryStatus,
  updateProductTaxCategoryAssignment,
  updateProductTaxCategoryAssignmentStatus,
  updateTaxCategory,
  updateTaxCategoryStatus,
  updateTaxComponent,
  updateTaxComponentStatus,
  updateTaxGroup,
  updateTaxGroupComponent,
  updateTaxGroupComponentStatus,
  updateTaxGroupStatus,
  updateTaxRate,
  updateTaxRateStatus,
  updateTaxRule,
  updateTaxRuleStatus,
  updateTaxType,
  updateTaxTypeStatus,
  updateTaxJurisdiction,
  updateTaxJurisdictionStatus
} from '../../api/client';
import type {
  AdministrativeArea,
  AdministrativeAreaType,
  Country,
  Product,
  ProductTaxCategoryAssignment,
  TaxCategory,
  TaxComponent,
  TaxGroup,
  TaxGroupComponent,
  TaxRate,
  TaxRateStatus,
  TaxRule,
  TaxRuleActionType,
  TaxRuleConditionOperator,
  TaxRuleConditionType,
  TaxTreatment,
  TaxType,
  TaxJurisdiction,
  TaxJurisdictionType,
  UserRole
} from '../../api/types';
import { useSession } from '../../app/session';

type TaxPageKind = 'rules' | 'categories' | 'groups' | 'groupComponents' | 'assignments' | 'types' | 'components' | 'rates' | 'countries' | 'areas' | 'jurisdictions';

const areaTypes: AdministrativeAreaType[] = ['PROVINCE', 'TERRITORY', 'STATE', 'REGION', 'COUNTY', 'MUNICIPAL', 'LOCAL', 'SPECIAL'];
const jurisdictionTypes: TaxJurisdictionType[] = ['NATIONAL', 'PROVINCIAL', 'TERRITORIAL', 'STATE', 'REGIONAL', 'COUNTY', 'MUNICIPAL', 'LOCAL', 'SPECIAL'];
const rateStatuses: TaxRateStatus[] = ['DRAFT', 'SCHEDULED', 'ACTIVE', 'RETIRED'];
const taxTreatments: TaxTreatment[] = ['STANDARD', 'REDUCED', 'ZERO_RATED', 'EXEMPT', 'OUT_OF_SCOPE', 'SPECIAL'];
const conditionTypes: TaxRuleConditionType[] = ['STORE_JURISDICTION', 'SUPPLY_JURISDICTION', 'PRODUCT_TAX_CATEGORY', 'PRODUCT', 'CUSTOMER_EXEMPTION', 'TRANSACTION_DATE', 'SALE_CHANNEL'];
const conditionOperators: TaxRuleConditionOperator[] = ['EQUALS', 'NOT_EQUALS', 'IN', 'IS_TRUE', 'IS_FALSE', 'ON_OR_AFTER', 'ON_OR_BEFORE', 'BETWEEN'];
const actionTypes: TaxRuleActionType[] = ['APPLY_TAX_GROUP', 'APPLY_TAX_COMPONENT', 'EXCLUDE_COMPONENT', 'ZERO_RATE', 'EXEMPT', 'OUT_OF_SCOPE', 'INCLUDED_PRICE_BEHAVIOR', 'ROUNDING_STRATEGY'];

const countrySchema = z.object({
  code: z.string().trim().min(2, 'Use a two-letter country code').max(2, 'Use a two-letter country code').regex(/^[A-Za-z]{2}$/, 'Use a two-letter country code'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  active: z.boolean()
});

const areaSchema = z.object({
  countryId: z.string().min(1, 'Country is required'),
  code: z.string().trim().min(1, 'Code is required').max(16, 'Code must be 16 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  type: z.enum(areaTypes as [AdministrativeAreaType, ...AdministrativeAreaType[]]),
  active: z.boolean()
});

const jurisdictionSchema = z.object({
  countryId: z.string().min(1, 'Country is required'),
  administrativeAreaId: z.string().optional(),
  code: z.string().trim().min(1, 'Code is required').max(64, 'Code must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  type: z.enum(jurisdictionTypes as [TaxJurisdictionType, ...TaxJurisdictionType[]]),
  active: z.boolean()
}).refine((values) => values.type !== 'NATIONAL' || !values.administrativeAreaId, {
  message: 'National jurisdictions cannot use an administrative area',
  path: ['administrativeAreaId']
});

const taxTypeSchema = z.object({
  code: z.string().trim().min(1, 'Code is required').max(32, 'Code must be 32 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  description: z.string().max(1000, 'Description must be 1000 characters or fewer').optional(),
  active: z.boolean()
});

const taxComponentSchema = z.object({
  taxTypeId: z.string().min(1, 'Tax type is required'),
  taxJurisdictionId: z.string().min(1, 'Jurisdiction is required'),
  code: z.string().trim().min(1, 'Code is required').max(64, 'Code must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  description: z.string().max(1000, 'Description must be 1000 characters or fewer').optional(),
  active: z.boolean()
});

const taxRateSchema = z.object({
  taxComponentId: z.string().min(1, 'Tax component is required'),
  percentageRate: z.coerce.number().min(0, 'Percentage rate must be zero or greater'),
  effectiveFrom: z.string().min(1, 'Effective from is required'),
  effectiveTo: z.string().optional(),
  includedInPrice: z.boolean(),
  compoundOnPreviousTax: z.boolean(),
  calculationOrder: z.coerce.number().int('Calculation order must be a whole number').min(0, 'Calculation order must be zero or greater'),
  status: z.enum(rateStatuses as [TaxRateStatus, ...TaxRateStatus[]]),
  source: z.string().max(180, 'Source must be 180 characters or fewer').optional(),
  sourceReference: z.string().max(500, 'Source reference must be 500 characters or fewer').optional(),
  verifiedBy: z.string().max(180, 'Verified by must be 180 characters or fewer').optional(),
  verifiedAt: z.string().optional()
}).refine((values) => !values.effectiveTo || values.effectiveTo >= values.effectiveFrom, {
  message: 'Effective to must be on or after effective from',
  path: ['effectiveTo']
});

const taxGroupSchema = z.object({
  code: z.string().trim().min(1, 'Code is required').max(64, 'Code must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  description: z.string().max(1000, 'Description must be 1000 characters or fewer').optional(),
  active: z.boolean()
});

const taxGroupComponentSchema = z.object({
  taxGroupId: z.string().min(1, 'Tax group is required'),
  taxComponentId: z.string().min(1, 'Tax component is required'),
  calculationOrder: z.coerce.number().int('Calculation order must be a whole number').min(0, 'Calculation order must be zero or greater'),
  active: z.boolean()
});

const taxCategorySchema = z.object({
  taxGroupId: z.string().optional(),
  code: z.string().trim().min(1, 'Code is required').max(64, 'Code must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  treatment: z.enum(taxTreatments as [TaxTreatment, ...TaxTreatment[]]),
  description: z.string().max(1000, 'Description must be 1000 characters or fewer').optional(),
  active: z.boolean()
});

const productTaxCategoryAssignmentSchema = z.object({
  productId: z.string().min(1, 'Product is required'),
  taxCategoryId: z.string().min(1, 'Tax category is required'),
  active: z.boolean()
});

const taxRuleConditionSchema = z.object({
  conditionType: z.enum(conditionTypes as [TaxRuleConditionType, ...TaxRuleConditionType[]]),
  operator: z.enum(conditionOperators as [TaxRuleConditionOperator, ...TaxRuleConditionOperator[]]),
  value: z.string().max(180, 'Value must be 180 characters or fewer').optional(),
  secondValue: z.string().max(180, 'Second value must be 180 characters or fewer').optional()
});

const taxRuleActionSchema = z.object({
  actionType: z.enum(actionTypes as [TaxRuleActionType, ...TaxRuleActionType[]]),
  taxGroupId: z.string().optional(),
  taxComponentId: z.string().optional(),
  value: z.string().max(180, 'Value must be 180 characters or fewer').optional()
});

const taxRuleSchema = z.object({
  code: z.string().trim().min(1, 'Code is required').max(64, 'Code must be 64 characters or fewer')
    .regex(/^[A-Za-z0-9][A-Za-z0-9_-]*$/, 'Use letters, numbers, underscores, and hyphens'),
  name: z.string().trim().min(1, 'Name is required').max(180, 'Name must be 180 characters or fewer'),
  description: z.string().max(1000, 'Description must be 1000 characters or fewer').optional(),
  priority: z.coerce.number().int('Priority must be a whole number').min(0, 'Priority must be zero or greater'),
  effectiveFrom: z.string().min(1, 'Effective from is required'),
  effectiveTo: z.string().optional(),
  active: z.boolean(),
  conditions: z.array(taxRuleConditionSchema),
  actions: z.array(taxRuleActionSchema).min(1, 'At least one action is required')
}).refine((values) => !values.effectiveTo || values.effectiveTo >= values.effectiveFrom, {
  message: 'Effective to must be on or after effective from',
  path: ['effectiveTo']
});

type CountryFormValues = z.infer<typeof countrySchema>;
type AreaFormValues = z.infer<typeof areaSchema>;
type JurisdictionFormValues = z.infer<typeof jurisdictionSchema>;
type TaxTypeFormValues = z.infer<typeof taxTypeSchema>;
type TaxComponentFormValues = z.infer<typeof taxComponentSchema>;
type TaxRateFormValues = z.infer<typeof taxRateSchema>;
type TaxGroupFormValues = z.infer<typeof taxGroupSchema>;
type TaxGroupComponentFormValues = z.infer<typeof taxGroupComponentSchema>;
type TaxCategoryFormValues = z.infer<typeof taxCategorySchema>;
type ProductTaxCategoryAssignmentFormValues = z.infer<typeof productTaxCategoryAssignmentSchema>;
type TaxRuleFormValues = z.infer<typeof taxRuleSchema>;

type FilterForm = {
  code: string;
  name: string;
  countryId: string;
  type: string;
  active: '' | 'true' | 'false';
};

const emptyFilters: FilterForm = {
  code: '',
  name: '',
  countryId: '',
  type: '',
  active: ''
};

function canViewTax(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function useTaxPermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  return {
    canView: canViewTax(roles),
    canManage: canViewTax(roles)
  };
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function statusChip(active: boolean) {
  return <Chip label={active ? 'Active' : 'Inactive'} color={active ? 'success' : 'default'} size="small" />;
}

function displayEnum(value: string) {
  return value.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 240 }}>
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function useCountryOptions(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['tax-countries-options'],
    queryFn: async () => listCountries(await getValidAccessToken(), { active: true, size: 100 }),
    enabled
  });
}

function useAreaOptions(enabled: boolean, countryId?: string) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['tax-areas-options', countryId ?? 'all'],
    queryFn: async () => listAdministrativeAreas(await getValidAccessToken(), { countryId: optionalText(countryId), active: true, size: 100 }),
    enabled
  });
}

function useTaxTypeOptions(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['tax-types-options'],
    queryFn: async () => listTaxTypes(await getValidAccessToken(), { active: true, size: 100 }),
    enabled
  });
}

function useJurisdictionOptions(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['tax-jurisdictions-options'],
    queryFn: async () => listTaxJurisdictions(await getValidAccessToken(), { active: true, size: 100 }),
    enabled
  });
}

function useComponentOptions(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['tax-components-options'],
    queryFn: async () => listTaxComponents(await getValidAccessToken(), { active: true, size: 100 }),
    enabled
  });
}

function useTaxGroupOptions(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['tax-groups-options'],
    queryFn: async () => listTaxGroups(await getValidAccessToken(), { active: true, size: 100 }),
    enabled
  });
}

function useTaxCategoryOptions(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['tax-categories-options'],
    queryFn: async () => listTaxCategories(await getValidAccessToken(), { active: true, size: 100 }),
    enabled
  });
}

function useProductOptions(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['products-options'],
    queryFn: async () => listProducts(await getValidAccessToken(), { active: true, size: 100 }),
    enabled
  });
}

function TaxTabs({ current }: { current: TaxPageKind }) {
  const navigate = useNavigate();
  const routes: Record<TaxPageKind, string> = {
    rules: '/tax/rules',
    categories: '/tax/categories',
    groups: '/tax/groups',
    groupComponents: '/tax/group-components',
    assignments: '/tax/product-category-assignments',
    types: '/tax/types',
    components: '/tax/components',
    rates: '/tax/rates',
    countries: '/tax/countries',
    areas: '/tax/administrative-areas',
    jurisdictions: '/tax/jurisdictions'
  };

  return (
    <Tabs value={routes[current]} onChange={(_, next) => navigate(next)} aria-label="Tax administration sections" variant="scrollable" allowScrollButtonsMobile>
      <Tab label="Rules" value="/tax/rules" />
      <Tab label="Categories" value="/tax/categories" />
      <Tab label="Groups" value="/tax/groups" />
      <Tab label="Group components" value="/tax/group-components" />
      <Tab label="Product assignments" value="/tax/product-category-assignments" />
      <Tab label="Types" value="/tax/types" />
      <Tab label="Components" value="/tax/components" />
      <Tab label="Rates" value="/tax/rates" />
      <Tab label="Countries" value="/tax/countries" />
      <Tab label="Administrative areas" value="/tax/administrative-areas" />
      <Tab label="Jurisdictions" value="/tax/jurisdictions" />
    </Tabs>
  );
}

function PageHeader({ title, subtitle, current }: { title: string; subtitle: string; current: TaxPageKind }) {
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1.5} alignItems="center">
        <PublicOutlinedIcon color="primary" />
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" component="h1">{title}</Typography>
          <Typography color="text.secondary">{subtitle}</Typography>
        </Box>
      </Stack>
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <TaxTabs current={current} />
      </Paper>
    </Stack>
  );
}

function CountryDialog({
  open,
  country,
  loading,
  error,
  onClose,
  onSubmit
}: {
  open: boolean;
  country: Country | null;
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: CountryFormValues) => void;
}) {
  const form = useForm<CountryFormValues>({
    resolver: zodResolver(countrySchema),
    defaultValues: countryFormValues(country),
    values: countryFormValues(country)
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{country ? 'Edit country' : 'New country'}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="country-form" spacing={2} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
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
            name="active"
            control={form.control}
            render={({ field }) => (
              <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Active" />
            )}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form="country-form" variant="contained" startIcon={<SaveIcon />} disabled={loading}>
          {country ? 'Save changes' : 'Create country'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function countryFormValues(country?: Country | null): CountryFormValues {
  return {
    code: country?.code ?? '',
    name: country?.name ?? '',
    active: country?.active ?? true
  };
}

function cleanCountry(values: CountryFormValues): CountryPayload {
  return {
    code: values.code.trim().toUpperCase(),
    name: values.name.trim(),
    active: values.active
  };
}

export function CountriesPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useTaxPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState<FilterForm>(emptyFilters);
  const [appliedFilters, setAppliedFilters] = React.useState<FilterForm>(emptyFilters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<Country | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);

  const params = React.useMemo<CountrySearchParams>(() => ({
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const countries = useQuery({
    queryKey: ['tax-countries', params],
    queryFn: async () => listCountries(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: CountryFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: CountryUpdatePayload = { ...cleanCountry(values), version: editing.version };
        return updateCountry(token, editing.id, payload);
      }
      return createCountry(token, cleanCountry(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['tax-countries'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-countries-options'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (country: Country) => updateCountryStatus(await getValidAccessToken(), country.id, {
      active: !country.active,
      version: country.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tax-countries'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-countries-options'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <PageHeader title="Countries" subtitle="Country records used by tax geography and store setup." current="countries" />

      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack component="form" direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ p: 2 }} onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setAppliedFilters(filters);
        }}>
          <TextField label="Code" value={filters.code} onChange={(event) => setFilters((value) => ({ ...value, code: event.target.value }))} />
          <TextField label="Name" value={filters.name} onChange={(event) => setFilters((value) => ({ ...value, name: event.target.value }))} />
          <TextField select label="Status" value={filters.active} onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as FilterForm['active'] }))} sx={{ minWidth: 150 }}>
            <MenuItem value="">Any</MenuItem>
            <MenuItem value="true">Active</MenuItem>
            <MenuItem value="false">Inactive</MenuItem>
          </TextField>
          <Button type="submit" variant="outlined" startIcon={<SearchIcon />} sx={{ minWidth: 112 }}>Search</Button>
        </Stack>
      </Paper>

      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}

      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ p: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>Country list</Typography>
          <Chip label={`${countries.data?.totalElements ?? 0} records`} size="small" />
          <Tooltip title="Refresh countries">
            <IconButton aria-label="Refresh countries" onClick={() => void countries.refetch()}><RefreshIcon /></IconButton>
          </Tooltip>
          {canManage ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setDialogOpen(true); }}>New country</Button> : null}
        </Stack>
        <Divider />
        {countries.isLoading ? <LoadingPanel label="Loading countries" /> : null}
        {countries.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(countries.error)}</Alert> : null}
        {!countries.isLoading && !countries.isError ? (
          <>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Code</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(countries.data?.content ?? []).map((country) => (
                  <TableRow key={country.id} hover>
                    <TableCell sx={{ fontWeight: 700 }}>{country.code}</TableCell>
                    <TableCell>{country.name}</TableCell>
                    <TableCell>{statusChip(country.active)}</TableCell>
                    <TableCell align="right">
                      {canManage ? (
                        <>
                          <Tooltip title={`Edit ${country.name}`}>
                            <IconButton aria-label={`Edit ${country.name}`} onClick={() => { setEditing(country); setDialogOpen(true); }}>
                              <EditIcon />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title={country.active ? `Deactivate ${country.name}` : `Activate ${country.name}`}>
                            <IconButton aria-label={`${country.active ? 'Deactivate' : 'Activate'} ${country.name}`} disabled={statusMutation.isPending} onClick={() => statusMutation.mutate(country)}>
                              {country.active ? <BlockIcon /> : <CheckCircleIcon />}
                            </IconButton>
                          </Tooltip>
                        </>
                      ) : null}
                    </TableCell>
                  </TableRow>
                ))}
                {(countries.data?.content ?? []).length === 0 ? (
                  <TableRow><TableCell colSpan={4}><Typography color="text.secondary" align="center" sx={{ py: 4 }}>No countries found.</Typography></TableCell></TableRow>
                ) : null}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={countries.data?.totalElements ?? 0}
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

      <CountryDialog
        open={dialogOpen}
        country={editing}
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

function areaFormValues(area?: AdministrativeArea | null, defaultCountryId = ''): AreaFormValues {
  return {
    countryId: area?.countryId ?? defaultCountryId,
    code: area?.code ?? '',
    name: area?.name ?? '',
    type: area?.type ?? 'PROVINCE',
    active: area?.active ?? true
  };
}

function cleanArea(values: AreaFormValues): AdministrativeAreaPayload {
  return {
    countryId: values.countryId,
    code: values.code.trim().toUpperCase(),
    name: values.name.trim(),
    type: values.type,
    active: values.active
  };
}

function AreaDialog({
  open,
  area,
  countries,
  loading,
  error,
  onClose,
  onSubmit
}: {
  open: boolean;
  area: AdministrativeArea | null;
  countries: Country[];
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: AreaFormValues) => void;
}) {
  const form = useForm<AreaFormValues>({
    resolver: zodResolver(areaSchema),
    defaultValues: areaFormValues(area, countries[0]?.id ?? ''),
    values: areaFormValues(area, countries[0]?.id ?? '')
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{area ? 'Edit administrative area' : 'New administrative area'}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="area-form" spacing={2} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Controller
            name="countryId"
            control={form.control}
            render={({ field, fieldState }) => (
              <TextField {...field} select label="Country" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                {countries.map((country) => <MenuItem key={country.id} value={country.id}>{country.code} - {country.name}</MenuItem>)}
              </TextField>
            )}
          />
          <Controller name="code" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Code" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="name" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Name" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="type" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} select label="Type" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              {areaTypes.map((type) => <MenuItem key={type} value={type}>{displayEnum(type)}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="active" control={form.control} render={({ field }) => (
            <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Active" />
          )} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form="area-form" variant="contained" startIcon={<SaveIcon />} disabled={loading || countries.length === 0}>
          {area ? 'Save changes' : 'Create area'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function AdministrativeAreasPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useTaxPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState<FilterForm>(emptyFilters);
  const [appliedFilters, setAppliedFilters] = React.useState<FilterForm>(emptyFilters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<AdministrativeArea | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const countryOptions = useCountryOptions(canView);

  const params = React.useMemo<AdministrativeAreaSearchParams>(() => ({
    countryId: optionalText(appliedFilters.countryId),
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    type: optionalText(appliedFilters.type) as AdministrativeAreaType | undefined,
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const areas = useQuery({
    queryKey: ['tax-areas', params],
    queryFn: async () => listAdministrativeAreas(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: AreaFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: AdministrativeAreaUpdatePayload = { ...cleanArea(values), version: editing.version };
        return updateAdministrativeArea(token, editing.id, payload);
      }
      return createAdministrativeArea(token, cleanArea(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['tax-areas'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-areas-options'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (area: AdministrativeArea) => updateAdministrativeAreaStatus(await getValidAccessToken(), area.id, {
      active: !area.active,
      version: area.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tax-areas'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-areas-options'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const countries = countryOptions.data?.content ?? [];
  const countryName = (id: string) => countries.find((country) => country.id === id)?.code ?? id;

  return (
    <Stack spacing={3}>
      <PageHeader title="Administrative areas" subtitle="Province, territory, state, regional, county, municipal, local, and special areas." current="areas" />
      <TaxFilterPanel
        filters={filters}
        countries={countries}
        types={areaTypes}
        showCountry
        showType
        onChange={setFilters}
        onSubmit={() => {
          setPage(0);
          setAppliedFilters(filters);
        }}
      />
      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <ListHeader title="Administrative area list" count={areas.data?.totalElements ?? 0} refreshLabel="Refresh administrative areas" onRefresh={() => void areas.refetch()}>
          {canManage ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setDialogOpen(true); }}>New area</Button> : null}
        </ListHeader>
        {areas.isLoading || countryOptions.isLoading ? <LoadingPanel label="Loading administrative areas" /> : null}
        {areas.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(areas.error)}</Alert> : null}
        {!areas.isLoading && !countryOptions.isLoading && !areas.isError ? (
          <TaxTable
            rows={areas.data?.content ?? []}
            columns={[
              { label: 'Country', value: (area) => countryName(area.countryId) },
              { label: 'Code', value: (area) => area.code, strong: true },
              { label: 'Name', value: (area) => area.name },
              { label: 'Type', value: (area) => displayEnum(area.type) },
              { label: 'Status', value: (area) => statusChip(area.active) }
            ]}
            canManage={canManage}
            emptyLabel="No administrative areas found."
            onEdit={(area) => { setEditing(area); setDialogOpen(true); }}
            onStatus={(area) => statusMutation.mutate(area)}
            statusPending={statusMutation.isPending}
          />
        ) : null}
        <TablePagination
          component="div"
          count={areas.data?.totalElements ?? 0}
          page={page}
          rowsPerPage={size}
          onPageChange={(_, nextPage) => setPage(nextPage)}
          onRowsPerPageChange={(event) => {
            setSize(Number(event.target.value));
            setPage(0);
          }}
        />
      </TableContainer>
      <AreaDialog
        open={dialogOpen}
        area={editing}
        countries={countries}
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

function jurisdictionFormValues(jurisdiction?: TaxJurisdiction | null, defaultCountryId = ''): JurisdictionFormValues {
  return {
    countryId: jurisdiction?.countryId ?? defaultCountryId,
    administrativeAreaId: jurisdiction?.administrativeAreaId ?? '',
    code: jurisdiction?.code ?? '',
    name: jurisdiction?.name ?? '',
    type: jurisdiction?.type ?? 'NATIONAL',
    active: jurisdiction?.active ?? true
  };
}

function cleanJurisdiction(values: JurisdictionFormValues): TaxJurisdictionPayload {
  return {
    countryId: values.countryId,
    administrativeAreaId: optionalText(values.administrativeAreaId),
    code: values.code.trim().toUpperCase(),
    name: values.name.trim(),
    type: values.type,
    active: values.active
  };
}

function JurisdictionDialog({
  open,
  jurisdiction,
  countries,
  areas,
  loading,
  error,
  onClose,
  onSubmit
}: {
  open: boolean;
  jurisdiction: TaxJurisdiction | null;
  countries: Country[];
  areas: AdministrativeArea[];
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: JurisdictionFormValues) => void;
}) {
  const form = useForm<JurisdictionFormValues>({
    resolver: zodResolver(jurisdictionSchema),
    defaultValues: jurisdictionFormValues(jurisdiction, countries[0]?.id ?? ''),
    values: jurisdictionFormValues(jurisdiction, countries[0]?.id ?? '')
  });
  const selectedCountryId = form.watch('countryId');
  const availableAreas = areas.filter((area) => area.countryId === selectedCountryId);

  React.useEffect(() => {
    const currentAreaId = form.getValues('administrativeAreaId');
    if (currentAreaId && !availableAreas.some((area) => area.id === currentAreaId)) {
      form.setValue('administrativeAreaId', '');
    }
  }, [availableAreas, form]);

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{jurisdiction ? 'Edit tax jurisdiction' : 'New tax jurisdiction'}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="jurisdiction-form" spacing={2} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Controller name="countryId" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} select label="Country" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              {countries.map((country) => <MenuItem key={country.id} value={country.id}>{country.code} - {country.name}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="administrativeAreaId" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} select label="Administrative area" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              <MenuItem value="">None</MenuItem>
              {availableAreas.map((area) => <MenuItem key={area.id} value={area.id}>{area.code} - {area.name}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="code" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Code" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="name" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Name" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="type" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} select label="Type" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              {jurisdictionTypes.map((type) => <MenuItem key={type} value={type}>{displayEnum(type)}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="active" control={form.control} render={({ field }) => (
            <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Active" />
          )} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form="jurisdiction-form" variant="contained" startIcon={<SaveIcon />} disabled={loading || countries.length === 0}>
          {jurisdiction ? 'Save changes' : 'Create jurisdiction'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function TaxJurisdictionsPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useTaxPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState<FilterForm>(emptyFilters);
  const [appliedFilters, setAppliedFilters] = React.useState<FilterForm>(emptyFilters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<TaxJurisdiction | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const countryOptions = useCountryOptions(canView);
  const areaOptions = useAreaOptions(canView);

  const params = React.useMemo<TaxJurisdictionSearchParams>(() => ({
    countryId: optionalText(appliedFilters.countryId),
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    type: optionalText(appliedFilters.type) as TaxJurisdictionType | undefined,
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const jurisdictions = useQuery({
    queryKey: ['tax-jurisdictions', params],
    queryFn: async () => listTaxJurisdictions(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: JurisdictionFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: TaxJurisdictionUpdatePayload = { ...cleanJurisdiction(values), version: editing.version };
        return updateTaxJurisdiction(token, editing.id, payload);
      }
      return createTaxJurisdiction(token, cleanJurisdiction(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['tax-jurisdictions'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (jurisdiction: TaxJurisdiction) => updateTaxJurisdictionStatus(await getValidAccessToken(), jurisdiction.id, {
      active: !jurisdiction.active,
      version: jurisdiction.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tax-jurisdictions'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const countries = countryOptions.data?.content ?? [];
  const areas = areaOptions.data?.content ?? [];
  const countryName = (id: string) => countries.find((country) => country.id === id)?.code ?? id;
  const areaName = (id: string | null) => id ? areas.find((area) => area.id === id)?.code ?? id : '-';

  return (
    <Stack spacing={3}>
      <PageHeader title="Tax jurisdictions" subtitle="Jurisdiction records for tax configuration; calculation rules are added later." current="jurisdictions" />
      <TaxFilterPanel
        filters={filters}
        countries={countries}
        types={jurisdictionTypes}
        showCountry
        showType
        onChange={setFilters}
        onSubmit={() => {
          setPage(0);
          setAppliedFilters(filters);
        }}
      />
      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <ListHeader title="Tax jurisdiction list" count={jurisdictions.data?.totalElements ?? 0} refreshLabel="Refresh tax jurisdictions" onRefresh={() => void jurisdictions.refetch()}>
          {canManage ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setDialogOpen(true); }}>New jurisdiction</Button> : null}
        </ListHeader>
        {jurisdictions.isLoading || countryOptions.isLoading || areaOptions.isLoading ? <LoadingPanel label="Loading tax jurisdictions" /> : null}
        {jurisdictions.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(jurisdictions.error)}</Alert> : null}
        {!jurisdictions.isLoading && !countryOptions.isLoading && !areaOptions.isLoading && !jurisdictions.isError ? (
          <TaxTable
            rows={jurisdictions.data?.content ?? []}
            columns={[
              { label: 'Country', value: (jurisdiction) => countryName(jurisdiction.countryId) },
              { label: 'Area', value: (jurisdiction) => areaName(jurisdiction.administrativeAreaId) },
              { label: 'Code', value: (jurisdiction) => jurisdiction.code, strong: true },
              { label: 'Name', value: (jurisdiction) => jurisdiction.name },
              { label: 'Type', value: (jurisdiction) => displayEnum(jurisdiction.type) },
              { label: 'Status', value: (jurisdiction) => statusChip(jurisdiction.active) }
            ]}
            canManage={canManage}
            emptyLabel="No tax jurisdictions found."
            onEdit={(jurisdiction) => { setEditing(jurisdiction); setDialogOpen(true); }}
            onStatus={(jurisdiction) => statusMutation.mutate(jurisdiction)}
            statusPending={statusMutation.isPending}
          />
        ) : null}
        <TablePagination
          component="div"
          count={jurisdictions.data?.totalElements ?? 0}
          page={page}
          rowsPerPage={size}
          onPageChange={(_, nextPage) => setPage(nextPage)}
          onRowsPerPageChange={(event) => {
            setSize(Number(event.target.value));
            setPage(0);
          }}
        />
      </TableContainer>
      <JurisdictionDialog
        open={dialogOpen}
        jurisdiction={editing}
        countries={countries}
        areas={areas}
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

function TaxFilterPanel({
  filters,
  countries,
  types,
  showCountry,
  showType,
  onChange,
  onSubmit
}: {
  filters: FilterForm;
  countries: Country[];
  types: string[];
  showCountry: boolean;
  showType: boolean;
  onChange: React.Dispatch<React.SetStateAction<FilterForm>>;
  onSubmit: () => void;
}) {
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
      <Stack component="form" direction={{ xs: 'column', lg: 'row' }} spacing={2} sx={{ p: 2 }} onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}>
        {showCountry ? (
          <TextField select label="Country" value={filters.countryId} onChange={(event) => onChange((value) => ({ ...value, countryId: event.target.value }))} sx={{ minWidth: 210 }}>
            <MenuItem value="">Any</MenuItem>
            {countries.map((country) => <MenuItem key={country.id} value={country.id}>{country.code} - {country.name}</MenuItem>)}
          </TextField>
        ) : null}
        <TextField label="Code" value={filters.code} onChange={(event) => onChange((value) => ({ ...value, code: event.target.value }))} />
        <TextField label="Name" value={filters.name} onChange={(event) => onChange((value) => ({ ...value, name: event.target.value }))} />
        {showType ? (
          <TextField select label="Type" value={filters.type} onChange={(event) => onChange((value) => ({ ...value, type: event.target.value }))} sx={{ minWidth: 190 }}>
            <MenuItem value="">Any</MenuItem>
            {types.map((type) => <MenuItem key={type} value={type}>{displayEnum(type)}</MenuItem>)}
          </TextField>
        ) : null}
        <TextField select label="Status" value={filters.active} onChange={(event) => onChange((value) => ({ ...value, active: event.target.value as FilterForm['active'] }))} sx={{ minWidth: 150 }}>
          <MenuItem value="">Any</MenuItem>
          <MenuItem value="true">Active</MenuItem>
          <MenuItem value="false">Inactive</MenuItem>
        </TextField>
        <Button type="submit" variant="outlined" startIcon={<SearchIcon />} sx={{ minWidth: 112 }}>Search</Button>
      </Stack>
    </Paper>
  );
}

function ListHeader({ title, count, refreshLabel, onRefresh, children }: {
  title: string;
  count: number;
  refreshLabel: string;
  onRefresh: () => void;
  children: React.ReactNode;
}) {
  return (
    <>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ p: 2 }}>
        <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>{title}</Typography>
        <Chip label={`${count} records`} size="small" />
        <Tooltip title={refreshLabel}>
          <IconButton aria-label={refreshLabel} onClick={onRefresh}><RefreshIcon /></IconButton>
        </Tooltip>
        {children}
      </Stack>
      <Divider />
    </>
  );
}

function TaxTable<T extends { id: string; name: string; active: boolean }>({
  rows,
  columns,
  canManage,
  emptyLabel,
  onEdit,
  onStatus,
  statusPending
}: {
  rows: T[];
  columns: Array<{ label: string; value: (row: T) => React.ReactNode; strong?: boolean }>;
  canManage: boolean;
  emptyLabel: string;
  onEdit: (row: T) => void;
  onStatus: (row: T) => void;
  statusPending: boolean;
}) {
  return (
    <Table>
      <TableHead>
        <TableRow>
          {columns.map((column) => <TableCell key={column.label}>{column.label}</TableCell>)}
          <TableCell align="right">Actions</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.id} hover>
            {columns.map((column) => (
              <TableCell key={column.label} sx={column.strong ? { fontWeight: 700 } : undefined}>{column.value(row)}</TableCell>
            ))}
            <TableCell align="right">
              {canManage ? (
                <>
                  <Tooltip title={`Edit ${row.name}`}>
                    <IconButton aria-label={`Edit ${row.name}`} onClick={() => onEdit(row)}>
                      <EditIcon />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title={row.active ? `Deactivate ${row.name}` : `Activate ${row.name}`}>
                    <IconButton aria-label={`${row.active ? 'Deactivate' : 'Activate'} ${row.name}`} disabled={statusPending} onClick={() => onStatus(row)}>
                      {row.active ? <BlockIcon /> : <CheckCircleIcon />}
                    </IconButton>
                  </Tooltip>
                </>
              ) : null}
            </TableCell>
          </TableRow>
        ))}
        {rows.length === 0 ? (
          <TableRow>
            <TableCell colSpan={columns.length + 1}>
              <Typography color="text.secondary" align="center" sx={{ py: 4 }}>{emptyLabel}</Typography>
            </TableCell>
          </TableRow>
        ) : null}
      </TableBody>
    </Table>
  );
}

function cleanTaxType(values: TaxTypeFormValues): TaxTypePayload {
  return {
    code: values.code.trim().toUpperCase(),
    name: values.name.trim(),
    description: optionalText(values.description),
    active: values.active
  };
}

function taxTypeValues(type?: TaxType | null): TaxTypeFormValues {
  return {
    code: type?.code ?? '',
    name: type?.name ?? '',
    description: type?.description ?? '',
    active: type?.active ?? true
  };
}

function TaxTypeDialog({ open, taxType, loading, error, onClose, onSubmit }: {
  open: boolean;
  taxType: TaxType | null;
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: TaxTypeFormValues) => void;
}) {
  const form = useForm<TaxTypeFormValues>({
    resolver: zodResolver(taxTypeSchema),
    defaultValues: taxTypeValues(taxType),
    values: taxTypeValues(taxType)
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{taxType ? 'Edit tax type' : 'New tax type'}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="tax-type-form" spacing={2} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Controller name="code" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Code" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="name" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Name" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="description" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} value={field.value ?? ''} label="Description" multiline minRows={3} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="active" control={form.control} render={({ field }) => (
            <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Active" />
          )} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form="tax-type-form" variant="contained" startIcon={<SaveIcon />} disabled={loading}>
          {taxType ? 'Save changes' : 'Create type'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function TaxTypesPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useTaxPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState<FilterForm>(emptyFilters);
  const [appliedFilters, setAppliedFilters] = React.useState<FilterForm>(emptyFilters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<TaxType | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);

  const params = React.useMemo<TaxTypeSearchParams>(() => ({
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const types = useQuery({
    queryKey: ['tax-types', params],
    queryFn: async () => listTaxTypes(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: TaxTypeFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: TaxTypeUpdatePayload = { ...cleanTaxType(values), version: editing.version };
        return updateTaxType(token, editing.id, payload);
      }
      return createTaxType(token, cleanTaxType(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['tax-types'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-types-options'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (type: TaxType) => updateTaxTypeStatus(await getValidAccessToken(), type.id, {
      active: !type.active,
      version: type.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tax-types'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-types-options'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <PageHeader title="Tax types" subtitle="Reusable tax classifications such as GST, HST, VAT, or sales tax." current="types" />
      <TaxFilterPanel filters={filters} countries={[]} types={[]} showCountry={false} showType={false} onChange={setFilters} onSubmit={() => {
        setPage(0);
        setAppliedFilters(filters);
      }} />
      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <ListHeader title="Tax type list" count={types.data?.totalElements ?? 0} refreshLabel="Refresh tax types" onRefresh={() => void types.refetch()}>
          {canManage ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setDialogOpen(true); }}>New type</Button> : null}
        </ListHeader>
        {types.isLoading ? <LoadingPanel label="Loading tax types" /> : null}
        {types.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(types.error)}</Alert> : null}
        {!types.isLoading && !types.isError ? (
          <TaxTable
            rows={types.data?.content ?? []}
            columns={[
              { label: 'Code', value: (type) => type.code, strong: true },
              { label: 'Name', value: (type) => type.name },
              { label: 'Description', value: (type) => type.description ?? '-' },
              { label: 'Status', value: (type) => statusChip(type.active) }
            ]}
            canManage={canManage}
            emptyLabel="No tax types found."
            onEdit={(type) => { setEditing(type); setDialogOpen(true); }}
            onStatus={(type) => statusMutation.mutate(type)}
            statusPending={statusMutation.isPending}
          />
        ) : null}
        <TablePagination component="div" count={types.data?.totalElements ?? 0} page={page} rowsPerPage={size} onPageChange={(_, nextPage) => setPage(nextPage)} onRowsPerPageChange={(event) => {
          setSize(Number(event.target.value));
          setPage(0);
        }} />
      </TableContainer>
      <TaxTypeDialog open={dialogOpen} taxType={editing} loading={saveMutation.isPending} error={saveMutation.isError ? errorMessage(saveMutation.error) : undefined} onClose={() => {
        if (!saveMutation.isPending) {
          setDialogOpen(false);
          setEditing(null);
        }
      }} onSubmit={(values) => saveMutation.mutate(values)} />
    </Stack>
  );
}

function cleanTaxComponent(values: TaxComponentFormValues): TaxComponentPayload {
  return {
    taxTypeId: values.taxTypeId,
    taxJurisdictionId: values.taxJurisdictionId,
    code: values.code.trim().toUpperCase(),
    name: values.name.trim(),
    description: optionalText(values.description),
    active: values.active
  };
}

function taxComponentValues(component?: TaxComponent | null, defaultTypeId = '', defaultJurisdictionId = ''): TaxComponentFormValues {
  return {
    taxTypeId: component?.taxTypeId ?? defaultTypeId,
    taxJurisdictionId: component?.taxJurisdictionId ?? defaultJurisdictionId,
    code: component?.code ?? '',
    name: component?.name ?? '',
    description: component?.description ?? '',
    active: component?.active ?? true
  };
}

function TaxComponentDialog({ open, component, taxTypes, jurisdictions, loading, error, onClose, onSubmit }: {
  open: boolean;
  component: TaxComponent | null;
  taxTypes: TaxType[];
  jurisdictions: TaxJurisdiction[];
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: TaxComponentFormValues) => void;
}) {
  const form = useForm<TaxComponentFormValues>({
    resolver: zodResolver(taxComponentSchema),
    defaultValues: taxComponentValues(component, taxTypes[0]?.id ?? '', jurisdictions[0]?.id ?? ''),
    values: taxComponentValues(component, taxTypes[0]?.id ?? '', jurisdictions[0]?.id ?? '')
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{component ? 'Edit tax component' : 'New tax component'}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="tax-component-form" spacing={2} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Controller name="taxTypeId" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} select label="Tax type" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              {taxTypes.map((type) => <MenuItem key={type.id} value={type.id}>{type.code} - {type.name}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="taxJurisdictionId" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} select label="Jurisdiction" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              {jurisdictions.map((jurisdiction) => <MenuItem key={jurisdiction.id} value={jurisdiction.id}>{jurisdiction.code} - {jurisdiction.name}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="code" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Code" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="name" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Name" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="description" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} value={field.value ?? ''} label="Description" multiline minRows={3} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="active" control={form.control} render={({ field }) => (
            <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Active" />
          )} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form="tax-component-form" variant="contained" startIcon={<SaveIcon />} disabled={loading || taxTypes.length === 0 || jurisdictions.length === 0}>
          {component ? 'Save changes' : 'Create component'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function TaxComponentsPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useTaxPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState({ taxTypeId: '', taxJurisdictionId: '', code: '', name: '', active: '' as '' | 'true' | 'false' });
  const [appliedFilters, setAppliedFilters] = React.useState(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<TaxComponent | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const taxTypeOptions = useTaxTypeOptions(canView);
  const jurisdictionOptions = useJurisdictionOptions(canView);

  const params = React.useMemo<TaxComponentSearchParams>(() => ({
    taxTypeId: optionalText(appliedFilters.taxTypeId),
    taxJurisdictionId: optionalText(appliedFilters.taxJurisdictionId),
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const components = useQuery({
    queryKey: ['tax-components', params],
    queryFn: async () => listTaxComponents(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: TaxComponentFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: TaxComponentUpdatePayload = { ...cleanTaxComponent(values), version: editing.version };
        return updateTaxComponent(token, editing.id, payload);
      }
      return createTaxComponent(token, cleanTaxComponent(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['tax-components'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-components-options'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (component: TaxComponent) => updateTaxComponentStatus(await getValidAccessToken(), component.id, {
      active: !component.active,
      version: component.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tax-components'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-components-options'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const taxTypes = taxTypeOptions.data?.content ?? [];
  const jurisdictions = jurisdictionOptions.data?.content ?? [];
  const typeName = (id: string) => taxTypes.find((type) => type.id === id)?.code ?? id;
  const jurisdictionName = (id: string) => jurisdictions.find((jurisdiction) => jurisdiction.id === id)?.code ?? id;

  return (
    <Stack spacing={3}>
      <PageHeader title="Tax components" subtitle="Tax components connect a tax type to the jurisdiction where it applies." current="components" />
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack component="form" direction={{ xs: 'column', lg: 'row' }} spacing={2} sx={{ p: 2 }} onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setAppliedFilters(filters);
        }}>
          <TextField select label="Tax type" value={filters.taxTypeId} onChange={(event) => setFilters((value) => ({ ...value, taxTypeId: event.target.value }))} sx={{ minWidth: 190 }}>
            <MenuItem value="">Any</MenuItem>
            {taxTypes.map((type) => <MenuItem key={type.id} value={type.id}>{type.code} - {type.name}</MenuItem>)}
          </TextField>
          <TextField select label="Jurisdiction" value={filters.taxJurisdictionId} onChange={(event) => setFilters((value) => ({ ...value, taxJurisdictionId: event.target.value }))} sx={{ minWidth: 220 }}>
            <MenuItem value="">Any</MenuItem>
            {jurisdictions.map((jurisdiction) => <MenuItem key={jurisdiction.id} value={jurisdiction.id}>{jurisdiction.code} - {jurisdiction.name}</MenuItem>)}
          </TextField>
          <TextField label="Code" value={filters.code} onChange={(event) => setFilters((value) => ({ ...value, code: event.target.value }))} />
          <TextField label="Name" value={filters.name} onChange={(event) => setFilters((value) => ({ ...value, name: event.target.value }))} />
          <TextField select label="Status" value={filters.active} onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as '' | 'true' | 'false' }))} sx={{ minWidth: 150 }}>
            <MenuItem value="">Any</MenuItem>
            <MenuItem value="true">Active</MenuItem>
            <MenuItem value="false">Inactive</MenuItem>
          </TextField>
          <Button type="submit" variant="outlined" startIcon={<SearchIcon />} sx={{ minWidth: 112 }}>Search</Button>
        </Stack>
      </Paper>
      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <ListHeader title="Tax component list" count={components.data?.totalElements ?? 0} refreshLabel="Refresh tax components" onRefresh={() => void components.refetch()}>
          {canManage ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setDialogOpen(true); }}>New component</Button> : null}
        </ListHeader>
        {components.isLoading || taxTypeOptions.isLoading || jurisdictionOptions.isLoading ? <LoadingPanel label="Loading tax components" /> : null}
        {components.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(components.error)}</Alert> : null}
        {!components.isLoading && !taxTypeOptions.isLoading && !jurisdictionOptions.isLoading && !components.isError ? (
          <TaxTable
            rows={components.data?.content ?? []}
            columns={[
              { label: 'Type', value: (component) => typeName(component.taxTypeId) },
              { label: 'Jurisdiction', value: (component) => jurisdictionName(component.taxJurisdictionId) },
              { label: 'Code', value: (component) => component.code, strong: true },
              { label: 'Name', value: (component) => component.name },
              { label: 'Status', value: (component) => statusChip(component.active) }
            ]}
            canManage={canManage}
            emptyLabel="No tax components found."
            onEdit={(component) => { setEditing(component); setDialogOpen(true); }}
            onStatus={(component) => statusMutation.mutate(component)}
            statusPending={statusMutation.isPending}
          />
        ) : null}
        <TablePagination component="div" count={components.data?.totalElements ?? 0} page={page} rowsPerPage={size} onPageChange={(_, nextPage) => setPage(nextPage)} onRowsPerPageChange={(event) => {
          setSize(Number(event.target.value));
          setPage(0);
        }} />
      </TableContainer>
      <TaxComponentDialog open={dialogOpen} component={editing} taxTypes={taxTypes} jurisdictions={jurisdictions} loading={saveMutation.isPending} error={saveMutation.isError ? errorMessage(saveMutation.error) : undefined} onClose={() => {
        if (!saveMutation.isPending) {
          setDialogOpen(false);
          setEditing(null);
        }
      }} onSubmit={(values) => saveMutation.mutate(values)} />
    </Stack>
  );
}

function cleanTaxRate(values: TaxRateFormValues): TaxRatePayload {
  return {
    taxComponentId: values.taxComponentId,
    percentageRate: values.percentageRate,
    effectiveFrom: values.effectiveFrom,
    effectiveTo: optionalText(values.effectiveTo),
    includedInPrice: values.includedInPrice,
    compoundOnPreviousTax: values.compoundOnPreviousTax,
    calculationOrder: values.calculationOrder,
    status: values.status,
    source: optionalText(values.source),
    sourceReference: optionalText(values.sourceReference),
    verifiedBy: optionalText(values.verifiedBy),
    verifiedAt: optionalText(values.verifiedAt)
  };
}

function taxRateValues(rate?: TaxRate | null, defaultComponentId = ''): TaxRateFormValues {
  return {
    taxComponentId: rate?.taxComponentId ?? defaultComponentId,
    percentageRate: rate?.percentageRate ?? 0,
    effectiveFrom: rate?.effectiveFrom ?? '',
    effectiveTo: rate?.effectiveTo ?? '',
    includedInPrice: rate?.includedInPrice ?? false,
    compoundOnPreviousTax: rate?.compoundOnPreviousTax ?? false,
    calculationOrder: rate?.calculationOrder ?? 0,
    status: rate?.status ?? 'DRAFT',
    source: rate?.source ?? '',
    sourceReference: rate?.sourceReference ?? '',
    verifiedBy: rate?.verifiedBy ?? '',
    verifiedAt: rate?.verifiedAt ?? ''
  };
}

function TaxRateDialog({ open, rate, components, loading, error, onClose, onSubmit }: {
  open: boolean;
  rate: TaxRate | null;
  components: TaxComponent[];
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: TaxRateFormValues) => void;
}) {
  const form = useForm<TaxRateFormValues>({
    resolver: zodResolver(taxRateSchema),
    defaultValues: taxRateValues(rate, components[0]?.id ?? ''),
    values: taxRateValues(rate, components[0]?.id ?? '')
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{rate ? 'Edit tax rate' : 'New tax rate'}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="tax-rate-form" spacing={2} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <Controller name="taxComponentId" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} select label="Tax component" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                {components.map((component) => <MenuItem key={component.id} value={component.id}>{component.code} - {component.name}</MenuItem>)}
              </TextField>
            )} />
            <Controller name="status" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} select label="Status" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                {rateStatuses.map((status) => <MenuItem key={status} value={status}>{displayEnum(status)}</MenuItem>)}
              </TextField>
            )} />
          </Stack>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <Controller name="percentageRate" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} label="Percentage rate" type="number" inputProps={{ step: '0.000001', min: 0 }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
            <Controller name="calculationOrder" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} label="Calculation order" type="number" inputProps={{ step: 1, min: 0 }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
          </Stack>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <Controller name="effectiveFrom" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} label="Effective from" type="date" InputLabelProps={{ shrink: true }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
            <Controller name="effectiveTo" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} value={field.value ?? ''} label="Effective to" type="date" InputLabelProps={{ shrink: true }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
          </Stack>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <Controller name="includedInPrice" control={form.control} render={({ field }) => (
              <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Included in price" />
            )} />
            <Controller name="compoundOnPreviousTax" control={form.control} render={({ field }) => (
              <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Compound on previous tax" />
            )} />
          </Stack>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <Controller name="source" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} value={field.value ?? ''} label="Source" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
            <Controller name="sourceReference" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} value={field.value ?? ''} label="Source reference" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
          </Stack>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <Controller name="verifiedBy" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} value={field.value ?? ''} label="Verified by" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
            <Controller name="verifiedAt" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} value={field.value ?? ''} label="Verified at" type="datetime-local" InputLabelProps={{ shrink: true }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form="tax-rate-form" variant="contained" startIcon={<SaveIcon />} disabled={loading || components.length === 0}>
          {rate ? 'Save changes' : 'Create rate'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function rateStatusChip(status: TaxRateStatus) {
  const color = status === 'ACTIVE' ? 'success' : status === 'SCHEDULED' ? 'info' : status === 'RETIRED' ? 'default' : 'warning';
  return <Chip label={displayEnum(status)} color={color} size="small" />;
}

export function TaxRatesPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useTaxPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState({ taxComponentId: '', status: '' as '' | TaxRateStatus, includedInPrice: '' as '' | 'true' | 'false' });
  const [appliedFilters, setAppliedFilters] = React.useState(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<TaxRate | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const componentOptions = useComponentOptions(canView);

  const params = React.useMemo<TaxRateSearchParams>(() => ({
    taxComponentId: optionalText(appliedFilters.taxComponentId),
    status: appliedFilters.status,
    includedInPrice: appliedFilters.includedInPrice === '' ? '' : appliedFilters.includedInPrice === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const rates = useQuery({
    queryKey: ['tax-rates', params],
    queryFn: async () => listTaxRates(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: TaxRateFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: TaxRateUpdatePayload = { ...cleanTaxRate(values), version: editing.version };
        return updateTaxRate(token, editing.id, payload);
      }
      return createTaxRate(token, cleanTaxRate(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['tax-rates'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async ({ rate, status }: { rate: TaxRate; status: TaxRateStatus }) => updateTaxRateStatus(await getValidAccessToken(), rate.id, {
      status,
      version: rate.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tax-rates'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const components = componentOptions.data?.content ?? [];
  const componentName = (id: string) => components.find((component) => component.id === id)?.code ?? id;

  return (
    <Stack spacing={3}>
      <PageHeader title="Tax rates" subtitle="Effective-dated percentage rates with source and verification metadata." current="rates" />
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack component="form" direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ p: 2 }} onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setAppliedFilters(filters);
        }}>
          <TextField select label="Tax component" value={filters.taxComponentId} onChange={(event) => setFilters((value) => ({ ...value, taxComponentId: event.target.value }))} sx={{ minWidth: 220 }}>
            <MenuItem value="">Any</MenuItem>
            {components.map((component) => <MenuItem key={component.id} value={component.id}>{component.code} - {component.name}</MenuItem>)}
          </TextField>
          <TextField select label="Status" value={filters.status} onChange={(event) => setFilters((value) => ({ ...value, status: event.target.value as '' | TaxRateStatus }))} sx={{ minWidth: 170 }}>
            <MenuItem value="">Any</MenuItem>
            {rateStatuses.map((status) => <MenuItem key={status} value={status}>{displayEnum(status)}</MenuItem>)}
          </TextField>
          <TextField select label="Included in price" value={filters.includedInPrice} onChange={(event) => setFilters((value) => ({ ...value, includedInPrice: event.target.value as '' | 'true' | 'false' }))} sx={{ minWidth: 180 }}>
            <MenuItem value="">Any</MenuItem>
            <MenuItem value="true">Yes</MenuItem>
            <MenuItem value="false">No</MenuItem>
          </TextField>
          <Button type="submit" variant="outlined" startIcon={<SearchIcon />} sx={{ minWidth: 112 }}>Search</Button>
        </Stack>
      </Paper>
      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <ListHeader title="Tax rate list" count={rates.data?.totalElements ?? 0} refreshLabel="Refresh tax rates" onRefresh={() => void rates.refetch()}>
          {canManage ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setDialogOpen(true); }}>New rate</Button> : null}
        </ListHeader>
        {rates.isLoading || componentOptions.isLoading ? <LoadingPanel label="Loading tax rates" /> : null}
        {rates.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(rates.error)}</Alert> : null}
        {!rates.isLoading && !componentOptions.isLoading && !rates.isError ? (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Component</TableCell>
                <TableCell>Rate</TableCell>
                <TableCell>Effective period</TableCell>
                <TableCell>Flags</TableCell>
                <TableCell>Order</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Source</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(rates.data?.content ?? []).map((rate) => (
                <TableRow key={rate.id} hover>
                  <TableCell sx={{ fontWeight: 700 }}>{componentName(rate.taxComponentId)}</TableCell>
                  <TableCell>{Number(rate.percentageRate).toFixed(6)}%</TableCell>
                  <TableCell>{rate.effectiveFrom} to {rate.effectiveTo ?? 'open'}</TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={1}>
                      {rate.includedInPrice ? <Chip label="Included" size="small" /> : null}
                      {rate.compoundOnPreviousTax ? <Chip label="Compound" size="small" /> : null}
                      {!rate.includedInPrice && !rate.compoundOnPreviousTax ? '-' : null}
                    </Stack>
                  </TableCell>
                  <TableCell>{rate.calculationOrder}</TableCell>
                  <TableCell>{rateStatusChip(rate.status)}</TableCell>
                  <TableCell>{rate.source ?? '-'}</TableCell>
                  <TableCell align="right">
                    {canManage ? (
                      <>
                        <Tooltip title={`Edit ${componentName(rate.taxComponentId)} rate`}>
                          <IconButton aria-label={`Edit ${componentName(rate.taxComponentId)} rate`} onClick={() => { setEditing(rate); setDialogOpen(true); }}>
                            <EditIcon />
                          </IconButton>
                        </Tooltip>
                        <TextField
                          select
                          size="small"
                          aria-label={`Set status for ${componentName(rate.taxComponentId)} rate`}
                          value={rate.status}
                          disabled={statusMutation.isPending}
                          onChange={(event) => statusMutation.mutate({ rate, status: event.target.value as TaxRateStatus })}
                          sx={{ width: 132 }}
                        >
                          {rateStatuses.map((status) => <MenuItem key={status} value={status}>{displayEnum(status)}</MenuItem>)}
                        </TextField>
                      </>
                    ) : null}
                  </TableCell>
                </TableRow>
              ))}
              {(rates.data?.content ?? []).length === 0 ? (
                <TableRow><TableCell colSpan={8}><Typography color="text.secondary" align="center" sx={{ py: 4 }}>No tax rates found.</Typography></TableCell></TableRow>
              ) : null}
            </TableBody>
          </Table>
        ) : null}
        <TablePagination component="div" count={rates.data?.totalElements ?? 0} page={page} rowsPerPage={size} onPageChange={(_, nextPage) => setPage(nextPage)} onRowsPerPageChange={(event) => {
          setSize(Number(event.target.value));
          setPage(0);
        }} />
      </TableContainer>
      <TaxRateDialog open={dialogOpen} rate={editing} components={components} loading={saveMutation.isPending} error={saveMutation.isError ? errorMessage(saveMutation.error) : undefined} onClose={() => {
        if (!saveMutation.isPending) {
          setDialogOpen(false);
          setEditing(null);
        }
      }} onSubmit={(values) => saveMutation.mutate(values)} />
    </Stack>
  );
}

function cleanTaxGroup(values: TaxGroupFormValues): TaxGroupPayload {
  return {
    code: values.code.trim().toUpperCase(),
    name: values.name.trim(),
    description: optionalText(values.description),
    active: values.active
  };
}

function taxGroupValues(group?: TaxGroup | null): TaxGroupFormValues {
  return {
    code: group?.code ?? '',
    name: group?.name ?? '',
    description: group?.description ?? '',
    active: group?.active ?? true
  };
}

function TaxGroupDialog({ open, group, loading, error, onClose, onSubmit }: {
  open: boolean;
  group: TaxGroup | null;
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: TaxGroupFormValues) => void;
}) {
  const form = useForm<TaxGroupFormValues>({
    resolver: zodResolver(taxGroupSchema),
    defaultValues: taxGroupValues(group),
    values: taxGroupValues(group)
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{group ? 'Edit tax group' : 'New tax group'}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="tax-group-form" spacing={2} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Controller name="code" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Code" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="name" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Name" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="description" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} value={field.value ?? ''} label="Description" multiline minRows={3} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="active" control={form.control} render={({ field }) => (
            <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Active" />
          )} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form="tax-group-form" variant="contained" startIcon={<SaveIcon />} disabled={loading}>
          {group ? 'Save changes' : 'Create group'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function TaxGroupsPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useTaxPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState<FilterForm>(emptyFilters);
  const [appliedFilters, setAppliedFilters] = React.useState<FilterForm>(emptyFilters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<TaxGroup | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);

  const params = React.useMemo<TaxGroupSearchParams>(() => ({
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const groups = useQuery({
    queryKey: ['tax-groups', params],
    queryFn: async () => listTaxGroups(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: TaxGroupFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: TaxGroupUpdatePayload = { ...cleanTaxGroup(values), version: editing.version };
        return updateTaxGroup(token, editing.id, payload);
      }
      return createTaxGroup(token, cleanTaxGroup(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['tax-groups'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-groups-options'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (group: TaxGroup) => updateTaxGroupStatus(await getValidAccessToken(), group.id, {
      active: !group.active,
      version: group.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tax-groups'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-groups-options'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3}>
      <PageHeader title="Tax groups" subtitle="Reusable bundles of tax components applied through product tax categories." current="groups" />
      <TaxFilterPanel filters={filters} countries={[]} types={[]} showCountry={false} showType={false} onChange={setFilters} onSubmit={() => {
        setPage(0);
        setAppliedFilters(filters);
      }} />
      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <ListHeader title="Tax group list" count={groups.data?.totalElements ?? 0} refreshLabel="Refresh tax groups" onRefresh={() => void groups.refetch()}>
          {canManage ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setDialogOpen(true); }}>New group</Button> : null}
        </ListHeader>
        {groups.isLoading ? <LoadingPanel label="Loading tax groups" /> : null}
        {groups.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(groups.error)}</Alert> : null}
        {!groups.isLoading && !groups.isError ? (
          <TaxTable
            rows={groups.data?.content ?? []}
            columns={[
              { label: 'Code', value: (group) => group.code, strong: true },
              { label: 'Name', value: (group) => group.name },
              { label: 'Description', value: (group) => group.description ?? '-' },
              { label: 'Status', value: (group) => statusChip(group.active) }
            ]}
            canManage={canManage}
            emptyLabel="No tax groups found."
            onEdit={(group) => { setEditing(group); setDialogOpen(true); }}
            onStatus={(group) => statusMutation.mutate(group)}
            statusPending={statusMutation.isPending}
          />
        ) : null}
        <TablePagination component="div" count={groups.data?.totalElements ?? 0} page={page} rowsPerPage={size} onPageChange={(_, nextPage) => setPage(nextPage)} onRowsPerPageChange={(event) => {
          setSize(Number(event.target.value));
          setPage(0);
        }} />
      </TableContainer>
      <TaxGroupDialog open={dialogOpen} group={editing} loading={saveMutation.isPending} error={saveMutation.isError ? errorMessage(saveMutation.error) : undefined} onClose={() => {
        if (!saveMutation.isPending) {
          setDialogOpen(false);
          setEditing(null);
        }
      }} onSubmit={(values) => saveMutation.mutate(values)} />
    </Stack>
  );
}

function taxGroupComponentValues(component?: TaxGroupComponent | null, defaultGroupId = '', defaultComponentId = ''): TaxGroupComponentFormValues {
  return {
    taxGroupId: component?.taxGroupId ?? defaultGroupId,
    taxComponentId: component?.taxComponentId ?? defaultComponentId,
    calculationOrder: component?.calculationOrder ?? 0,
    active: component?.active ?? true
  };
}

function cleanTaxGroupComponent(values: TaxGroupComponentFormValues): TaxGroupComponentPayload {
  return {
    taxGroupId: values.taxGroupId,
    taxComponentId: values.taxComponentId,
    calculationOrder: values.calculationOrder,
    active: values.active
  };
}

function TaxGroupComponentDialog({ open, item, groups, components, loading, error, onClose, onSubmit }: {
  open: boolean;
  item: TaxGroupComponent | null;
  groups: TaxGroup[];
  components: TaxComponent[];
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: TaxGroupComponentFormValues) => void;
}) {
  const form = useForm<TaxGroupComponentFormValues>({
    resolver: zodResolver(taxGroupComponentSchema),
    defaultValues: taxGroupComponentValues(item, groups[0]?.id ?? '', components[0]?.id ?? ''),
    values: taxGroupComponentValues(item, groups[0]?.id ?? '', components[0]?.id ?? '')
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{item ? 'Edit group component' : 'New group component'}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="tax-group-component-form" spacing={2} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Controller name="taxGroupId" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} select label="Tax group" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              {groups.map((group) => <MenuItem key={group.id} value={group.id}>{group.code} - {group.name}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="taxComponentId" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} select label="Tax component" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              {components.map((component) => <MenuItem key={component.id} value={component.id}>{component.code} - {component.name}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="calculationOrder" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Calculation order" type="number" inputProps={{ step: 1, min: 0 }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="active" control={form.control} render={({ field }) => (
            <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Active" />
          )} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form="tax-group-component-form" variant="contained" startIcon={<SaveIcon />} disabled={loading || groups.length === 0 || components.length === 0}>
          {item ? 'Save changes' : 'Create group component'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function TaxGroupComponentsPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useTaxPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState({ taxGroupId: '', taxComponentId: '', active: '' as '' | 'true' | 'false' });
  const [appliedFilters, setAppliedFilters] = React.useState(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<TaxGroupComponent | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const groupOptions = useTaxGroupOptions(canView);
  const componentOptions = useComponentOptions(canView);

  const params = React.useMemo<TaxGroupComponentSearchParams>(() => ({
    taxGroupId: optionalText(appliedFilters.taxGroupId),
    taxComponentId: optionalText(appliedFilters.taxComponentId),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const groupComponents = useQuery({
    queryKey: ['tax-group-components', params],
    queryFn: async () => listTaxGroupComponents(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: TaxGroupComponentFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: TaxGroupComponentUpdatePayload = { ...cleanTaxGroupComponent(values), version: editing.version };
        return updateTaxGroupComponent(token, editing.id, payload);
      }
      return createTaxGroupComponent(token, cleanTaxGroupComponent(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['tax-group-components'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (item: TaxGroupComponent) => updateTaxGroupComponentStatus(await getValidAccessToken(), item.id, {
      active: !item.active,
      version: item.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tax-group-components'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const groups = groupOptions.data?.content ?? [];
  const components = componentOptions.data?.content ?? [];
  const groupName = (id: string) => groups.find((group) => group.id === id)?.code ?? id;
  const componentName = (id: string) => components.find((component) => component.id === id)?.code ?? id;

  return (
    <Stack spacing={3}>
      <PageHeader title="Tax group components" subtitle="Control which tax components belong to each group and their calculation order." current="groupComponents" />
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack component="form" direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ p: 2 }} onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setAppliedFilters(filters);
        }}>
          <TextField select label="Tax group" value={filters.taxGroupId} onChange={(event) => setFilters((value) => ({ ...value, taxGroupId: event.target.value }))} sx={{ minWidth: 220 }}>
            <MenuItem value="">Any</MenuItem>
            {groups.map((group) => <MenuItem key={group.id} value={group.id}>{group.code} - {group.name}</MenuItem>)}
          </TextField>
          <TextField select label="Tax component" value={filters.taxComponentId} onChange={(event) => setFilters((value) => ({ ...value, taxComponentId: event.target.value }))} sx={{ minWidth: 220 }}>
            <MenuItem value="">Any</MenuItem>
            {components.map((component) => <MenuItem key={component.id} value={component.id}>{component.code} - {component.name}</MenuItem>)}
          </TextField>
          <TextField select label="Status" value={filters.active} onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as '' | 'true' | 'false' }))} sx={{ minWidth: 150 }}>
            <MenuItem value="">Any</MenuItem>
            <MenuItem value="true">Active</MenuItem>
            <MenuItem value="false">Inactive</MenuItem>
          </TextField>
          <Button type="submit" variant="outlined" startIcon={<SearchIcon />} sx={{ minWidth: 112 }}>Search</Button>
        </Stack>
      </Paper>
      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <ListHeader title="Tax group component list" count={groupComponents.data?.totalElements ?? 0} refreshLabel="Refresh tax group components" onRefresh={() => void groupComponents.refetch()}>
          {canManage ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setDialogOpen(true); }}>New group component</Button> : null}
        </ListHeader>
        {groupComponents.isLoading || groupOptions.isLoading || componentOptions.isLoading ? <LoadingPanel label="Loading tax group components" /> : null}
        {groupComponents.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(groupComponents.error)}</Alert> : null}
        {!groupComponents.isLoading && !groupOptions.isLoading && !componentOptions.isLoading && !groupComponents.isError ? (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Group</TableCell>
                <TableCell>Component</TableCell>
                <TableCell>Order</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(groupComponents.data?.content ?? []).map((item) => {
                const label = `${groupName(item.taxGroupId)} ${componentName(item.taxComponentId)}`;
                return (
                  <TableRow key={item.id} hover>
                    <TableCell sx={{ fontWeight: 700 }}>{groupName(item.taxGroupId)}</TableCell>
                    <TableCell>{componentName(item.taxComponentId)}</TableCell>
                    <TableCell>{item.calculationOrder}</TableCell>
                    <TableCell>{statusChip(item.active)}</TableCell>
                    <TableCell align="right">
                      {canManage ? (
                        <>
                          <Tooltip title={`Edit ${label}`}>
                            <IconButton aria-label={`Edit ${label}`} onClick={() => { setEditing(item); setDialogOpen(true); }}>
                              <EditIcon />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title={item.active ? `Deactivate ${label}` : `Activate ${label}`}>
                            <IconButton aria-label={`${item.active ? 'Deactivate' : 'Activate'} ${label}`} disabled={statusMutation.isPending} onClick={() => statusMutation.mutate(item)}>
                              {item.active ? <BlockIcon /> : <CheckCircleIcon />}
                            </IconButton>
                          </Tooltip>
                        </>
                      ) : null}
                    </TableCell>
                  </TableRow>
                );
              })}
              {(groupComponents.data?.content ?? []).length === 0 ? (
                <TableRow><TableCell colSpan={5}><Typography color="text.secondary" align="center" sx={{ py: 4 }}>No tax group components found.</Typography></TableCell></TableRow>
              ) : null}
            </TableBody>
          </Table>
        ) : null}
        <TablePagination component="div" count={groupComponents.data?.totalElements ?? 0} page={page} rowsPerPage={size} onPageChange={(_, nextPage) => setPage(nextPage)} onRowsPerPageChange={(event) => {
          setSize(Number(event.target.value));
          setPage(0);
        }} />
      </TableContainer>
      <TaxGroupComponentDialog open={dialogOpen} item={editing} groups={groups} components={components} loading={saveMutation.isPending} error={saveMutation.isError ? errorMessage(saveMutation.error) : undefined} onClose={() => {
        if (!saveMutation.isPending) {
          setDialogOpen(false);
          setEditing(null);
        }
      }} onSubmit={(values) => saveMutation.mutate(values)} />
    </Stack>
  );
}

function taxCategoryValues(category?: TaxCategory | null): TaxCategoryFormValues {
  return {
    taxGroupId: category?.taxGroupId ?? '',
    code: category?.code ?? '',
    name: category?.name ?? '',
    treatment: category?.treatment ?? 'STANDARD',
    description: category?.description ?? '',
    active: category?.active ?? true
  };
}

function cleanTaxCategory(values: TaxCategoryFormValues): TaxCategoryPayload {
  return {
    taxGroupId: optionalText(values.taxGroupId),
    code: values.code.trim().toUpperCase(),
    name: values.name.trim(),
    treatment: values.treatment,
    description: optionalText(values.description),
    active: values.active
  };
}

function TaxCategoryDialog({ open, category, groups, loading, error, onClose, onSubmit }: {
  open: boolean;
  category: TaxCategory | null;
  groups: TaxGroup[];
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: TaxCategoryFormValues) => void;
}) {
  const form = useForm<TaxCategoryFormValues>({
    resolver: zodResolver(taxCategorySchema),
    defaultValues: taxCategoryValues(category),
    values: taxCategoryValues(category)
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{category ? 'Edit tax category' : 'New tax category'}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="tax-category-form" spacing={2} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Controller name="taxGroupId" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} value={field.value ?? ''} select label="Tax group" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              <MenuItem value="">None</MenuItem>
              {groups.map((group) => <MenuItem key={group.id} value={group.id}>{group.code} - {group.name}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="code" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Code" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="name" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} label="Name" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="treatment" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} select label="Treatment" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              {taxTreatments.map((treatment) => <MenuItem key={treatment} value={treatment}>{displayEnum(treatment)}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="description" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} value={field.value ?? ''} label="Description" multiline minRows={3} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="active" control={form.control} render={({ field }) => (
            <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Active" />
          )} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form="tax-category-form" variant="contained" startIcon={<SaveIcon />} disabled={loading}>
          {category ? 'Save changes' : 'Create category'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function TaxCategoriesPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useTaxPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState({ taxGroupId: '', code: '', name: '', treatment: '' as '' | TaxTreatment, active: '' as '' | 'true' | 'false' });
  const [appliedFilters, setAppliedFilters] = React.useState(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<TaxCategory | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const groupOptions = useTaxGroupOptions(canView);

  const params = React.useMemo<TaxCategorySearchParams>(() => ({
    taxGroupId: optionalText(appliedFilters.taxGroupId),
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    treatment: appliedFilters.treatment,
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const categories = useQuery({
    queryKey: ['tax-categories', params],
    queryFn: async () => listTaxCategories(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: TaxCategoryFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: TaxCategoryUpdatePayload = { ...cleanTaxCategory(values), version: editing.version };
        return updateTaxCategory(token, editing.id, payload);
      }
      return createTaxCategory(token, cleanTaxCategory(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['tax-categories'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-categories-options'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (category: TaxCategory) => updateTaxCategoryStatus(await getValidAccessToken(), category.id, {
      active: !category.active,
      version: category.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tax-categories'] });
      await queryClient.invalidateQueries({ queryKey: ['tax-categories-options'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const groups = groupOptions.data?.content ?? [];
  const groupName = (id: string | null) => id ? groups.find((group) => group.id === id)?.code ?? id : '-';

  return (
    <Stack spacing={3}>
      <PageHeader title="Tax categories" subtitle="Product-facing tax classifications and treatments." current="categories" />
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack component="form" direction={{ xs: 'column', lg: 'row' }} spacing={2} sx={{ p: 2 }} onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setAppliedFilters(filters);
        }}>
          <TextField select label="Tax group" value={filters.taxGroupId} onChange={(event) => setFilters((value) => ({ ...value, taxGroupId: event.target.value }))} sx={{ minWidth: 220 }}>
            <MenuItem value="">Any</MenuItem>
            {groups.map((group) => <MenuItem key={group.id} value={group.id}>{group.code} - {group.name}</MenuItem>)}
          </TextField>
          <TextField label="Code" value={filters.code} onChange={(event) => setFilters((value) => ({ ...value, code: event.target.value }))} />
          <TextField label="Name" value={filters.name} onChange={(event) => setFilters((value) => ({ ...value, name: event.target.value }))} />
          <TextField select label="Treatment" value={filters.treatment} onChange={(event) => setFilters((value) => ({ ...value, treatment: event.target.value as '' | TaxTreatment }))} sx={{ minWidth: 190 }}>
            <MenuItem value="">Any</MenuItem>
            {taxTreatments.map((treatment) => <MenuItem key={treatment} value={treatment}>{displayEnum(treatment)}</MenuItem>)}
          </TextField>
          <TextField select label="Status" value={filters.active} onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as '' | 'true' | 'false' }))} sx={{ minWidth: 150 }}>
            <MenuItem value="">Any</MenuItem>
            <MenuItem value="true">Active</MenuItem>
            <MenuItem value="false">Inactive</MenuItem>
          </TextField>
          <Button type="submit" variant="outlined" startIcon={<SearchIcon />} sx={{ minWidth: 112 }}>Search</Button>
        </Stack>
      </Paper>
      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <ListHeader title="Tax category list" count={categories.data?.totalElements ?? 0} refreshLabel="Refresh tax categories" onRefresh={() => void categories.refetch()}>
          {canManage ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setDialogOpen(true); }}>New category</Button> : null}
        </ListHeader>
        {categories.isLoading || groupOptions.isLoading ? <LoadingPanel label="Loading tax categories" /> : null}
        {categories.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(categories.error)}</Alert> : null}
        {!categories.isLoading && !groupOptions.isLoading && !categories.isError ? (
          <TaxTable
            rows={categories.data?.content ?? []}
            columns={[
              { label: 'Group', value: (category) => groupName(category.taxGroupId) },
              { label: 'Code', value: (category) => category.code, strong: true },
              { label: 'Name', value: (category) => category.name },
              { label: 'Treatment', value: (category) => displayEnum(category.treatment) },
              { label: 'Status', value: (category) => statusChip(category.active) }
            ]}
            canManage={canManage}
            emptyLabel="No tax categories found."
            onEdit={(category) => { setEditing(category); setDialogOpen(true); }}
            onStatus={(category) => statusMutation.mutate(category)}
            statusPending={statusMutation.isPending}
          />
        ) : null}
        <TablePagination component="div" count={categories.data?.totalElements ?? 0} page={page} rowsPerPage={size} onPageChange={(_, nextPage) => setPage(nextPage)} onRowsPerPageChange={(event) => {
          setSize(Number(event.target.value));
          setPage(0);
        }} />
      </TableContainer>
      <TaxCategoryDialog open={dialogOpen} category={editing} groups={groups} loading={saveMutation.isPending} error={saveMutation.isError ? errorMessage(saveMutation.error) : undefined} onClose={() => {
        if (!saveMutation.isPending) {
          setDialogOpen(false);
          setEditing(null);
        }
      }} onSubmit={(values) => saveMutation.mutate(values)} />
    </Stack>
  );
}

function assignmentValues(assignment?: ProductTaxCategoryAssignment | null, defaultProductId = '', defaultCategoryId = ''): ProductTaxCategoryAssignmentFormValues {
  return {
    productId: assignment?.productId ?? defaultProductId,
    taxCategoryId: assignment?.taxCategoryId ?? defaultCategoryId,
    active: assignment?.active ?? true
  };
}

function cleanAssignment(values: ProductTaxCategoryAssignmentFormValues): ProductTaxCategoryAssignmentPayload {
  return {
    productId: values.productId,
    taxCategoryId: values.taxCategoryId,
    active: values.active
  };
}

function productLabel(product: Product) {
  return `${product.sku} - ${product.name}`;
}

function AssignmentDialog({ open, assignment, products, categories, loading, error, onClose, onSubmit }: {
  open: boolean;
  assignment: ProductTaxCategoryAssignment | null;
  products: Product[];
  categories: TaxCategory[];
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: ProductTaxCategoryAssignmentFormValues) => void;
}) {
  const form = useForm<ProductTaxCategoryAssignmentFormValues>({
    resolver: zodResolver(productTaxCategoryAssignmentSchema),
    defaultValues: assignmentValues(assignment, products[0]?.id ?? '', categories[0]?.id ?? ''),
    values: assignmentValues(assignment, products[0]?.id ?? '', categories[0]?.id ?? '')
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{assignment ? 'Edit product tax assignment' : 'New product tax assignment'}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="product-tax-assignment-form" spacing={2} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Controller name="productId" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} select label="Product" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              {products.map((product) => <MenuItem key={product.id} value={product.id}>{productLabel(product)}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="taxCategoryId" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} select label="Tax category" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
              {categories.map((category) => <MenuItem key={category.id} value={category.id}>{category.code} - {category.name}</MenuItem>)}
            </TextField>
          )} />
          <Controller name="active" control={form.control} render={({ field }) => (
            <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Active" />
          )} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form="product-tax-assignment-form" variant="contained" startIcon={<SaveIcon />} disabled={loading || products.length === 0 || categories.length === 0}>
          {assignment ? 'Save changes' : 'Create assignment'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function ProductTaxCategoryAssignmentsPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useTaxPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState({ productId: '', taxCategoryId: '', active: '' as '' | 'true' | 'false' });
  const [appliedFilters, setAppliedFilters] = React.useState(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<ProductTaxCategoryAssignment | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const productOptions = useProductOptions(canView);
  const categoryOptions = useTaxCategoryOptions(canView);

  const params = React.useMemo<ProductTaxCategoryAssignmentSearchParams>(() => ({
    productId: optionalText(appliedFilters.productId),
    taxCategoryId: optionalText(appliedFilters.taxCategoryId),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const assignments = useQuery({
    queryKey: ['product-tax-category-assignments', params],
    queryFn: async () => listProductTaxCategoryAssignments(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: ProductTaxCategoryAssignmentFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: ProductTaxCategoryAssignmentUpdatePayload = { ...cleanAssignment(values), version: editing.version };
        return updateProductTaxCategoryAssignment(token, editing.id, payload);
      }
      return createProductTaxCategoryAssignment(token, cleanAssignment(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['product-tax-category-assignments'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (assignment: ProductTaxCategoryAssignment) => updateProductTaxCategoryAssignmentStatus(await getValidAccessToken(), assignment.id, {
      active: !assignment.active,
      version: assignment.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['product-tax-category-assignments'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const products = productOptions.data?.content ?? [];
  const categories = categoryOptions.data?.content ?? [];
  const productName = (id: string) => products.find((product) => product.id === id)?.sku ?? id;
  const categoryName = (id: string) => categories.find((category) => category.id === id)?.code ?? id;

  return (
    <Stack spacing={3}>
      <PageHeader title="Product tax assignments" subtitle="Assign product-facing tax categories to products." current="assignments" />
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack component="form" direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ p: 2 }} onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setAppliedFilters(filters);
        }}>
          <TextField select label="Product" value={filters.productId} onChange={(event) => setFilters((value) => ({ ...value, productId: event.target.value }))} sx={{ minWidth: 240 }}>
            <MenuItem value="">Any</MenuItem>
            {products.map((product) => <MenuItem key={product.id} value={product.id}>{productLabel(product)}</MenuItem>)}
          </TextField>
          <TextField select label="Tax category" value={filters.taxCategoryId} onChange={(event) => setFilters((value) => ({ ...value, taxCategoryId: event.target.value }))} sx={{ minWidth: 220 }}>
            <MenuItem value="">Any</MenuItem>
            {categories.map((category) => <MenuItem key={category.id} value={category.id}>{category.code} - {category.name}</MenuItem>)}
          </TextField>
          <TextField select label="Status" value={filters.active} onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as '' | 'true' | 'false' }))} sx={{ minWidth: 150 }}>
            <MenuItem value="">Any</MenuItem>
            <MenuItem value="true">Active</MenuItem>
            <MenuItem value="false">Inactive</MenuItem>
          </TextField>
          <Button type="submit" variant="outlined" startIcon={<SearchIcon />} sx={{ minWidth: 112 }}>Search</Button>
        </Stack>
      </Paper>
      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <ListHeader title="Product tax assignment list" count={assignments.data?.totalElements ?? 0} refreshLabel="Refresh product tax assignments" onRefresh={() => void assignments.refetch()}>
          {canManage ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setDialogOpen(true); }}>New assignment</Button> : null}
        </ListHeader>
        {assignments.isLoading || productOptions.isLoading || categoryOptions.isLoading ? <LoadingPanel label="Loading product tax assignments" /> : null}
        {assignments.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(assignments.error)}</Alert> : null}
        {!assignments.isLoading && !productOptions.isLoading && !categoryOptions.isLoading && !assignments.isError ? (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Product</TableCell>
                <TableCell>Tax category</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(assignments.data?.content ?? []).map((assignment) => {
                const label = `${productName(assignment.productId)} ${categoryName(assignment.taxCategoryId)}`;
                return (
                  <TableRow key={assignment.id} hover>
                    <TableCell sx={{ fontWeight: 700 }}>{productName(assignment.productId)}</TableCell>
                    <TableCell>{categoryName(assignment.taxCategoryId)}</TableCell>
                    <TableCell>{statusChip(assignment.active)}</TableCell>
                    <TableCell align="right">
                      {canManage ? (
                        <>
                          <Tooltip title={`Edit ${label}`}>
                            <IconButton aria-label={`Edit ${label}`} onClick={() => { setEditing(assignment); setDialogOpen(true); }}>
                              <EditIcon />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title={assignment.active ? `Deactivate ${label}` : `Activate ${label}`}>
                            <IconButton aria-label={`${assignment.active ? 'Deactivate' : 'Activate'} ${label}`} disabled={statusMutation.isPending} onClick={() => statusMutation.mutate(assignment)}>
                              {assignment.active ? <BlockIcon /> : <CheckCircleIcon />}
                            </IconButton>
                          </Tooltip>
                        </>
                      ) : null}
                    </TableCell>
                  </TableRow>
                );
              })}
              {(assignments.data?.content ?? []).length === 0 ? (
                <TableRow><TableCell colSpan={4}><Typography color="text.secondary" align="center" sx={{ py: 4 }}>No product tax assignments found.</Typography></TableCell></TableRow>
              ) : null}
            </TableBody>
          </Table>
        ) : null}
        <TablePagination component="div" count={assignments.data?.totalElements ?? 0} page={page} rowsPerPage={size} onPageChange={(_, nextPage) => setPage(nextPage)} onRowsPerPageChange={(event) => {
          setSize(Number(event.target.value));
          setPage(0);
        }} />
      </TableContainer>
      <AssignmentDialog open={dialogOpen} assignment={editing} products={products} categories={categories} loading={saveMutation.isPending} error={saveMutation.isError ? errorMessage(saveMutation.error) : undefined} onClose={() => {
        if (!saveMutation.isPending) {
          setDialogOpen(false);
          setEditing(null);
        }
      }} onSubmit={(values) => saveMutation.mutate(values)} />
    </Stack>
  );
}

function taxRuleValues(rule?: TaxRule | null): TaxRuleFormValues {
  return {
    code: rule?.code ?? '',
    name: rule?.name ?? '',
    description: rule?.description ?? '',
    priority: rule?.priority ?? 0,
    effectiveFrom: rule?.effectiveFrom ?? '',
    effectiveTo: rule?.effectiveTo ?? '',
    active: rule?.active ?? true,
    conditions: rule?.conditions.map((condition) => ({
      conditionType: condition.conditionType,
      operator: condition.operator,
      value: condition.value ?? '',
      secondValue: condition.secondValue ?? ''
    })) ?? [],
    actions: rule?.actions.map((action) => ({
      actionType: action.actionType,
      taxGroupId: action.taxGroupId ?? '',
      taxComponentId: action.taxComponentId ?? '',
      value: action.value ?? ''
    })) ?? [{ actionType: 'APPLY_TAX_GROUP', taxGroupId: '', taxComponentId: '', value: '' }]
  };
}

function cleanTaxRule(values: TaxRuleFormValues): TaxRulePayload {
  return {
    code: values.code.trim().toUpperCase(),
    name: values.name.trim(),
    description: optionalText(values.description),
    priority: values.priority,
    effectiveFrom: values.effectiveFrom,
    effectiveTo: optionalText(values.effectiveTo),
    active: values.active,
    conditions: values.conditions.map(cleanRuleCondition),
    actions: values.actions.map(cleanRuleAction)
  };
}

function cleanRuleCondition(condition: TaxRuleConditionPayload): TaxRuleConditionPayload {
  return {
    conditionType: condition.conditionType,
    operator: condition.operator,
    value: condition.operator === 'IS_TRUE' || condition.operator === 'IS_FALSE' ? undefined : optionalText(condition.value),
    secondValue: condition.operator === 'BETWEEN' ? optionalText(condition.secondValue) : undefined
  };
}

function cleanRuleAction(action: TaxRuleActionPayload): TaxRuleActionPayload {
  const cleaned: TaxRuleActionPayload = { actionType: action.actionType };
  if (action.actionType === 'APPLY_TAX_GROUP') {
    cleaned.taxGroupId = optionalText(action.taxGroupId);
  } else if (action.actionType === 'APPLY_TAX_COMPONENT' || action.actionType === 'EXCLUDE_COMPONENT') {
    cleaned.taxComponentId = optionalText(action.taxComponentId);
  } else if (action.actionType === 'INCLUDED_PRICE_BEHAVIOR' || action.actionType === 'ROUNDING_STRATEGY') {
    cleaned.value = optionalText(action.value);
  }
  return cleaned;
}

function TaxRuleDialog({ open, rule, groups, components, loading, error, onClose, onSubmit }: {
  open: boolean;
  rule: TaxRule | null;
  groups: TaxGroup[];
  components: TaxComponent[];
  loading: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (values: TaxRuleFormValues) => void;
}) {
  const form = useForm<TaxRuleFormValues>({
    resolver: zodResolver(taxRuleSchema),
    defaultValues: taxRuleValues(rule),
    values: taxRuleValues(rule)
  });
  const conditions = form.watch('conditions');
  const actions = form.watch('actions');

  const addCondition = () => {
    form.setValue('conditions', [...conditions, { conditionType: 'SALE_CHANNEL', operator: 'EQUALS', value: '', secondValue: '' }], { shouldDirty: true });
  };
  const addAction = () => {
    form.setValue('actions', [...actions, { actionType: 'APPLY_TAX_GROUP', taxGroupId: groups[0]?.id ?? '', taxComponentId: '', value: '' }], { shouldDirty: true });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
      <DialogTitle>{rule ? 'Edit tax rule' : 'New tax rule'}</DialogTitle>
      <DialogContent>
        <Stack component="form" id="tax-rule-form" spacing={3} sx={{ pt: 1 }} onSubmit={form.handleSubmit(onSubmit)}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <Controller name="code" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} label="Code" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
            <Controller name="name" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} label="Name" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
            <Controller name="priority" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} label="Priority" type="number" inputProps={{ step: 1, min: 0 }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
          </Stack>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <Controller name="effectiveFrom" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} label="Effective from" type="date" InputLabelProps={{ shrink: true }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
            <Controller name="effectiveTo" control={form.control} render={({ field, fieldState }) => (
              <TextField {...field} value={field.value ?? ''} label="Effective to" type="date" InputLabelProps={{ shrink: true }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
            )} />
          </Stack>
          <Controller name="description" control={form.control} render={({ field, fieldState }) => (
            <TextField {...field} value={field.value ?? ''} label="Description" multiline minRows={2} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
          )} />
          <Controller name="active" control={form.control} render={({ field }) => (
            <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Active" />
          )} />

          <Stack spacing={1.5}>
            <Stack direction="row" alignItems="center" spacing={1}>
              <Typography variant="subtitle1" sx={{ flexGrow: 1 }}>Conditions</Typography>
              <Button startIcon={<AddIcon />} onClick={addCondition}>Add condition</Button>
            </Stack>
            {conditions.length === 0 ? <Typography color="text.secondary">No conditions. The rule will match any effective transaction.</Typography> : null}
            {conditions.map((condition, index) => (
              <Stack key={index} direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'flex-start' }}>
                <Controller name={`conditions.${index}.conditionType`} control={form.control} render={({ field }) => (
                  <TextField {...field} select label="Condition" sx={{ minWidth: 210 }}>
                    {conditionTypes.map((type) => <MenuItem key={type} value={type}>{displayEnum(type)}</MenuItem>)}
                  </TextField>
                )} />
                <Controller name={`conditions.${index}.operator`} control={form.control} render={({ field }) => (
                  <TextField {...field} select label="Operator" sx={{ minWidth: 170 }}>
                    {conditionOperators.map((operator) => <MenuItem key={operator} value={operator}>{displayEnum(operator)}</MenuItem>)}
                  </TextField>
                )} />
                <Controller name={`conditions.${index}.value`} control={form.control} render={({ field, fieldState }) => (
                  <TextField {...field} value={field.value ?? ''} label="Value" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                )} />
                <Controller name={`conditions.${index}.secondValue`} control={form.control} render={({ field, fieldState }) => (
                  <TextField {...field} value={field.value ?? ''} label="Second value" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                )} />
                <Button color="inherit" onClick={() => form.setValue('conditions', conditions.filter((_, current) => current !== index), { shouldDirty: true })}>Remove</Button>
              </Stack>
            ))}
          </Stack>

          <Stack spacing={1.5}>
            <Stack direction="row" alignItems="center" spacing={1}>
              <Typography variant="subtitle1" sx={{ flexGrow: 1 }}>Actions</Typography>
              <Button startIcon={<AddIcon />} onClick={addAction}>Add action</Button>
            </Stack>
            {actions.map((action, index) => (
              <Stack key={index} direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'flex-start' }}>
                <Controller name={`actions.${index}.actionType`} control={form.control} render={({ field }) => (
                  <TextField {...field} select label="Action" sx={{ minWidth: 220 }}>
                    {actionTypes.map((type) => <MenuItem key={type} value={type}>{displayEnum(type)}</MenuItem>)}
                  </TextField>
                )} />
                <Controller name={`actions.${index}.taxGroupId`} control={form.control} render={({ field }) => (
                  <TextField {...field} value={field.value ?? ''} select label="Tax group" disabled={action.actionType !== 'APPLY_TAX_GROUP'} sx={{ minWidth: 220 }}>
                    <MenuItem value="">None</MenuItem>
                    {groups.map((group) => <MenuItem key={group.id} value={group.id}>{group.code} - {group.name}</MenuItem>)}
                  </TextField>
                )} />
                <Controller name={`actions.${index}.taxComponentId`} control={form.control} render={({ field }) => (
                  <TextField {...field} value={field.value ?? ''} select label="Tax component" disabled={action.actionType !== 'APPLY_TAX_COMPONENT' && action.actionType !== 'EXCLUDE_COMPONENT'} sx={{ minWidth: 220 }}>
                    <MenuItem value="">None</MenuItem>
                    {components.map((component) => <MenuItem key={component.id} value={component.id}>{component.code} - {component.name}</MenuItem>)}
                  </TextField>
                )} />
                <Controller name={`actions.${index}.value`} control={form.control} render={({ field, fieldState }) => (
                  <TextField {...field} value={field.value ?? ''} label="Value" disabled={action.actionType !== 'INCLUDED_PRICE_BEHAVIOR' && action.actionType !== 'ROUNDING_STRATEGY'} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                )} />
                <Button color="inherit" disabled={actions.length === 1} onClick={() => form.setValue('actions', actions.filter((_, current) => current !== index), { shouldDirty: true })}>Remove</Button>
              </Stack>
            ))}
            {form.formState.errors.actions?.message ? <Typography color="error">{form.formState.errors.actions.message}</Typography> : null}
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" form="tax-rule-form" variant="contained" startIcon={<SaveIcon />} disabled={loading}>
          {rule ? 'Save changes' : 'Create rule'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function TaxRulesPage() {
  const { getValidAccessToken } = useSession();
  const { canView, canManage } = useTaxPermissions();
  const queryClient = useQueryClient();
  const [filters, setFilters] = React.useState({ code: '', name: '', effectiveOn: '', active: '' as '' | 'true' | 'false' });
  const [appliedFilters, setAppliedFilters] = React.useState(filters);
  const [page, setPage] = React.useState(0);
  const [size, setSize] = React.useState(10);
  const [editing, setEditing] = React.useState<TaxRule | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const groupOptions = useTaxGroupOptions(canView);
  const componentOptions = useComponentOptions(canView);

  const params = React.useMemo<TaxRuleSearchParams>(() => ({
    code: optionalText(appliedFilters.code),
    name: optionalText(appliedFilters.name),
    effectiveOn: optionalText(appliedFilters.effectiveOn),
    active: appliedFilters.active === '' ? '' : appliedFilters.active === 'true',
    page,
    size
  }), [appliedFilters, page, size]);

  const rules = useQuery({
    queryKey: ['tax-rules', params],
    queryFn: async () => listTaxRules(await getValidAccessToken(), params),
    enabled: canView
  });

  const saveMutation = useMutation({
    mutationFn: async (values: TaxRuleFormValues) => {
      const token = await getValidAccessToken();
      if (editing) {
        const payload: TaxRuleUpdatePayload = { ...cleanTaxRule(values), version: editing.version };
        return updateTaxRule(token, editing.id, payload);
      }
      return createTaxRule(token, cleanTaxRule(values));
    },
    onSuccess: async () => {
      setDialogOpen(false);
      setEditing(null);
      await queryClient.invalidateQueries({ queryKey: ['tax-rules'] });
    }
  });

  const statusMutation = useMutation({
    mutationFn: async (rule: TaxRule) => updateTaxRuleStatus(await getValidAccessToken(), rule.id, {
      active: !rule.active,
      version: rule.version
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tax-rules'] });
    }
  });

  if (!canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const groups = groupOptions.data?.content ?? [];
  const components = componentOptions.data?.content ?? [];

  return (
    <Stack spacing={3}>
      <PageHeader title="Tax rules" subtitle="Prioritized effective-dated rules that select tax groups, components, exclusions, and tax treatment overrides." current="rules" />
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <Stack component="form" direction={{ xs: 'column', lg: 'row' }} spacing={2} sx={{ p: 2 }} onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setAppliedFilters(filters);
        }}>
          <TextField label="Code" value={filters.code} onChange={(event) => setFilters((value) => ({ ...value, code: event.target.value }))} />
          <TextField label="Name" value={filters.name} onChange={(event) => setFilters((value) => ({ ...value, name: event.target.value }))} />
          <TextField label="Effective on" type="date" InputLabelProps={{ shrink: true }} value={filters.effectiveOn} onChange={(event) => setFilters((value) => ({ ...value, effectiveOn: event.target.value }))} />
          <TextField select label="Status" value={filters.active} onChange={(event) => setFilters((value) => ({ ...value, active: event.target.value as '' | 'true' | 'false' }))} sx={{ minWidth: 150 }}>
            <MenuItem value="">Any</MenuItem>
            <MenuItem value="true">Active</MenuItem>
            <MenuItem value="false">Inactive</MenuItem>
          </TextField>
          <Button type="submit" variant="outlined" startIcon={<SearchIcon />} sx={{ minWidth: 112 }}>Search</Button>
        </Stack>
      </Paper>
      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {statusMutation.isError ? <Alert severity="error">{errorMessage(statusMutation.error)}</Alert> : null}
      <TableContainer component={Paper} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <ListHeader title="Tax rule list" count={rules.data?.totalElements ?? 0} refreshLabel="Refresh tax rules" onRefresh={() => void rules.refetch()}>
          {canManage ? <Button variant="contained" startIcon={<AddIcon />} onClick={() => { setEditing(null); setDialogOpen(true); }}>New rule</Button> : null}
        </ListHeader>
        {rules.isLoading || groupOptions.isLoading || componentOptions.isLoading ? <LoadingPanel label="Loading tax rules" /> : null}
        {rules.isError ? <Alert severity="error" sx={{ m: 2 }}>{errorMessage(rules.error)}</Alert> : null}
        {!rules.isLoading && !groupOptions.isLoading && !componentOptions.isLoading && !rules.isError ? (
          <TaxTable
            rows={rules.data?.content ?? []}
            columns={[
              { label: 'Priority', value: (rule) => rule.priority },
              { label: 'Code', value: (rule) => rule.code, strong: true },
              { label: 'Name', value: (rule) => rule.name },
              { label: 'Effective period', value: (rule) => `${rule.effectiveFrom} to ${rule.effectiveTo ?? 'open'}` },
              { label: 'Logic', value: (rule) => `${rule.conditions.length} conditions / ${rule.actions.length} actions` },
              { label: 'Status', value: (rule) => statusChip(rule.active) }
            ]}
            canManage={canManage}
            emptyLabel="No tax rules found."
            onEdit={(rule) => { setEditing(rule); setDialogOpen(true); }}
            onStatus={(rule) => statusMutation.mutate(rule)}
            statusPending={statusMutation.isPending}
          />
        ) : null}
        <TablePagination component="div" count={rules.data?.totalElements ?? 0} page={page} rowsPerPage={size} onPageChange={(_, nextPage) => setPage(nextPage)} onRowsPerPageChange={(event) => {
          setSize(Number(event.target.value));
          setPage(0);
        }} />
      </TableContainer>
      <TaxRuleDialog open={dialogOpen} rule={editing} groups={groups} components={components} loading={saveMutation.isPending} error={saveMutation.isError ? errorMessage(saveMutation.error) : undefined} onClose={() => {
        if (!saveMutation.isPending) {
          setDialogOpen(false);
          setEditing(null);
        }
      }} onSubmit={(values) => saveMutation.mutate(values)} />
    </Stack>
  );
}

export function TaxGeographyRedirect() {
  const location = useLocation();
  return <Navigate to="/tax/rules" replace state={{ from: location }} />;
}
