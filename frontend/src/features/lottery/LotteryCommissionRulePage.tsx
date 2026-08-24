import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/Edit';
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
import { Controller, useForm } from 'react-hook-form';
import { Navigate } from 'react-router-dom';
import { z } from 'zod';
import {
  createLotteryCommissionRule,
  deleteLotteryCommissionRule,
  getFeatureResolution,
  listLotteryCommissionRules,
  listLotteryOperators,
  listStores,
  updateLotteryCommissionRule,
  type LotteryCommissionRulePayload,
  type LotteryCommissionRuleSearchParams,
  type LotteryCommissionRuleUpdatePayload
} from '../../api/client';
import type {
  LotteryCommissionPeriod,
  LotteryCommissionRule,
  LotteryCommissionRuleStatus,
  LotteryCommissionRuleType,
  LotteryOperator,
  Store,
  UserRole
} from '../../api/types';
import { useSession } from '../../app/session';

const ruleTypes: LotteryCommissionRuleType[] = [
  'PERCENT_OF_SALES',
  'PERCENT_OF_PAYOUT',
  'FIXED_PER_TRANSACTION',
  'FIXED_PER_PERIOD',
  'MANUAL'
];
const ruleStatuses: LotteryCommissionRuleStatus[] = ['DRAFT', 'ACTIVE', 'RETIRED'];
const periods: LotteryCommissionPeriod[] = ['DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY'];

const ruleSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(120, 'Name is too long'),
  operatorId: z.string().trim().min(1, 'Operator is required'),
  storeId: z.string().trim().min(1, 'Store is required'),
  ruleType: z.enum(ruleTypes as [LotteryCommissionRuleType, ...LotteryCommissionRuleType[]]),
  commissionRatePercent: z.coerce.number().optional(),
  fixedAmount: z.coerce.number().optional(),
  currencyCode: z.string().trim().optional(),
  fixedPeriod: z.enum(periods as [LotteryCommissionPeriod, ...LotteryCommissionPeriod[]]).optional(),
  effectiveFrom: z.string().trim().min(1, 'Effective from is required'),
  effectiveTo: z.string().optional(),
  status: z.enum(ruleStatuses as [LotteryCommissionRuleStatus, ...LotteryCommissionRuleStatus[]]),
  notes: z.string().max(500, 'Notes are too long').optional()
}).superRefine((values, context) => {
  if (values.effectiveTo && values.effectiveTo < values.effectiveFrom) {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ['effectiveTo'], message: 'Effective to must be on or after effective from' });
  }
  if (values.ruleType === 'PERCENT_OF_SALES' || values.ruleType === 'PERCENT_OF_PAYOUT') {
    if (!values.commissionRatePercent || values.commissionRatePercent <= 0 || values.commissionRatePercent > 100) {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['commissionRatePercent'], message: 'Rate must be greater than 0 and no more than 100' });
    }
  }
  if (values.ruleType === 'FIXED_PER_TRANSACTION' || values.ruleType === 'FIXED_PER_PERIOD') {
    if (!values.fixedAmount || values.fixedAmount <= 0) {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['fixedAmount'], message: 'Fixed amount must be greater than 0' });
    }
    if (!values.currencyCode?.trim()) {
      context.addIssue({ code: z.ZodIssueCode.custom, path: ['currencyCode'], message: 'Currency is required' });
    }
  }
  if (values.ruleType === 'FIXED_PER_PERIOD' && !values.fixedPeriod) {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ['fixedPeriod'], message: 'Period is required' });
  }
});

type RuleFormValues = z.infer<typeof ruleSchema>;

const emptyRule: RuleFormValues = {
  name: '',
  operatorId: '',
  storeId: '',
  ruleType: 'PERCENT_OF_SALES',
  commissionRatePercent: 5,
  fixedAmount: undefined,
  currencyCode: 'USD',
  fixedPeriod: undefined,
  effectiveFrom: new Date().toISOString().slice(0, 10),
  effectiveTo: '',
  status: 'DRAFT',
  notes: ''
};

function canManageLotteryCommissionRules(roles: UserRole[]) {
  return roles.some((role) => role === 'OWNER' || role === 'TENANT_OWNER' || role === 'MANAGER' || role === 'STORE_MANAGER');
}

function useRulePermissions() {
  const { currentUser, session } = useSession();
  const roles = currentUser?.roles ?? session?.roles ?? [];
  return {
    canView: canManageLotteryCommissionRules(roles),
    canManage: canManageLotteryCommissionRules(roles)
  };
}

function label(value: string) {
  return value.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Lottery commission rule request failed';
}

function optionalText(value?: string) {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function statusColor(status: LotteryCommissionRuleStatus) {
  if (status === 'ACTIVE') {
    return 'success';
  }
  if (status === 'RETIRED') {
    return 'default';
  }
  return 'warning';
}

function formatValue(rule: LotteryCommissionRule) {
  if (rule.ruleType === 'PERCENT_OF_SALES' || rule.ruleType === 'PERCENT_OF_PAYOUT') {
    return `${rule.commissionRatePercent}%`;
  }
  if (rule.ruleType === 'FIXED_PER_TRANSACTION') {
    return `${rule.currencyCode ?? 'USD'} ${Number(rule.fixedAmount ?? 0).toFixed(2)} / transaction`;
  }
  if (rule.ruleType === 'FIXED_PER_PERIOD') {
    return `${rule.currencyCode ?? 'USD'} ${Number(rule.fixedAmount ?? 0).toFixed(2)} / ${label(rule.fixedPeriod ?? '')}`;
  }
  return 'Manual';
}

function useLotteryFeatureEnabled(enabled: boolean) {
  const { getValidAccessToken } = useSession();
  return useQuery({
    queryKey: ['features', 'resolution', 'lottery-commission-rules'],
    queryFn: async () => getFeatureResolution(await getValidAccessToken()),
    enabled,
    select: (resolutions) => resolutions.find((resolution) => resolution.definition.code === 'LOTTERY_SALES')?.enabled
  });
}

function ruleFormValues(rule: LotteryCommissionRule): RuleFormValues {
  return {
    name: rule.name,
    operatorId: rule.operatorId,
    storeId: rule.storeId,
    ruleType: rule.ruleType,
    commissionRatePercent: rule.commissionRatePercent ?? undefined,
    fixedAmount: rule.fixedAmount ?? undefined,
    currencyCode: rule.currencyCode ?? 'USD',
    fixedPeriod: rule.fixedPeriod ?? undefined,
    effectiveFrom: rule.effectiveFrom,
    effectiveTo: rule.effectiveTo ?? '',
    status: rule.status,
    notes: rule.notes ?? ''
  };
}

function cleanPayload(values: RuleFormValues, operators: LotteryOperator[]): LotteryCommissionRulePayload {
  const operator = operators.find((candidate) => candidate.id === values.operatorId);
  const percentRule = values.ruleType === 'PERCENT_OF_SALES' || values.ruleType === 'PERCENT_OF_PAYOUT';
  const fixedRule = values.ruleType === 'FIXED_PER_TRANSACTION' || values.ruleType === 'FIXED_PER_PERIOD';
  return {
    name: values.name.trim(),
    operatorId: values.operatorId,
    jurisdictionId: operator?.jurisdictionId ?? '',
    storeId: values.storeId,
    ruleType: values.ruleType,
    commissionRatePercent: percentRule ? values.commissionRatePercent : undefined,
    fixedAmount: fixedRule ? values.fixedAmount : undefined,
    currencyCode: fixedRule ? values.currencyCode?.trim().toUpperCase() : undefined,
    fixedPeriod: values.ruleType === 'FIXED_PER_PERIOD' ? values.fixedPeriod : undefined,
    effectiveFrom: values.effectiveFrom,
    effectiveTo: optionalText(values.effectiveTo),
    status: values.status,
    notes: optionalText(values.notes)
  };
}

export function LotteryCommissionRulePage() {
  const queryClient = useQueryClient();
  const { getValidAccessToken } = useSession();
  const permissions = useRulePermissions();
  const [page, setPage] = React.useState(0);
  const [selected, setSelected] = React.useState<LotteryCommissionRule | null>(null);
  const [statusFilter, setStatusFilter] = React.useState<LotteryCommissionRuleStatus | ''>('');
  const featureEnabled = useLotteryFeatureEnabled(permissions.canView);

  const operators = useQuery({
    queryKey: ['lottery-operators', 'commission-rules'],
    queryFn: async () => listLotteryOperators(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: permissions.canView && featureEnabled.data !== false
  });
  const stores = useQuery({
    queryKey: ['stores', 'commission-rules'],
    queryFn: async () => listStores(await getValidAccessToken(), { active: true, size: 100 }),
    enabled: permissions.canView && featureEnabled.data !== false
  });
  const rules = useQuery({
    queryKey: ['lottery-commission-rules', page, statusFilter],
    queryFn: async () => {
      const params: LotteryCommissionRuleSearchParams = { page, size: 10, status: statusFilter };
      return listLotteryCommissionRules(await getValidAccessToken(), params);
    },
    enabled: permissions.canView && featureEnabled.data !== false
  });

  const form = useForm<RuleFormValues>({
    resolver: zodResolver(ruleSchema),
    defaultValues: emptyRule
  });
  const ruleType = form.watch('ruleType');

  const saveMutation = useMutation({
    mutationFn: async (values: RuleFormValues) => {
      const token = await getValidAccessToken();
      const payload = cleanPayload(values, operators.data?.content ?? []);
      if (selected) {
        return updateLotteryCommissionRule(token, selected.id, { ...payload, version: selected.version } satisfies LotteryCommissionRuleUpdatePayload);
      }
      return createLotteryCommissionRule(token, payload);
    },
    onSuccess: async () => {
      setSelected(null);
      form.reset(emptyRule);
      await queryClient.invalidateQueries({ queryKey: ['lottery-commission-rules'] });
    }
  });

  const deleteMutation = useMutation({
    mutationFn: async (rule: LotteryCommissionRule) => deleteLotteryCommissionRule(await getValidAccessToken(), rule.id, rule.version),
    onSuccess: async () => {
      setSelected(null);
      form.reset(emptyRule);
      await queryClient.invalidateQueries({ queryKey: ['lottery-commission-rules'] });
    }
  });

  if (!permissions.canView) {
    return <Navigate to="/unauthorized" replace />;
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 1180 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h5" component="h1">Lottery commission rules</Typography>
        </Box>
        <TextField
          select
          size="small"
          label="Status"
          value={statusFilter}
          onChange={(event) => {
            setStatusFilter(event.target.value as LotteryCommissionRuleStatus | '');
            setPage(0);
          }}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">All</MenuItem>
          {ruleStatuses.map((status) => <MenuItem key={status} value={status}>{label(status)}</MenuItem>)}
        </TextField>
        <Tooltip title="Refresh commission rules">
          <IconButton aria-label="Refresh commission rules" onClick={() => void rules.refetch()}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Stack>

      {featureEnabled.data === false ? (
        <Alert severity="warning">Lottery sales is disabled.</Alert>
      ) : null}
      {rules.isError ? <Alert severity="error">{errorMessage(rules.error)}</Alert> : null}
      {saveMutation.isError ? <Alert severity="error">{errorMessage(saveMutation.error)}</Alert> : null}
      {deleteMutation.isError ? <Alert severity="error">{errorMessage(deleteMutation.error)}</Alert> : null}

      <Grid container spacing={3}>
        <Grid item xs={12} md={7}>
          <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
            {rules.isLoading ? (
              <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 320 }}>
                <CircularProgress aria-label="Loading lottery commission rules" />
                <Typography color="text.secondary">Loading lottery commission rules</Typography>
              </Stack>
            ) : (
              <>
                <TableContainer>
                  <Table aria-label="Lottery commission rules">
                    <TableHead>
                      <TableRow>
                        <TableCell>Name</TableCell>
                        <TableCell>Operator</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Value</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(rules.data?.content ?? []).map((rule) => (
                        <TableRow key={rule.id} hover selected={selected?.id === rule.id}>
                          <TableCell>
                            <Typography fontWeight={700}>{rule.name}</Typography>
                            <Typography variant="body2" color="text.secondary">{rule.storeName}</Typography>
                          </TableCell>
                          <TableCell>{rule.operatorName}</TableCell>
                          <TableCell>{label(rule.ruleType)}</TableCell>
                          <TableCell>{formatValue(rule)}</TableCell>
                          <TableCell><Chip size="small" label={label(rule.status)} color={statusColor(rule.status)} /></TableCell>
                          <TableCell align="right">
                            <Tooltip title="Edit rule">
                              <IconButton
                                aria-label={`Edit ${rule.name}`}
                                onClick={() => {
                                  setSelected(rule);
                                  form.reset(ruleFormValues(rule));
                                }}
                              >
                                <EditIcon />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Delete rule">
                              <span>
                                <IconButton
                                  aria-label={`Delete ${rule.name}`}
                                  disabled={!permissions.canManage || deleteMutation.isPending}
                                  onClick={() => deleteMutation.mutate(rule)}
                                >
                                  <DeleteOutlineIcon />
                                </IconButton>
                              </span>
                            </Tooltip>
                          </TableCell>
                        </TableRow>
                      ))}
                      {rules.data?.content.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={6}>
                            <Alert severity="info">No lottery commission rules found.</Alert>
                          </TableCell>
                        </TableRow>
                      ) : null}
                    </TableBody>
                  </Table>
                </TableContainer>
                <TablePagination
                  component="div"
                  count={rules.data?.totalElements ?? 0}
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
          <Paper
            component="form"
            elevation={0}
            onSubmit={form.handleSubmit((values) => saveMutation.mutate(values))}
            sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 3 }}
          >
            <Stack spacing={2}>
              <Stack direction="row" spacing={1.5} alignItems="center">
                <Typography variant="h6" sx={{ flexGrow: 1 }}>{selected ? 'Edit rule' : 'New rule'}</Typography>
                {selected ? (
                  <Button
                    type="button"
                    size="small"
                    startIcon={<AddIcon />}
                    onClick={() => {
                      setSelected(null);
                      form.reset(emptyRule);
                    }}
                  >
                    New
                  </Button>
                ) : null}
              </Stack>
              <Controller
                name="name"
                control={form.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} label="Name" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                )}
              />
              <Controller
                name="operatorId"
                control={form.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} select label="Operator" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                    {(operators.data?.content ?? []).map((operator) => <MenuItem key={operator.id} value={operator.id}>{operator.name}</MenuItem>)}
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
              <Controller
                name="ruleType"
                control={form.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} select label="Type" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                    {ruleTypes.map((type) => <MenuItem key={type} value={type}>{label(type)}</MenuItem>)}
                  </TextField>
                )}
              />
              {ruleType === 'PERCENT_OF_SALES' || ruleType === 'PERCENT_OF_PAYOUT' ? (
                <Controller
                  name="commissionRatePercent"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField {...field} label="Rate percent" type="number" inputProps={{ min: 0, max: 100, step: '0.0001' }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                  )}
                />
              ) : null}
              {ruleType === 'FIXED_PER_TRANSACTION' || ruleType === 'FIXED_PER_PERIOD' ? (
                <Grid container spacing={2}>
                  <Grid item xs={7}>
                    <Controller
                      name="fixedAmount"
                      control={form.control}
                      render={({ field, fieldState }) => (
                        <TextField {...field} label="Fixed amount" type="number" inputProps={{ min: 0, step: '0.01' }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                      )}
                    />
                  </Grid>
                  <Grid item xs={5}>
                    <Controller
                      name="currencyCode"
                      control={form.control}
                      render={({ field, fieldState }) => (
                        <TextField {...field} label="Currency" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                      )}
                    />
                  </Grid>
                </Grid>
              ) : null}
              {ruleType === 'FIXED_PER_PERIOD' ? (
                <Controller
                  name="fixedPeriod"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <TextField {...field} select label="Period" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                      {periods.map((period) => <MenuItem key={period} value={period}>{label(period)}</MenuItem>)}
                    </TextField>
                  )}
                />
              ) : null}
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Controller
                    name="effectiveFrom"
                    control={form.control}
                    render={({ field, fieldState }) => (
                      <TextField {...field} label="Effective from" type="date" InputLabelProps={{ shrink: true }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                    )}
                  />
                </Grid>
                <Grid item xs={6}>
                  <Controller
                    name="effectiveTo"
                    control={form.control}
                    render={({ field, fieldState }) => (
                      <TextField {...field} label="Effective to" type="date" InputLabelProps={{ shrink: true }} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                    )}
                  />
                </Grid>
              </Grid>
              <Controller
                name="status"
                control={form.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} select label="Status" error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth>
                    {ruleStatuses.map((status) => <MenuItem key={status} value={status}>{label(status)}</MenuItem>)}
                  </TextField>
                )}
              />
              <Controller
                name="notes"
                control={form.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} label="Notes" multiline minRows={2} error={Boolean(fieldState.error)} helperText={fieldState.error?.message} fullWidth />
                )}
              />
              <Button type="submit" variant="contained" startIcon={<SaveIcon />} disabled={!permissions.canManage || saveMutation.isPending}>
                {selected ? 'Save rule' : 'Create rule'}
              </Button>
            </Stack>
          </Paper>
        </Grid>
      </Grid>
    </Stack>
  );
}
