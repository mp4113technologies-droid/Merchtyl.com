import AssessmentOutlinedIcon from '@mui/icons-material/AssessmentOutlined';
import DownloadIcon from '@mui/icons-material/Download';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import LockOpenOutlinedIcon from '@mui/icons-material/LockOpenOutlined';
import PrintIcon from '@mui/icons-material/Print';
import RefreshIcon from '@mui/icons-material/Refresh';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import StorefrontIcon from '@mui/icons-material/Storefront';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
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
  FormControlLabel,
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
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import {
  closeBusinessDay,
  exportEndOfDayReportCsv,
  exportEndOfDayReportPdf,
  forceCloseBusinessDay,
  getBusinessDayClosingPreview,
  getBusinessDayClosingValidation,
  getCurrentBusinessDay,
  getLatestBusinessDay,
  getEndOfDayReport,
  getEndOfDayReportPrintHtml,
  listBusinessDays,
  listEndOfDayReports,
  listStores,
  openBusinessDay,
  reopenBusinessDay,
  startBusinessDayClosing
} from '../../api/client';
import type { BusinessDay, BusinessDayStatus, ClosingBlocker, EndOfDayClosingPreview, EndOfDayReport, Store, UserRole } from '../../api/types';
import { useSession } from '../../app/session';

function canManageBusinessDay(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function canForceOrReopen(roles: UserRole[]) {
  return roles.includes('OWNER') || roles.includes('TENANT_OWNER');
}

function canReopenBusinessDay(roles: UserRole[]) {
  return canForceOrReopen(roles) || roles.includes('MANAGER') || roles.includes('STORE_MANAGER');
}

function useRoles() {
  const { currentUser, session } = useSession();
  return currentUser?.roles ?? session?.roles ?? [];
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function idempotencyKey(action: string) {
  return `${action}-${crypto.randomUUID()}`;
}

function money(value: number | null | undefined, currencyCode = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currencyCode }).format(Number(value ?? 0));
}

function statusColor(status: BusinessDayStatus): 'success' | 'warning' | 'default' | 'info' {
  if (status === 'CLOSED') return 'success';
  if (status === 'CLOSING') return 'warning';
  if (status === 'REOPENED') return 'info';
  return 'default';
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 220 }} role="status" aria-live="polite">
      <CircularProgress aria-label={label} />
      <Typography color="text.secondary">{label}</Typography>
    </Stack>
  );
}

function StoreSelect({ stores, value, onChange }: { stores: Store[]; value: string; onChange: (value: string) => void }) {
  return (
    <TextField select label="Store" value={value} onChange={(event) => onChange(event.target.value)} sx={{ minWidth: { xs: '100%', sm: 280 } }}>
      {stores.map((store) => <MenuItem key={store.id} value={store.id}>{store.name} ({store.code})</MenuItem>)}
    </TextField>
  );
}

function BlockerList({ blockers }: { blockers: ClosingBlocker[] }) {
  if (blockers.length === 0) {
    return <Alert severity="success">No closing blockers detected.</Alert>;
  }
  return (
    <Alert severity="warning">
      <Stack component="ul" sx={{ m: 0, pl: 2 }}>
        {blockers.map((blocker) => (
          <Typography component="li" key={`${blocker.code}-${blocker.relatedId ?? blocker.message}`}>{blocker.message}</Typography>
        ))}
      </Stack>
    </Alert>
  );
}

function Metric({ label, value, tone = 'default' }: { label: string; value: string; tone?: 'default' | 'warning' | 'success' }) {
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 2, minHeight: 96 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h6" color={tone === 'default' ? 'text.primary' : `${tone}.main`}>{value}</Typography>
    </Paper>
  );
}

function downloadText(filename: string, content: string, type: string) {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function downloadBlob(filename: string, blob: Blob) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export function BusinessDayPage() {
  const roles = useRoles();
  const allowed = canManageBusinessDay(roles);
  const canForce = canForceOrReopen(roles);
  const canReopen = canReopenBusinessDay(roles);
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [storeId, setStoreId] = React.useState('');
  const [reopenOpen, setReopenOpen] = React.useState(false);
  const [reopenReason, setReopenReason] = React.useState('');

  const stores = useQuery({
    queryKey: ['stores', 'business-day'],
    queryFn: async () => listStores(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: allowed
  });

  React.useEffect(() => {
    if (!storeId && stores.data?.content?.[0]) {
      setStoreId(stores.data.content[0].id);
    }
  }, [storeId, stores.data]);

  const current = useQuery({
    queryKey: ['business-day', 'current', storeId],
    queryFn: async () => (await getCurrentBusinessDay(await getValidAccessToken(), storeId)) ?? null,
    enabled: allowed && Boolean(storeId)
  });

  const latest = useQuery({
    queryKey: ['business-day', 'latest', storeId],
    queryFn: async () => (await getLatestBusinessDay(await getValidAccessToken(), storeId)) ?? null,
    enabled: allowed && Boolean(storeId)
  });

  const validation = useQuery({
    queryKey: ['business-day', 'validation', current.data?.id],
    queryFn: async () => getBusinessDayClosingValidation(await getValidAccessToken(), current.data!.id),
    enabled: allowed && Boolean(current.data?.id) && current.data?.status !== 'CLOSED'
  });

  const open = useMutation({
    mutationFn: async () => openBusinessDay(await getValidAccessToken(), { storeId }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['business-day'] });
    }
  });

  const startClosing = useMutation({
    mutationFn: async (day: BusinessDay) => startBusinessDayClosing(await getValidAccessToken(), day.id, idempotencyKey('start-closing')),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['business-day'] });
    }
  });

  const reopen = useMutation({
    mutationFn: async (day: BusinessDay) => reopenBusinessDay(await getValidAccessToken(), day.id, {
      version: day.version,
      reason: reopenReason.trim()
    }, idempotencyKey('reopen')),
    onSuccess: async (day) => {
      setReopenOpen(false);
      setReopenReason('');
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['business-day', 'current', day.storeId] }),
        queryClient.invalidateQueries({ queryKey: ['business-day', 'latest', day.storeId] }),
        queryClient.invalidateQueries({ queryKey: ['register-session'] })
      ]);
    }
  });

  if (!allowed) {
    return <Navigate to="/unauthorized" replace />;
  }

  const day = current.data ?? latest.data;
  const storeRows = stores.data?.content ?? [];

  return (
    <Stack spacing={3} sx={{ maxWidth: 1180 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Stack direction="row" spacing={1.5} alignItems="center" sx={{ flexGrow: 1 }}>
          <StorefrontIcon color="primary" />
          <Typography variant="h5" component="h1">Business day</Typography>
        </Stack>
        <StoreSelect stores={storeRows} value={storeId} onChange={setStoreId} />
        <Tooltip title="Refresh business day">
          <IconButton aria-label="Refresh business day" onClick={() => void current.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Stack>

      {current.isLoading || latest.isLoading || stores.isLoading ? <LoadingPanel label="Loading business-day status" /> : null}
      {current.isError ? <Alert severity="error">{errorMessage(current.error)}</Alert> : null}
      {open.isError ? <Alert severity="error">{errorMessage(open.error)}</Alert> : null}
      {startClosing.isError ? <Alert severity="error">{errorMessage(startClosing.error)}</Alert> : null}
      {reopen.isError ? <Alert severity="error">{errorMessage(reopen.error)}</Alert> : null}

      {!current.isLoading && !latest.isLoading && !day ? (
        <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 3 }}>
          <Stack spacing={2}>
            <Typography variant="h6">No active business day</Typography>
            <Typography color="text.secondary">Open a business day before register sales and closing reconciliation.</Typography>
            <Button variant="contained" startIcon={<LockOpenOutlinedIcon />} disabled={!storeId || open.isPending} onClick={() => open.mutate()}>
              Open business day
            </Button>
          </Stack>
        </Paper>
      ) : null}

      {day ? (
        <>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6} md={3}><Metric label="Business date" value={day.businessDate} /></Grid>
            <Grid item xs={12} sm={6} md={3}><Metric label="Store" value={`${day.storeName} (${day.storeCode})`} /></Grid>
            <Grid item xs={12} sm={6} md={3}><Metric label="Status" value={day.status} tone={day.status === 'CLOSED' ? 'success' : 'default'} /></Grid>
            <Grid item xs={12} sm={6} md={3}><Metric label="Opened by" value={day.openedByName} /></Grid>
          </Grid>

          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 3 }}>
            <Stack spacing={2}>
              <Typography variant="h6">Closing readiness</Typography>
              {day.status === 'CLOSED'
                ? <Typography color="text.secondary">This Store business day is closed. Reopening preserves the existing report and register history.</Typography>
                : validation.isLoading ? <LoadingPanel label="Checking closing blockers" /> : <BlockerList blockers={validation.data?.blockers ?? []} />}
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                {day.status !== 'CLOSED' ? <Button variant="contained" onClick={() => startClosing.mutate(day)} disabled={startClosing.isPending}>Start closing</Button> : null}
                {day.status !== 'CLOSED' ? <Button component={Link} to="/business-day/close" variant="outlined">Close</Button> : null}
                {canForce ? (
                  <Button component={Link} to="/business-day/close?force=true" color="warning" disabled={day.status === 'CLOSED'}>
                    Force close
                  </Button>
                ) : null}
                {day.status === 'CLOSED' && canReopen ? (
                  <Button color="warning" startIcon={<RestartAltIcon />} onClick={() => setReopenOpen(true)}>Reopen business day</Button>
                ) : null}
                <Button component={Link} to="/business-day/history" startIcon={<HistoryOutlinedIcon />}>
                  History
                </Button>
              </Stack>
            </Stack>
          </Paper>
        </>
      ) : null}
      <Dialog open={reopenOpen} onClose={() => setReopenOpen(false)} fullWidth maxWidth="sm" transitionDuration={0}>
        <DialogTitle>Reopen Business Day — {day?.businessDate}</DialogTitle>
        <DialogContent>
          <Typography color="text.secondary" sx={{ mb: 2 }}>This allows new register activity against the same Store Business Day. Closed register sessions remain closed.</Typography>
          <TextField autoFocus fullWidth required multiline minRows={3} label="Reason for reopening" value={reopenReason} onChange={(event) => setReopenReason(event.target.value)} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReopenOpen(false)}>Cancel</Button>
          <Button color="warning" variant="contained" disabled={!day || !reopenReason.trim() || reopen.isPending} onClick={() => day && reopen.mutate(day)}>Reopen</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

export function BusinessDayClosePage() {
  const roles = useRoles();
  const allowed = canManageBusinessDay(roles);
  const canForce = canForceOrReopen(roles);
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [storeId, setStoreId] = React.useState('');
  const [managerNotes, setManagerNotes] = React.useState('');
  const [varianceExplanation, setVarianceExplanation] = React.useState('');
  const [confirmationAccepted, setConfirmationAccepted] = React.useState(false);
  const [forceReason, setForceReason] = React.useState('');
  const forceMode = new URLSearchParams(window.location.search).get('force') === 'true';

  const stores = useQuery({
    queryKey: ['stores', 'business-day-close'],
    queryFn: async () => listStores(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: allowed
  });

  React.useEffect(() => {
    if (!storeId && stores.data?.content?.[0]) {
      setStoreId(stores.data.content[0].id);
    }
  }, [storeId, stores.data]);

  const current = useQuery({
    queryKey: ['business-day', 'current', storeId, 'close'],
    queryFn: async () => getCurrentBusinessDay(await getValidAccessToken(), storeId),
    enabled: allowed && Boolean(storeId)
  });

  const validation = useQuery({
    queryKey: ['business-day', 'validation', current.data?.id, 'close'],
    queryFn: async () => getBusinessDayClosingValidation(await getValidAccessToken(), current.data!.id),
    enabled: allowed && Boolean(current.data?.id)
  });

  const preview = useQuery({
    queryKey: ['business-day', 'preview', current.data?.id, 'close'],
    queryFn: async () => getBusinessDayClosingPreview(await getValidAccessToken(), current.data!.id),
    enabled: allowed && Boolean(current.data?.id)
  });

  const close = useMutation({
    mutationFn: async (day: BusinessDay) => {
      const token = await getValidAccessToken();
      const payload = { version: day.version, managerNotes, varianceExplanation, confirmationAccepted };
      return forceMode
        ? forceCloseBusinessDay(token, day.id, { ...payload, reason: forceReason }, idempotencyKey('force-close'))
        : closeBusinessDay(token, day.id, payload, idempotencyKey('close'));
    },
    onSuccess: async (report) => {
      await queryClient.invalidateQueries({ queryKey: ['business-day'] });
      await queryClient.invalidateQueries({ queryKey: ['end-of-day-reports'] });
      navigate(`/end-of-day-reports/${report.id}`);
    }
  });

  if (!allowed || (forceMode && !canForce)) {
    return <Navigate to="/unauthorized" replace />;
  }

  const day = current.data;
  const blockers = validation.data?.blockers ?? [];
  const varianceExplanationRequired = preview.data?.varianceExplanationRequired ?? false;

  return (
    <Stack spacing={3} sx={{ maxWidth: 1180 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Typography variant="h5" component="h1" sx={{ flexGrow: 1 }}>{forceMode ? 'Force close business day' : 'Close business day'}</Typography>
        <StoreSelect stores={stores.data?.content ?? []} value={storeId} onChange={setStoreId} />
      </Stack>
      {current.isLoading ? <LoadingPanel label="Loading closing workflow" /> : null}
      {current.isError ? <Alert severity="error">{errorMessage(current.error)}</Alert> : null}
      {preview.isError ? <Alert severity="error">{errorMessage(preview.error)}</Alert> : null}
      {close.isError ? <Alert severity="error">{errorMessage(close.error)}</Alert> : null}
      {!day && !current.isLoading ? <Alert severity="info">No active business day is available for this store.</Alert> : null}
      {day ? (
        <>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6} md={3}><Metric label="Business date" value={day.businessDate} /></Grid>
            <Grid item xs={12} sm={6} md={3}><Metric label="Status" value={day.status} /></Grid>
            <Grid item xs={12} sm={6} md={3}><Metric label="Opened by" value={day.openedByName} /></Grid>
            <Grid item xs={12} sm={6} md={3}><Metric label="Started closing" value={day.closingStartedAt ? new Date(day.closingStartedAt).toLocaleString() : 'Not started'} /></Grid>
          </Grid>
          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 3 }}>
            <Stack spacing={2}>
              <Typography variant="h6">Closing blockers and reconciliation</Typography>
              <BlockerList blockers={blockers} />
              {preview.isLoading ? <LoadingPanel label="Calculating closing preview" /> : null}
              {preview.data ? <ClosingPreview preview={preview.data} /> : null}
            </Stack>
          </Paper>
          <Paper elevation={0} component="form" sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 3 }}>
            <Stack spacing={2}>
              {forceMode ? <TextField label="Force-close reason" value={forceReason} onChange={(event) => setForceReason(event.target.value)} required fullWidth multiline minRows={2} /> : null}
              <TextField label="Manager notes" value={managerNotes} onChange={(event) => setManagerNotes(event.target.value)} fullWidth multiline minRows={2} />
              <TextField
                label="Variance explanation"
                value={varianceExplanation}
                onChange={(event) => setVarianceExplanation(event.target.value)}
                required={varianceExplanationRequired}
                helperText={varianceExplanationRequired ? `Required because variance exceeds ${money(preview.data?.cashVarianceExplanationThreshold, preview.data?.currencyCode)}` : undefined}
                fullWidth
                multiline
                minRows={2}
              />
              <FormControlLabel control={<Checkbox checked={confirmationAccepted} onChange={(event) => setConfirmationAccepted(event.target.checked)} />} label={preview.data?.managerSignOffRequired === false ? 'I confirm this end-of-day close.' : 'I confirm this end-of-day report and sign-off electronically.'} />
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                <Button
                  variant="contained"
                  color={forceMode ? 'warning' : 'primary'}
                  disabled={!confirmationAccepted || close.isPending || (!forceMode && blockers.length > 0) || (forceMode && !forceReason.trim()) || (varianceExplanationRequired && !varianceExplanation.trim())}
                  onClick={() => close.mutate(day)}
                >
                  {forceMode ? 'Force close and generate report' : 'Close and generate report'}
                </Button>
                <Button component={Link} to="/business-day">Cancel</Button>
              </Stack>
            </Stack>
          </Paper>
        </>
      ) : null}
    </Stack>
  );
}

export function BusinessDayHistoryPage() {
  const roles = useRoles();
  const allowed = canManageBusinessDay(roles);
  const { getValidAccessToken } = useSession();
  const [storeId, setStoreId] = React.useState('');
  const stores = useQuery({
    queryKey: ['stores', 'business-day-history'],
    queryFn: async () => listStores(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: allowed
  });
  React.useEffect(() => {
    if (!storeId && stores.data?.content?.[0]) setStoreId(stores.data.content[0].id);
  }, [storeId, stores.data]);
  const days = useQuery({
    queryKey: ['business-days', storeId],
    queryFn: async () => listBusinessDays(await getValidAccessToken(), { storeId, size: 50 }),
    enabled: allowed && Boolean(storeId)
  });
  if (!allowed) return <Navigate to="/unauthorized" replace />;
  return (
    <Stack spacing={3} sx={{ maxWidth: 1180 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Typography variant="h5" component="h1" sx={{ flexGrow: 1 }}>Business-day history</Typography>
        <StoreSelect stores={stores.data?.content ?? []} value={storeId} onChange={setStoreId} />
      </Stack>
      {days.isLoading ? <LoadingPanel label="Loading history" /> : null}
      {days.isError ? <Alert severity="error">{errorMessage(days.error)}</Alert> : null}
      <TableContainer component={Paper} variant="outlined">
        <Table aria-label="Business-day history">
          <TableHead><TableRow><TableCell>Date</TableCell><TableCell>Status</TableCell><TableCell>Opened</TableCell><TableCell>Closed</TableCell><TableCell>Closed by</TableCell></TableRow></TableHead>
          <TableBody>
            {(days.data?.content ?? []).map((day) => (
              <TableRow key={day.id}>
                <TableCell>{day.businessDate}</TableCell>
                <TableCell><Chip size="small" color={statusColor(day.status)} label={day.status} /></TableCell>
                <TableCell>{new Date(day.openedAt).toLocaleString()}</TableCell>
                <TableCell>{day.closedAt ? new Date(day.closedAt).toLocaleString() : ''}</TableCell>
                <TableCell>{day.closedByName ?? ''}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Stack>
  );
}

function ClosingPreview({ preview }: { preview: EndOfDayClosingPreview }) {
  const statusRows = [
    ['Register reconciliation', `${preview.registers.length} register session${preview.registers.length === 1 ? '' : 's'}`],
    ['Sales summary', `${preview.transactionCount} posted transaction${preview.transactionCount === 1 ? '' : 's'}`],
    ['Payment summary', `${preview.payments.length} payment method${preview.payments.length === 1 ? '' : 's'}`],
    ['Tax summary', `${preview.taxes.length} tax component${preview.taxes.length === 1 ? '' : 's'}`],
    ['Lottery summary', preview.lottery?.enabled ? 'Enabled' : 'Disabled'],
    ['Inventory summary', `${preview.inventory?.negativeStockProducts ?? 0} negative-stock product${preview.inventory?.negativeStockProducts === 1 ? '' : 's'}`],
    ['Cashier summary', `${preview.cashiers.length} cashier${preview.cashiers.length === 1 ? '' : 's'}`],
    ['Exceptions', `${preview.exceptions.length} exception group${preview.exceptions.length === 1 ? '' : 's'}`]
  ];
  return (
    <Stack spacing={2}>
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}><Metric label="Gross sales" value={money(preview.grossSales, preview.currencyCode)} /></Grid>
        <Grid item xs={12} sm={6} md={3}><Metric label="Net sales" value={money(preview.netSales, preview.currencyCode)} /></Grid>
        <Grid item xs={12} sm={6} md={3}><Metric label="Payments net" value={money(preview.payments.reduce((total, row) => total + row.net, 0), preview.currencyCode)} /></Grid>
        <Grid item xs={12} sm={6} md={3}><Metric label="Cash variance" value={money(preview.cashVariance, preview.currencyCode)} tone={preview.cashVariance === 0 ? 'success' : 'warning'} /></Grid>
      </Grid>
      <TableContainer>
        <Table size="small" aria-label="Closing preview sections">
          <TableHead><TableRow><TableCell>Section</TableCell><TableCell>Preview</TableCell></TableRow></TableHead>
          <TableBody>
            {statusRows.map((row) => <TableRow key={row[0]}><TableCell>{row[0]}</TableCell><TableCell>{row[1]}</TableCell></TableRow>)}
          </TableBody>
        </Table>
      </TableContainer>
      <ReportTable title="Register reconciliation preview" rows={<SimpleTable headers={['Register', 'Expected', 'Counted', 'Variance']} rows={preview.registers.map((row) => [row.registerCode, money(row.expectedCash, preview.currencyCode), money(row.countedCash, preview.currencyCode), money(row.variance, preview.currencyCode)])} />} />
      <ReportTable title="Payment preview" rows={<SimpleTable headers={['Method', 'Collected', 'Refunded', 'Net']} rows={preview.payments.map((row) => [row.paymentMethod, money(row.collected, preview.currencyCode), money(row.refunded, preview.currencyCode), money(row.net, preview.currencyCode)])} />} />
      <ReportTable title="Tax preview" rows={<SimpleTable headers={['Component', 'Taxable', 'Collected', 'Refunded', 'Net']} rows={preview.taxes.map((row) => [row.componentCode, money(row.taxableSales, preview.currencyCode), money(row.taxCollected, preview.currencyCode), money(row.taxRefunded, preview.currencyCode), money(row.netTaxCollected, preview.currencyCode)])} />} />
      <ReportTable title="Cashier preview" rows={<SimpleTable headers={['Cashier', 'Transactions', 'Net sales', 'Cash handled']} rows={preview.cashiers.map((row) => [row.cashierName, String(row.transactionCount), money(row.netSales, preview.currencyCode), money(row.cashHandled, preview.currencyCode)])} />} />
      <ReportTable title="Exception preview" rows={<SimpleTable headers={['Type', 'Count', 'Amount']} rows={preview.exceptions.map((row) => [row.exceptionType, String(row.count), money(row.totalAmount, preview.currencyCode)])} />} />
    </Stack>
  );
}

export function EndOfDayReportsPage() {
  const roles = useRoles();
  const allowed = canManageBusinessDay(roles);
  const { getValidAccessToken } = useSession();
  const [filters, setFilters] = React.useState({ reportNumber: '', status: '' as BusinessDayStatus | '' });
  const reports = useQuery({
    queryKey: ['end-of-day-reports', filters],
    queryFn: async () => listEndOfDayReports(await getValidAccessToken(), { reportNumber: filters.reportNumber || undefined, status: filters.status, size: 50 }),
    enabled: allowed
  });
  if (!allowed) return <Navigate to="/unauthorized" replace />;
  return (
    <Stack spacing={3} sx={{ maxWidth: 1180 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Stack direction="row" spacing={1.5} alignItems="center" sx={{ flexGrow: 1 }}>
          <AssessmentOutlinedIcon color="primary" />
          <Typography variant="h5" component="h1">End-of-day reports</Typography>
        </Stack>
        <Tooltip title="Refresh reports"><IconButton aria-label="Refresh reports" onClick={() => void reports.refetch()}><RefreshIcon /></IconButton></Tooltip>
      </Stack>
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 2 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField label="Report number" value={filters.reportNumber} onChange={(event) => setFilters((current) => ({ ...current, reportNumber: event.target.value }))} />
          <TextField select label="Status" value={filters.status} onChange={(event) => setFilters((current) => ({ ...current, status: event.target.value as BusinessDayStatus | '' }))} sx={{ minWidth: 180 }}>
            <MenuItem value="">Any status</MenuItem>
            {(['OPEN', 'CLOSING', 'CLOSED', 'REOPENED'] as BusinessDayStatus[]).map((status) => <MenuItem key={status} value={status}>{status}</MenuItem>)}
          </TextField>
        </Stack>
      </Paper>
      {reports.isLoading ? <LoadingPanel label="Loading end-of-day reports" /> : null}
      {reports.isError ? <Alert severity="error">{errorMessage(reports.error)}</Alert> : null}
      <TableContainer component={Paper} variant="outlined">
        <Table aria-label="End-of-day report history">
          <TableHead><TableRow><TableCell>Report</TableCell><TableCell>Store</TableCell><TableCell>Date</TableCell><TableCell align="right">Net sales</TableCell><TableCell align="right">Variance</TableCell><TableCell /></TableRow></TableHead>
          <TableBody>
            {(reports.data?.content ?? []).map((report) => (
              <TableRow key={report.id}>
                <TableCell>{report.reportNumber}</TableCell>
                <TableCell>{report.storeName}</TableCell>
                <TableCell>{report.businessDate}</TableCell>
                <TableCell align="right">{money(report.netSales, report.currencyCode)}</TableCell>
                <TableCell align="right">{money(report.cashVariance, report.currencyCode)}</TableCell>
                <TableCell align="right"><Button component={Link} to={`/end-of-day-reports/${report.id}`}>View</Button></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Stack>
  );
}

function ReportTable({ title, rows }: { title: string; rows: React.ReactNode }) {
  return (
    <Accordion defaultExpanded>
      <AccordionSummary expandIcon={<ExpandMoreIcon />}><Typography fontWeight={700}>{title}</Typography></AccordionSummary>
      <AccordionDetails>{rows}</AccordionDetails>
    </Accordion>
  );
}

export function EndOfDayReportDetailPage() {
  const roles = useRoles();
  const allowed = canManageBusinessDay(roles);
  const canReopen = canReopenBusinessDay(roles);
  const { id } = useParams();
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const [reopenReason, setReopenReason] = React.useState('');
  const report = useQuery({
    queryKey: ['end-of-day-report', id],
    queryFn: async () => getEndOfDayReport(await getValidAccessToken(), id!),
    enabled: allowed && Boolean(id)
  });
  const reopen = useMutation({
    mutationFn: async (data: EndOfDayReport) => reopenBusinessDay(await getValidAccessToken(), data.businessDayId, { version: data.businessDayVersion, reason: reopenReason }, idempotencyKey('reopen')),
    onSuccess: async (reopened) => {
      await Promise.all([
        report.refetch(),
        queryClient.invalidateQueries({ queryKey: ['business-day', 'current', reopened.storeId] }),
        queryClient.invalidateQueries({ queryKey: ['business-day', 'validation', reopened.id] }),
        queryClient.invalidateQueries({ queryKey: ['register-session'] })
      ]);
    }
  });
  const print = useMutation({
    mutationFn: async (data: EndOfDayReport) => getEndOfDayReportPrintHtml(await getValidAccessToken(), data.id),
    onSuccess: (html) => {
      const win = window.open('', '_blank');
      if (win) {
        win.document.write(html);
        win.document.close();
        win.focus();
        win.print();
      }
    }
  });
  const csv = useMutation({
    mutationFn: async (data: EndOfDayReport) => exportEndOfDayReportCsv(await getValidAccessToken(), data.id),
    onSuccess: (content, data) => downloadText(`${data.reportNumber}.csv`, content, 'text/csv;charset=utf-8')
  });
  const pdf = useMutation({
    mutationFn: async (data: EndOfDayReport) => exportEndOfDayReportPdf(await getValidAccessToken(), data.id),
    onSuccess: (blob, data) => downloadBlob(`${data.reportNumber}.pdf`, blob)
  });
  if (!allowed) return <Navigate to="/unauthorized" replace />;
  if (report.isLoading) return <LoadingPanel label="Loading report" />;
  if (report.isError) return <Alert severity="error">{errorMessage(report.error)}</Alert>;
  const data = report.data;
  if (!data) return <Alert severity="info">Report not found.</Alert>;
  return (
    <Stack spacing={3} sx={{ maxWidth: 1180 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">Merchtyl End-of-Day Report</Typography>
          <Typography color="text.secondary">{data.reportNumber} - {data.storeName} - {data.businessDate}</Typography>
        </Box>
        <Button startIcon={<PrintIcon />} onClick={() => print.mutate(data)}>Print</Button>
        <Button startIcon={<DownloadIcon />} onClick={() => csv.mutate(data)}>CSV</Button>
        <Button startIcon={<DownloadIcon />} onClick={() => pdf.mutate(data)}>PDF</Button>
      </Stack>
      {reopen.isError ? <Alert severity="error">{errorMessage(reopen.error)}</Alert> : null}
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}><Metric label="Gross sales" value={money(data.grossSales, data.currencyCode)} /></Grid>
        <Grid item xs={12} sm={6} md={3}><Metric label="Net sales" value={money(data.netSales, data.currencyCode)} /></Grid>
        <Grid item xs={12} sm={6} md={3}><Metric label="Tax" value={money(data.taxTotal, data.currencyCode)} /></Grid>
        <Grid item xs={12} sm={6} md={3}><Metric label="Cash variance" value={money(data.cashVariance, data.currencyCode)} tone={data.cashVariance === 0 ? 'success' : 'warning'} /></Grid>
      </Grid>
      <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 2 }}>
        <Typography variant="subtitle1" fontWeight={700}>Manager sign-off</Typography>
        <Divider sx={{ my: 1.5 }} />
        <Typography>{data.signOff?.managerName ?? 'Unsigned'}</Typography>
        <Typography color="text.secondary">{data.signOff?.signedAt ? new Date(data.signOff.signedAt).toLocaleString() : ''}</Typography>
        {data.signOff?.varianceExplanation ? <Typography sx={{ mt: 1 }}>Variance: {data.signOff.varianceExplanation}</Typography> : null}
      </Paper>
      <ReportTable title="Payments" rows={<SimpleTable headers={['Method', 'Collected', 'Refunded', 'Net']} rows={data.payments.map((row) => [row.paymentMethod, money(row.collected, data.currencyCode), money(row.refunded, data.currencyCode), money(row.net, data.currencyCode)])} />} />
      <ReportTable title="Registers" rows={<SimpleTable headers={['Register', 'Expected', 'Counted', 'Variance', 'Force close']} rows={data.registers.map((row) => [row.registerCode, money(row.expectedCash, data.currencyCode), money(row.countedCash, data.currencyCode), money(row.variance, data.currencyCode), row.forceClosed ? 'Yes' : 'No'])} />} />
      <ReportTable title="Taxes" rows={<SimpleTable headers={['Component', 'Taxable', 'Collected', 'Refunded', 'Net']} rows={data.taxes.map((row) => [row.componentCode, money(row.taxableSales, data.currencyCode), money(row.taxCollected, data.currencyCode), money(row.taxRefunded, data.currencyCode), money(row.netTaxCollected, data.currencyCode)])} />} />
      <ReportTable title="Cashiers" rows={<SimpleTable headers={['Cashier', 'Transactions', 'Net sales', 'Refunds', 'Cash handled']} rows={data.cashiers.map((row) => [row.cashierName, String(row.transactionCount), money(row.netSales, data.currencyCode), money(row.refundTotal, data.currencyCode), money(row.cashHandled, data.currencyCode)])} />} />
      <ReportTable title="Exceptions" rows={<SimpleTable headers={['Type', 'Count', 'Amount', 'Details']} rows={data.exceptions.map((row) => [row.exceptionType, String(row.count), money(row.totalAmount, data.currencyCode), row.details ?? ''])} />} />
      {canReopen ? (
        <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 2 }}>
          <Stack spacing={2}>
            <Typography variant="subtitle1" fontWeight={700}>Reopen business day</Typography>
            <TextField label="Reopen reason" value={reopenReason} onChange={(event) => setReopenReason(event.target.value)} multiline minRows={2} />
            <Button color="warning" startIcon={<RestartAltIcon />} disabled={!reopenReason.trim() || reopen.isPending} onClick={() => reopen.mutate(data)} sx={{ alignSelf: 'flex-start' }}>
              Reopen
            </Button>
          </Stack>
        </Paper>
      ) : null}
    </Stack>
  );
}

function SimpleTable({ headers, rows }: { headers: string[]; rows: string[][] }) {
  return (
    <TableContainer>
      <Table size="small">
        <TableHead><TableRow>{headers.map((header) => <TableCell key={header}>{header}</TableCell>)}</TableRow></TableHead>
        <TableBody>
          {rows.map((row, index) => <TableRow key={index}>{row.map((cell, cellIndex) => <TableCell key={cellIndex}>{cell}</TableCell>)}</TableRow>)}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
