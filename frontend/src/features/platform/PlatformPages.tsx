import AddIcon from '@mui/icons-material/Add';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import DeleteIcon from '@mui/icons-material/Delete';
import RefreshIcon from '@mui/icons-material/Refresh';
import SaveIcon from '@mui/icons-material/Save';
import SettingsIcon from '@mui/icons-material/Settings';
import StorefrontIcon from '@mui/icons-material/Storefront';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Divider,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Paper,
  Select,
  Stack,
  Step,
  StepLabel,
  Stepper,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TablePagination,
  TextField,
  Typography
} from '@mui/material';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { compactFilterBarSx } from '../../app/responsive';
import {
  activateOwnerInvitation,
  closePlatformTenant,
  createPlatformTenant,
  getApiErrorMessage,
  getApiFieldErrors,
  deleteEmptyPlatformTenant,
  getEmailProviderStatus,
  getOwnerActivationStatus,
  getTenantDeletionEligibility,
  getPlatformDashboard,
  getPlatformBillingSubscription,
  getPlatformPricingPreview,
  getPlatformSettings,
  getPlatformTenant,
  listPlatformAuditEvents,
  listActivePlatformPricingPlans,
  listTenantEmailDeliveries,
  listTenantStatusHistory,
  listPlatformTenants,
  listPlatformTenantStores,
  listReferenceAdministrativeDivisions,
  listReferenceCountries,
  listReferenceCountryCurrencies,
  listReferenceDivisionTaxRegions,
  listReferenceDivisionTimezones,
  reactivatePlatformTenant,
  reopenPlatformTenant,
  resendOwnerInvitation,
  resendTemporaryCredentials,
  retryEmailDelivery,
  sendPlatformTestEmail,
  sendPlatformUserPasswordReset,
  unlockPlatformUser,
  suspendPlatformTenant,
  previewPlatformStoreCapabilities,
  updatePlatformStoreCapabilities,
  updatePlatformTenantSubscription
} from '../../api/client';
import type { MerchantOnboardingPayload, OwnerInvitationResendPayload, TenantLifecyclePayload, TenantSubscriptionPayload } from '../../api/client';
import type { EmailDelivery, MerchantStoreCapability, OwnerActivationStatus, StoreCapability, StoreCapabilityChangePreview, TenantDeletionEligibility, TenantDetail, TenantSummary } from '../../api/types';
import { useSession } from '../../app/session';

const steps = ['Merchant details', 'Business defaults', 'Initial owner', 'Pricing Plan', 'Review', 'Completion'];

function usePlatformToken() {
  const { session, currentUser, getValidAccessToken } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const isPlatform = roles.some((role) => role === 'PLATFORM_SUPER_ADMIN' || role === 'PLATFORM_SUPPORT_ADMIN');
  return { isPlatform, getValidAccessToken };
}

function useAuthedQuery<T>(key: unknown[], queryFn: (token: string) => Promise<T>, enabled = true) {
  const { getValidAccessToken, isPlatform } = usePlatformToken();
  return useQuery({
    queryKey: key,
    queryFn: async () => queryFn(await getValidAccessToken()),
    enabled: enabled && isPlatform
  });
}

function RequirePlatform({ children }: { children: JSX.Element }) {
  const { isPlatform } = usePlatformToken();
  if (!isPlatform) {
    return <Navigate to="/unauthorized" replace />;
  }
  return children;
}

function StatusChip({ status }: { status: string }) {
  const color = status === 'ACTIVE' ? 'success' : status === 'SUSPENDED' ? 'error' : status === 'CLOSED' ? 'default' : 'warning';
  return <Chip label={status.replaceAll('_', ' ')} color={color} size="small" />;
}

function StatCard({ label, value }: { label: string; value: number | string }) {
  return (
    <Paper variant="outlined" sx={{ p: 2, borderRadius: 1, height: '100%' }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h4" component="div">{value}</Typography>
    </Paper>
  );
}

type LifecycleAction = 'suspend' | 'reactivate' | 'close' | 'reopen' | 'delete';

function validLifecycleActions(status: TenantSummary['status']): LifecycleAction[] {
  if (status === 'ACTIVE') return ['suspend', 'close'];
  if (status === 'SUSPENDED') return ['reactivate', 'close'];
  if (status === 'PENDING_ONBOARDING' || status === 'PENDING_OWNER_ACTIVATION' || status === 'REJECTED') return ['suspend', 'delete'];
  if (status === 'CLOSED') return ['reopen'];
  return [];
}

function lifecycleLabel(action: LifecycleAction) {
  return action === 'delete' ? 'Delete empty merchant' : `${action.charAt(0).toUpperCase()}${action.slice(1)} merchant`;
}

function LifecycleActions({ merchant, compact = false, onDeleted }: { merchant: TenantSummary; compact?: boolean; onDeleted?: () => void }) {
  const { getValidAccessToken } = usePlatformToken();
  const queryClient = useQueryClient();
  const [action, setAction] = useState<LifecycleAction | null>(null);
  const [reason, setReason] = useState('');
  const [notes, setNotes] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [checked, setChecked] = useState(false);
  const [eligibility, setEligibility] = useState<TenantDeletionEligibility | null>(null);

  const eligibilityMutation = useMutation({
    mutationFn: async () => getTenantDeletionEligibility(await getValidAccessToken(), merchant.id),
    onSuccess: setEligibility
  });

  const lifecycle = useMutation({
    mutationFn: async () => {
      if (!action) throw new Error('Action is required');
      const token = await getValidAccessToken();
      const payload = { reason: reason || `${lifecycleLabel(action)} requested`, notes, confirmation, version: merchant.version } satisfies TenantLifecyclePayload;
      if (action === 'suspend') return suspendPlatformTenant(token, merchant.id, payload);
      if (action === 'reactivate') return reactivatePlatformTenant(token, merchant.id, payload);
      if (action === 'close') return closePlatformTenant(token, merchant.id, payload);
      if (action === 'reopen') return reopenPlatformTenant(token, merchant.id, payload);
      await deleteEmptyPlatformTenant(token, merchant.id, { confirmation, reason, version: merchant.version });
      return null;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
      await queryClient.invalidateQueries({ queryKey: ['platform-dashboard'] });
      await queryClient.invalidateQueries({ queryKey: ['platform-tenant', merchant.id] });
      setAction(null);
      setReason('');
      setNotes('');
      setConfirmation('');
      setChecked(false);
      setEligibility(null);
      if (action === 'delete') onDeleted?.();
    }
  });

  function open(nextAction: LifecycleAction) {
    setAction(nextAction);
    setReason('');
    setNotes('');
    setConfirmation('');
    setChecked(false);
    setEligibility(null);
    if (nextAction === 'delete') {
      eligibilityMutation.mutate();
    }
  }

  const requiresReason = action === 'suspend' || action === 'close';
  const requiresConfirmation = action === 'close' || action === 'reopen' || action === 'delete';
  const expectedDeleteConfirmation = `DELETE ${merchant.tenantCode}`;
  const confirmationValid = !requiresConfirmation
    || (action === 'delete' ? confirmation === expectedDeleteConfirmation : confirmation === merchant.tenantCode || confirmation === merchant.displayName);
  const canSubmit = Boolean(action)
    && checked
    && (!requiresReason || reason.trim().length > 0)
    && confirmationValid
    && (action !== 'delete' || Boolean(eligibility?.eligible));

  return (
    <>
      <Stack direction="row" gap={1} flexWrap="wrap">
        <Button component={Link} to={`/platform/merchants/${merchant.id}`} size={compact ? 'small' : 'medium'}>View</Button>
        {validLifecycleActions(merchant.status).map((item) => (
          <Button
            key={item}
            size={compact ? 'small' : 'medium'}
            variant="outlined"
            color={item === 'close' || item === 'delete' || item === 'suspend' ? 'error' : 'primary'}
            startIcon={item === 'delete' ? <DeleteIcon /> : item === 'suspend' ? <BlockIcon /> : <RefreshIcon />}
            onClick={() => open(item)}
          >
            {compact ? item : lifecycleLabel(item)}
          </Button>
        ))}
      </Stack>
      <Dialog open={Boolean(action)} onClose={() => setAction(null)} fullWidth maxWidth="sm">
        <DialogTitle>{action ? lifecycleLabel(action) : 'Merchant lifecycle action'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {action === 'suspend' && <Alert severity="warning">Merchant users will be unable to log in or access store operations until reactivated. Existing data will be preserved.</Alert>}
            {action === 'reactivate' && <Alert severity="info">Merchant users regain access according to existing account, store, and permission status.</Alert>}
            {action === 'close' && <Alert severity="error">Closing disables merchant access. Financial and audit records are retained. This is not a deletion.</Alert>}
            {action === 'delete' && <Alert severity="error">This is allowed only for an unused merchant and permanently removes eligible onboarding data.</Alert>}
            {action === 'delete' && eligibilityMutation.isPending && <CircularProgress aria-label="Checking deletion eligibility" />}
            {action === 'delete' && eligibility && !eligibility.eligible && (
              <Alert severity="warning">
                Delete is blocked. Recommended action: {eligibility.recommendedAction}.
                <List dense>
                  {eligibility.blockers.map((blocker) => (
                    <ListItem key={blocker.type} disableGutters>
                      <ListItemText primary={`${blocker.message} (${blocker.count})`} secondary={blocker.type} />
                    </ListItem>
                  ))}
                </List>
              </Alert>
            )}
            <TextField label={action === 'close' ? 'Closure reason' : 'Reason or notes'} value={reason} onChange={(event) => setReason(event.target.value)} required={requiresReason} multiline minRows={2} />
            <TextField label="Internal notes" value={notes} onChange={(event) => setNotes(event.target.value)} multiline minRows={2} />
            {requiresConfirmation && (
              <TextField
                label={action === 'delete' ? `Type ${expectedDeleteConfirmation}` : 'Type tenant code or display name'}
                value={confirmation}
                onChange={(event) => setConfirmation(event.target.value)}
                error={confirmation.length > 0 && !confirmationValid}
                helperText={action === 'delete' ? expectedDeleteConfirmation : `${merchant.tenantCode} or ${merchant.displayName}`}
              />
            )}
            <FormControlLabel control={<Switch checked={checked} onChange={(event) => setChecked(event.target.checked)} />} label="I understand the effect of this action" />
            {(lifecycle.error || eligibilityMutation.error) && <Alert severity="error">{(lifecycle.error || eligibilityMutation.error)?.message}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAction(null)}>Cancel</Button>
          <Button color={action === 'close' || action === 'delete' || action === 'suspend' ? 'error' : 'primary'} variant="contained" disabled={!canSubmit || lifecycle.isPending} onClick={() => lifecycle.mutate()}>
            {action ? lifecycleLabel(action) : 'Continue'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

export function PlatformDashboardPage() {
  const dashboard = useAuthedQuery(['platform-dashboard'], getPlatformDashboard);
  if (dashboard.isLoading) return <CircularProgress aria-label="Loading platform dashboard" />;
  if (dashboard.error) return <Alert severity="error">{dashboard.error.message}</Alert>;
  const data = dashboard.data;
  if (!data) return null;

  return (
    <RequirePlatform>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2}>
          <Box>
            <Typography variant="h4" component="h1">Platform</Typography>
            <Typography color="text.secondary">Merchant onboarding and tenant operations</Typography>
          </Box>
          <Button component={Link} to="/platform/merchants/new" variant="contained" startIcon={<AddIcon />}>New merchant</Button>
        </Stack>
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6} md={3}><StatCard label="Active merchants" value={data.totalActiveMerchants} /></Grid>
          <Grid item xs={12} sm={6} md={3}><StatCard label="Pending onboardings" value={data.pendingOnboardings} /></Grid>
          <Grid item xs={12} sm={6} md={3}><StatCard label="Suspended merchants" value={data.suspendedMerchants} /></Grid>
          <Grid item xs={12} sm={6} md={3}><StatCard label="Closed merchants" value={data.closedMerchants ?? 0} /></Grid>
          <Grid item xs={12} sm={6} md={3}><StatCard label="Merchants requiring attention" value={data.merchantsRequiringAttention ?? 0} /></Grid>
          <Grid item xs={12} sm={6} md={3}><StatCard label="Active merchant users" value={data.activeMerchantUsers} /></Grid>
          <Grid item xs={12} sm={6} md={3}><StatCard label="Trial subscriptions" value={data.trialSubscriptions} /></Grid>
          <Grid item xs={12} sm={6} md={3}><StatCard label="Failed invitations" value={data.failedInvitations} /></Grid>
          <Grid item xs={12} sm={6} md={3}><StatCard label="Active stores" value={data.activeStores} /></Grid>
          <Grid item xs={12} sm={6} md={3}><StatCard label="Support access" value={data.supportAccessEnabled ? `${data.supportAccessDefaultMinutes} min` : 'Disabled'} /></Grid>
        </Grid>
        <MerchantTable merchants={data.recentOnboardingActivity} />
        <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
          <Typography variant="h6">Recent lifecycle activity</Typography>
          <Stack spacing={1} sx={{ mt: 2 }}>
            {(data.recentLifecycleActivity ?? []).map((event) => (
              <Stack key={event.id} direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={1}>
                <Typography>{event.tenantCodeSnapshot ?? event.tenantId}: {event.previousStatus ?? 'CREATED'} to {event.newStatus}</Typography>
                <Typography color="text.secondary">{new Date(event.changedAt).toLocaleString()}</Typography>
              </Stack>
            ))}
          </Stack>
        </Paper>
      </Stack>
    </RequirePlatform>
  );
}

export function PlatformMerchantsPage() {
  const { getValidAccessToken, isPlatform } = usePlatformToken();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Math.max(0, Number(searchParams.get('page') ?? 0) || 0);
  const size = [10, 25, 50].includes(Number(searchParams.get('size'))) ? Number(searchParams.get('size')) : 10;
  const [searchInput, setSearchInput] = useState(searchParams.get('search') ?? '');
  const search = searchParams.get('search') ?? '';
  const status = searchParams.get('status') ?? '';
  const country = searchParams.get('country') ?? '';
  const province = searchParams.get('province') ?? '';
  const createdFrom = searchParams.get('createdFrom') ?? '';
  const createdTo = searchParams.get('createdTo') ?? '';
  const setParam = (name: string, value: string, resetPage = true) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      value ? next.set(name, value) : next.delete(name);
      if (resetPage) next.set('page', '0');
      return next;
    });
  };
  const setCountryFilter = (value: string) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      value ? next.set('country', value) : next.delete('country');
      next.delete('province');
      next.set('page', '0');
      return next;
    });
  };
  useEffect(() => {
    const timeout = window.setTimeout(() => {
      const trimmed = searchInput.trim().slice(0, 100);
      if (trimmed !== search) setParam('search', trimmed);
    }, 350);
    return () => window.clearTimeout(timeout);
  }, [searchInput, search]);
  const countries = useAuthedQuery(['reference-countries', 'merchant-filter'], listReferenceCountries);
  const provinces = useAuthedQuery(['reference-divisions', country, 'merchant-filter'], (token) => listReferenceAdministrativeDivisions(token, country), Boolean(country));
  const queryParams = { page, size, search, status, country, province, createdFrom, createdTo, sort: 'createdAt,desc' as const };
  const merchants = useQuery({
    queryKey: ['platform-tenants', queryParams],
    queryFn: async () => listPlatformTenants(await getValidAccessToken(), queryParams),
    enabled: isPlatform,
    placeholderData: keepPreviousData
  });
  const clearFilters = () => {
    setSearchInput('');
    setSearchParams({ page: '0', size: String(size) });
  };
  return (
    <RequirePlatform>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2}>
          <Typography variant="h4" component="h1">Merchants</Typography>
          <Button component={Link} to="/platform/merchants/new" variant="contained" startIcon={<AddIcon />}>New merchant</Button>
        </Stack>
        <Paper variant="outlined">
          <Stack sx={compactFilterBarSx}>
            <TextField label="Search merchants..." value={searchInput} onChange={(event) => setSearchInput(event.target.value)} inputProps={{ maxLength: 100 }} fullWidth />
            <TextField select label="Status" value={status} onChange={(event) => setParam('status', event.target.value)}>
              <MenuItem value="">All statuses</MenuItem>
              {['PENDING_ONBOARDING', 'PENDING_OWNER_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'CLOSED', 'REJECTED'].map((value) => <MenuItem key={value} value={value}>{value.replaceAll('_', ' ')}</MenuItem>)}
            </TextField>
            <TextField select label="Country" value={country} onChange={(event) => setCountryFilter(event.target.value)}>
              <MenuItem value="">All countries</MenuItem>
              {(countries.data ?? []).map((item) => <MenuItem key={item.alpha2Code} value={item.alpha2Code}>{item.name}</MenuItem>)}
            </TextField>
            <TextField select label="Province / State" value={province} disabled={!country} onChange={(event) => setParam('province', event.target.value)}>
              <MenuItem value="">All regions</MenuItem>
              {(provinces.data ?? []).map((item) => <MenuItem key={item.code} value={item.code}>{item.name}</MenuItem>)}
            </TextField>
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ px: 2, pb: 2 }}>
            <TextField label="Created from" type="date" value={createdFrom} onChange={(event) => setParam('createdFrom', event.target.value)} InputLabelProps={{ shrink: true }} />
            <TextField label="Created to" type="date" value={createdTo} onChange={(event) => setParam('createdTo', event.target.value)} InputLabelProps={{ shrink: true }} />
            <Button onClick={clearFilters}>Clear filters</Button>
          </Stack>
        </Paper>
        {merchants.isLoading && <CircularProgress aria-label="Loading merchants" />}
        {merchants.error && <Alert severity="error" action={<Button color="inherit" onClick={() => merchants.refetch()}>Retry</Button>}>Unable to load merchants.</Alert>}
        {!merchants.isLoading && !merchants.error && merchants.data?.content.length === 0 && (
          <Alert severity="info" action={(search || status || country || province || createdFrom || createdTo) ? <Button color="inherit" onClick={clearFilters}>Clear Filters</Button> : undefined}>
            {(search || status || country || province || createdFrom || createdTo) ? 'No merchants match your filters.' : 'No merchants yet.'}
          </Alert>
        )}
        {merchants.data && merchants.data.content.length > 0 && <MerchantTable merchants={merchants.data.content} returnSearch={searchParams.toString()} />}
        {merchants.data && (
          <TablePagination component="div" count={merchants.data.totalElements} page={page} rowsPerPage={size}
            rowsPerPageOptions={[10, 25, 50]}
            onPageChange={(_, nextPage) => setParam('page', String(Math.max(0, nextPage)), false)}
            onRowsPerPageChange={(event) => { setParam('size', event.target.value); }} />
        )}
      </Stack>
    </RequirePlatform>
  );
}

function MerchantTable({ merchants, returnSearch = '' }: { merchants: TenantSummary[]; returnSearch?: string }) {
  return (
    <Paper variant="outlined" sx={{ borderRadius: 1, overflow: 'auto' }}>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Merchant</TableCell>
            <TableCell>Code</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Owner</TableCell>
            <TableCell>Owner Activation</TableCell>
            <TableCell>Plan</TableCell>
            <TableCell>Stores</TableCell>
            <TableCell>Users</TableCell>
            <TableCell>Stage</TableCell>
            <TableCell>Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {merchants.map((merchant) => (
            <TableRow key={merchant.id} hover>
              <TableCell>
                <Button component={Link} to={`/platform/merchants/${merchant.id}`} state={{ merchantListSearch: returnSearch }} sx={{ justifyContent: 'flex-start', px: 0 }}>
                  {merchant.displayName}
                </Button>
                <Typography variant="caption" display="block" color="text.secondary">{merchant.legalName}</Typography>
              </TableCell>
              <TableCell>{merchant.tenantCode}</TableCell>
              <TableCell><StatusChip status={merchant.status} /></TableCell>
              <TableCell>{merchant.primaryOwnerEmail ?? 'Pending'}</TableCell>
              <TableCell><OwnerActivationListChip merchant={merchant} /></TableCell>
              <TableCell>{merchant.subscriptionPlan ?? 'Unassigned'}</TableCell>
              <TableCell>{merchant.storeCount}</TableCell>
              <TableCell>{merchant.userCount}</TableCell>
              <TableCell>{merchant.onboardingStage.replaceAll('_', ' ')}</TableCell>
              <TableCell>
                <Stack direction="row" gap={1} flexWrap="wrap">
                  {merchant.status === 'PENDING_OWNER_ACTIVATION' && (
                    <Button component={Link} to={`/platform/merchants/${merchant.id}`} size="small">Resend Activation</Button>
                  )}
                  <LifecycleActions merchant={merchant} compact />
                </Stack>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Paper>
  );
}

function OwnerActivationListChip({ merchant }: { merchant: TenantSummary }) {
  const label = merchant.activatedAt
    ? 'Activated'
    : merchant.status === 'PENDING_OWNER_ACTIVATION'
      ? 'Pending'
      : merchant.status === 'SUSPENDED' || merchant.status === 'CLOSED'
        ? 'Unavailable'
        : 'Pending';
  const color = label === 'Activated' ? 'success' : label === 'Unavailable' ? 'default' : 'warning';
  return <Chip label={label} color={color} size="small" />;
}

export function NewPlatformMerchantPage() {
  const { getValidAccessToken } = usePlatformToken();
  const navigate = useNavigate();
  const [activeStep, setActiveStep] = useState(0);
  const [created, setCreated] = useState<TenantDetail | null>(null);
  const [form, setForm] = useState<MerchantOnboardingPayload>({
    tenantCode: '',
    legalBusinessName: '',
    operatingName: '',
    countryCode: '',
    administrativeDivisionCode: '',
    primaryTimezone: '',
    defaultCurrencyCode: '',
    defaultTaxRegionCode: '',
    businessNumber: '',
    industryType: '',
    estimatedStoreCount: 1,
    notes: '',
    pricingPlanId: '',
    maximumUsers: 5,
    features: { pos: true, inventory: true, lottery: false },
    ownerFirstName: '',
    ownerLastName: '',
    ownerEmail: '',
    ownerPhone: '',
    storeCapabilities: ['RETAIL'],
    kitchenDisplayName: ''
  });
  const countries = useAuthedQuery(['reference-countries'], listReferenceCountries);
  const pricingPlans = useAuthedQuery(['platform-pricing-plan-options'], listActivePlatformPricingPlans);
  const selectedPlan = pricingPlans.data?.find((plan) => plan.id === form.pricingPlanId);
  const pricingPreview = useAuthedQuery(['platform-pricing-preview',form.pricingPlanId,form.estimatedStoreCount,form.storeCapabilities],token=>getPlatformPricingPreview(token,form.pricingPlanId,form.estimatedStoreCount??1,form.storeCapabilities.includes('FOOD_SERVICE')?(form.estimatedStoreCount??1):0),Boolean(form.pricingPlanId));
  const divisions = useAuthedQuery(
    ['reference-divisions', form.countryCode],
    (token) => listReferenceAdministrativeDivisions(token, form.countryCode),
    Boolean(form.countryCode)
  );
  const currencies = useAuthedQuery(
    ['reference-currencies', form.countryCode],
    (token) => listReferenceCountryCurrencies(token, form.countryCode),
    Boolean(form.countryCode)
  );
  const selectedCountry = countries.data?.find((country) => country.alpha2Code === form.countryCode);
  const selectedDivision = divisions.data?.find((division) => division.code === form.administrativeDivisionCode);
  const timezones = useAuthedQuery(
    ['reference-timezones', selectedDivision?.id],
    (token) => listReferenceDivisionTimezones(token, selectedDivision?.id ?? ''),
    Boolean(selectedDivision?.id)
  );
  const taxRegions = useAuthedQuery(
    ['reference-tax-regions', selectedDivision?.id],
    (token) => listReferenceDivisionTaxRegions(token, selectedDivision?.id ?? ''),
    Boolean(selectedDivision?.id)
  );
  const selectedCurrency = currencies.data?.find((currency) => currency.code === form.defaultCurrencyCode);
  const selectedTimezone = timezones.data?.find((timezone) => timezone.ianaName === form.primaryTimezone);
  const selectedTaxRegion = taxRegions.data?.find((region) => region.code === form.defaultTaxRegionCode);
  const mutation = useMutation({
    mutationFn: async () => createPlatformTenant(await getValidAccessToken(), form),
    onSuccess: (tenant) => {
      setCreated(tenant);
      setActiveStep(5);
    },
    onError: (error) => {
      const fields = getApiFieldErrors(error);
      if (fields.tenantCode || fields.businessNumber) setActiveStep(0);
      else if (fields.ownerEmail) setActiveStep(2);
      else if (fields.pricingPlanId) setActiveStep(3);
    }
  });
  const fieldErrors = getApiFieldErrors(mutation.error);

  function field<K extends keyof MerchantOnboardingPayload>(key: K, value: MerchantOnboardingPayload[K]) {
    if (mutation.error) mutation.reset();
    setForm((current) => {
      const next = { ...current, [key]: value };
      if (key === 'countryCode') {
        next.administrativeDivisionCode = '';
        next.defaultCurrencyCode = '';
        next.primaryTimezone = '';
        next.defaultTaxRegionCode = '';
      }
      if (key === 'administrativeDivisionCode') {
        next.primaryTimezone = '';
        next.defaultTaxRegionCode = '';
      }
      return next;
    });
  }

  useEffect(() => {
    if (!form.countryCode || form.defaultCurrencyCode || !currencies.data?.length) return;
    const suggested = selectedCountry?.defaultCurrencyCode
      ? currencies.data.find((currency) => currency.code === selectedCountry.defaultCurrencyCode)
      : undefined;
    field('defaultCurrencyCode', suggested?.code ?? currencies.data[0].code);
  }, [currencies.data, form.countryCode, form.defaultCurrencyCode, selectedCountry?.defaultCurrencyCode]);

  useEffect(() => {
    if (!selectedDivision || form.primaryTimezone || !timezones.data?.length) return;
    field('primaryTimezone', timezones.data.find((timezone) => timezone.defaultForDivision)?.ianaName ?? timezones.data[0].ianaName);
  }, [form.primaryTimezone, selectedDivision, timezones.data]);

  useEffect(() => {
    if (!selectedDivision || form.defaultTaxRegionCode || !taxRegions.data?.length) return;
    field('defaultTaxRegionCode', taxRegions.data.find((region) => region.defaultForDivision)?.code ?? taxRegions.data[0].code);
  }, [form.defaultTaxRegionCode, selectedDivision, taxRegions.data]);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (activeStep < 4) {
      setActiveStep((step) => step + 1);
      return;
    }
    mutation.mutate();
  }

  const divisionLabel = form.countryCode === 'US' ? 'State' : 'Province / Territory';

  return (
    <RequirePlatform>
      <Stack spacing={3} component="form" onSubmit={submit}>
        <Typography variant="h4" component="h1">New merchant</Typography>
        <Stepper activeStep={activeStep} alternativeLabel>
          {steps.map((step) => <Step key={step}><StepLabel>{step}</StepLabel></Step>)}
        </Stepper>
        {mutation.error && <Alert severity="error">{Object.keys(fieldErrors).length ? 'Merchant could not be created. Review the highlighted fields.' : getApiErrorMessage(mutation.error, 'Something went wrong while saving the merchant.')}</Alert>}
        {activeStep === 0 && (
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}><TextField fullWidth required label="Legal business name" value={form.legalBusinessName} onChange={(e) => field('legalBusinessName', e.target.value)} /></Grid>
            <Grid item xs={12} md={6}><TextField fullWidth required label="Operating name" value={form.operatingName} onChange={(e) => field('operatingName', e.target.value)} /></Grid>
            <Grid item xs={12} md={4}><TextField fullWidth required label="Tenant code" value={form.tenantCode} error={Boolean(fieldErrors.tenantCode)} helperText={fieldErrors.tenantCode} onChange={(e) => field('tenantCode', e.target.value.toUpperCase())} /></Grid>
            <Grid item xs={12} md={4}><TextField fullWidth label="Business number" value={form.businessNumber} error={Boolean(fieldErrors.businessNumber)} helperText={fieldErrors.businessNumber} onChange={(e) => field('businessNumber', e.target.value)} /></Grid>
            <Grid item xs={12} md={4}><TextField fullWidth label="Industry type" value={form.industryType} onChange={(e) => field('industryType', e.target.value)} /></Grid>
            <Grid item xs={12} md={4}><TextField fullWidth type="number" label="Estimated store count" value={form.estimatedStoreCount} onChange={(e) => field('estimatedStoreCount', Number(e.target.value))} /></Grid>
          </Grid>
        )}
        {activeStep === 1 && (
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <TextField fullWidth select required label="Country" value={form.countryCode} onChange={(e) => field('countryCode', e.target.value)} helperText={countries.isLoading ? 'Loading countries...' : undefined}>
                {(countries.data ?? []).map((country) => <MenuItem key={country.alpha2Code} value={country.alpha2Code}>{country.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} md={6}>
              <TextField fullWidth select required label={divisionLabel} value={form.administrativeDivisionCode} disabled={!form.countryCode || divisions.isLoading} onChange={(e) => field('administrativeDivisionCode', e.target.value)} helperText={!form.countryCode ? 'Select a country first' : divisions.isLoading ? 'Loading divisions...' : undefined}>
                {(divisions.data ?? []).map((division) => <MenuItem key={division.id} value={division.code}>{division.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField fullWidth select required label="Default currency" value={form.defaultCurrencyCode} disabled={!form.countryCode || currencies.isLoading} onChange={(e) => field('defaultCurrencyCode', e.target.value)}>
                {(currencies.data ?? []).map((currency) => <MenuItem key={currency.id} value={currency.code}>{currency.code} — {currency.name}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField fullWidth select required label="Primary timezone" value={form.primaryTimezone} disabled={!selectedDivision || timezones.isLoading} onChange={(e) => field('primaryTimezone', e.target.value)} helperText={!selectedDivision ? `Select ${divisionLabel.toLowerCase()} first` : undefined}>
                {(timezones.data ?? []).map((timezone) => <MenuItem key={timezone.id} value={timezone.ianaName}>{timezone.ianaName}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField fullWidth select required label="Default tax region" value={form.defaultTaxRegionCode} disabled={!selectedDivision || taxRegions.isLoading} onChange={(e) => field('defaultTaxRegionCode', e.target.value)}>
                {(taxRegions.data ?? []).map((region) => <MenuItem key={region.id} value={region.code}>{region.code} — {region.name}</MenuItem>)}
              </TextField>
            </Grid>
          </Grid>
        )}
        {activeStep === 2 && (
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}><TextField fullWidth required label="Owner first name" value={form.ownerFirstName} onChange={(e) => field('ownerFirstName', e.target.value)} /></Grid>
            <Grid item xs={12} sm={6}><TextField fullWidth required label="Owner last name" value={form.ownerLastName} onChange={(e) => field('ownerLastName', e.target.value)} /></Grid>
            <Grid item xs={12} sm={6}><TextField fullWidth required type="email" label="Owner email" value={form.ownerEmail} error={Boolean(fieldErrors.ownerEmail)} helperText={fieldErrors.ownerEmail} onChange={(e) => field('ownerEmail', e.target.value)} /></Grid>
            <Grid item xs={12} sm={6}><TextField fullWidth label="Owner phone" value={form.ownerPhone} onChange={(e) => field('ownerPhone', e.target.value)} /></Grid>
          </Grid>
        )}
        {activeStep === 3 && (
          <Grid container spacing={2}>
            <Grid item xs={12}><Paper variant="outlined" sx={{ p: 2 }}><Typography variant="h6">Store Operations</Typography><Typography color="text.secondary">What does this location operate?</Typography>
              <FormControlLabel control={<Checkbox checked={form.storeCapabilities.includes('RETAIL')} onChange={(_, checked) => field('storeCapabilities', checked ? [...form.storeCapabilities, 'RETAIL'] : form.storeCapabilities.filter((item) => item !== 'RETAIL'))} />} label="Convenience / Retail Store — Barcode scanning, retail inventory and standard POS" />
              <FormControlLabel control={<Checkbox checked={form.storeCapabilities.includes('FOOD_SERVICE')} onChange={(_, checked) => field('storeCapabilities', checked ? [...form.storeCapabilities, 'FOOD_SERVICE'] : form.storeCapabilities.filter((item) => item !== 'FOOD_SERVICE'))} />} label="Kitchen / Food Service — Food menu, tile POS and kitchen operations" />
              {form.storeCapabilities.includes('FOOD_SERVICE') ? <TextField fullWidth sx={{ mt: 1 }} label="Kitchen / Food Service Name" value={form.kitchenDisplayName} placeholder={`${form.operatingName || 'Store'} Kitchen`} onChange={(event) => field('kitchenDisplayName', event.target.value)} /> : null}
              {form.storeCapabilities.length === 0 ? <Alert severity="error">Select at least one operation.</Alert> : null}
            </Paper></Grid>
            <Grid item xs={12} md={6}><TextField fullWidth select required label="Pricing Plan" value={form.pricingPlanId} error={Boolean(fieldErrors.pricingPlanId)} onChange={(e) => field('pricingPlanId', e.target.value)} helperText={fieldErrors.pricingPlanId ?? (pricingPlans.isLoading ? 'Loading active pricing plans...' : undefined)}>
              {(pricingPlans.data ?? []).map((plan) => <MenuItem key={plan.id} value={plan.id}>{plan.name} — {new Intl.NumberFormat(undefined, { style: 'currency', currency: plan.currency }).format(plan.basePrice)}/month</MenuItem>)}
            </TextField></Grid>
            {selectedPlan && <Grid item xs={12}><Paper variant="outlined" sx={{ p: 2 }}><Typography variant="h6">{selectedPlan.name}</Typography>
              <Typography>Monthly Base: {new Intl.NumberFormat(undefined, { style: 'currency', currency: selectedPlan.currency }).format(selectedPlan.basePrice)}</Typography>
              <Typography>Included Stores: {selectedPlan.includedStores ?? 0}</Typography>
              <Typography>Additional Store: {new Intl.NumberFormat(undefined, { style: 'currency', currency: selectedPlan.currency }).format(selectedPlan.additionalStorePrice ?? 0)}/store/month</Typography>
              <Typography>Registers Included: {selectedPlan.includedRegisters ?? 0} per store</Typography>
              <Typography>Additional Register: {new Intl.NumberFormat(undefined, { style: 'currency', currency: selectedPlan.currency }).format(selectedPlan.additionalRegisterPrice ?? 0)}/register/month</Typography>
              <Typography>One-Time Onboarding: {new Intl.NumberFormat(undefined, { style: 'currency', currency: selectedPlan.currency }).format(selectedPlan.oneTimeOnboardingFee)}</Typography>
              {pricingPreview.data?.capabilityCharges.map(charge=><Typography key={charge.capability}>{charge.description}: +{new Intl.NumberFormat(undefined,{style:'currency',currency:pricingPreview.data.currency}).format(charge.monthlyPricePerStore)} / {charge.billingUnit.replace('PER_','').toLowerCase()} / month ({charge.storeCount} units; {new Intl.NumberFormat(undefined,{style:'currency',currency:pricingPreview.data.currency}).format(charge.monthlyTotal)} total)</Typography>)}
              <Typography fontWeight={600}>Estimated Monthly: {new Intl.NumberFormat(undefined, { style: 'currency', currency: selectedPlan.currency }).format(pricingPreview.data?.estimatedMonthlySubscription??selectedPlan.basePrice)}</Typography>
              <Typography color="text.secondary">Additional stores are billed beginning with the next billing cycle. Tax is calculated by the backend.</Typography>
            </Paper></Grid>}
            {Object.entries(form.features).map(([key, enabled]) => (
              <Grid item xs={12} sm={4} key={key}>
                <FormControlLabel control={<Switch checked={enabled} onChange={(e) => field('features', { ...form.features, [key]: e.target.checked })} />} label={key.toUpperCase()} />
              </Grid>
            ))}
          </Grid>
        )}
        {activeStep === 4 && (
          <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
            <Typography variant="h6">Review</Typography>
            <Divider sx={{ my: 2 }} />
            <Typography>{form.operatingName} ({form.legalBusinessName})</Typography>
            <Typography color="text.secondary">Tenant code: {form.tenantCode}</Typography>
            <Typography color="text.secondary">{selectedCountry?.name ?? form.countryCode} · {selectedDivision?.name ?? form.administrativeDivisionCode}</Typography>
            <Typography color="text.secondary">{selectedCurrency ? `${selectedCurrency.code} — ${selectedCurrency.name}` : form.defaultCurrencyCode} · {selectedTimezone?.ianaName ?? form.primaryTimezone} · {selectedTaxRegion ? `${selectedTaxRegion.code} — ${selectedTaxRegion.name}` : form.defaultTaxRegionCode}</Typography>
            <Typography color="text.secondary">Owner: {form.ownerFirstName} {form.ownerLastName} · {form.ownerEmail}</Typography>
            <Typography color="text.secondary">Plan: {selectedPlan?.name ?? 'Not selected'} · {selectedPlan?.currency ?? ''} {selectedPlan?.basePrice ?? ''}/month</Typography>
          </Paper>
        )}
        {activeStep === 5 && created && (
          <Alert severity="success" icon={<CheckCircleIcon />}>
            Merchant {created.tenant.displayName} created with code {created.tenant.tenantCode} on Pricing Plan {selectedPlan?.name}. {created.tenant.countryCode}/{created.tenant.administrativeDivisionCode}; {created.tenant.defaultCurrencyCode}; {created.tenant.primaryTimezone}; tax region {created.tenant.defaultTaxRegionCode}. Owner {created.tenant.primaryOwnerEmail}. Onboarding: {created.onboarding.currentStage.replaceAll('_', ' ')}.
          </Alert>
        )}
        <Stack direction="row" gap={1}>
          {activeStep > 0 && activeStep < 5 && <Button onClick={() => setActiveStep((step) => step - 1)}>Back</Button>}
          {activeStep < 5 && <Button type="submit" variant="contained" startIcon={activeStep === 4 ? <SaveIcon /> : undefined} disabled={mutation.isPending}>{activeStep === 4 ? 'Create merchant' : 'Continue'}</Button>}
          {created && <Button onClick={() => navigate(`/platform/merchants/${created.tenant.id}`)}>Open merchant</Button>}
        </Stack>
      </Stack>
    </RequirePlatform>
  );
}

export function PlatformMerchantDetailPage() {
  const location = useLocation();
  const { tenantId = '' } = useParams();
  const { getValidAccessToken } = usePlatformToken();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { currentUser, session } = useSession();
  const isSuperAdmin = (currentUser?.roles ?? session?.roles ?? []).includes('PLATFORM_SUPER_ADMIN');
  const tenant = useAuthedQuery(['platform-tenant', tenantId], (token) => getPlatformTenant(token, tenantId), Boolean(tenantId));
  const billingSubscription = useAuthedQuery(['platform-billing-subscription', tenantId], (token) => getPlatformBillingSubscription(token, tenantId), Boolean(tenantId));
  const ownerActivation = useAuthedQuery(['platform-owner-activation', tenantId], (token) => getOwnerActivationStatus(token, tenantId), Boolean(tenantId));
  const history = useAuthedQuery(['platform-tenant-status-history', tenantId], (token) => listTenantStatusHistory(token, tenantId), Boolean(tenantId));
  const deliveries = useAuthedQuery(['platform-email-deliveries', tenantId], (token) => listTenantEmailDeliveries(token, tenantId), Boolean(tenantId));
  const [plan, setPlan] = useState('');
  const [resendDialogOpen, setResendDialogOpen] = useState(false);
  const [resendReason, setResendReason] = useState('');
  const [resendNotes, setResendNotes] = useState('');
  const [resendConfirmed, setResendConfirmed] = useState(false);
  const [credentialsDialogOpen, setCredentialsDialogOpen] = useState(false);
  const [credentialsReason, setCredentialsReason] = useState('');
  const [credentialsNotes, setCredentialsNotes] = useState('');
  const [credentialsConfirmed, setCredentialsConfirmed] = useState(false);
  const [resetDialogOpen, setResetDialogOpen] = useState(false);
  const [resetReason, setResetReason] = useState('');

  const subscription = useMutation({
    mutationFn: async () => {
      const token = await getValidAccessToken();
      const current = tenant.data?.subscription;
      if (!current) throw new Error('Subscription is unavailable');
      return updatePlatformTenantSubscription(token, tenantId, {
        planCode: plan || current.planCode,
        status: current.status,
        startsAt: current.startsAt,
        trialEndsAt: current.trialEndsAt,
        renewsAt: current.renewsAt,
        cancelledAt: current.cancelledAt,
        maximumStores: current.maximumStores,
        maximumUsers: current.maximumUsers,
        features: current.features,
        version: current.version
      } satisfies TenantSubscriptionPayload);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['platform-tenant', tenantId] })
  });
  const resend = useMutation({
    mutationFn: async (payload: OwnerInvitationResendPayload) => resendOwnerInvitation(await getValidAccessToken(), tenantId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['platform-email-deliveries', tenantId] });
      queryClient.invalidateQueries({ queryKey: ['platform-owner-activation', tenantId] });
      queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
      setResendDialogOpen(false);
      setResendReason('');
      setResendNotes('');
      setResendConfirmed(false);
    }
  });
  const retryDelivery = useMutation({
    mutationFn: async (deliveryId: string) => retryEmailDelivery(await getValidAccessToken(), deliveryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['platform-email-deliveries', tenantId] });
      queryClient.invalidateQueries({ queryKey: ['platform-owner-activation', tenantId] });
    }
  });
  const reissueCredentials = useMutation({
    mutationFn: async (payload: OwnerInvitationResendPayload) => {
      const ownerId = ownerActivation.data?.ownerId;
      if (!ownerId) throw new Error('Owner account is unavailable');
      return resendTemporaryCredentials(await getValidAccessToken(), tenantId, ownerId, payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['platform-email-deliveries', tenantId] });
      queryClient.invalidateQueries({ queryKey: ['platform-owner-activation', tenantId] });
      queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
      setCredentialsDialogOpen(false);
      setCredentialsReason('');
      setCredentialsNotes('');
      setCredentialsConfirmed(false);
    }
  });
  const sendReset = useMutation({
    mutationFn: async () => {
      const ownerId = ownerActivation.data?.ownerId;
      if (!ownerId) throw new Error('Owner account is unavailable');
      return sendPlatformUserPasswordReset(await getValidAccessToken(), tenantId, ownerId, resetReason.trim());
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['platform-email-deliveries', tenantId] });
      setResetDialogOpen(false);
      setResetReason('');
    }
  });
  const unlockOwner = useMutation({
    mutationFn: async () => {
      const ownerId = ownerActivation.data?.ownerId;
      if (!ownerId) throw new Error('Owner account is unavailable');
      return unlockPlatformUser(await getValidAccessToken(), tenantId, ownerId, 'Identity verified by platform administrator');
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['platform-owner-activation', tenantId] })
  });

  if (tenant.isLoading) return <CircularProgress aria-label="Loading merchant" />;
  if (tenant.error) return <Alert severity="error">{tenant.error.message}</Alert>;
  const data = tenant.data;
  if (!data) return null;

  return (
    <RequirePlatform>
      <>
      <Stack spacing={3}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2}>
          <Box>
            <Typography variant="h4" component="h1">{data.tenant.displayName}</Typography>
            <Stack direction="row" gap={1} alignItems="center"><StatusChip status={data.tenant.status} /><Typography color="text.secondary">{data.tenant.tenantCode}</Typography></Stack>
          </Box>
          <Button component={Link} to={`/platform/merchants${location.state?.merchantListSearch ? `?${location.state.merchantListSearch}` : ''}`}>All merchants</Button>
        </Stack>
        {(subscription.error || resend.error || retryDelivery.error || reissueCredentials.error || sendReset.error || unlockOwner.error || ownerActivation.error) && <Alert severity="error">{(subscription.error || resend.error || retryDelivery.error || reissueCredentials.error || sendReset.error || unlockOwner.error || ownerActivation.error)?.message}</Alert>}
        {sendReset.data && <Alert severity={sendReset.data.status === 'SENT' ? 'success' : 'warning'}>Password reset delivery status: {sendReset.data.status}. No password or reset token is visible to administrators.</Alert>}
        {resend.data && (
          <Alert severity={resend.data.delivery?.status === 'SENT' ? 'success' : 'warning'}>
            {resend.data.delivery?.status === 'SENT'
              ? 'Activation email sent successfully. A new invitation link was generated and the previous link was invalidated.'
              : 'A new activation link was created, but the email could not be sent. You can retry the email delivery without generating another link.'}
          </Alert>
        )}
        {reissueCredentials.data && (
          <Alert severity="success">
            Temporary credentials were reissued and emailed. The previous temporary password and password-change tokens were invalidated.
          </Alert>
        )}
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}><TenantOverview tenant={data} /></Grid>
          <Grid item xs={12} md={6}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
              <Typography variant="h6">Lifecycle</Typography>
              <Stack spacing={2} sx={{ mt: 2 }}>
                <Typography>Status: {data.tenant.status.replaceAll('_', ' ')}</Typography>
                <Typography>Created: {new Date(data.tenant.createdAt).toLocaleString()}</Typography>
                <Typography>Activated: {data.tenant.activatedAt ? new Date(data.tenant.activatedAt).toLocaleString() : 'Not activated'}</Typography>
                <Typography>Suspended: {data.tenant.suspendedAt ? new Date(data.tenant.suspendedAt).toLocaleString() : 'Not suspended'}</Typography>
                {data.tenant.suspensionReason && <Typography>Suspension reason: {data.tenant.suspensionReason}</Typography>}
                <Typography>Closed: {data.tenant.closedAt ? new Date(data.tenant.closedAt).toLocaleString() : 'Not closed'}</Typography>
                {data.tenant.closureReason && <Typography>Closure reason: {data.tenant.closureReason}</Typography>}
                <Typography>Last reactivation: {data.tenant.reactivatedAt ? new Date(data.tenant.reactivatedAt).toLocaleString() : 'None'}</Typography>
                <LifecycleActions merchant={data.tenant} onDeleted={() => navigate('/platform/merchants')} />
              </Stack>
            </Paper>
          </Grid>
          <Grid item xs={12}>
            <OwnerActivationCard
              status={ownerActivation.data}
              loading={ownerActivation.isLoading}
              onResend={() => setResendDialogOpen(true)}
              onRetry={(deliveryId) => retryDelivery.mutate(deliveryId)}
              onResendTemporaryCredentials={() => setCredentialsDialogOpen(true)}
              retrying={retryDelivery.isPending}
              resending={resend.isPending}
              reissuingCredentials={reissueCredentials.isPending}
              canManagePasswordReset={isSuperAdmin && data.tenant.status !== 'CLOSED'}
              onSendPasswordReset={() => setResetDialogOpen(true)}
              onUnlock={() => unlockOwner.mutate()}
              sendingPasswordReset={sendReset.isPending}
              unlocking={unlockOwner.isPending}
            />
          </Grid>
          <Grid item xs={12}>
            <MerchantStoreCapabilities tenantId={tenantId} canEdit={isSuperAdmin} />
          </Grid>
          <Grid item xs={12} md={6}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
              <Typography variant="h6">Subscription & Billing</Typography>
              {billingSubscription.data ? <Stack spacing={1} sx={{ mt: 2 }}>
                <Typography>Plan: {billingSubscription.data.planName}</Typography>
                <Typography>Monthly Base: {new Intl.NumberFormat(undefined, { style: 'currency', currency: billingSubscription.data.currency }).format(billingSubscription.data.merchantBasePrice)}</Typography>
                <Typography>Included Stores: {billingSubscription.data.includedStoresSnapshot ?? 0}</Typography>
                <Typography>Current Billable Stores: {billingSubscription.data.currentBillableStores}</Typography>
                <Typography>Additional Billable Stores: {billingSubscription.data.additionalBillableStores}</Typography>
                <Typography>Additional Store Rate: {new Intl.NumberFormat(undefined, { style: 'currency', currency: billingSubscription.data.currency }).format(billingSubscription.data.additionalStorePriceSnapshot ?? 0)}</Typography>
                <Typography>Included Registers Per Store: {billingSubscription.data.includedRegistersPerStoreSnapshot ?? 0}</Typography>
                <Typography>Additional Register Rate: {new Intl.NumberFormat(undefined, { style: 'currency', currency: billingSubscription.data.currency }).format(billingSubscription.data.additionalRegisterPriceSnapshot ?? 0)}</Typography>
                <Typography>Active Registers: {billingSubscription.data.currentBillableRegisters}</Typography>
                <Typography>Additional Registers: {billingSubscription.data.additionalBillableRegisters}</Typography>
                {(billingSubscription.data.registerUsage??[]).map(store=><Typography key={store.storeId} variant="body2">{store.storeName}: {store.activeRegisters} registers · {store.includedRegisters} included · {store.additionalRegisters} additional</Typography>)}
                <Typography>Upcoming Additional Register Charge: {new Intl.NumberFormat(undefined, { style: 'currency', currency: billingSubscription.data.currency }).format(billingSubscription.data.estimatedAdditionalRegisterCharge)}</Typography>
                <Typography fontWeight={600}>Estimated Monthly: {new Intl.NumberFormat(undefined, { style: 'currency', currency: billingSubscription.data.currency }).format(billingSubscription.data.estimatedMonthlyPrice)}</Typography>
                <Typography>One-Time Onboarding Fee: {new Intl.NumberFormat(undefined, { style: 'currency', currency: billingSubscription.data.currency }).format(billingSubscription.data.onboardingFeeSnapshot ?? 0)}</Typography>
                <Typography>Onboarding Fee Status: {billingSubscription.data.onboardingFeeInvoicedAt ? 'INVOICED' : 'NOT INVOICED'}</Typography>
                <Typography>Next Billing Date: {billingSubscription.data.nextBillingDate}</Typography>
                <Stack direction="row"><Button component={Link} to="/platform/billing/subscriptions">Change Plan / Edit Pricing</Button><Button component={Link} to="/platform/billing/invoices">View Invoices</Button></Stack>
              </Stack> : <Typography sx={{ mt: 2 }} color="text.secondary">Billing subscription details are unavailable.</Typography>}
            </Paper>
          </Grid>
          <Grid item xs={12} md={6}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
              <Typography variant="h6">Onboarding</Typography>
              <Stack spacing={1} sx={{ mt: 2 }}>
                {data.onboarding.stages.map((stage) => (
                  <Stack key={stage.stage} direction="row" justifyContent="space-between">
                    <Typography>{stage.stage.replaceAll('_', ' ')}</Typography>
                    <Typography color="text.secondary">{stage.completedAt ? new Date(stage.completedAt).toLocaleString() : 'Pending'}</Typography>
                  </Stack>
                ))}
              </Stack>
            </Paper>
          </Grid>
          <Grid item xs={12}>
            <EmailDeliveryHistory
              deliveries={deliveries.data ?? []}
              loading={deliveries.isLoading}
              error={deliveries.error?.message}
              retryingId={retryDelivery.variables}
              onRetry={(deliveryId) => retryDelivery.mutate(deliveryId)}
            />
          </Grid>
          <Grid item xs={12}>
            <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
              <Typography variant="h6">Recent status history</Typography>
              {history.isLoading && <CircularProgress aria-label="Loading status history" size={24} sx={{ mt: 2 }} />}
              {history.error && <Alert severity="error" sx={{ mt: 2 }}>{history.error.message}</Alert>}
              <Stack spacing={1} sx={{ mt: 2 }}>
                {(history.data ?? []).map((event) => (
                  <Stack key={event.id} direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={1}>
                    <Typography>{event.previousStatus ?? 'CREATED'} to {event.newStatus}{event.reason ? `: ${event.reason}` : ''}</Typography>
                    <Typography color="text.secondary">{new Date(event.changedAt).toLocaleString()}</Typography>
                  </Stack>
                ))}
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      </Stack>
      <Dialog open={resendDialogOpen} onClose={() => setResendDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Resend Activation Email</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Alert severity="warning">A new activation link will be generated. Any previous unused activation link will stop working.</Alert>
            <TextField label="Owner email" value={ownerActivation.data?.ownerEmail ?? ''} InputProps={{ readOnly: true }} />
            <TextField label="Current invitation status" value={ownerActivation.data?.invitationStatus ?? ''} InputProps={{ readOnly: true }} />
            <TextField label="Current expiry time" value={ownerActivation.data?.invitationExpiresAt ? new Date(ownerActivation.data.invitationExpiresAt).toLocaleString() : ''} InputProps={{ readOnly: true }} />
            <TextField label="Reason" value={resendReason} onChange={(event) => setResendReason(event.target.value)} required multiline minRows={2} />
            <TextField label="Notes" value={resendNotes} onChange={(event) => setResendNotes(event.target.value)} multiline minRows={2} />
            <FormControlLabel
              control={<Switch checked={resendConfirmed} onChange={(event) => setResendConfirmed(event.target.checked)} />}
              label="I understand the previous unused activation link will stop working"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResendDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={resend.isPending || !resendConfirmed || resendReason.trim() === ''}
            onClick={() => resend.mutate({ reason: resendReason.trim(), notes: resendNotes.trim() || undefined })}
          >
            Resend Activation Email
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={credentialsDialogOpen} onClose={() => setCredentialsDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Resend Temporary Credentials</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Alert severity="warning">A new temporary password will be generated. Any previous temporary password and pending password-change token will stop working.</Alert>
            <TextField label="Owner email" value={ownerActivation.data?.ownerEmail ?? ''} InputProps={{ readOnly: true }} />
            <TextField label="Temporary credentials expire" value={formatDateTime(ownerActivation.data?.temporaryCredentialsExpiresAt)} InputProps={{ readOnly: true }} />
            <TextField label="Reason" value={credentialsReason} onChange={(event) => setCredentialsReason(event.target.value)} required multiline minRows={2} />
            <TextField label="Notes" value={credentialsNotes} onChange={(event) => setCredentialsNotes(event.target.value)} multiline minRows={2} />
            <FormControlLabel
              control={<Switch checked={credentialsConfirmed} onChange={(event) => setCredentialsConfirmed(event.target.checked)} />}
              label="I understand previous temporary credentials will stop working"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCredentialsDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={reissueCredentials.isPending || !credentialsConfirmed || credentialsReason.trim() === ''}
            onClick={() => reissueCredentials.mutate({ reason: credentialsReason.trim(), notes: credentialsNotes.trim() || undefined })}
          >
            Resend Temporary Credentials
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={resetDialogOpen} onClose={() => setResetDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Send Password Reset Link</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Alert severity="info">The user chooses their new password. Sending this email does not unlock the account.</Alert>
            <TextField label="User" value={ownerActivation.data?.ownerEmail ?? ''} InputProps={{ readOnly: true }} />
            <TextField label="Reason" value={resetReason} onChange={(event) => setResetReason(event.target.value)} required multiline minRows={2} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResetDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={sendReset.isPending || !resetReason.trim()} onClick={() => sendReset.mutate()}>Send Password Reset Link</Button>
        </DialogActions>
      </Dialog>
      </>
    </RequirePlatform>
  );
}

const capabilityLabels: Record<StoreCapability, string> = {
  RETAIL: 'Retail POS',
  FOOD_SERVICE: 'Restaurant / Kitchen POS',
  LOTTERY: 'Lottery'
};

function MerchantStoreCapabilities({ tenantId, canEdit }: { tenantId: string; canEdit: boolean }) {
  const { getValidAccessToken } = usePlatformToken();
  const queryClient = useQueryClient();
  const stores = useAuthedQuery(['platform-tenant-stores', tenantId], (token) => listPlatformTenantStores(token, tenantId), Boolean(tenantId));
  const [editing, setEditing] = useState<MerchantStoreCapability | null>(null);
  const [selected, setSelected] = useState<StoreCapability[]>([]);
  const [kitchenName, setKitchenName] = useState('');
  const [preview, setPreview] = useState<StoreCapabilityChangePreview | null>(null);

  const save = useMutation({
    mutationFn: async (confirmPaidAddOns: boolean) => {
      if (!editing) throw new Error('Store is unavailable');
      const token = await getValidAccessToken();
      const payload = { capabilities: selected, kitchenDisplayName: kitchenName || null, confirmPaidAddOns, version: editing.version };
      if (!confirmPaidAddOns) {
        const next = await previewPlatformStoreCapabilities(token, tenantId, editing.storeId, payload);
        if (next.confirmationRequired) { setPreview(next); return null; }
      }
      return updatePlatformStoreCapabilities(token, tenantId, editing.storeId, payload);
    },
    onSuccess: (updated) => {
      if (!updated) return;
      queryClient.invalidateQueries({ queryKey: ['platform-tenant-stores', tenantId] });
      queryClient.invalidateQueries({ queryKey: ['platform-billing-subscription', tenantId] });
      queryClient.invalidateQueries({ queryKey: ['platform-pricing-preview', tenantId] });
      setEditing(null);
      setPreview(null);
    }
  });

  function open(store: MerchantStoreCapability) {
    setEditing(store);
    setSelected(store.capabilities);
    setKitchenName(store.kitchenDisplayName ?? `${store.storeName} Kitchen`);
    setPreview(null);
    save.reset();
  }

  function toggle(capability: StoreCapability, checked: boolean) {
    setSelected((current) => checked ? Array.from(new Set([...current, capability])) : current.filter((value) => value !== capability));
  }

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 2.5 }, borderRadius: 1, minWidth: 0 }}>
      <Stack spacing={2}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={1}>
          <Box><Typography variant="h6">Store Operations & Capabilities</Typography><Typography color="text.secondary">Capabilities are managed independently for each store.</Typography></Box>
        </Stack>
        {stores.isLoading && <CircularProgress size={24} aria-label="Loading merchant stores" />}
        {stores.error && <Alert severity="error">{getApiErrorMessage(stores.error, 'Merchant stores could not be loaded.')}</Alert>}
        <Grid container spacing={2}>
          {(stores.data ?? []).map((store) => (
            <Grid item xs={12} md={6} key={store.storeId} sx={{ minWidth: 0 }}>
              <Paper variant="outlined" sx={{ p: 2, height: '100%', minWidth: 0 }}>
                <Stack spacing={1.5}>
                  <Stack direction="row" justifyContent="space-between" gap={1} alignItems="flex-start">
                    <Box sx={{ minWidth: 0 }}><Typography fontWeight={700} noWrap title={store.storeName}>{store.storeName}</Typography><Typography variant="body2" color="text.secondary">{store.storeCode}</Typography></Box>
                    {canEdit && <Button size="small" onClick={() => open(store)}>Edit</Button>}
                  </Stack>
                  <Stack direction="row" gap={1} flexWrap="wrap">
                    {store.capabilities.map((capability) => <Chip key={capability} label={capabilityLabels[capability]} size="small" color="primary" variant="outlined" />)}
                  </Stack>
                  {store.capabilities.includes('FOOD_SERVICE') && <Typography variant="body2">Kitchen display: {store.kitchenDisplayName}</Typography>}
                </Stack>
              </Paper>
            </Grid>
          ))}
        </Grid>
      </Stack>
      <Dialog open={Boolean(editing)} onClose={() => !save.isPending && setEditing(null)} fullWidth maxWidth="sm">
        <DialogTitle>Edit Store Capabilities — {editing?.storeName}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {save.error && <Alert severity="error">{getApiErrorMessage(save.error, 'Store capabilities could not be updated.')}</Alert>}
            {(['RETAIL', 'FOOD_SERVICE', 'LOTTERY'] as StoreCapability[]).map((capability) => (
              <FormControlLabel key={capability} control={<Checkbox checked={selected.includes(capability)} onChange={(_, checked) => toggle(capability, checked)} />} label={capabilityLabels[capability]} />
            ))}
            {selected.includes('FOOD_SERVICE') && <TextField fullWidth label="Kitchen Display Name" value={kitchenName} onChange={(event) => setKitchenName(event.target.value)} inputProps={{ maxLength: 180 }} />}
          </Stack>
        </DialogContent>
        <DialogActions><Button onClick={() => setEditing(null)}>Cancel</Button><Button variant="contained" disabled={save.isPending || selected.length === 0} onClick={() => save.mutate(false)}>Review & Save</Button></DialogActions>
      </Dialog>
      <Dialog open={Boolean(preview)} onClose={() => !save.isPending && setPreview(null)} fullWidth maxWidth="sm">
        <DialogTitle>Confirm Pricing Change</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Alert severity="info">Operational access activates immediately. The updated quantity is reflected on the next billing cycle; no mid-cycle proration is created.</Alert>
            {(preview?.impacts ?? []).map((impact) => (
              <Paper variant="outlined" sx={{ p: 2 }} key={impact.capability}>
                <Typography fontWeight={700}>{capabilityLabels[impact.capability === 'RETAIL_POS' ? 'RETAIL' : impact.capability]}</Typography>
                <Typography>{impact.currentQuantity} → {impact.newQuantity} {impact.billingUnit?.replace('PER_', 'per ').toLowerCase()}</Typography>
                <Typography>{new Intl.NumberFormat(undefined, { style: 'currency', currency: preview?.currency ?? 'CAD' }).format(impact.currentMonthlyAmount)} → {new Intl.NumberFormat(undefined, { style: 'currency', currency: preview?.currency ?? 'CAD' }).format(impact.newMonthlyAmount)} / month</Typography>
              </Paper>
            ))}
            <Typography variant="body2" color="text.secondary">Effective billing date: {preview?.effectiveDate}</Typography>
          </Stack>
        </DialogContent>
        <DialogActions><Button onClick={() => setPreview(null)}>Cancel</Button><Button variant="contained" disabled={save.isPending} onClick={() => save.mutate(true)}>Confirm Change</Button></DialogActions>
      </Dialog>
    </Paper>
  );
}

function OwnerActivationCard({
  status,
  loading,
  onResend,
  onRetry,
  onResendTemporaryCredentials,
  retrying,
  resending,
  reissuingCredentials,
  canManagePasswordReset,
  onSendPasswordReset,
  onUnlock,
  sendingPasswordReset,
  unlocking
}: {
  status?: OwnerActivationStatus;
  loading: boolean;
  onResend: () => void;
  onRetry: (deliveryId: string) => void;
  onResendTemporaryCredentials: () => void;
  retrying: boolean;
  resending: boolean;
  reissuingCredentials: boolean;
  canManagePasswordReset: boolean;
  onSendPasswordReset: () => void;
  onUnlock: () => void;
  sendingPasswordReset: boolean;
  unlocking: boolean;
}) {
  if (loading) {
    return (
      <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
        <Typography variant="h6">Owner Activation</Typography>
        <CircularProgress aria-label="Loading owner activation" size={24} sx={{ mt: 2 }} />
      </Paper>
    );
  }
  if (!status) return null;
  const invitationColor = status.invitationStatus === 'USED' ? 'success' : status.invitationStatus === 'EXPIRED' || status.invitationStatus === 'INVALIDATED' ? 'warning' : 'info';
  const deliveryColor = status.latestEmailDeliveryStatus === 'SENT' ? 'success' : status.latestEmailDeliveryStatus === 'FAILED' ? 'error' : status.latestEmailDeliveryStatus === 'RETRY_SCHEDULED' ? 'warning' : 'default';
  return (
    <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
      <Stack spacing={2}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={1}>
          <Typography variant="h6">Owner Activation</Typography>
          <Stack direction="row" gap={1} flexWrap="wrap">
            <Chip label={`Invitation ${status.invitationStatus}`} color={invitationColor} size="small" />
            <Chip label={`Owner ${status.ownerAccountStatus.replaceAll('_', ' ')}`} color={status.ownerAccountStatus === 'ACTIVATED' ? 'success' : 'warning'} size="small" />
            {status.latestEmailDeliveryStatus && <Chip label={`Email ${status.latestEmailDeliveryStatus}`} color={deliveryColor} size="small" />}
          </Stack>
        </Stack>
        <Grid container spacing={1.5}>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Owner name" value={status.ownerName} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Owner email" value={status.ownerEmail} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Owner account status" value={status.ownerAccountStatus.replaceAll('_', ' ')} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Invitation status" value={status.invitationStatus} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Invitation created time" value={formatDateTime(status.invitationCreatedAt)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Invitation expiry time" value={formatDateTime(status.invitationExpiresAt)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Email provider" value={status.emailProvider} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Latest email-delivery status" value={status.latestEmailDeliveryStatus ?? 'No delivery'} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Latest attempt time" value={formatDateTime(status.latestAttemptAt)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Email sent time" value={formatDateTime(status.emailSentAt)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Attempt count" value={String(status.attemptCount)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Activation completed time" value={formatDateTime(status.activationCompletedAt)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Temporary credentials issued" value={formatDateTime(status.temporaryCredentialsIssuedAt)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Temporary credentials expire" value={formatDateTime(status.temporaryCredentialsExpiresAt)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Credentials delivery status" value={status.credentialsDeliveryStatus ?? 'No delivery'} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="First login completed" value={formatDateTime(status.firstLoginAt)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Password change completed" value={formatDateTime(status.passwordChangedAt)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Failed attempts" value={String(status.failedLoginAttempts ?? 0)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Locked at" value={formatDateTime(status.lockedAt)} /></Grid>
          <Grid item xs={12} sm={6} md={3}><InfoValue label="Lock reason" value={status.lockReason?.replaceAll('_', ' ') ?? 'Not locked'} /></Grid>
          <Grid item xs={12}><InfoValue label="Sanitized failure message" value={status.sanitizedFailureMessage ?? 'None'} /></Grid>
        </Grid>
        {status.temporaryCredentialsExpired && (
          <Alert severity="warning">Temporary credentials have expired. Reissue temporary credentials before the owner can complete first login.</Alert>
        )}
        {status.ownerAccountStatus === 'ACTIVATED' && (
          <Alert severity="success">Merchant owner has already activated their account. Invitation SENT is separate from Owner ACTIVATED.</Alert>
        )}
        <Stack direction="row" gap={1} flexWrap="wrap">
          {status.canResend && (
            <Button variant="contained" startIcon={<RefreshIcon />} onClick={onResend} disabled={resending}>
              Resend Activation Email
            </Button>
          )}
          {status.canRetry && status.retryDeliveryId && (
            <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => onRetry(status.retryDeliveryId!)} disabled={retrying}>
              Retry Failed Email
            </Button>
          )}
          {status.canResendTemporaryCredentials && (
            <Button variant="outlined" startIcon={<RefreshIcon />} onClick={onResendTemporaryCredentials} disabled={reissuingCredentials}>
              Resend Temporary Credentials
            </Button>
          )}
          {canManagePasswordReset && (
            <Button variant="contained" startIcon={<RefreshIcon />} onClick={onSendPasswordReset} disabled={sendingPasswordReset}>
              Send Password Reset Link
            </Button>
          )}
          {canManagePasswordReset && status.ownerAccountStatus === 'LOCKED' && (
            <Button variant="outlined" onClick={onUnlock} disabled={unlocking}>Unlock Account</Button>
          )}
          <Button variant="outlined" href="#delivery-history">View Delivery History</Button>
          {status.canCopyActivationLink && status.activationUrl && (
            <Button variant="outlined" onClick={() => navigator.clipboard.writeText(status.activationUrl!)}>
              Copy Activation Link
            </Button>
          )}
        </Stack>
      </Stack>
    </Paper>
  );
}

function InfoValue({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" display="block">{label}</Typography>
      <Typography>{value || 'Not available'}</Typography>
    </Box>
  );
}

function formatDateTime(value?: string | null) {
  return value ? new Date(value).toLocaleString() : 'Not available';
}

function TenantOverview({ tenant }: { tenant: TenantDetail }) {
  return (
    <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
      <Typography variant="h6">Merchant metadata</Typography>
      <Stack spacing={1} sx={{ mt: 2 }}>
        <Typography>Owner: {tenant.tenant.primaryOwnerEmail ?? 'Pending'}</Typography>
        <Typography>Country: {tenant.tenant.countryCode}</Typography>
        <Typography>Province / State: {tenant.tenant.administrativeDivisionCode ?? 'Not set'}</Typography>
        <Typography>Currency: {tenant.tenant.defaultCurrencyCode}</Typography>
        <Typography>Timezone: {tenant.tenant.primaryTimezone}</Typography>
        <Typography>Tax region: {tenant.tenant.defaultTaxRegionCode ?? 'Not set'}</Typography>
        <Typography>Stores: {tenant.tenant.storeCount}</Typography>
        <Typography>Users: {tenant.tenant.userCount}</Typography>
      </Stack>
    </Paper>
  );
}

function EmailDeliveryHistory({
  deliveries,
  loading,
  error,
  retryingId,
  onRetry
}: {
  deliveries: EmailDelivery[];
  loading: boolean;
  error?: string;
  retryingId?: string;
  onRetry: (deliveryId: string) => void;
}) {
  return (
    <Paper id="delivery-history" variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
      <Typography variant="h6">Invitation delivery history</Typography>
      {loading && <CircularProgress aria-label="Loading email deliveries" size={24} sx={{ mt: 2 }} />}
      {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      {!loading && !error && deliveries.length === 0 && (
        <Typography sx={{ mt: 2 }} color="text.secondary">No invitation email deliveries recorded.</Typography>
      )}
      {deliveries.length > 0 && (
        <Table size="small" sx={{ mt: 2 }}>
          <TableHead>
            <TableRow>
              <TableCell>Attempt</TableCell>
              <TableCell>Invitation Revision</TableCell>
              <TableCell>Provider</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Requested By</TableCell>
              <TableCell>Requested At</TableCell>
              <TableCell>Sent At</TableCell>
              <TableCell>Failure</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {deliveries.map((delivery) => {
              const canRetry = delivery.status === 'FAILED' || delivery.status === 'RETRY_SCHEDULED';
              return (
                <TableRow key={delivery.id}>
                  <TableCell>{delivery.attemptCount}</TableCell>
                  <TableCell>{delivery.invitationId ? delivery.invitationId.slice(0, 8) : 'None'}</TableCell>
                  <TableCell>{delivery.provider}</TableCell>
                  <TableCell><EmailStatusChip status={delivery.status} /></TableCell>
                  <TableCell>{delivery.requestedByPlatformUserId ? delivery.requestedByPlatformUserId.slice(0, 8) : 'System'}</TableCell>
                  <TableCell>{new Date(delivery.createdAt).toLocaleString()}</TableCell>
                  <TableCell>{delivery.sentAt ? new Date(delivery.sentAt).toLocaleString() : ''}</TableCell>
                  <TableCell>{delivery.failureMessageSanitized ?? ''}</TableCell>
                  <TableCell align="right">
                    {canRetry && (
                      <Button size="small" startIcon={<RefreshIcon />} onClick={() => onRetry(delivery.id)} disabled={retryingId === delivery.id}>
                        Retry
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </Paper>
  );
}

function EmailStatusChip({ status }: { status: EmailDelivery['status'] }) {
  const color = status === 'SENT' ? 'success' : status === 'FAILED' ? 'error' : status === 'RETRY_SCHEDULED' ? 'warning' : 'default';
  return <Chip label={status.replaceAll('_', ' ')} color={color} size="small" />;
}

export function PlatformAuditPage() {
  const audit = useAuthedQuery(['platform-audit'], (token) => listPlatformAuditEvents(token, { size: 50 }));
  return (
    <RequirePlatform>
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">Platform audit</Typography>
        {audit.isLoading && <CircularProgress aria-label="Loading audit events" />}
        {audit.error && <Alert severity="error">{audit.error.message}</Alert>}
        <Paper variant="outlined" sx={{ borderRadius: 1, overflow: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Time</TableCell>
                <TableCell>Action</TableCell>
                <TableCell>Entity</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell>Correlation</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(audit.data?.content ?? []).map((event) => (
                <TableRow key={event.id}>
                  <TableCell>{new Date(event.createdAt).toLocaleString()}</TableCell>
                  <TableCell>{event.action}</TableCell>
                  <TableCell>{event.entityType}{event.entityId ? ` ${event.entityId.slice(0, 8)}` : ''}</TableCell>
                  <TableCell>{event.reason ?? ''}</TableCell>
                  <TableCell>{event.correlationId ?? ''}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      </Stack>
    </RequirePlatform>
  );
}

export function PlatformSettingsPage() {
  const { getValidAccessToken } = usePlatformToken();
  const settings = useAuthedQuery(['platform-settings'], getPlatformSettings);
  const providerStatus = useAuthedQuery(['platform-email-provider-status'], getEmailProviderStatus);
  const [testRecipient, setTestRecipient] = useState('');
  const testEmail = useMutation({
    mutationFn: async () => sendPlatformTestEmail(await getValidAccessToken(), testRecipient)
  });

  return (
    <RequirePlatform>
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">Platform settings</Typography>
        {settings.isLoading && <CircularProgress aria-label="Loading settings" />}
        {settings.error && <Alert severity="error">{settings.error.message}</Alert>}
        {providerStatus.error && <Alert severity="error">{providerStatus.error.message}</Alert>}
        {testEmail.error && <Alert severity="error">{testEmail.error.message}</Alert>}
        {testEmail.data && (
          <Alert severity={testEmail.data.status === 'SENT' ? 'success' : 'warning'}>
            Test email {testEmail.data.status.replaceAll('_', ' ').toLowerCase()} via {testEmail.data.provider}.
          </Alert>
        )}
        {settings.data && (
          <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
            <Stack spacing={1}>
              <Stack direction="row" spacing={1} alignItems="center"><SettingsIcon color="primary" /><Typography>Bootstrap {settings.data.bootstrapEnabled ? 'enabled' : 'disabled'}</Typography></Stack>
              <Typography>Owner invitation expiry: {settings.data.ownerInvitationExpiryHours} hours</Typography>
              <Typography>Support access: {settings.data.supportAccessEnabled ? `${settings.data.supportAccessDefaultMinutes} minutes` : 'disabled'}</Typography>
              <Typography>Tenant statuses: {settings.data.tenantStatuses.join(', ')}</Typography>
            </Stack>
          </Paper>
        )}
        <Paper variant="outlined" sx={{ p: 2, borderRadius: 1 }}>
          <Stack spacing={2}>
            <Typography variant="h6">Transactional email</Typography>
            {providerStatus.isLoading && <CircularProgress aria-label="Loading email provider status" size={24} />}
            {providerStatus.data && (
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                <Chip label={`Provider ${providerStatus.data.provider}`} color={providerStatus.data.configured ? 'success' : 'warning'} />
                <Chip label={providerStatus.data.enabled ? 'Resend enabled' : 'Resend disabled'} />
                <Chip label={providerStatus.data.fromAddressConfigured ? 'Sender configured' : 'Sender missing'} color={providerStatus.data.fromAddressConfigured ? 'default' : 'warning'} />
              </Stack>
            )}
            <Stack
              component="form"
              direction={{ xs: 'column', sm: 'row' }}
              gap={2}
              onSubmit={(event) => {
                event.preventDefault();
                testEmail.mutate();
              }}
            >
              <TextField
                label="Test recipient"
                type="email"
                value={testRecipient}
                onChange={(event) => setTestRecipient(event.target.value)}
                required
                fullWidth
              />
              <Button type="submit" variant="contained" disabled={testEmail.isPending || testRecipient.trim() === ''}>
                Send Test Email
              </Button>
            </Stack>
          </Stack>
        </Paper>
      </Stack>
    </RequirePlatform>
  );
}

export function PlatformLoginPage() {
  const { session, loginWithPlatformCredentials } = useSession();
  const [searchParams] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const from = searchParams.get('from') || '/platform';
  const isPlatform = useMemo(() => session?.roles.some((role) => role === 'PLATFORM_SUPER_ADMIN' || role === 'PLATFORM_SUPPORT_ADMIN') ?? false, [session]);

  if (session && isPlatform) {
    return <Navigate to={from} replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await loginWithPlatformCredentials({ email, password });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    }
  }

  return (
    <Stack minHeight="100dvh" alignItems="center" justifyContent="center" sx={{ bgcolor: 'background.default', p: 2 }}>
      <Paper component="form" onSubmit={submit} variant="outlined" sx={{ width: '100%', maxWidth: 420, p: 3, borderRadius: 1 }}>
        <Stack spacing={2}>
          <Stack direction="row" spacing={1} alignItems="center">
            <StorefrontIcon color="primary" />
            <Typography variant="h5" component="h1">Merchtyl Platform</Typography>
          </Stack>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          <TextField label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <Button type="submit" variant="contained">Sign in</Button>
        </Stack>
      </Paper>
    </Stack>
  );
}
