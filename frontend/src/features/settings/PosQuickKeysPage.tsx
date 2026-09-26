import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import AddIcon from '@mui/icons-material/Add';
import { Alert, Box, Button, Chip, CircularProgress, IconButton, Paper, Stack, Switch, TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { createPosQuickKey, deletePosQuickKey, listPosQuickKeyConfiguration, listProducts, updatePosQuickKey } from '../../api/client';
import type { PosQuickKey, Product } from '../../api/types';
import { useSession } from '../../app/session';

const queryKey = ['pos-quick-keys', 'configuration'] as const;

export function PosQuickKeysPage() {
  const { currentUser, getValidAccessToken } = useSession();
  const queryClient = useQueryClient();
  const [search, setSearch] = React.useState('');
  const [submitted, setSubmitted] = React.useState('');
  const [labels, setLabels] = React.useState<Record<string, string>>({});
  const canManage = Boolean(currentUser?.permissions?.includes('PRODUCT_UPDATE'));
  const keys = useQuery({ queryKey, queryFn: async () => listPosQuickKeyConfiguration(await getValidAccessToken()) });
  const products = useQuery({ queryKey: ['products', 'quick-key-picker', submitted], queryFn: async () => listProducts(await getValidAccessToken(), { q: submitted, active: true, page: 0, size: 20 }), enabled: submitted.length > 0 });
  const refresh = async () => { await queryClient.invalidateQueries({ queryKey: ['pos-quick-keys'] }); };
  const add = useMutation({ mutationFn: async (variantId: string) => createPosQuickKey(await getValidAccessToken(), { productVariantId: variantId }), onSuccess: async () => { setSearch(''); setSubmitted(''); await refresh(); } });
  const update = useMutation({ mutationFn: async ({ key, order, active, label }: { key: PosQuickKey; order: number; active: boolean; label?: string }) => updatePosQuickKey(await getValidAccessToken(), key.id, { displayLabel: label ?? key.displayLabel ?? undefined, displayOrder: order, active }), onSuccess: refresh });
  const remove = useMutation({ mutationFn: async (id: string) => deletePosQuickKey(await getValidAccessToken(), id), onSuccess: refresh });
  const configured = keys.data ?? [];
  const configuredVariants = new Set(configured.map(key => key.productVariantId));
  const candidates = (products.data?.content ?? []).flatMap((product: Product) => product.variants.map(variant => ({ product, variant }))).filter(row => !configuredVariants.has(row.variant.id));
  const error = keys.error ?? products.error ?? add.error ?? update.error ?? remove.error;

  return <Stack spacing={3} sx={{ maxWidth: 980 }}>
    <Box><Typography variant="h5" component="h1">Retail POS Quick Keys</Typography><Typography color="text.secondary">Configure up to 20 merchant-wide ProductVariant shortcuts. Current product pricing and tax rules are always used.</Typography></Box>
    {error ? <Alert severity="error">{error instanceof Error ? error.message : 'Quick Key request failed'}</Alert> : null}
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Stack component="form" direction={{ xs: 'column', sm: 'row' }} spacing={1} onSubmit={event => { event.preventDefault(); setSubmitted(search.trim()); }}>
        <TextField size="small" fullWidth label="Find product variant" placeholder="Search product, variant, SKU or barcode" value={search} onChange={event => setSearch(event.target.value)} disabled={!canManage} />
        <Button type="submit" variant="contained" disabled={!canManage || !search.trim()}>Search</Button>
      </Stack>
      {products.isFetching ? <CircularProgress size={22} sx={{ mt: 2 }} aria-label="Searching products" /> : null}
      {submitted && !products.isFetching ? <Stack spacing={1} sx={{ mt: 2 }}>{candidates.length === 0 ? <Typography color="text.secondary">No available variants found.</Typography> : candidates.map(({ product, variant }) => <Paper variant="outlined" key={variant.id} sx={{ p: 1.5 }}><Stack direction="row" spacing={2} alignItems="center"><Box sx={{ flex: 1 }}><Typography fontWeight={700}>{product.name} — {variant.name}</Typography><Typography variant="body2" color="text.secondary">{variant.sku} · Current price {variant.price.toFixed(2)}</Typography></Box><Button startIcon={<AddIcon />} disabled={add.isPending || configured.length >= 20} onClick={() => add.mutate(variant.id)}>Add</Button></Stack></Paper>)}</Stack> : null}
    </Paper>
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Stack spacing={1.5}>
        <Stack direction="row" justifyContent="space-between"><Typography variant="h6">Configured keys</Typography><Chip label={`${configured.length} / 20`} /></Stack>
        {keys.isLoading ? <CircularProgress aria-label="Loading Quick Keys" /> : null}
        {!keys.isLoading && configured.length === 0 ? <Alert severity="info">No quick products configured.</Alert> : null}
        {configured.map((key, index) => <Paper variant="outlined" key={key.id} sx={{ p: 1.5, opacity: key.active ? 1 : 0.7 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} alignItems={{ md: 'center' }}>
            <Box sx={{ flex: 1, minWidth: 0 }}><Typography fontWeight={700}>{key.productName} — {key.variantName}</Typography><Typography variant="body2" color="text.secondary">{key.sku} · {key.productAvailable ? 'Available' : 'Product unavailable'}</Typography></Box>
            <TextField size="small" label="Quick label" value={labels[key.id] ?? key.displayLabel ?? ''} onChange={event => setLabels(current => ({ ...current, [key.id]: event.target.value }))} disabled={!canManage} sx={{ width: { md: 190 } }} />
            <Button size="small" disabled={!canManage || update.isPending} onClick={() => update.mutate({ key, order: index, active: key.active, label: labels[key.id] ?? key.displayLabel ?? '' })}>Save label</Button>
            <Switch checked={key.active} disabled={!canManage || update.isPending || !key.productAvailable} inputProps={{ 'aria-label': `Enable ${key.productName} ${key.variantName}` }} onChange={event => update.mutate({ key, order: index, active: event.target.checked })} />
            <IconButton aria-label={`Move ${key.productName} up`} disabled={!canManage || index === 0} onClick={() => update.mutate({ key, order: index - 1, active: key.active })}><ArrowUpwardIcon /></IconButton>
            <IconButton aria-label={`Move ${key.productName} down`} disabled={!canManage || index === configured.length - 1} onClick={() => update.mutate({ key, order: index + 1, active: key.active })}><ArrowDownwardIcon /></IconButton>
            <IconButton aria-label={`Remove ${key.productName}`} disabled={!canManage || remove.isPending} onClick={() => remove.mutate(key.id)}><DeleteOutlineIcon /></IconButton>
          </Stack>
        </Paper>)}
      </Stack>
    </Paper>
  </Stack>;
}
