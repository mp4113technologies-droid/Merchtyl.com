import TuneOutlinedIcon from '@mui/icons-material/TuneOutlined';
import {
  Alert,
  Box,
  Button,
  Chip,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import {
  getFeatureResolution,
  listRegisters,
  listStores,
  updateDeploymentFeature,
  updateRegisterFeature,
  updateStoreFeature,
  type FeatureOverridePayload
} from '../../api/client';
import type { FeatureCode, FeatureOverride, FeatureResolution } from '../../api/types';
import { useSession } from '../../app/session';

type OverrideChoice = 'INHERIT' | 'ENABLED' | 'DISABLED';
type Scope = 'deployment' | 'store' | 'register';

type PendingOverride = {
  scope: Scope;
  featureCode: FeatureCode;
  choice: OverrideChoice;
  version?: number;
};

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Feature request failed';
}

function choiceFromOverride(override: FeatureOverride | null): OverrideChoice {
  if (!override) {
    return 'INHERIT';
  }
  return override.enabled ? 'ENABLED' : 'DISABLED';
}

function payload(choice: OverrideChoice, override: FeatureOverride | null): FeatureOverridePayload {
  return {
    enabled: choice === 'INHERIT' ? null : choice === 'ENABLED',
    version: override?.version
  };
}

function sourceColor(source: FeatureResolution['source']) {
  if (source === 'REGISTER') {
    return 'primary';
  }
  if (source === 'STORE') {
    return 'secondary';
  }
  if (source === 'TENANT') {
    return 'info';
  }
  return 'default';
}

function formatFeatureCode(code: FeatureCode) {
  return code.replaceAll('_', ' ');
}

export function FeatureSettingsPage() {
  const { getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const [selectedStoreId, setSelectedStoreId] = React.useState('');
  const [selectedRegisterId, setSelectedRegisterId] = React.useState('');
  const [status, setStatus] = React.useState<{ severity: 'success' | 'error'; message: string } | null>(null);

  const storesQuery = useQuery({
    queryKey: ['stores', 'feature-settings'],
    queryFn: async () => listStores(await getValidAccessToken(), { active: true, size: 100 })
  });

  const registersQuery = useQuery({
    queryKey: ['registers', 'feature-settings', selectedStoreId],
    queryFn: async () => listRegisters(await getValidAccessToken(), {
      storeId: selectedStoreId || undefined,
      active: true,
      size: 100
    })
  });

  const resolutionQuery = useQuery({
    queryKey: ['features', 'resolution', selectedStoreId, selectedRegisterId],
    queryFn: async () => getFeatureResolution(await getValidAccessToken(), {
      storeId: selectedStoreId || undefined,
      registerId: selectedRegisterId || undefined
    })
  });

  const mutation = useMutation({
    mutationFn: async (request: PendingOverride) => {
      const token = await getValidAccessToken();
      if (request.scope === 'deployment') {
        return updateDeploymentFeature(token, request.featureCode, { enabled: request.choice === 'INHERIT' ? null : request.choice === 'ENABLED', version: request.version });
      }
      if (request.scope === 'store') {
        if (!selectedStoreId) {
          throw new Error('Select a store before changing a store override.');
        }
        return updateStoreFeature(token, request.featureCode, selectedStoreId, { enabled: request.choice === 'INHERIT' ? null : request.choice === 'ENABLED', version: request.version });
      }
      if (!selectedRegisterId) {
        throw new Error('Select a register before changing a register override.');
      }
      return updateRegisterFeature(token, request.featureCode, selectedRegisterId, { enabled: request.choice === 'INHERIT' ? null : request.choice === 'ENABLED', version: request.version });
    },
    onSuccess: async (_, request) => {
      setStatus({ severity: 'success', message: `${formatFeatureCode(request.featureCode)} ${request.scope} override saved.` });
      await queryClient.invalidateQueries({ queryKey: ['features', 'resolution'] });
    },
    onError: (error) => {
      setStatus({ severity: 'error', message: errorMessage(error) });
    }
  });

  function updateOverride(
    scope: Scope,
    featureCode: FeatureCode,
    choice: OverrideChoice,
    override: FeatureOverride | null
  ) {
    mutation.mutate({
      scope,
      featureCode,
      choice,
      version: payload(choice, override).version
    });
  }

  const stores = storesQuery.data?.content ?? [];
  const registers = registersQuery.data?.content ?? [];
  const resolutions = resolutionQuery.data ?? [];
  const pageError = storesQuery.error ?? registersQuery.error ?? resolutionQuery.error;
  const busy = mutation.isPending;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
        <Box>
          <Typography variant="h5" component="h1">Feature flags</Typography>
          <Typography color="text.secondary">Manage deployment, store, and register feature availability.</Typography>
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          <TuneOutlinedIcon color="primary" />
          <Typography color="text.secondary">{resolutions.length} features</Typography>
        </Stack>
      </Stack>

      {status ? (
        <Alert
          severity={status.severity}
          action={<Button color="inherit" size="small" onClick={() => setStatus(null)}>Clear</Button>}
        >
          {status.message}
        </Alert>
      ) : null}
      {pageError ? <Alert severity="error">{errorMessage(pageError)}</Alert> : null}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <FormControl fullWidth>
              <InputLabel id="feature-store-label">Store scope</InputLabel>
              <Select
                labelId="feature-store-label"
                label="Store scope"
                value={selectedStoreId}
                onChange={(event) => {
                  setSelectedStoreId(event.target.value);
                  setSelectedRegisterId('');
                }}
              >
                <MenuItem value="">Deployment only</MenuItem>
                {stores.map((store) => (
                  <MenuItem key={store.id} value={store.id}>{store.name}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={12} md={6}>
            <FormControl fullWidth disabled={!selectedStoreId}>
              <InputLabel id="feature-register-label">Register scope</InputLabel>
              <Select
                labelId="feature-register-label"
                label="Register scope"
                value={selectedRegisterId}
                onChange={(event) => setSelectedRegisterId(event.target.value)}
              >
                <MenuItem value="">Store only</MenuItem>
                {registers.map((register) => (
                  <MenuItem key={register.id} value={register.id}>{register.name}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
        </Grid>
      </Paper>

      <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        <Table aria-label="Feature flags">
          <TableHead>
            <TableRow>
              <TableCell>Feature</TableCell>
              <TableCell>Resolved</TableCell>
              <TableCell>Deployment</TableCell>
              <TableCell>Store</TableCell>
              <TableCell>Register</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {resolutions.map((resolution) => (
              <TableRow key={resolution.definition.code} hover>
                <TableCell sx={{ maxWidth: 320 }}>
                  <Typography fontWeight={700}>{resolution.definition.name}</Typography>
                  <Typography variant="body2" color="text.secondary">{resolution.definition.description}</Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                    {resolution.definition.code}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Stack spacing={1} alignItems="flex-start">
                    <Chip
                      label={resolution.enabled ? 'Enabled' : 'Disabled'}
                      color={resolution.enabled ? 'success' : 'default'}
                      size="small"
                    />
                    <Chip label={resolution.source} color={sourceColor(resolution.source)} size="small" variant="outlined" />
                  </Stack>
                </TableCell>
                <TableCell>
                  <OverrideSelect
                    label={`${resolution.definition.name} deployment override`}
                    value={choiceFromOverride(resolution.tenantOverride)}
                    disabled={busy}
                    onChange={(choice) => updateOverride('deployment', resolution.definition.code, choice, resolution.tenantOverride)}
                  />
                </TableCell>
                <TableCell>
                  <OverrideSelect
                    label={`${resolution.definition.name} store override`}
                    value={choiceFromOverride(resolution.storeOverride)}
                    disabled={busy || !selectedStoreId}
                    onChange={(choice) => updateOverride('store', resolution.definition.code, choice, resolution.storeOverride)}
                  />
                </TableCell>
                <TableCell>
                  <OverrideSelect
                    label={`${resolution.definition.name} register override`}
                    value={choiceFromOverride(resolution.registerOverride)}
                    disabled={busy || !selectedRegisterId}
                    onChange={(choice) => updateOverride('register', resolution.definition.code, choice, resolution.registerOverride)}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </Stack>
  );
}

function OverrideSelect({
  label,
  value,
  disabled,
  onChange
}: {
  label: string;
  value: OverrideChoice;
  disabled?: boolean;
  onChange: (choice: OverrideChoice) => void;
}) {
  return (
    <FormControl size="small" sx={{ minWidth: 132 }} disabled={disabled}>
      <Select
        inputProps={{ 'aria-label': label }}
        value={value}
        onChange={(event) => onChange(event.target.value as OverrideChoice)}
      >
        <MenuItem value="INHERIT">Inherit</MenuItem>
        <MenuItem value="ENABLED">Enabled</MenuItem>
        <MenuItem value="DISABLED">Disabled</MenuItem>
      </Select>
    </FormControl>
  );
}
