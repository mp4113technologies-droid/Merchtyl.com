import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import RemoveIcon from '@mui/icons-material/Remove';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import { Alert, Box, Button, Card, CardActionArea, CardContent, Chip, CircularProgress, Divider, Grid, IconButton, Paper, Stack, Typography } from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import * as React from 'react';
import { Link } from 'react-router-dom';
import { addFoodMenuItemToSale, completeSale, createSaleDraft, getCurrentRegisterSession, getFoodServiceConfiguration, getKitchenTicket, getSaleReceipt, listFoodMenuCategories, listFoodMenuItems, listStores, recordSalePayment, removeSaleItem, reprintKitchenTicket, reprintSaleReceipt, updateSaleItemQuantity } from '../../api/client';
import type { FoodMenuItem, KitchenTicket, PaymentMethod, ReceiptDocument, Sale } from '../../api/types';
import { getApplicationDeviceIdentifier } from '../../app/deviceIdentity';
import { useSession } from '../../app/session';
import { PaymentDialog } from './PosPages';
import { loadReceiptPrinterPreferences } from './receiptPrinter';
import { printFoodDocuments, type FoodPrintDocument, type FoodPrintStatus } from './foodOrderPrinter';

function money(value: number, currency = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value);
}

function completionKey() {
  return globalThis.crypto?.randomUUID?.() ?? `food-${Date.now()}`;
}

export function FoodPosPage() {
  const { currentUser, getValidAccessToken } = useSession();
  const [categoryId, setCategoryId] = React.useState<string | null>(null);
  const [sale, setSale] = React.useState<Sale | null>(null);
  const [paymentOpen, setPaymentOpen] = React.useState(false);
  const [printStates, setPrintStates] = React.useState<Record<FoodPrintDocument, { status: FoodPrintStatus; error?: string }>>({
    KITCHEN_TICKET: { status: 'READY' },
    CUSTOMER_RECEIPT: { status: 'READY' }
  });
  const deviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);
  const permitted = currentUser?.permissions?.includes('FOOD_POS_ACCESS') ?? false;
  const current = useQuery({ queryKey: ['register-session', 'food-pos', deviceIdentifier], queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier }), enabled: permitted });
  const stores = useQuery({ queryKey: ['stores', 'food-pos'], queryFn: async () => listStores(await getValidAccessToken(), { size: 100 }), enabled: permitted });
  const store = stores.data?.content.find((candidate) => candidate.id === current.data?.storeId);
  const configuration = useQuery({ queryKey: ['food-service', current.data?.storeId], queryFn: async () => getFoodServiceConfiguration(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && Boolean(current.data?.storeId) });
  const categories = useQuery({ queryKey: ['food-menu-categories', current.data?.storeId], queryFn: async () => listFoodMenuCategories(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && configuration.isSuccess && Boolean(current.data?.storeId) });
  const products = useQuery({ queryKey: ['food-menu-items', current.data?.storeId], queryFn: async () => listFoodMenuItems(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && configuration.isSuccess && Boolean(current.data?.storeId) });

  React.useEffect(() => {
    if (!categoryId && categories.data?.find(category => category.active)) setCategoryId(categories.data.find(category => category.active)?.id ?? null);
  }, [categories.data, categoryId]);

  async function draft(token: string) {
    if (sale?.status === 'DRAFT') return sale;
    if (!current.data) throw new Error('Open a register before starting an order');
    return createSaleDraft(token, { registerSessionId: current.data.id, saleChannel: 'POS' });
  }

  const add = useMutation({ mutationFn: async (product: FoodMenuItem) => { const token = await getValidAccessToken(); const active = await draft(token); return addFoodMenuItemToSale(token, current.data?.storeId ?? '', product.id, active.id, 1); }, onSuccess: setSale });
  const quantity = useMutation({ mutationFn: async ({ itemId, value }: { itemId: string; value: number }) => updateSaleItemQuantity(await getValidAccessToken(), sale?.id ?? '', itemId, { quantity: value }), onSuccess: setSale });
  const remove = useMutation({ mutationFn: async (itemId: string) => removeSaleItem(await getValidAccessToken(), sale?.id ?? '', itemId), onSuccess: setSale });
  const payment = useMutation({ mutationFn: async (value: { method: PaymentMethod; amount: number; cashTendered?: number; reference?: string; notes?: string }) => recordSalePayment(await getValidAccessToken(), sale?.id ?? '', value), onSuccess: (updated) => { setSale(updated); setPaymentOpen(!updated.paymentComplete); } });
  const complete = useMutation({ mutationFn: async () => completeSale(await getValidAccessToken(), sale?.id ?? '', completionKey()), onSuccess: setSale });
  const receipt = useQuery({ queryKey: ['food-pos-receipt', sale?.id], queryFn: async () => getSaleReceipt(await getValidAccessToken(), sale?.id ?? ''), enabled: sale?.status === 'COMPLETED' });
  const kitchenTicket = useQuery({ queryKey: ['food-pos-kitchen-ticket', sale?.id], queryFn: async () => getKitchenTicket(await getValidAccessToken(), sale?.id ?? ''), enabled: sale?.status === 'COMPLETED' });
  const busy = add.isPending || quantity.isPending || remove.isPending || payment.isPending || complete.isPending;
  const canManageMenu = currentUser?.permissions?.some(permission => permission === 'PRODUCT_MANAGE' || permission === 'FOOD_ORDER_UPDATE');

  if (currentUser && !permitted) return <Alert severity="error">FOOD_POS_ACCESS is required.</Alert>;
  if (configuration.isError) return <Alert severity="error">This store is not enabled for FOOD_SERVICE.</Alert>;
  if (!current.isLoading && !current.data) return <Alert severity="info" action={<Button component={Link} to="/register/open">Open register</Button>}>Open a register session to use Food POS.</Alert>;
  async function printDocuments(documents: FoodPrintDocument[]) {
    if (!receipt.data || !kitchenTicket.data) return;
    const preferences = loadReceiptPrinterPreferences();
    const selected = new Set(documents);
    await printFoodDocuments(kitchenTicket.data, receipt.data.document, preferences,
      (document) => selected.has(document) && printStates[document].status !== 'PRINTED',
      (document, status, error) => setPrintStates((currentStates) => ({ ...currentStates, [document]: { status, error } })));
  }

  async function reprintDocument(document: FoodPrintDocument) {
    if (!sale) return;
    const token = await getValidAccessToken();
    const preferences = loadReceiptPrinterPreferences();
    const ticket: KitchenTicket = document === 'KITCHEN_TICKET'
      ? await reprintKitchenTicket(token, sale.id)
      : kitchenTicket.data!;
    const customerReceipt: ReceiptDocument = document === 'CUSTOMER_RECEIPT'
      ? (await reprintSaleReceipt(token, sale.id)).document
      : receipt.data!.document;
    setPrintStates((states) => ({ ...states, [document]: { status: 'READY' } }));
    await printFoodDocuments(ticket, customerReceipt, preferences, (candidate) => candidate === document,
      (candidate, status, error) => setPrintStates((states) => ({ ...states, [candidate]: { status, error } })));
  }

  if (sale?.status === 'COMPLETED') return <Stack spacing={3} sx={{ maxWidth: 700 }}>
    <Typography variant="h4">Order completed</Typography>
    <Alert severity="success">Order {sale.foodOrderToken ?? ''} completed successfully. Printing does not affect payment or inventory.</Alert>
    <Typography variant="h3" fontWeight={900}>TOKEN {sale.foodOrderToken ?? '…'}</Typography>
    <Typography>Receipt: {receipt.data?.receiptNumber ?? 'Loading…'}</Typography>
    <PrintState label="Kitchen Ticket" value={printStates.KITCHEN_TICKET} />
    <PrintState label="Customer Receipt" value={printStates.CUSTOMER_RECEIPT} />
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
      <Button variant="contained" disabled={!receipt.data || !kitchenTicket.data} onClick={() => void printDocuments(['KITCHEN_TICKET', 'CUSTOMER_RECEIPT'])}>Print Both</Button>
      <Button variant="outlined" disabled={!kitchenTicket.data} onClick={() => void (printStates.KITCHEN_TICKET.status === 'PRINTED' ? reprintDocument('KITCHEN_TICKET') : printDocuments(['KITCHEN_TICKET']))}>{printStates.KITCHEN_TICKET.status === 'FAILED' ? 'Retry Kitchen Ticket' : printStates.KITCHEN_TICKET.status === 'PRINTED' ? 'Reprint Kitchen Ticket' : 'Kitchen Ticket'}</Button>
      <Button variant="outlined" disabled={!receipt.data} onClick={() => void (printStates.CUSTOMER_RECEIPT.status === 'PRINTED' ? reprintDocument('CUSTOMER_RECEIPT') : printDocuments(['CUSTOMER_RECEIPT']))}>{printStates.CUSTOMER_RECEIPT.status === 'FAILED' ? 'Retry Customer Receipt' : printStates.CUSTOMER_RECEIPT.status === 'PRINTED' ? 'Reprint Customer Receipt' : 'Customer Receipt'}</Button>
      <Button onClick={() => setSale(null)}>New Order</Button>
    </Stack>
  </Stack>;

  return (
    <Stack spacing={2} sx={{ minHeight: 'calc(100dvh - 88px)', minWidth: 0 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ minWidth: 0 }}><RestaurantIcon color="primary" fontSize="large" /><Box sx={{ minWidth: 0 }}><Typography variant="h4">{configuration.data?.kitchenDisplayName ?? 'Restaurant / Kitchen POS'}</Typography><Typography color="text.secondary" noWrap>{store?.name}</Typography></Box></Stack>
      {(current.isLoading || configuration.isLoading) ? <CircularProgress aria-label="Loading Food POS" /> : null}
      {categories.isSuccess && products.isSuccess && categories.data.length === 0 && products.data.length === 0 ? (
        <Alert severity="info" action={canManageMenu ? <Button component={Link} to="/food-menu">Create Restaurant Menu</Button> : undefined}>
          {canManageMenu ? 'No Restaurant Menu has been configured for this store.' : 'No Restaurant Menu has been configured for this store. Ask a manager to configure the Restaurant Menu.'}
        </Alert>
      ) : null}
      <Grid container spacing={2} sx={{ flex: 1 }}>
        <Grid item xs={12} md={8} sx={{ minWidth: 0 }}>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" aria-label="Food categories">
              {(categories.data ?? []).filter(category => category.active).map(category => <Button key={category.id} variant={categoryId === category.id ? 'contained' : 'outlined'} onClick={() => setCategoryId(category.id)} sx={{ minHeight: 64, minWidth: 120 }}>{category.name}</Button>)}
            </Stack>
            <Grid container spacing={2} aria-label="Food products">
              {(products.data ?? []).filter(product => product.categoryId === categoryId).map((product) => <Grid item xs={12} sm={6} md={4} xl={3} key={product.id}><Card variant="outlined" sx={{ height: '100%', opacity: product.available ? 1 : .55 }}><CardActionArea disabled={!product.available || busy} onClick={() => add.mutate(product)} sx={{ minHeight: 150, height: '100%' }}>{product.imageUrl ? <Box component="img" src={product.imageUrl} alt="" sx={{ width: '100%', height: 88, objectFit: 'cover' }} /> : null}<CardContent><Typography variant="h6">{product.displayName}</Typography><Typography color="primary" fontWeight={800}>{money(product.price, store?.currencyCode)}</Typography>{!product.available ? <Chip label="Sold Out" size="small" /> : null}</CardContent></CardActionArea></Card></Grid>)}
            </Grid>
          </Stack>
        </Grid>
        <Grid item xs={12} md={4} sx={{ minWidth: 0 }}>
          <Paper variant="outlined" sx={{ p: { xs: 1.5, sm: 2 }, position: { md: 'sticky' }, top: { md: 72 }, maxHeight: { md: 'calc(100dvh - 88px)' }, overflowY: { md: 'auto' } }}><Stack spacing={2}><Typography variant="h5">Order</Typography><Divider />
            {sale?.items.map((item) => <Stack key={item.id} direction={{ xs: 'column', sm: 'row', md: 'column', lg: 'row' }} alignItems={{ xs: 'stretch', sm: 'center', md: 'stretch', lg: 'center' }} spacing={1}><Box flex={1} minWidth={0}><Typography fontWeight={700}>{item.productName}</Typography><Typography variant="body2">{item.quantity} × {money(item.unitPrice, sale.currencyCode)} = {money(item.lineTotal, sale.currencyCode)}</Typography></Box><Stack direction="row" alignSelf={{ xs: 'flex-end', sm: 'auto', md: 'flex-end', lg: 'auto' }}><IconButton aria-label={`Decrease ${item.productName}`} disabled={busy || item.quantity <= 1} onClick={() => quantity.mutate({ itemId: item.id, value: item.quantity - 1 })}><RemoveIcon /></IconButton><IconButton aria-label={`Increase ${item.productName}`} disabled={busy} onClick={() => quantity.mutate({ itemId: item.id, value: item.quantity + 1 })}><AddIcon /></IconButton><IconButton aria-label={`Remove ${item.productName}`} disabled={busy} onClick={() => remove.mutate(item.id)}><DeleteOutlineIcon /></IconButton></Stack></Stack>)}
            {!sale?.items.length ? <Typography color="text.secondary">Tap a product tile to begin.</Typography> : null}<Divider />
            <Stack direction="row" justifyContent="space-between"><Typography>Subtotal</Typography><Typography>{money(sale?.subtotalAmount ?? 0, sale?.currencyCode ?? store?.currencyCode)}</Typography></Stack><Stack direction="row" justifyContent="space-between"><Typography>Tax</Typography><Typography>{money(sale?.estimatedTaxAmount ?? 0, sale?.currencyCode ?? store?.currencyCode)}</Typography></Stack><Stack direction="row" justifyContent="space-between"><Typography variant="h6">Total</Typography><Typography variant="h6">{money(sale?.totalAmount ?? 0, sale?.currencyCode ?? store?.currencyCode)}</Typography></Stack>
            <Button variant="contained" size="large" disabled={!sale?.items.length || busy || sale.paymentComplete} onClick={() => setPaymentOpen(true)} sx={{ minHeight: 64 }}>Pay</Button><Button variant="contained" color="success" size="large" disabled={!sale?.paymentComplete || busy} onClick={() => complete.mutate()} sx={{ minHeight: 64 }}>Complete order</Button>
          </Stack></Paper>
        </Grid>
      </Grid>
      <PaymentDialog open={paymentOpen} sale={sale} busy={payment.isPending} onClose={() => setPaymentOpen(false)} onSubmit={(value) => payment.mutate(value)} />
    </Stack>
  );
}

function PrintState({ label, value }: { label: string; value: { status: FoodPrintStatus; error?: string } }) {
  return <Alert severity={value.status === 'FAILED' ? 'error' : value.status === 'PRINTED' ? 'success' : 'info'}>
    {label}: {value.status === 'FAILED' ? `Print failed${value.error ? ` — ${value.error}` : ''}` : value.status.charAt(0) + value.status.slice(1).toLowerCase()}
  </Alert>;
}
