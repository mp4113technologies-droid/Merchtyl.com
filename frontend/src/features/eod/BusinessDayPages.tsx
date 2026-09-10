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
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  ApiClientError,
  cancelSale,
  forceCloseDraftSale,
  getSale,
  closeBusinessDay,
  exportEndOfDayReportCsv,
  exportEndOfDayReportPdf,
  forceCloseBusinessDay,
  getBusinessDayOperationalState,
  getBusinessDayClosingPreview,
  getBusinessDayClosingValidation,
  getCurrentBusinessDay,
  getEndOfDayReport,
  getEndOfDayReportPrintHtml,
  listBusinessDays,
  listEndOfDayReports,
  listStores,
  openBusinessDay,
  reopenBusinessDay,
  startBusinessDayClosing
} from '../../api/client';
import type { BusinessDay, BusinessDayStatus, ClosingBlocker, ClosingValidation, EndOfDayClosingPreview, EndOfDayReport, RegisterReconciliation, Store, UserRole } from '../../api/types';
import { useSession } from '../../app/session';
import { RegisterReconciliationDialog } from '../registersessions/RegisterReconciliation';
import { resolveBusinessDayAccess } from './businessDayAccess';

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

function useBusinessDayAccess() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  const access = resolveBusinessDayAccess(roles, currentUser?.permissions);
  return {
    roles,
    ...access
  };
}

function RegisterReconciliationPanel({ validation, currencyCode, onCompleted, onCancelDraft, cancellingDraft }: {
  validation?: ClosingValidation; currencyCode?: string; onCompleted: () => void | Promise<void>;
  onCancelDraft?: (saleId: string) => void; cancellingDraft?: boolean;
}) {
  const { roles } = useBusinessDayAccess();
  const [selected, setSelected] = React.useState<RegisterReconciliation | null>(null);
  const [forceCloseSaleId, setForceCloseSaleId] = React.useState<string | null>(null);
  const sessions = validation?.registerSessions ?? [];
  const required = sessions.filter((session) => session.reconciliationRequired);
  const registerCodes = new Set(['OPEN_REGISTER_SESSION', 'MISSING_COUNTED_CASH', 'MISSING_RECONCILIATION']);
  const otherBlockers = (validation?.blockers ?? []).filter((blocker) => !registerCodes.has(blocker.code));
  return <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 3 }}>
    <Stack spacing={2}>
      <Typography variant="h6">Register Reconciliation</Typography>
      {sessions.length === 0 ? <Typography color="text.secondary">No register sessions are associated with this Business Day.</Typography> : null}
      {sessions.length > 0 && required.length === 0 ? <Alert severity="success">All register sessions reconciled.</Alert> : null}
      {otherBlockers.length > 0 ? <Box><Typography fontWeight={700} sx={{ mb: 1 }}>Business Day cannot close yet</Typography><BlockerList blockers={otherBlockers} onCancelDraft={onCancelDraft} cancellingDraft={cancellingDraft} onForceCloseDraft={canForceOrReopen(roles) ? setForceCloseSaleId : undefined} /></Box> : null}
      {required.length > 0 ? <Alert severity="warning">{required.length} register{required.length === 1 ? '' : 's'} require{required.length === 1 ? 's' : ''} reconciliation before this Business Day can be closed.</Alert> : null}
      {sessions.map((session) => <Paper key={session.registerSessionId} variant="outlined" sx={{ p: 2 }}><Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'center' }}>
        <Box sx={{ flexGrow: 1 }}><Typography fontWeight={700}>{session.registerName} ({session.registerCode})</Typography><Typography variant="body2" color="text.secondary">{session.registerType.replace('_', ' ')} · {session.sessionStatus}</Typography>
          <Typography variant="body2">Opened by {session.openedByName ?? 'Operator not recorded'} · {new Date(session.openedAt).toLocaleString()}</Typography>
          <Typography variant="body2">Opening cash: {money(session.openingCash, currencyCode)} · Expected cash: {money(session.expectedCash, currencyCode)}</Typography>
        </Box>
        {session.reconciliationComplete ? <Chip color="success" label="Reconciled" /> : <Chip color="warning" label="Reconciliation Required" />}
        {session.reconciliationRequired && session.canReconcile ? <Button variant="contained" onClick={() => setSelected(session)}>Complete Reconciliation</Button> : null}
        {session.reconciliationRequired && !session.canReconcile ? <Typography variant="body2" color="text.secondary">You do not have permission to reconcile this session.</Typography> : null}
      </Stack></Paper>)}
    </Stack>
    <RegisterReconciliationDialog open={Boolean(selected)} registerName={selected ? `${selected.registerName} (${selected.registerCode})` : ''} currencyCode={currencyCode}
      session={selected ? { id: selected.registerSessionId, status: selected.sessionStatus, version: selected.version, openingCash: selected.openingCash, expectedCash: selected.expectedCash, countedCash: selected.countedCash, differenceCash: selected.variance, reconciliation: selected.reconciliation } : null}
      onClose={() => setSelected(null)} onCompleted={onCompleted} />
    <ForceCloseDraftDialog saleId={forceCloseSaleId} currencyCode={currencyCode} onClose={() => setForceCloseSaleId(null)} onCompleted={onCompleted} />
  </Paper>;
}

function ForceCloseDraftDialog({ saleId, currencyCode, onClose, onCompleted }: { saleId: string | null; currencyCode?: string; onClose: () => void; onCompleted: () => void | Promise<void> }) {
  const { getValidAccessToken } = useSession();
  const [reasonCode, setReasonCode] = React.useState('ABANDONED_TRANSACTION');
  const [note, setNote] = React.useState('');
  const sale = useQuery({ queryKey: ['sale', saleId], queryFn: async () => getSale(await getValidAccessToken(), saleId!), enabled: Boolean(saleId) });
  const forceClose = useMutation({ mutationFn: async () => forceCloseDraftSale(await getValidAccessToken(), saleId!, { version: sale.data!.version, reasonCode, note: note.trim() || undefined }), onSuccess: async () => { await onCompleted(); onClose(); } });
  const invalid = !reasonCode || (reasonCode === 'OTHER' && !note.trim());
  return <Dialog open={Boolean(saleId)} onClose={onClose} fullWidth maxWidth="sm"><DialogTitle>Force Close Draft Sale?</DialogTitle><DialogContent><Stack spacing={2} sx={{ mt: 1 }}>
    {sale.data ? <><Typography>Sale: {sale.data.id.slice(0, 8).toUpperCase()}</Typography><Typography>Sale Total: {money(sale.data.totalAmount, currencyCode ?? sale.data.currencyCode)}</Typography><Typography>Recorded Payments: {money(sale.data.paidAmount, currencyCode ?? sale.data.currencyCode)}</Typography><Typography>Remaining Balance: {money(sale.data.balanceDue, currencyCode ?? sale.data.currencyCode)}</Typography></> : <CircularProgress size={24} />}
    <Alert severity="warning">Recorded payment history will be preserved. No refund, void, or Register reconciliation change will occur.</Alert>
    <TextField select label="Reason" value={reasonCode} onChange={(event) => setReasonCode(event.target.value)} required>{['ABANDONED_TRANSACTION','DUPLICATE_DRAFT','CHECKOUT_INTERRUPTED','INCORRECT_DRAFT','PAYMENT_HANDLED_EXTERNALLY','OTHER'].map((value) => <MenuItem key={value} value={value}>{value.replaceAll('_', ' ')}</MenuItem>)}</TextField>
    <TextField label="Note" value={note} onChange={(event) => setNote(event.target.value)} required={reasonCode === 'OTHER'} multiline minRows={2} />
    {forceClose.isError ? <Alert severity="error">{errorMessage(forceClose.error)}</Alert> : null}
  </Stack></DialogContent><DialogActions><Button onClick={onClose}>Cancel</Button><Button color="error" variant="contained" disabled={!sale.data || invalid || forceClose.isPending} onClick={() => forceClose.mutate()}>Force Close Draft</Button></DialogActions></Dialog>;
}

function errorMessage(error: unknown) {
  if (error instanceof ApiClientError) {
    if (error.code === 'BUSINESS_DAY_HAS_OPEN_REGISTER_SESSIONS') return 'Close all open registers before closing the business day.';
    if (error.code === 'BUSINESS_DAY_STATE_CHANGED' || error.code === 'RECORD_UPDATED_BY_ANOTHER_USER') return 'The business day changed. Refresh and try again.';
    if (error.code === 'VARIANCE_EXPLANATION_REQUIRED') return 'Please explain the cash variance before closing.';
    if (error.code === 'BUSINESS_DAY_RECONCILIATION_INCOMPLETE') return 'Complete register reconciliation before closing the business day.';
    if (error.code === 'SALE_HAS_RECORDED_PAYMENTS') return 'This draft has recorded payments and cannot be cancelled. Open the sale to review its payments.';
  }
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

function BlockerList({ blockers, onCancelDraft, cancellingDraft, onForceCloseDraft }: { blockers: ClosingBlocker[]; onCancelDraft?: (saleId: string) => void; cancellingDraft?: boolean; onForceCloseDraft?: (saleId: string) => void }) {
  if (blockers.length === 0) {
    return <Alert severity="success">No closing blockers detected.</Alert>;
  }
  return (
    <Alert severity="warning">
      <Stack component="ul" sx={{ m: 0, pl: 2 }}>
        {blockers.map((blocker) => <Box component="li" key={`${blocker.code}-${blocker.relatedId ?? blocker.message}`} sx={{ mb: 0.5 }}>
              <Typography>{blocker.message}</Typography>
          {blocker.code === 'UNFINALIZED_DRAFT_SALE' && blocker.relatedId && onCancelDraft ? <Button size="small" color="warning" disabled={cancellingDraft} onClick={() => onCancelDraft(blocker.relatedId!)}>Cancel Draft Sale</Button> : null}
          {blocker.code === 'UNFINALIZED_PAID_DRAFT_SALE' && blocker.relatedId ? <Button size="small" component={Link} to={`/pos?saleId=${blocker.relatedId}`}>Review Paid Draft Sale</Button> : null}
          {blocker.code === 'UNFINALIZED_PAID_DRAFT_SALE' && blocker.relatedId && onForceCloseDraft ? <Button size="small" color="error" onClick={() => onForceCloseDraft(blocker.relatedId!)}>Force Close Draft</Button> : null}
          {blocker.code === 'UNFINALIZED_HELD_SALE' ? <Button size="small" component={Link} to="/pos/held-sales">Review Held Sales</Button> : null}
        </Box>)}
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
  const { roles, canView: allowed, canOpen, canClose, canReopen } = useBusinessDayAccess();
  const canForce = canForceOrReopen(roles);
  const { getValidAccessToken, currentUser } = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [storeId, setStoreId] = React.useState('');
  const [reopenOpen, setReopenOpen] = React.useState(false);
  const [reopenReason, setReopenReason] = React.useState('');
  const [closePreviousOpen, setClosePreviousOpen] = React.useState(false);

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

  const operationalState = useQuery({
    queryKey: ['business-day', 'operational-state', storeId],
    queryFn: async () => getBusinessDayOperationalState(await getValidAccessToken(), storeId),
    enabled: allowed && Boolean(storeId)
  });

  const reconciliationDay = operationalState.data?.currentBusinessDay
    ?? (operationalState.data?.state === 'PREVIOUS_DAY_STILL_OPEN' ? operationalState.data?.previousBusinessDay : null);

  const validation = useQuery({
    queryKey: ['business-day', 'validation', reconciliationDay?.id],
    queryFn: async () => getBusinessDayClosingValidation(await getValidAccessToken(), reconciliationDay!.id),
    enabled: allowed && Boolean(reconciliationDay?.id) && reconciliationDay?.status !== 'CLOSED'
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
        queryClient.invalidateQueries({ queryKey: ['business-day', 'operational-state', day.storeId] }),
        queryClient.invalidateQueries({ queryKey: ['register-session'] })
      ]);
    }
  });

  const closePrevious = useMutation({
    mutationFn: async (previousDay: BusinessDay) => closeBusinessDay(await getValidAccessToken(), previousDay.id, {
      version: previousDay.version,
      managerNotes: 'Closed from previous business day recovery action.',
      varianceExplanation: '',
      confirmationAccepted: true
    }, idempotencyKey('close-previous')),
    onSuccess: async () => {
      setClosePreviousOpen(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['business-day'] }),
        queryClient.invalidateQueries({ queryKey: ['register-session'] }),
        queryClient.invalidateQueries({ queryKey: ['end-of-day'] })
      ]);
    }
  });

  const cancelDraft = useMutation({
    mutationFn: async (saleId: string) => cancelSale(await getValidAccessToken(), saleId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['business-day'] }),
        queryClient.invalidateQueries({ queryKey: ['sales'] })
      ]);
    }
  });

  if (!allowed) {
    return <Navigate to="/unauthorized" replace />;
  }

  const day = operationalState.data?.currentBusinessDay ?? null;
  const previousDay = operationalState.data?.previousBusinessDay ?? null;
  const storeRows = stores.data?.content ?? [];
  const selectedStore = storeRows.find((store) => store.id === storeId);
  const refreshAfterReconciliation = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['business-day'] }),
      queryClient.invalidateQueries({ queryKey: ['register-session'] }),
      queryClient.invalidateQueries({ queryKey: ['register-sessions'] })
    ]);
  };

  return (
    <Stack spacing={3} sx={{ maxWidth: 1180 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Stack direction="row" spacing={1.5} alignItems="center" sx={{ flexGrow: 1 }}>
          <StorefrontIcon color="primary" />
          <Typography variant="h5" component="h1">Business day</Typography>
        </Stack>
        <StoreSelect stores={storeRows} value={storeId} onChange={setStoreId} />
        <Tooltip title="Refresh business day">
          <IconButton aria-label="Refresh business day" onClick={() => void operationalState.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Stack>

      {operationalState.isLoading || stores.isLoading ? <LoadingPanel label="Loading business-day status" /> : null}
      {operationalState.isError ? <Alert severity="error">{errorMessage(operationalState.error)}</Alert> : null}
      {open.isError ? <Alert severity="error">{errorMessage(open.error)}</Alert> : null}
      {startClosing.isError ? <Alert severity="error">{errorMessage(startClosing.error)}</Alert> : null}
      {reopen.isError ? <Alert severity="error">{errorMessage(reopen.error)}</Alert> : null}
      {closePrevious.isError ? <Alert severity="error">{errorMessage(closePrevious.error)}</Alert> : null}
      {cancelDraft.isError ? <Alert severity="error">{errorMessage(cancelDraft.error)}</Alert> : null}

      {!operationalState.isLoading && (operationalState.data?.state === 'NO_BUSINESS_DAY_TODAY' || operationalState.data?.state === 'HISTORICAL_CLOSED') ? (
        <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 3 }}>
          <Stack spacing={2}>
            <Typography variant="h6">Business date {operationalState.data.currentBusinessDate}</Typography>
            <Typography color="text.secondary">Business Day not started. Start it before opening an assigned register.</Typography>
            {canOpen ? (
              <Button variant="contained" startIcon={<LockOpenOutlinedIcon />} disabled={!storeId || open.isPending} onClick={() => open.mutate()}>
                {open.isPending ? 'Starting…' : 'Start Business Day'}
              </Button>
            ) : null}
            {previousDay ? <Typography variant="body2" color="text.secondary">Previous business day: {previousDay.businessDate} — {previousDay.status}</Typography> : null}
          </Stack>
        </Paper>
      ) : null}

      {!operationalState.isLoading && operationalState.data?.state === 'PREVIOUS_DAY_STILL_OPEN' ? (
        <Alert severity="warning">
          <Stack spacing={1.5}>
            <Typography fontWeight={700}>Previous Business Day Still Open</Typography>
            <Typography>
              {previousDay?.businessDate} must be closed before opening {operationalState.data.currentBusinessDate}.
            </Typography>
            {previousDay && canClose ? (
              <Button color="warning" variant="contained" sx={{ alignSelf: 'flex-start' }}
                disabled={closePrevious.isPending || validation.data?.closable === false || (Boolean(reconciliationDay) && !validation.data && !validation.isError)} onClick={() => setClosePreviousOpen(true)}>
                Close Previous Business Day
              </Button>
            ) : <Typography variant="body2">The previous business day is still open. Ask a Manager or Owner to close it before starting today's business day.</Typography>}
          </Stack>
        </Alert>
      ) : null}

      {reconciliationDay && reconciliationDay.status !== 'CLOSED' ? <RegisterReconciliationPanel validation={validation.data} currencyCode={selectedStore?.currencyCode} onCompleted={refreshAfterReconciliation}
        cancellingDraft={cancelDraft.isPending}
        onCancelDraft={currentUser?.permissions?.includes('SALE_CREATE') ? (saleId) => window.confirm('Cancel this draft sale? This cannot be undone.') && cancelDraft.mutate(saleId) : undefined} /> : null}

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
                ? <Typography color="text.secondary">{canReopen ? 'This Store business day is closed. Reopening preserves the existing report and register history.' : "Today's business day has been closed. Ask a Manager or Owner to reopen it."}</Typography>
                : !canClose
                  ? <Typography color="text.secondary">Business Day is open. Continue to your assigned register.</Typography>
                  : validation.isLoading ? <LoadingPanel label="Checking closing blockers" /> : <BlockerList blockers={validation.data?.blockers ?? []} />}
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                {day.status !== 'CLOSED' && canClose ? <Button variant="contained" onClick={() => startClosing.mutate(day)} disabled={startClosing.isPending}>Start closing</Button> : null}
                {day.status !== 'CLOSED' && canClose ? <Button component={Link} to={`/business-day/close?storeId=${day.storeId}`} variant="outlined" disabled={!validation.data?.closable}>Close Business Day</Button> : null}
                {canForce ? (
                  <Button component={Link} to="/business-day/close?force=true" color="warning" disabled={day.status === 'CLOSED'}>
                    Force close
                  </Button>
                ) : null}
                {operationalState.data?.state === 'CLOSED_TODAY' && day.status === 'CLOSED' && canReopen ? (
                  <Button color="warning" startIcon={<RestartAltIcon />} onClick={() => setReopenOpen(true)}>Reopen Business Day</Button>
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
      <Dialog open={closePreviousOpen} onClose={() => setClosePreviousOpen(false)} fullWidth maxWidth="sm" transitionDuration={0}>
        <DialogTitle>Close Previous Business Day?</DialogTitle>
        <DialogContent>
          <Typography>
            Close {previousDay?.businessDate} for {previousDay?.storeName}? Any open register sessions must be closed first.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setClosePreviousOpen(false)}>Cancel</Button>
          <Button color="warning" variant="contained" disabled={!previousDay || closePrevious.isPending}
            onClick={() => previousDay && closePrevious.mutate(previousDay)}>
            {closePrevious.isPending ? 'Closing…' : 'Close Previous Business Day'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

export function BusinessDayClosePage() {
  const { roles, canClose: allowed } = useBusinessDayAccess();
  const canForce = canForceOrReopen(roles);
  const [searchParams] = useSearchParams();
  const { getValidAccessToken, currentUser } = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [storeId, setStoreId] = React.useState(searchParams.get('storeId') ?? '');
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
      const canViewReport = currentUser?.permissions?.includes('END_OF_DAY_REPORT_VIEW')
        ?? canManageBusinessDay(roles);
      navigate(canViewReport ? `/end-of-day-reports/${report.id}` : '/business-day');
    }
  });

  const cancelDraft = useMutation({
    mutationFn: async (saleId: string) => cancelSale(await getValidAccessToken(), saleId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['business-day'] }),
        queryClient.invalidateQueries({ queryKey: ['sales'] })
      ]);
    }
  });

  if (!allowed || (forceMode && !canForce)) {
    return <Navigate to="/unauthorized" replace />;
  }

  const day = current.data;
  const blockers = validation.data?.blockers ?? [];
  const varianceExplanationRequired = preview.data?.varianceExplanationRequired ?? false;
  const selectedStore = stores.data?.content.find((store) => store.id === storeId);
  const refreshAfterReconciliation = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['business-day'] }),
      queryClient.invalidateQueries({ queryKey: ['register-session'] }),
      queryClient.invalidateQueries({ queryKey: ['register-sessions'] })
    ]);
  };

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
      {cancelDraft.isError ? <Alert severity="error">{errorMessage(cancelDraft.error)}</Alert> : null}
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
          <RegisterReconciliationPanel validation={validation.data} currencyCode={selectedStore?.currencyCode} onCompleted={refreshAfterReconciliation}
            cancellingDraft={cancelDraft.isPending}
            onCancelDraft={currentUser?.permissions?.includes('SALE_CREATE') ? (saleId) => window.confirm('Cancel this draft sale? This cannot be undone.') && cancelDraft.mutate(saleId) : undefined} />
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
