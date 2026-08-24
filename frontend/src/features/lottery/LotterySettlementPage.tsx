import ApprovalOutlinedIcon from '@mui/icons-material/ApprovalOutlined';
import CalculateOutlinedIcon from '@mui/icons-material/CalculateOutlined';
import LockOpenOutlinedIcon from '@mui/icons-material/LockOpenOutlined';
import PublishOutlinedIcon from '@mui/icons-material/PublishOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
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
import { Controller, useForm } from 'react-hook-form';
import { Navigate } from 'react-router-dom';
import { z } from 'zod';
import {
  approveLotterySettlement,
  calculateLotterySettlement,
  getFeatureResolution,
  listLotteryOperators,
  listLotterySettlements,
  listStores,
  postLotterySettlement,
  reopenLotterySettlement,
  type LotterySettlementCalculationPayload,
  type LotterySettlementLifecyclePayload,
  type LotterySettlementSearchParams
} from '../../api/client';
import type {
  LotteryOperator,
  LotterySettlement,
  LotterySettlementStatus,
  Store,
  UserRole
} from '../../api/types';
import { useSession } from '../../app/session';

const settlementStatuses: LotterySettlementStatus[] = [
  'DRAFT',
  'CALCULATED',
  'UNDER_REVIEW',
  'APPROVED',
  'POSTED',
  'REOPENED'
];

const today = new Date().toISOString().slice(0, 10);

const calculationSchema = z.object({
  operatorId: z.string().trim().min(1, 'Operator is required'),
  storeId: z.string().trim().min(1, 'Store is required'),
  periodStart: z.string().trim().min(1, 'Period start is required'),
  periodEnd: z.string().trim().min(1, 'Period end is required')
}).superRefine((values, context) => {
  if (values.periodEnd < values.periodStart) {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ['periodEnd'], message: 'Period end must be on or after period start' });
  }
});

type CalculationFormValues = z.infer<typeof calculationSchema>;

type LifecycleAction = 'approve' | 'reopen' | 'post';

type LifecycleDialogState = {
  action: LifecycleAction;
  settlement: LotterySettlement;
} | null;

const emptyCalculation: CalculationFormValues = {
  operatorId: '',
  storeId: '',
  periodStart: today,
  periodEnd: today
};

function canViewLotterySettlements(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function canPostLotterySettlements(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER');
}

function useSettlementPermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  return {
    canView: canViewLotterySettlements(roles),
    canManage: canViewLotterySettlements(roles),
    canPost: canPostLotterySettlements(roles)
  };
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Lottery settlement request failed';
}

function money(amount: number, currencyCode = 'USD') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: currencyCode
  }).format(Number(amount ?? 0));
}

function statusColor(status: LotterySettlementStatus) {
  if (status === 'POSTED') {
    return 'success';
  }
  if (status === 'APPROVED') {
    return 'primary';
  }
  if (status === 'REOPENED') {
    return 'warning';
  }
  return 'default';
}

function canApprove(status: LotterySettlementStatus) {
  return status === 'CALCULATED' || status === 'UNDER_REVIEW' || status === 'REOPENED';
}

function canReopen(status: LotterySettlementStatus) {
  return status === 'APPROVED' || status === 'POSTED';
}

function canPost(status: LotterySettlementStatus) {
  return status === 'APPROVED';
}

function cleanOptional(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function useLotteryFeatureEnabled(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['features', 'resolution', 'lottery-settlements'],
    queryFn: async () => getFeatureResolution(await getValidAccessToken()),
    enabled,
    select: (resolutions) => resolutions.find((resolution) => resolution.definition.code === 'LOTTERY_SALES')?.enabled
  });
}

function SettlementBreakdown({ settlement }: { settlement: LotterySettlement | null }) {
  if (!settlement) {
    return (
      <Alert severity="info">
        Select or calculate a settlement to review the breakdown.
      </Alert>
    );
  }

  const rows = [
    ['Gross sales', settlement.grossSales],
    ['Total payouts', -settlement.totalPayouts],
    ['Cancellations', -settlement.cancellations],
    ['Adjustments', settlement.adjustments],
    ['Commission', -settlement.commission],
    ['Expected settlement', settlement.expectedSettlement]
  ] as const;

  return (
    <Stack spacing={2}>
      <Stack spacing={0.5}>
        <Typography variant="h6">{settlement.operatorName}</Typography>
        <Typography color="text.secondary">
          {settlement.storeName} - {settlement.periodStart} to {settlement.periodEnd}
        </Typography>
        <Chip
          size="small"
          label={label(settlement.status)}
          color={statusColor(settlement.status)}
          sx={{ alignSelf: 'flex-start' }}
        />
      </Stack>

      <TableContainer>
        <Table size="small" aria-label="Settlement breakdown">
          <TableBody>
            {rows.map(([name, amount]) => (
              <TableRow key={name}>
                <TableCell>{name}</TableCell>
                <TableCell align="right">
                  <Typography fontWeight={name === 'Expected settlement' ? 700 : 400}>
                    {money(amount, settlement.currencyCode)}
                  </Typography>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {settlement.lifecycleNotes ? (
        <Alert severity="info">Notes: {settlement.lifecycleNotes}</Alert>
      ) : null}
      {settlement.reopenReason ? (
        <Alert severity="warning">Reopen reason: {settlement.reopenReason}</Alert>
      ) : null}
    </Stack>
  );
}

export function LotterySettlementPage() {
  const queryClient = useQueryClient();
  const { getValidAccessToken } = useSession();
  const permissions = useSettlementPermissions();
  const [page, setPage] = React.useState(0);
  const [statusFilter, setStatusFilter] = React.useState<LotterySettlementStatus | ''>('');
  const [selected, setSelected] = React.useState<LotterySettlement | null>(null);
  const [dialog, setDialog] = React.useState<LifecycleDialogState>(null);
  const [lifecycleText, setLifecycleText] = React.useState('');
  const featureEnabled = useLotteryFeatureEnabled(permissions.canView);

  const operators = useQuery({
    queryKey: ['lottery-operators', 'settlements'],
    queryFn: async () => listLotteryOperators(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: permissions.canView && featureEnabled.data !== false
  });

  const stores = useQuery({
    queryKey: ['stores', 'settlements'],
    queryFn: async () => listStores(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: permissions.canView && featureEnabled.data !== false
  });

  const settlements = useQuery({
    queryKey: ['lottery-settlements', page, statusFilter],
    queryFn: async () => {
      const params: LotterySettlementSearchParams = { page, size: 10, status: statusFilter };
      return listLotterySettlements(await getValidAccessToken(), params);
    },
    enabled: permissions.canView && featureEnabled.data !== false
  });

  const form = useForm<CalculationFormValues>({
    resolver: zodResolver(calculationSchema),
    defaultValues: emptyCalculation
  });

  const calculateMutation = useMutation({
    mutationFn: async (values: CalculationFormValues) => {
      const payload: LotterySettlementCalculationPayload = {
        operatorId: values.operatorId,
        storeId: values.storeId,
        periodStart: values.periodStart,
        periodEnd: values.periodEnd
      };
      return calculateLotterySettlement(await getValidAccessToken(), payload);
    },
    onSuccess: async (settlement) => {
      setSelected(settlement);
      await queryClient.invalidateQueries({ queryKey: ['lottery-settlements'] });
    }
  });

  const lifecycleMutation = useMutation({
    mutationFn: async ({ action, settlement, text }: { action: LifecycleAction; settlement: LotterySettlement; text: string }) => {
      const token = await getValidAccessToken();
      const payload: LotterySettlementLifecyclePayload = {
        version: settlement.version,
        notes: action === 'reopen' ? undefined : cleanOptional(text),
        reason: action === 'reopen' ? cleanOptional(text) : undefined
      };
      if (action === 'approve') {
        return approveLotterySettlement(token, settlement.id, payload);
      }
      if (action === 'post') {
        return postLotterySettlement(token, settlement.id, payload);
      }
      return reopenLotterySettlement(token, settlement.id, payload);
    },
    onSuccess: async (settlement) => {
      setSelected(settlement);
      setDialog(null);
      setLifecycleText('');
      await queryClient.invalidateQueries({ queryKey: ['lottery-settlements'] });
    }
  });

  if (!permissions.canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  const dialogTitle = dialog ? `${label(dialog.action)} settlement` : '';
  const dialogLabel = dialog?.action === 'reopen' ? 'Reason' : 'Notes';
  const actionDisabled = lifecycleMutation.isPending
    || (dialog?.action === 'reopen' && lifecycleText.trim().length === 0);

  return (
    <Stack spacing={3} sx={{ maxWidth: 1240 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">Lottery settlements</Typography>
        </Box>
        <TextField
          select
          size="small"
          label="Status"
          value={statusFilter}
          onChange={(event) => {
            setStatusFilter(event.target.value as LotterySettlementStatus | '');
            setPage(0);
          }}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">All</MenuItem>
          {settlementStatuses.map((status) => <MenuItem key={status} value={status}>{label(status)}</MenuItem>)}
        </TextField>
        <Tooltip title="Refresh settlements">
          <IconButton aria-label="Refresh settlements" onClick={() => void settlements.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Stack>

      {featureEnabled.data === false ? <Alert severity="warning">Lottery sales is disabled.</Alert> : null}
      {settlements.isError ? <Alert severity="error">{errorMessage(settlements.error)}</Alert> : null}
      {calculateMutation.isError ? <Alert severity="error">{errorMessage(calculateMutation.error)}</Alert> : null}
      {lifecycleMutation.isError ? <Alert severity="error">{errorMessage(lifecycleMutation.error)}</Alert> : null}

      <Grid container spacing={3}>
        <Grid item xs={12} md={7}>
          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
            {settlements.isLoading ? (
              <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 320 }}>
                <CircularProgress aria-label="Loading lottery settlements" />
                <Typography color="text.secondary">Loading lottery settlements</Typography>
              </Stack>
            ) : (
              <>
                <TableContainer>
                  <Table aria-label="Lottery settlements">
                    <TableHead>
                      <TableRow>
                        <TableCell>Period</TableCell>
                        <TableCell>Operator</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Expected</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(settlements.data?.content ?? []).map((settlement) => (
                        <TableRow
                          key={settlement.id}
                          hover
                          selected={selected?.id === settlement.id}
                          onClick={() => setSelected(settlement)}
                          sx={{ cursor: 'pointer' }}
                        >
                          <TableCell>
                            <Typography fontWeight={700}>{settlement.periodStart} to {settlement.periodEnd}</Typography>
                            <Typography variant="body2" color="text.secondary">{settlement.storeName}</Typography>
                          </TableCell>
                          <TableCell>{settlement.operatorName}</TableCell>
                          <TableCell><Chip size="small" label={label(settlement.status)} color={statusColor(settlement.status)} /></TableCell>
                          <TableCell align="right">{money(settlement.expectedSettlement, settlement.currencyCode)}</TableCell>
                          <TableCell align="right" onClick={(event) => event.stopPropagation()}>
                            <Tooltip title="Approve settlement">
                              <span>
                                <IconButton
                                  aria-label={`Approve settlement ${settlement.operatorName}`}
                                  disabled={!permissions.canManage || !canApprove(settlement.status) || lifecycleMutation.isPending}
                                  onClick={() => {
                                    setDialog({ action: 'approve', settlement });
                                    setLifecycleText('');
                                  }}
                                >
                                  <ApprovalOutlinedIcon />
                                </IconButton>
                              </span>
                            </Tooltip>
                            <Tooltip title="Reopen settlement">
                              <span>
                                <IconButton
                                  aria-label={`Reopen settlement ${settlement.operatorName}`}
                                  disabled={!permissions.canManage || !canReopen(settlement.status) || lifecycleMutation.isPending}
                                  onClick={() => {
                                    setDialog({ action: 'reopen', settlement });
                                    setLifecycleText('');
                                  }}
                                >
                                  <LockOpenOutlinedIcon />
                                </IconButton>
                              </span>
                            </Tooltip>
                            <Tooltip title="Post settlement">
                              <span>
                                <IconButton
                                  aria-label={`Post settlement ${settlement.operatorName}`}
                                  disabled={!permissions.canPost || !canPost(settlement.status) || lifecycleMutation.isPending}
                                  onClick={() => {
                                    setDialog({ action: 'post', settlement });
                                    setLifecycleText('');
                                  }}
                                >
                                  <PublishOutlinedIcon />
                                </IconButton>
                              </span>
                            </Tooltip>
                          </TableCell>
                        </TableRow>
                      ))}
                      {settlements.data?.content.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={5}>
                            <Alert severity="info">No lottery settlements found.</Alert>
                          </TableCell>
                        </TableRow>
                      ) : null}
                    </TableBody>
                  </Table>
                </TableContainer>
                <TablePagination
                  component="div"
                  count={settlements.data?.totalElements ?? 0}
                  page={page}
                  onPageChange={(_, nextPage) => setPage(nextPage)}
                  rowsPerPage={10}
                  rowsPerPageOptions={[10]}
                />
              </>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={5}>
          <Stack spacing={3}>
            <Paper
              component="form"
              elevation={0}
              onSubmit={form.handleSubmit((values) => calculateMutation.mutate(values))}
              sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}
            >
              <Stack spacing={2}>
                <Typography variant="h6">Calculate settlement</Typography>
                <Controller
                  name="operatorId"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField {...field} select label="Operator" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                      {(operators.data?.content ?? []).map((operator: LotteryOperator) => <MenuItem key={operator.id} value={operator.id}>{operator.name}</MenuItem>)}
                    </TextField>
                  )}
                />
                <Controller
                  name="storeId"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField {...field} select label="Store" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                      {(stores.data?.content ?? []).map((store: Store) => <MenuItem key={store.id} value={store.id}>{store.name}</MenuItem>)}
                    </TextField>
                  )}
                />
                <Grid container spacing={2}>
                  <Grid item xs={6}>
                    <Controller
                      name="periodStart"
                      control={form.control}
                      render={({ field, fieldState }) => (
                        <TextField {...field} label="Period start" type="date" InputLabelProps={{ shrink: true }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                      )}
                    />
                  </Grid>
                  <Grid item xs={6}>
                    <Controller
                      name="periodEnd"
                      control={form.control}
                      render={({ field, fieldState }) => (
                        <TextField {...field} label="Period end" type="date" InputLabelProps={{ shrink: true }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                      )}
                    />
                  </Grid>
                </Grid>
                <Button
                  type="submit"
                  variant="contained"
                  startIcon={<CalculateOutlinedIcon />}
                  disabled={!permissions.canManage || calculateMutation.isPending}
                >
                  Calculate settlement
                </Button>
              </Stack>
            </Paper>

            <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}>
              <SettlementBreakdown settlement={selected} />
            </Paper>
          </Stack>
        </Grid>
      </Grid>

      <Dialog open={Boolean(dialog)} onClose={() => setDialog(null)} fullWidth maxWidth="sm">
        <DialogTitle>{dialogTitle}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography color="text.secondary">
              {dialog?.settlement.operatorName} - {dialog?.settlement.periodStart} to {dialog?.settlement.periodEnd}
            </Typography>
            <TextField
              label={dialogLabel}
              value={lifecycleText}
              onChange={(event) => setLifecycleText(event.target.value)}
              multiline
              minRows={3}
              required={dialog?.action === 'reopen'}
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialog(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={actionDisabled}
            onClick={() => {
              if (dialog) {
                lifecycleMutation.mutate({ ...dialog, text: lifecycleText });
              }
            }}
          >
            {dialog?.action ? label(dialog.action) : 'Submit'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
