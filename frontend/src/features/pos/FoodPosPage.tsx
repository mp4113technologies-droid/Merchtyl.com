import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import RemoveIcon from '@mui/icons-material/Remove';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import { Alert, Box, Button, Card, CardActionArea, CardContent, Chip, CircularProgress, Divider, Grid, IconButton, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import * as React from 'react';
import { Link } from 'react-router-dom';
import { checkoutSaleCart, completeSale, getCurrentRegisterSession, getFoodServiceConfiguration, getKitchenTicket, getSaleReceipt, listActiveStoreDiscounts, listFoodMenuCategories, listFoodMenuItems, listStores, recordSalePayment, reprintKitchenTicket, reprintSaleReceipt } from '../../api/client';
import type { FoodMenuItem, KitchenTicket, PaymentMethod, ReceiptDocument, Sale } from '../../api/types';
import { getApplicationDeviceIdentifier } from '../../app/deviceIdentity';
import { useSession } from '../../app/session';
import { PaymentDialog } from './PosPages';
import { loadReceiptPrinterPreferences } from './receiptPrinter';
import { printFoodDocuments, type FoodPrintDocument, type FoodPrintStatus } from './foodOrderPrinter';
import { DiscountDialog, type OrderDiscount } from './DiscountDialog';

function money(value: number, currency = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value);
}

function completionKey() {
  return globalThis.crypto?.randomUUID?.() ?? `food-${Date.now()}`;
}

type FoodPrintStates = Record<FoodPrintDocument, { status: FoodPrintStatus; error?: string }>;

function freshPrintStates(): FoodPrintStates {
  return {
    KITCHEN_TICKET: { status: 'READY' },
    CUSTOMER_RECEIPT: { status: 'READY' }
  };
}

export function FoodPosPage() {
  const { currentUser, getValidAccessToken } = useSession();
  const [categoryId, setCategoryId] = React.useState<string | null>(null);
  const [sale, setSale] = React.useState<Sale | null>(null);
  const [cart, setCart] = React.useState<Array<{ item: FoodMenuItem; quantity: number }>>([]);
  const [paymentOpen, setPaymentOpen] = React.useState(false);
  const [discount, setDiscount] = React.useState<OrderDiscount | null>(null);
  const [discountOpen, setDiscountOpen] = React.useState(false);
  const [printStates, setPrintStates] = React.useState<FoodPrintStates>(freshPrintStates);
  const deviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);
  const permitted = currentUser?.permissions?.includes('FOOD_POS_ACCESS') ?? false;
  const current = useQuery({ queryKey: ['register-session', 'food-pos', deviceIdentifier], queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier }), enabled: permitted });
  const stores = useQuery({ queryKey: ['stores', 'food-pos'], queryFn: async () => listStores(await getValidAccessToken(), { size: 100 }), enabled: permitted });
  const store = stores.data?.content.find((candidate) => candidate.id === current.data?.storeId);
  const canDiscount = currentUser?.permissions?.includes('POS_SALE_DISCOUNT') ?? false;
  const configuration = useQuery({ queryKey: ['food-service', current.data?.storeId], queryFn: async () => getFoodServiceConfiguration(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && Boolean(current.data?.storeId) });
  const categories = useQuery({ queryKey: ['food-menu-categories', current.data?.storeId], queryFn: async () => listFoodMenuCategories(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && configuration.isSuccess && Boolean(current.data?.storeId) });
  const products = useQuery({ queryKey: ['food-menu-items', current.data?.storeId], queryFn: async () => listFoodMenuItems(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && configuration.isSuccess && Boolean(current.data?.storeId) });
  const savedDiscounts = useQuery({ queryKey: ['active-pos-discounts', current.data?.storeId], queryFn: async () => listActiveStoreDiscounts(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && canDiscount && Boolean(current.data?.storeId), staleTime: 5 * 60_000 });

  React.useEffect(() => {
    if (!categoryId && categories.data?.find(category => category.active)) setCategoryId(categories.data.find(category => category.active)?.id ?? null);
  }, [categories.data, categoryId]);

  function changeCart(update: (lines: typeof cart) => typeof cart) {
    setCart(update);
    setSale(null);
    setPaymentOpen(false);
  }
  function add(product: FoodMenuItem) {
    changeCart(lines => {
      const existing = lines.find(line => line.item.id === product.id);
      return existing
        ? lines.map(line => line.item.id === product.id ? { ...line, quantity: line.quantity + 1 } : line)
        : [...lines, { item: product, quantity: 1 }];
    });
  }
  const checkout = useMutation({
    mutationFn: async (openPayment: boolean) => {
      if (!current.data) throw new Error('Open a register before starting an order');
      const updated = await checkoutSaleCart(await getValidAccessToken(), {
        registerSessionId: current.data.id,
        saleChannel: 'POS',
        items: cart.map(line => ({ foodMenuItemId: line.item.id, quantity: line.quantity })),
        discount: discount ? (discount.definitionId ? { discountDefinitionId: discount.definitionId } : { type: discount.type, value: discount.value, reason: discount.reason || undefined }) : undefined
      });
      return { updated, openPayment };
    },
    onSuccess: ({ updated, openPayment }) => { setSale(updated); if (openPayment) setPaymentOpen(true); }
  });
  const payment = useMutation({ mutationFn: async (value: { method: PaymentMethod; amount: number; cashTendered?: number; reference?: string; notes?: string }) => recordSalePayment(await getValidAccessToken(), sale?.id ?? '', value), onSuccess: (updated) => { setSale(updated); setPaymentOpen(!updated.paymentComplete); } });
  const complete = useMutation({ mutationFn: async () => completeSale(await getValidAccessToken(), sale?.id ?? '', completionKey()), onSuccess: setSale });
  const receipt = useQuery({ queryKey: ['food-pos-receipt', sale?.id], queryFn: async () => getSaleReceipt(await getValidAccessToken(), sale?.id ?? ''), enabled: sale?.status === 'COMPLETED' });
  const kitchenTicket = useQuery({ queryKey: ['food-pos-kitchen-ticket', sale?.id], queryFn: async () => getKitchenTicket(await getValidAccessToken(), sale?.id ?? ''), enabled: sale?.status === 'COMPLETED' });
  const busy = checkout.isPending || payment.isPending || complete.isPending;
  const canManageMenu = currentUser?.permissions?.some(permission => permission === 'PRODUCT_MANAGE' || permission === 'FOOD_ORDER_UPDATE');
  const provisionalSubtotal = cart.reduce((sum, line) => sum + line.item.price * line.quantity, 0);
  const provisionalDiscount = discount
    ? Math.min(provisionalSubtotal, discount.type === 'DISCOUNT_PERCENTAGE' ? provisionalSubtotal * discount.value / 100 : discount.value)
    : 0;

  function startNewOrder() {
    setCart([]);
    setSale(null);
    setDiscount(null);
    setDiscountOpen(false);
    setPaymentOpen(false);
    setPrintStates(freshPrintStates());
    checkout.reset();
    payment.reset();
    complete.reset();
  }

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
    <Typography>{receipt.data?.receiptNumber ? `Receipt #${receipt.data.receiptNumber}` : 'Receipt loading…'}</Typography>
    <PrintState label="Kitchen Ticket" value={printStates.KITCHEN_TICKET} />
    <PrintState label="Customer Receipt" value={printStates.CUSTOMER_RECEIPT} />
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
      <Button variant="contained" disabled={!receipt.data || !kitchenTicket.data} onClick={() => void printDocuments(['KITCHEN_TICKET', 'CUSTOMER_RECEIPT'])}>Print Both</Button>
      <Button variant="outlined" disabled={!kitchenTicket.data} onClick={() => void (printStates.KITCHEN_TICKET.status === 'PRINTED' ? reprintDocument('KITCHEN_TICKET') : printDocuments(['KITCHEN_TICKET']))}>{printStates.KITCHEN_TICKET.status === 'FAILED' ? 'Retry Kitchen Ticket' : printStates.KITCHEN_TICKET.status === 'PRINTED' ? 'Reprint Kitchen Ticket' : 'Kitchen Ticket'}</Button>
      <Button variant="outlined" disabled={!receipt.data} onClick={() => void (printStates.CUSTOMER_RECEIPT.status === 'PRINTED' ? reprintDocument('CUSTOMER_RECEIPT') : printDocuments(['CUSTOMER_RECEIPT']))}>{printStates.CUSTOMER_RECEIPT.status === 'FAILED' ? 'Retry Customer Receipt' : printStates.CUSTOMER_RECEIPT.status === 'PRINTED' ? 'Reprint Customer Receipt' : 'Customer Receipt'}</Button>
      <Button onClick={startNewOrder}>New Order</Button>
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
              {(products.data ?? []).filter(product => product.categoryId === categoryId).map((product) => <Grid item xs={12} sm={6} md={4} xl={3} key={product.id}><Card variant="outlined" sx={{ height: '100%', opacity: product.available ? 1 : .55 }}><CardActionArea disabled={!product.available || busy} onClick={() => add(product)} sx={{ minHeight: 150, height: '100%' }}>{product.imageUrl ? <Box component="img" src={product.imageUrl} alt="" sx={{ width: '100%', height: 88, objectFit: 'cover' }} /> : null}<CardContent><Typography variant="h6">{product.displayName}</Typography><Typography color="primary" fontWeight={800}>{money(product.price, store?.currencyCode)}</Typography>{!product.available ? <Chip label="Sold Out" size="small" /> : null}</CardContent></CardActionArea></Card></Grid>)}
            </Grid>
          </Stack>
        </Grid>
        <Grid item xs={12} md={4} sx={{ minWidth: 0 }}>
          <Paper variant="outlined" sx={{ p: { xs: 1.5, sm: 2 }, position: { md: 'sticky' }, top: { md: 72 }, maxHeight: { md: 'calc(100dvh - 88px)' }, overflowY: { md: 'auto' } }}><Stack spacing={2}><Typography variant="h5">Order</Typography><Divider />
            {cart.map(({ item, quantity }) => <Stack key={item.id} direction={{ xs: 'column', sm: 'row', md: 'column', lg: 'row' }} alignItems={{ xs: 'stretch', sm: 'center', md: 'stretch', lg: 'center' }} spacing={1}><Box flex={1} minWidth={0}><Typography fontWeight={700}>{item.displayName}</Typography><Typography variant="body2">{quantity} × {money(item.price, store?.currencyCode)} = {money(quantity * item.price, store?.currencyCode)}</Typography></Box><Stack direction="row" alignSelf={{ xs: 'flex-end', sm: 'auto', md: 'flex-end', lg: 'auto' }}><IconButton aria-label={`Decrease ${item.displayName}`} disabled={busy || quantity <= 1} onClick={() => changeCart(lines => lines.map(line => line.item.id === item.id ? { ...line, quantity: line.quantity - 1 } : line))}><RemoveIcon /></IconButton><IconButton aria-label={`Increase ${item.displayName}`} disabled={busy} onClick={() => changeCart(lines => lines.map(line => line.item.id === item.id ? { ...line, quantity: line.quantity + 1 } : line))}><AddIcon /></IconButton><IconButton aria-label={`Remove ${item.displayName}`} disabled={busy} onClick={() => changeCart(lines => lines.filter(line => line.item.id !== item.id))}><DeleteOutlineIcon /></IconButton></Stack></Stack>)}
            {!cart.length ? <Typography color="text.secondary">Tap a product tile to begin.</Typography> : null}<Divider />
            <Stack direction="row" justifyContent="space-between"><Typography>Subtotal</Typography><Typography>{money(sale?.subtotalAmount ?? provisionalSubtotal, sale?.currencyCode ?? store?.currencyCode)}</Typography></Stack>
            <Stack direction="row" justifyContent="space-between"><Typography>{discount?.name ?? 'Discount'}{discount?.type === 'DISCOUNT_PERCENTAGE' ? ` (${discount.value}%)` : ''}</Typography><Typography>{money(-(sale?.discountAmount ?? provisionalDiscount), sale?.currencyCode ?? store?.currencyCode)}</Typography></Stack>
            {canDiscount ? <Stack spacing={1}>
              <TextField select size="small" label="Discount" value={discount?.definitionId ?? (discount ? '__custom__' : '')} disabled={!cart.length || busy} onChange={(event) => {
                if (event.target.value === '__custom__') { setDiscountOpen(true); return; }
                const selected = savedDiscounts.data?.find(value => value.id === event.target.value);
                if (selected) { setDiscount({ definitionId:selected.id,name:selected.name,type:selected.type,value:selected.value,reason:'' }); setSale(null); setPaymentOpen(false); }
              }}>
                <MenuItem value=""><em>Select discount</em></MenuItem>
                {(savedDiscounts.data ?? []).map(value => <MenuItem key={value.id} value={value.id}>{value.name} — {value.type === 'DISCOUNT_PERCENTAGE' ? `${value.value}%` : money(value.value, store?.currencyCode)}</MenuItem>)}
                <Divider /><MenuItem value="__custom__">Custom Discount</MenuItem>
              </TextField>
              {discount ? <Stack direction="row" spacing={1}>{!discount.definitionId ? <Button size="small" onClick={() => setDiscountOpen(true)}>Edit Custom Discount</Button> : null}<Button size="small" color="error" disabled={busy} onClick={() => { setDiscount(null); setSale(null); setPaymentOpen(false); }}>Remove Discount</Button></Stack> : null}
            </Stack> : null}
            <Stack direction="row" justifyContent="space-between"><Typography>Tax</Typography><Typography>{sale ? money(sale.estimatedTaxAmount, sale.currencyCode) : 'At checkout'}</Typography></Stack><Stack direction="row" justifyContent="space-between"><Typography variant="h6">Total</Typography><Typography variant="h6">{sale ? money(sale.totalAmount, sale.currencyCode) : '—'}</Typography></Stack>
            {checkout.isError ? <Alert severity="error">Restaurant checkout could not be calculated. Your order is still in the cart.</Alert> : null}
            <Stack direction="row" spacing={1}><Button fullWidth variant="outlined" disabled={!cart.length || busy || Boolean(sale?.paymentComplete)} onClick={() => checkout.mutate(false)}>{checkout.isPending ? 'Calculating…' : 'Calculate Tax'}</Button><Button fullWidth variant="contained" disabled={!cart.length || busy || Boolean(sale?.paymentComplete)} onClick={() => sale ? setPaymentOpen(true) : checkout.mutate(true)}>{checkout.isPending ? 'Calculating total…' : 'Checkout'}</Button></Stack><Button variant="contained" color="success" size="large" disabled={!sale?.paymentComplete || busy} onClick={() => complete.mutate()} sx={{ minHeight: 64 }}>Complete order</Button>
          </Stack></Paper>
        </Grid>
      </Grid>
      <PaymentDialog open={paymentOpen} sale={sale} busy={payment.isPending} onClose={() => setPaymentOpen(false)} onSubmit={(value) => payment.mutate(value)} />
      <DiscountDialog open={discountOpen} initial={discount} currencyCode={store?.currencyCode ?? 'USD'} onClose={() => setDiscountOpen(false)} onApply={(value) => { setDiscount(value); setDiscountOpen(false); setSale(null); setPaymentOpen(false); }} />
    </Stack>
  );
}

function PrintState({ label, value }: { label: string; value: { status: FoodPrintStatus; error?: string } }) {
  return <Alert severity={value.status === 'FAILED' ? 'error' : value.status === 'PRINTED' ? 'success' : 'info'}>
    {label}: {value.status === 'FAILED' ? 'Order saved, but this document could not be sent for printing. Use the reprint action to try again.' : value.status.charAt(0) + value.status.slice(1).toLowerCase()}
  </Alert>;
}
