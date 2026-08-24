import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import StorefrontIcon from '@mui/icons-material/Storefront';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Paper,
  Stack,
  Typography
} from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { listAssignedStores, validateStoreAccess } from '../../api/client';
import type { AssignedStore } from '../../api/types';
import { useSession } from '../../app/session';

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Request failed';
}

function storeLocation(store: AssignedStore) {
  return [store.city, store.administrativeDivisionCode].filter(Boolean).join(', ') || 'Location not set';
}

export function StoreSelectionPage() {
  const { getValidAccessToken } = useSession();
  const navigate = useNavigate();
  const stores = useQuery({
    queryKey: ['store-access', 'assigned-stores'],
    queryFn: async () => listAssignedStores(await getValidAccessToken())
  });
  const selectMutation = useMutation({
    mutationFn: async (store: AssignedStore) => {
      const validated = await validateStoreAccess(await getValidAccessToken(), store.storeId);
      window.localStorage.setItem('merchtyl.activeStoreId', validated.storeId);
      return validated;
    },
    onSuccess: () => navigate('/')
  });

  if (stores.isLoading) {
    return (
      <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: 280 }}>
        <CircularProgress aria-label="Loading assigned stores" />
        <Typography color="text.secondary">Loading assigned stores</Typography>
      </Stack>
    );
  }

  if (stores.isError) {
    return <Alert severity="error">{errorMessage(stores.error)}</Alert>;
  }

  const assignedStores = stores.data ?? [];
  if (assignedStores.length === 0) {
    return (
      <Alert severity="warning">
        No active store assignments are available for this account.
      </Alert>
    );
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h5" component="h1">Select store</Typography>
        <Typography color="text.secondary">Choose one of your active store assignments.</Typography>
      </Box>
      {selectMutation.isError ? <Alert severity="error">{errorMessage(selectMutation.error)}</Alert> : null}
      <Stack spacing={2}>
        {assignedStores.map((store) => (
          <Paper key={store.storeId} elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 2 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ xs: 'stretch', sm: 'center' }}>
              <StorefrontIcon color="primary" />
              <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                <Typography variant="h6">{store.storeName}</Typography>
                <Typography color="text.secondary">{store.storeCode} · {storeLocation(store)}</Typography>
              </Box>
              <Chip label={store.assignmentRole} size="small" />
              <Button
                variant="contained"
                startIcon={<CheckCircleIcon />}
                disabled={selectMutation.isPending}
                onClick={() => selectMutation.mutate(store)}
              >
                Select
              </Button>
            </Stack>
          </Paper>
        ))}
      </Stack>
    </Stack>
  );
}
