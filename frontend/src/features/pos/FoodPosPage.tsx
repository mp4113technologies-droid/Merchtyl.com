import AddIcon from '@mui/icons-material/Add';
import ClearAllIcon from '@mui/icons-material/ClearAll';
import CloseIcon from '@mui/icons-material/Close';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import RemoveIcon from '@mui/icons-material/Remove';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import SearchIcon from '@mui/icons-material/Search';
import PauseCircleOutlineIcon from '@mui/icons-material/PauseCircleOutline';
import RestoreIcon from '@mui/icons-material/Restore';
import { Alert, Box, Button, Card, CardActionArea, CardContent, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Divider, FormControlLabel, IconButton, InputAdornment, MenuItem, Paper, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { acknowledgeKitchenPrintJob, cancelPhoneOrder, checkoutSaleCart, claimKitchenPrintJob, claimPhoneOrder, completeSale, confirmPhoneOrder, getCurrentRegisterSession, getFoodServiceConfiguration, getKitchenTicket, getSaleReceipt, holdSale, listActiveStoreDiscounts, listFoodMenuCategories, listFoodMenuItems, listKitchenPrintJobs, listPickupOrderHistory, listPickupOrders, listSales, listStores, printKitchenJobNow, recordSalePayment, reprintKitchenTicket, reprintSaleReceipt, resumeSale, updateFoodServiceConfiguration, updateKitchenOrderStatus } from '../../api/client';
import type { FoodComponentSelectionState, FoodMenuItem, KitchenPrintDispatch, KitchenTicket, PaymentMethod, ReceiptDocument, Sale } from '../../api/types';
import { getApplicationDeviceIdentifier } from '../../app/deviceIdentity';
import { useSession } from '../../app/session';
import { posTokens } from '../../app/theme';
import { PaymentDialog } from './PosPages';
import { loadReceiptPrinterPreferences } from './receiptPrinter';
import { printFoodDocuments, printKitchenTicket, type FoodPrintDocument, type FoodPrintStatus } from './foodOrderPrinter';
import { DiscountDialog, type OrderDiscount } from './DiscountDialog';
import { SecureTill } from './SecureTill';
import { bestMultiBuyPromotion } from './multiBuyPricing';

function money(value: number, currency = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value);
}

function currencySymbol(currency = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency })
    .formatToParts(0)
    .find(part => part.type === 'currency')?.value ?? '$';
}

function completionKey() {
  return globalThis.crypto?.randomUUID?.() ?? `food-${Date.now()}`;
}

type FoodPrintStates = Record<FoodPrintDocument, { status: FoodPrintStatus; error?: string }>;
type ComponentChoice = { componentId:string; state:FoodComponentSelectionState; name:string; priceAdjustment:number };
type CustomItemTaxTreatment = 'TAXABLE' | 'NON_TAXABLE';
type FoodCartLine = { key:string; item:FoodMenuItem; quantity:number; lineType?:'CUSTOM_ITEM'; customItemTaxTreatment?:CustomItemTaxTreatment; variantId?:string; variantName?:string; unitPrice:number; modifierOptionIds:string[]; modifierNames:string[]; componentChoices:ComponentChoice[]; preparationInstructions:string };

function customFoodItem(): FoodMenuItem {
  return { id:'custom-food-item',storeId:'',categoryId:'',categoryName:'',productId:null,productName:null,displayName:'Custom Food Item',description:null,price:0,inventoryTracked:false,madeToOrder:true,displayOrder:0,available:true,imageUrl:null,variants:[],modifierGroups:[],components:[],version:0 };
}

function freshPrintStates(): FoodPrintStates {
  return {
    KITCHEN_TICKET: { status: 'READY' },
    CUSTOMER_RECEIPT: { status: 'READY' }
  };
}

export function FoodPosPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { currentUser, getValidAccessToken } = useSession();
  const [categoryId, setCategoryId] = React.useState<string | null>(null);
  const [sale, setSale] = React.useState<Sale | null>(null);
  const [cart, setCart] = React.useState<FoodCartLine[]>([]);
  const [configuring, setConfiguring] = React.useState<FoodMenuItem | null>(null);
  const [chosenVariant, setChosenVariant] = React.useState('');
  const [chosenModifiers, setChosenModifiers] = React.useState<string[]>([]);
  const [chosenComponents, setChosenComponents] = React.useState<Record<string,FoodComponentSelectionState|undefined>>({});
  const [preparationInstructions,setPreparationInstructions]=React.useState('');
  const [paymentOpen, setPaymentOpen] = React.useState(false);
  const [discount, setDiscount] = React.useState<OrderDiscount | null>(null);
  const [discountOpen, setDiscountOpen] = React.useState(false);
  const [search, setSearch] = React.useState('');
  const [selectedItemId, setSelectedItemId] = React.useState<string | null>(null);
  const [heldOrdersOpen, setHeldOrdersOpen] = React.useState(false);
  const [phoneOrderOpen, setPhoneOrderOpen] = React.useState(false);
  const [pickupOrdersOpen, setPickupOrdersOpen] = React.useState(false);
  const [printSettingsOpen,setPrintSettingsOpen]=React.useState(false);
  const [autoPrintAsap,setAutoPrintAsap]=React.useState(false);
  const [autoPrintScheduled,setAutoPrintScheduled]=React.useState(false);
  const [printLeadMinutes,setPrintLeadMinutes]=React.useState(10);
  const [pickupOrdersView, setPickupOrdersView] = React.useState<'active' | 'history'>('active');
  const [selectedPickupOrder, setSelectedPickupOrder] = React.useState<Sale | null>(null);
  const [phoneCustomerName, setPhoneCustomerName] = React.useState('');
  const [phoneNumber, setPhoneNumber] = React.useState('');
  const [pickupAsap, setPickupAsap] = React.useState(true);
  const [pickupLocalDateTime, setPickupLocalDateTime] = React.useState('');
  const [orderNotes, setOrderNotes] = React.useState('');
  const [customTaxTreatment,setCustomTaxTreatment]=React.useState<CustomItemTaxTreatment|null>(null);
  const [customAmount,setCustomAmount]=React.useState('');
  const [customQuantity,setCustomQuantity]=React.useState(1);
  const [printStates, setPrintStates] = React.useState<FoodPrintStates>(freshPrintStates);
  const restoredSessionRef = React.useRef<string | null>(null);
  const kitchenPrintInFlight = React.useRef(false);
  const deviceIdentifier = React.useMemo(() => getApplicationDeviceIdentifier(), []);
  const permitted = currentUser?.permissions?.includes('FOOD_POS_ACCESS') ?? false;
  const current = useQuery({ queryKey: ['register-session', 'food-pos', deviceIdentifier], queryFn: async () => getCurrentRegisterSession(await getValidAccessToken(), { deviceIdentifier }), enabled: permitted });
  const stores = useQuery({ queryKey: ['stores', 'food-pos'], queryFn: async () => listStores(await getValidAccessToken(), { size: 100 }), enabled: permitted });
  const store = stores.data?.content.find((candidate) => candidate.id === current.data?.storeId);
  const canDiscount = currentUser?.permissions?.includes('POS_SALE_DISCOUNT') ?? false;
  const canAddCustomItem = currentUser?.permissions?.includes('POS_CUSTOM_ITEM') ?? false;
  const configuration = useQuery({ queryKey: ['food-service', current.data?.storeId], queryFn: async () => getFoodServiceConfiguration(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && Boolean(current.data?.storeId) });
  const categories = useQuery({ queryKey: ['food-menu-categories', current.data?.storeId], queryFn: async () => listFoodMenuCategories(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && configuration.isSuccess && Boolean(current.data?.storeId) });
  const products = useQuery({ queryKey: ['food-menu-items', current.data?.storeId], queryFn: async () => listFoodMenuItems(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && configuration.isSuccess && Boolean(current.data?.storeId) });
  const savedDiscounts = useQuery({ queryKey: ['active-pos-discounts', current.data?.storeId], queryFn: async () => listActiveStoreDiscounts(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && Boolean(current.data?.storeId), staleTime: 5 * 60_000 });
  const heldOrders = useQuery({ queryKey: ['sales', 'held', current.data?.id], queryFn: async () => listSales(await getValidAccessToken(), { registerSessionId: current.data?.id, status: 'HELD', size: 50 }), enabled: permitted && heldOrdersOpen && Boolean(current.data?.id) });
  const pickupOrders = useQuery({ queryKey: ['sales', 'phone-pickup', current.data?.storeId], queryFn: async () => listPickupOrders(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && pickupOrdersOpen && Boolean(current.data?.storeId), refetchInterval: 15_000 });
  const pickupHistory = useQuery({ queryKey: ['sales', 'phone-pickup-history', current.data?.id], queryFn: async () => listPickupOrderHistory(await getValidAccessToken(), current.data?.id ?? ''), enabled: permitted && pickupOrdersOpen && pickupOrdersView === 'history' && Boolean(current.data?.id) });
  const kitchenPrintJobs = useQuery({ queryKey: ['kitchen-print-jobs', current.data?.storeId], queryFn: async () => listKitchenPrintJobs(await getValidAccessToken(), current.data?.storeId ?? ''), enabled: permitted && pickupOrdersOpen && Boolean(current.data?.storeId), refetchInterval: 15_000 });
  React.useEffect(()=>{if(configuration.data){setAutoPrintAsap(configuration.data.autoPrintAsapKitchenTickets??false);setAutoPrintScheduled(configuration.data.autoPrintScheduledKitchenTickets??false);setPrintLeadMinutes(configuration.data.scheduledKitchenPrintLeadMinutes??10);}},[configuration.data]);
  const savePrintSettings=useMutation({mutationFn:async()=>updateFoodServiceConfiguration(await getValidAccessToken(),current.data?.storeId??'',{autoPrintAsapKitchenTickets:autoPrintAsap,autoPrintScheduledKitchenTickets:autoPrintScheduled,scheduledKitchenPrintLeadMinutes:printLeadMinutes}),onSuccess:async()=>{setPrintSettingsOpen(false);await queryClient.invalidateQueries({queryKey:['food-service',current.data?.storeId]});}});

  async function deliverKitchenDispatch(dispatch: KitchenPrintDispatch) {
    const token=await getValidAccessToken();
    try {
      await printKitchenTicket(dispatch.ticket, loadReceiptPrinterPreferences());
      await acknowledgeKitchenPrintJob(token, dispatch.job.id, deviceIdentifier, true);
    } catch (error) {
      const detail=error instanceof Error?error.message:String(error);
      try { await acknowledgeKitchenPrintJob(token, dispatch.job.id, deviceIdentifier, false, detail); } catch { /* The stale-claim recovery job keeps this retryable. */ }
      throw error;
    } finally {
      await queryClient.invalidateQueries({queryKey:['kitchen-print-jobs', current.data?.storeId]});
    }
  }

  React.useEffect(() => {
    if (!permitted || !current.data?.storeId || current.data.registerType !== 'FOOD_SERVICE') return;
    let stopped=false;
    const poll=async()=>{
      if(stopped||kitchenPrintInFlight.current)return;
      kitchenPrintInFlight.current=true;
      try { const dispatch=await claimKitchenPrintJob(await getValidAccessToken(),current.data!.storeId,deviceIdentifier); if(dispatch)await deliverKitchenDispatch(dispatch); }
      catch { /* Failed transport is persisted by acknowledgement and shown in Pickup Orders. */ }
      finally { kitchenPrintInFlight.current=false; }
    };
    void poll();
    const timer=window.setInterval(()=>void poll(),10_000);
    return()=>{stopped=true;window.clearInterval(timer);};
  },[permitted,current.data?.storeId,current.data?.registerType,deviceIdentifier]);

  React.useEffect(() => {
    if (current.data?.registerType === 'RETAIL') navigate('/pos', { replace: true });
  }, [current.data?.registerType, navigate]);

  React.useEffect(() => {
    if (!current.data || restoredSessionRef.current === current.data.id) return;
    restoredSessionRef.current = current.data.id;
    try {
      const raw = localStorage.getItem(`merchtyl.food-pos-state:${current.data.id}`);
      if (!raw) return;
      const saved = JSON.parse(raw) as { cart?: typeof cart; sale?: Sale | null; discount?: OrderDiscount | null };
      if (Array.isArray(saved.cart)) setCart(saved.cart.map((line,index) => ({...line,key:line.key??`${line.item.id}:restored-${index}`,unitPrice:line.unitPrice??line.item.price,modifierOptionIds:line.modifierOptionIds??[],modifierNames:line.modifierNames??[],componentChoices:line.componentChoices??[],preparationInstructions:line.preparationInstructions??''})));
      if (saved.sale) setSale(saved.sale);
      if (saved.discount) setDiscount(saved.discount);
    } catch { /* A corrupt recovery snapshot must never prevent POS access. */ }
  }, [current.data]);

  React.useEffect(() => {
    if (!current.data || restoredSessionRef.current !== current.data.id) return;
    const key = `merchtyl.food-pos-state:${current.data.id}`;
    if (!cart.length && !sale && !discount) localStorage.removeItem(key);
    else localStorage.setItem(key, JSON.stringify({ cart, sale, discount }));
  }, [cart, sale, discount, current.data]);

  React.useEffect(() => {
    if (!categoryId && categories.data?.find(category => category.active)) setCategoryId(categories.data.find(category => category.active)?.id ?? null);
  }, [categories.data, categoryId]);

  function changeCart(update: (lines: typeof cart) => typeof cart) {
    setCart(update);
    setSale(null);
    setPaymentOpen(false);
  }
  function add(product: FoodMenuItem) {
    if ((product.variants?.length ?? 0) > 0 || (product.modifierGroups?.length ?? 0) > 0 || (product.components?.some(value=>value.active) ?? false)) { setConfiguring(product); setChosenVariant(''); setChosenModifiers([]); setChosenComponents({}); setPreparationInstructions(''); return; }
    addConfigured(product);
  }
  function addConfigured(product: FoodMenuItem) {
    changeCart(lines => {
      const variant=product.variants?.find(value=>value.id===chosenVariant);
      const options=(product.modifierGroups??[]).flatMap(group=>group.options).filter(option=>chosenModifiers.includes(option.id));
      const modifierNames=[...(product.modifierGroups??[])].sort((left,right)=>left.displayOrder-right.displayOrder).flatMap(group=>group.options.filter(option=>chosenModifiers.includes(option.id)).map(option=>`${group.name}: ${option.name}`));
      const componentChoices=(product.components??[]).filter(component=>component.active&&chosenComponents[component.id]).map(component=>({componentId:component.id,state:chosenComponents[component.id]!,name:component.name,priceAdjustment:chosenComponents[component.id]==='EXTRA'?component.extraPrice:0}));
      const key=[product.id,variant?.id??'',...options.map(option=>option.id).sort(),...componentChoices.map(value=>`${value.componentId}-${value.state}`).sort(),preparationInstructions.trim()].join(':');
      setSelectedItemId(key);
      const existing = lines.find(line => line.key === key);
      return existing
        ? lines.map(line => line.key === key ? { ...line, quantity: line.quantity + 1 } : line)
        : [...lines, { key,item: product, quantity: 1,variantId:variant?.id,variantName:variant?.name,unitPrice:(variant?.price??product.price)+options.reduce((sum,option)=>sum+option.priceAdjustment,0)+componentChoices.reduce((sum,value)=>sum+value.priceAdjustment,0),modifierOptionIds:options.map(option=>option.id),modifierNames,componentChoices,preparationInstructions:preparationInstructions.trim() }];
    });
    setConfiguring(null);
  }
  function openCustomItem(taxTreatment:CustomItemTaxTreatment){setCustomTaxTreatment(taxTreatment);setCustomAmount('');setCustomQuantity(1);}
  function selectModifier(group:NonNullable<FoodMenuItem['modifierGroups']>[number],optionId:string,checked:boolean){
    setChosenModifiers(values=>{
      const groupOptionIds=new Set(group.options.map(option=>option.id));
      if(!checked)return values.filter(id=>id!==optionId);
      const outsideGroup=values.filter(id=>!groupOptionIds.has(id));
      const withinGroup=values.filter(id=>groupOptionIds.has(id));
      if(group.maximumSelections===1)return [...outsideGroup,optionId];
      if(withinGroup.length>=group.maximumSelections)return values;
      return [...values,optionId];
    });
  }
  function addCustomItem(){
    const amount=Number(customAmount);
    if(!customTaxTreatment||!Number.isFinite(amount)||amount<=0)return;
    const item=customFoodItem();
    changeCart(lines=>[...lines,{key:`custom:${crypto.randomUUID()}`,item,lineType:'CUSTOM_ITEM',customItemTaxTreatment:customTaxTreatment,quantity:customQuantity,unitPrice:amount,modifierOptionIds:[],modifierNames:[],componentChoices:[],preparationInstructions:''}]);
    setCustomTaxTreatment(null);
  }
  async function calculateOrder() {
      if (!current.data) throw new Error('Open a register before starting an order');
      return checkoutSaleCart(await getValidAccessToken(), {
        registerSessionId: current.data.id,
        saleChannel: 'POS',
        items: cart.map(line => line.lineType==='CUSTOM_ITEM'
          ? {lineType:'CUSTOM_ITEM' as const,description:'Custom Food Item',unitPrice:line.unitPrice,taxTreatment:line.customItemTaxTreatment,quantity:line.quantity}
          : { foodMenuItemId: line.item.id, foodMenuItemVariantId:line.variantId,foodMenuModifierOptionIds:line.modifierOptionIds.length?line.modifierOptionIds:undefined,foodMenuComponentSelections:line.componentChoices.length?line.componentChoices.map(({componentId,state})=>({componentId,state})):undefined,preparationInstructions:line.preparationInstructions||undefined, quantity: line.quantity }),
        discount: discount ? (discount.definitionId ? { discountDefinitionId: discount.definitionId } : { type: discount.type, value: discount.value, reason: discount.reason || undefined }) : undefined
      });
  }
  const checkout = useMutation({
    mutationFn: async (openPayment: boolean) => {
      const updated = await calculateOrder();
      return { updated, openPayment };
    },
    onSuccess: ({ updated, openPayment }) => { setSale(updated); if (openPayment) setPaymentOpen(true); }
  });
  const hold = useMutation({
    mutationFn: async () => {
      const active = sale ?? await calculateOrder();
      return holdSale(await getValidAccessToken(), active.id);
    },
    onSuccess: async () => {
      startNewOrder();
      await queryClient.invalidateQueries({ queryKey: ['sales', 'held'] });
      setHeldOrdersOpen(true);
    }
  });
  const resume = useMutation({
    mutationFn: async (saleId: string) => resumeSale(await getValidAccessToken(), saleId),
    onSuccess: async (resumed) => {
      const menuItems = products.data ?? [];
      const restored = resumed.items.map((saleItem, index) => {
        if(saleItem.lineType==='CUSTOM_ITEM') return {key:`custom:${saleItem.id}`,item:customFoodItem(),lineType:'CUSTOM_ITEM' as const,customItemTaxTreatment:saleItem.customItemTaxTreatment??'NON_TAXABLE',quantity:saleItem.quantity,unitPrice:saleItem.unitPrice,modifierOptionIds:[],modifierNames:[],componentChoices:[],preparationInstructions:''};
        const item = menuItems.find(candidate => candidate.id === saleItem.foodMenuItemId)
          ?? menuItems.find(candidate => candidate.productId === saleItem.productId);
        if (!item) return null;
        const modifierNames = saleItem.foodMenuModifiers ?? [];
        const modifierOptionIds = (item.modifierGroups ?? []).flatMap(group => group.options)
          .filter(option => modifierNames.some(snapshot=>snapshot===`+ ${option.name}`||snapshot===option.name||snapshot.endsWith(`: ${option.name}`))).map(option => option.id);
        const componentChoices=(saleItem.foodMenuComponents??[]).map(value=>({...value}));
        return { key: `${item.id}:${saleItem.foodMenuItemVariantId ?? ''}:${modifierOptionIds.sort().join(':')}:${componentChoices.map(value=>`${value.componentId}-${value.state}`).sort().join(':')}:held-${index}`, item,
          quantity: saleItem.quantity, variantId: saleItem.foodMenuItemVariantId ?? undefined,
          variantName: saleItem.foodMenuItemVariantName ?? undefined, unitPrice: saleItem.unitPrice,
          modifierOptionIds, modifierNames,componentChoices,preparationInstructions:saleItem.externalReference??'' };
      }).filter((line): line is NonNullable<typeof line> => line !== null);
      setCart(restored);
      setSale(resumed);
      setDiscount(resumed.discountType && resumed.discountValue != null ? { definitionId: resumed.discountDefinitionId ?? undefined,
        name: resumed.discountName ?? 'Discount', type: resumed.discountType, value: resumed.discountValue,
        reason: resumed.discountReason ?? '' } : null);
      setHeldOrdersOpen(false);
      setSelectedItemId(restored[0]?.key ?? null);
      await queryClient.invalidateQueries({ queryKey: ['sales', 'held'] });
    }
  });
  const payment = useMutation({ mutationFn: async (value: { method: PaymentMethod; amount: number; cashTendered?: number; reference?: string; notes?: string }) => recordSalePayment(await getValidAccessToken(), sale?.id ?? '', value), onSuccess: async (updated) => { setSale(updated); setPaymentOpen(!updated.paymentComplete); await queryClient.invalidateQueries({ queryKey: ['sales', 'phone-pickup'] }); } });
  const complete = useMutation({ mutationFn: async () => completeSale(await getValidAccessToken(), sale?.id ?? '', completionKey()), onSuccess: async (updated) => { setSale(updated); await Promise.all([queryClient.invalidateQueries({ queryKey: ['sales', 'phone-pickup'] }), queryClient.invalidateQueries({ queryKey: ['sales', 'phone-pickup-history'] })]); } });
  const confirmPhone = useMutation({
    mutationFn: async () => {
      const priced = sale ?? await calculateOrder();
      const confirmed = await confirmPhoneOrder(await getValidAccessToken(), priced.id, { customerName: phoneCustomerName.trim(), phoneNumber: phoneNumber.trim() || undefined, asap: pickupAsap, pickupLocalDateTime: pickupAsap ? undefined : pickupLocalDateTime, orderNotes: orderNotes.trim() || undefined });
      if(!kitchenPrintInFlight.current){
        kitchenPrintInFlight.current=true;
        try { const dispatch=await claimKitchenPrintJob(await getValidAccessToken(),confirmed.storeId,deviceIdentifier); if(dispatch)await deliverKitchenDispatch(dispatch); }
        catch { /* Confirmation remains successful; the durable job remains visible and retryable. */ }
        finally { kitchenPrintInFlight.current=false; }
      }
      return confirmed;
    },
    onSuccess: async () => {
      setPhoneOrderOpen(false);
      startNewOrder();
      await queryClient.invalidateQueries({ queryKey: ['sales', 'phone-pickup'] });
      setPickupOrdersOpen(true);
    }
  });
  const claimPhone = useMutation({ mutationFn: async (order: Sale) => claimPhoneOrder(await getValidAccessToken(), order.id, current.data?.id ?? ''), onSuccess: (claimed) => { setSale(claimed); setCart([]); setSelectedPickupOrder(null); setPickupOrdersOpen(false); setPaymentOpen(true); } });
  const cancelPhone = useMutation({ mutationFn: async ({ order, rebuild }: { order: Sale; rebuild: boolean }) => { const cancelled = await cancelPhoneOrder(await getValidAccessToken(), order.id, current.data?.id ?? ''); const ticket = await getKitchenTicket(await getValidAccessToken(), order.id); await printKitchenTicket(ticket, loadReceiptPrinterPreferences()); return { cancelled, order, rebuild }; }, onSuccess: async ({ order, rebuild }) => { setSelectedPickupOrder(null); if (rebuild) { startNewOrder(); setPhoneCustomerName(order.phoneCustomerName ?? ''); setPhoneNumber(order.phoneNumber ?? ''); setPickupAsap(order.pickupAsap ?? true); setPickupLocalDateTime(''); setOrderNotes(order.orderNotes ?? ''); setPickupOrdersOpen(false); } await queryClient.invalidateQueries({ queryKey: ['sales', 'phone-pickup'] }); } });
  const kitchenProgress = useMutation({ mutationFn: async ({ order, status }: { order: Sale; status: 'IN_PROGRESS' | 'READY' | 'COMPLETED' }) => updateKitchenOrderStatus(await getValidAccessToken(), order.id, status), onSuccess: async (updated) => { if (updated.kitchenStatus === 'COMPLETED') { setSelectedPickupOrder(null); setPickupOrdersView('history'); } else setSelectedPickupOrder(updated); await Promise.all([queryClient.invalidateQueries({ queryKey: ['sales', 'phone-pickup'] }), queryClient.invalidateQueries({ queryKey: ['sales', 'phone-pickup-history'] })]); } });
  const printNow = useMutation({ mutationFn: async (order:Sale) => { const dispatch=await printKitchenJobNow(await getValidAccessToken(),order.id,deviceIdentifier); await deliverKitchenDispatch(dispatch); return dispatch.job; } });
  const receipt = useQuery({ queryKey: ['food-pos-receipt', sale?.id], queryFn: async () => getSaleReceipt(await getValidAccessToken(), sale?.id ?? ''), enabled: sale?.status === 'COMPLETED' });
  const kitchenTicket = useQuery({ queryKey: ['food-pos-kitchen-ticket', sale?.id], queryFn: async () => getKitchenTicket(await getValidAccessToken(), sale?.id ?? ''), enabled: sale?.status === 'COMPLETED' });
  const busy = checkout.isPending || payment.isPending || complete.isPending || hold.isPending || resume.isPending || confirmPhone.isPending || claimPhone.isPending;
  const canManageMenu = currentUser?.permissions?.some(permission => permission === 'PRODUCT_MANAGE' || permission === 'FOOD_ORDER_UPDATE');
  const provisionalSubtotal = cart.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0);
  const automaticPromotion=bestMultiBuyPromotion(savedDiscounts.data??[],'FOOD_SERVICE',cart.map(line=>({id:line.key,quantity:line.quantity,unitPrice:line.unitPrice,targets:{MENU_ITEM:line.item.id,MENU_ITEM_VARIANT:line.variantId,MENU_CATEGORY:line.item.categoryId}})));
  const provisionalDiscount = discount
    ? Math.min(provisionalSubtotal, discount.type === 'DISCOUNT_PERCENTAGE' ? provisionalSubtotal * discount.value / 100 : discount.value)
    : automaticPromotion?.amount??0;
  const activeCategories = (categories.data ?? []).filter(category => category.active);
  const normalizedSearch = search.trim().toLocaleLowerCase();
  const visibleProducts = (products.data ?? [])
    .filter(product => normalizedSearch ? true : product.categoryId === categoryId)
    .filter(product => !normalizedSearch || product.displayName.toLocaleLowerCase().includes(normalizedSearch))
    .sort((left, right) => left.displayOrder - right.displayOrder || left.displayName.localeCompare(right.displayName));
  const depositTotal = sale?.containerDepositTotal
    ?? sale?.items.reduce((sum, item) => sum + (item.depositTotal ?? 0), 0)
    ?? 0;
  const restaurantHeldOrders = heldOrders.data?.content.filter(held => held.items.some(item => item.foodMenuItemId || item.lineType==='CUSTOM_ITEM')) ?? [];

  function startNewOrder() {
    setCart([]);
    setSale(null);
    setDiscount(null);
    setDiscountOpen(false);
    setPaymentOpen(false);
    setPrintStates(freshPrintStates());
    setSelectedItemId(null);
    checkout.reset();
    payment.reset();
    complete.reset();
    if (current.data) localStorage.removeItem(`merchtyl.food-pos-state:${current.data.id}`);
  }

  if (currentUser && !permitted) return <Alert severity="error">FOOD_POS_ACCESS is required.</Alert>;
  if (configuration.isError) return <Stack spacing={2}><Typography variant="h4">Restaurant / Kitchen POS</Typography><Alert severity="error">This store is not enabled for FOOD_SERVICE.</Alert></Stack>;
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
    <Box data-testid="restaurant-pos-shell" sx={{ height: 'calc(100dvh - 88px)', minHeight: 0, minWidth: 0, overflow: 'hidden', color: posTokens.colors.text }}>
      <Box sx={{ height: '100%', minHeight: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(340px, 38%) minmax(0, 1fr)' }, gap: 1.25 }}>
        <Paper component="section" aria-label="Current order" variant="outlined" sx={{ minWidth: 0, minHeight: 0, overflow: 'hidden', display: 'grid', gridTemplateRows: 'auto minmax(0, 1fr) auto', borderRadius: 2 }}>
          <Box sx={{ px: 1.75, py: 1.25, bgcolor: posTokens.colors.navy, color: '#fff' }}>
            <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
              <Box><Typography variant="overline" sx={{ lineHeight: 1, color: 'rgba(255,255,255,.7)' }}>Current order</Typography><Typography variant="h6" sx={{ lineHeight: 1.25 }}>{cart.reduce((sum, line) => sum + line.quantity, 0)} items</Typography></Box>
              {current.data ? <SecureTill session={current.data} storeName={store?.name} busy={busy} /> : null}
            </Stack>
          </Box>

          <Stack spacing={1} sx={{ minHeight: 0, overflowY: 'auto', p: 1.25 }}>
            {cart.map(({ key, item, quantity, lineType,customItemTaxTreatment,unitPrice, variantName, modifierNames,componentChoices,preparationInstructions }) => {
              const selected = selectedItemId === key;
              return <Paper key={key} component="article" variant="outlined" onClick={() => setSelectedItemId(key)} sx={{ p: 1.25, borderWidth: selected ? 2 : 1, borderColor: selected ? 'primary.main' : 'divider', bgcolor: selected ? posTokens.colors.blueLight : '#fff', cursor: 'pointer' }}>
                <Stack spacing={0.75}>
                  <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start"><Box minWidth={0}><Typography fontWeight={800}>{item.displayName}</Typography>{lineType==='CUSTOM_ITEM'?<Typography variant="caption" color="text.secondary">{customItemTaxTreatment==='TAXABLE'?'Taxable':'Non-Taxable'}</Typography>:null}{variantName?<Typography variant="body2">{variantName}</Typography>:null}{componentChoices.filter(value=>value.state==='REMOVED').map(value=><Typography key={`${value.componentId}-removed`} variant="caption" display="block" color="error.main" fontWeight={800}>NO {value.name.toUpperCase()}</Typography>)}{modifierNames.map(name=><Typography key={name} variant="caption" display="block" color="text.secondary">+ {name}</Typography>)}{componentChoices.filter(value=>value.state==='EXTRA').map(value=><Typography key={`${value.componentId}-extra`} variant="caption" display="block" color="text.secondary">+ Extra {value.name}</Typography>)}{preparationInstructions?<Typography variant="caption" display="block" fontWeight={700}>Note: {preparationInstructions}</Typography>:null}</Box><Typography fontWeight={800} noWrap>{money(quantity * unitPrice, store?.currencyCode)}</Typography></Stack>
                  <Typography variant="body2" color="text.secondary">{quantity} × {money(unitPrice, store?.currencyCode)} = {money(quantity * unitPrice, store?.currencyCode)}</Typography>
                  <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
                    <Stack direction="row" alignItems="center" spacing={0.5}>
                      <IconButton aria-label={`Decrease ${item.displayName}`} disabled={busy || quantity <= 1} onClick={(event) => { event.stopPropagation(); changeCart(lines => lines.map(line => line.key === key ? { ...line, quantity: line.quantity - 1 } : line)); }} sx={{ width: 44, height: 44, border: '1px solid', borderColor: 'divider', bgcolor: '#fff' }}><RemoveIcon /></IconButton>
                      <Typography aria-label={`${item.displayName} quantity`} fontWeight={900} sx={{ minWidth: 32, textAlign: 'center', fontSize: 18 }}>{quantity}</Typography>
                      <IconButton aria-label={`Increase ${item.displayName}`} disabled={busy} onClick={(event) => { event.stopPropagation(); changeCart(lines => lines.map(line => line.key === key ? { ...line, quantity: line.quantity + 1 } : line)); }} sx={{ width: 44, height: 44, border: '1px solid', borderColor: 'divider', bgcolor: '#fff' }}><AddIcon /></IconButton>
                    </Stack>
                    <Button color="error" startIcon={<DeleteOutlineIcon />} disabled={busy} onClick={(event) => { event.stopPropagation(); changeCart(lines => lines.filter(line => line.key !== key)); if (selected) setSelectedItemId(null); }} sx={{ minHeight: 44 }}>Remove</Button>
                  </Stack>
                </Stack>
              </Paper>;
            })}
            {!cart.length ? <Box sx={{ minHeight: 180, display: 'grid', placeItems: 'center', textAlign: 'center', px: 3 }}><Box><RestaurantIcon sx={{ fontSize: 44, color: 'text.disabled', mb: 1 }} /><Typography fontWeight={700}>Tap a product tile to begin.</Typography><Typography variant="body2" color="text.secondary">Your current order stays visible here.</Typography></Box></Box> : null}
          </Stack>

          <Box sx={{ borderTop: '1px solid', borderColor: 'divider', p: 1.5, bgcolor: '#fff' }}>
            <Stack spacing={0.65}>
              <MoneyRow label="Subtotal" value={money(sale?.subtotalAmount ?? provisionalSubtotal, sale?.currencyCode ?? store?.currencyCode)} />
              <MoneyRow label={`${discount?.name ?? automaticPromotion?.definition.name ?? 'Discount'}${discount?.type === 'DISCOUNT_PERCENTAGE' ? ` (${discount.value}%)` : ''}`} value={money(-(sale?.discountAmount ?? provisionalDiscount), sale?.currencyCode ?? store?.currencyCode)} />
              {depositTotal > 0 ? <MoneyRow label="Container deposits" value={money(depositTotal, sale?.currencyCode ?? store?.currencyCode)} /> : null}
              <MoneyRow label="Tax" value={sale ? money(sale.estimatedTaxAmount, sale.currencyCode) : 'At checkout'} />
              <Divider sx={{ my: 0.35 }} />
              <MoneyRow strong label="TOTAL" value={sale ? money(sale.totalAmount, sale.currencyCode) : '—'} />
              {checkout.isError ? <Alert severity="error" sx={{ py: 0 }}>Restaurant checkout could not be calculated. Your order is still in the cart.</Alert> : null}
              {complete.isError ? <Alert severity="error" sx={{ py: 0 }}>The order could not be completed. Payment and order details were preserved.</Alert> : null}
              {hold.isError ? <Alert severity="error" sx={{ py: 0 }}>This order could not be held. It remains in the cart.</Alert> : null}
              {resume.isError ? <Alert severity="error" sx={{ py: 0 }}>This held order could not be restored.</Alert> : null}
              {canDiscount ? <TextField select size="small" label="Discount" value={discount?.definitionId ?? (discount ? '__custom__' : '')} disabled={!cart.length || busy} onChange={(event) => {
                if (event.target.value === '__custom__') { setDiscountOpen(true); return; }
                const chosen = savedDiscounts.data?.find(value => value.id === event.target.value);
                if (chosen && chosen.type !== 'MULTI_BUY_FIXED_PRICE') { setDiscount({ definitionId:chosen.id,name:chosen.name,type:chosen.type,value:chosen.value,reason:'' }); setSale(null); setPaymentOpen(false); }
              }}>
                <MenuItem value=""><em>Select discount</em></MenuItem>
                {(savedDiscounts.data ?? []).filter(value=>value.type!=='MULTI_BUY_FIXED_PRICE').map(value => <MenuItem key={value.id} value={value.id}>{value.name} — {value.type === 'DISCOUNT_PERCENTAGE' ? `${value.value}%` : money(value.value, store?.currencyCode)}</MenuItem>)}
                <Divider /><MenuItem value="__custom__">Custom Discount</MenuItem>
              </TextField> : null}
              {discount ? <Stack direction="row" spacing={0.5}>{!discount.definitionId ? <Button size="small" onClick={() => setDiscountOpen(true)}>Edit Custom Discount</Button> : null}<Button size="small" color="error" disabled={busy} onClick={() => { setDiscount(null); setSale(null); setPaymentOpen(false); }}>Remove Discount</Button></Stack> : null}
              <Stack direction="row" spacing={0.75}>
                <Button variant="outlined" disabled={!cart.length || busy || Boolean(sale?.paymentComplete)} onClick={() => hold.mutate()} startIcon={<PauseCircleOutlineIcon />} sx={{ minHeight: 46 }}>Hold</Button>
                <Button variant="outlined" disabled={!cart.length || busy} onClick={() => { changeCart(() => []); setDiscount(null); setSelectedItemId(null); }} startIcon={<ClearAllIcon />} sx={{ minHeight: 46 }}>Clear</Button>
                <Button variant="outlined" disabled={!cart.length || busy || Boolean(sale?.paymentComplete)} onClick={() => checkout.mutate(false)} sx={{ minHeight: 46 }}>{checkout.isPending ? 'Calculating…' : 'Calculate Tax'}</Button>
                <Button aria-label="Checkout" fullWidth variant="contained" disabled={!cart.length || busy || Boolean(sale?.paymentComplete)} onClick={() => sale ? setPaymentOpen(true) : checkout.mutate(true)} sx={{ minHeight: 52, fontWeight: 900 }}>{checkout.isPending ? 'Calculating…' : `Checkout${sale ? ` ${money(sale.totalAmount, sale.currencyCode)}` : ''}`}</Button>
              </Stack>
              <Button variant="contained" color="success" disabled={!sale?.paymentComplete || busy} onClick={() => complete.mutate()} sx={{ minHeight: 52, fontWeight: 800 }}>Complete order</Button>
            </Stack>
          </Box>
        </Paper>

        <Paper component="section" aria-label="Restaurant menu" variant="outlined" sx={{ minWidth: 0, minHeight: 0, overflow: 'hidden', display: 'grid', gridTemplateRows: 'auto auto minmax(0, 1fr)', borderRadius: 2 }}>
          <Box sx={{ px: 1.5, pt: 1.25, pb: 1, borderBottom: '1px solid', borderColor: 'divider', bgcolor: '#fff' }}>
            <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1} sx={{ mb: 1 }}><Box minWidth={0}><Typography variant="h5" fontWeight={850} noWrap>{configuration.data?.kitchenDisplayName ?? 'Restaurant / Kitchen POS'}</Typography><Typography variant="body2" color="text.secondary" noWrap>{store?.name} · {current.data?.assignedCashierDisplayName ?? currentUser?.displayName ?? 'Cashier'}</Typography></Box><Stack direction="row" spacing={1} alignItems="center">{currentUser?.permissions?.includes('STORE_UPDATE')?<Button variant="text" onClick={()=>setPrintSettingsOpen(true)}>Print Settings</Button>:null}<Button variant="outlined" startIcon={<RestoreIcon />} onClick={() => setHeldOrdersOpen(true)} sx={{ minHeight: 44 }}>Held Orders</Button><Button variant="outlined" onClick={() => setPickupOrdersOpen(true)} sx={{ minHeight: 44 }}>Pickup Orders</Button><Button variant="contained" disabled={!cart.length || busy} onClick={() => setPhoneOrderOpen(true)} sx={{ minHeight: 44 }}>Phone Order</Button>{(current.isLoading || configuration.isLoading) ? <CircularProgress size={26} aria-label="Loading Food POS" /> : null}</Stack></Stack>
            <Stack direction="row" spacing={0.75} aria-label="Food categories" sx={{ overflowX: 'auto', pb: 0.5, scrollbarWidth: 'thin' }}>
              {activeCategories.map(category => <Button key={category.id} variant={categoryId === category.id ? 'contained' : 'outlined'} onClick={() => setCategoryId(category.id)} sx={{ minHeight: 48, minWidth: 112, flexShrink: 0, fontWeight: 800 }}>{category.name}</Button>)}
            </Stack>
          </Box>
          <Box sx={{ px: 1.5, py: 1, borderBottom: '1px solid', borderColor: 'divider', bgcolor: posTokens.colors.muted }}><Stack direction="row" spacing={1}><TextField fullWidth size="small" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search menu…" inputProps={{ 'aria-label': 'Search menu' }} InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> }} sx={{ maxWidth: 420, bgcolor: '#fff' }} />{canAddCustomItem?<><Button variant="outlined" startIcon={<AddIcon/>} onClick={()=>openCustomItem('TAXABLE')} sx={{whiteSpace:'nowrap'}}>Taxable Item</Button><Button variant="outlined" startIcon={<AddIcon/>} onClick={()=>openCustomItem('NON_TAXABLE')} sx={{whiteSpace:'nowrap'}}>Non-Taxable Item</Button></>:null}</Stack></Box>
          <Box sx={{ minHeight: 0, overflowY: 'auto', p: 1.5, bgcolor: posTokens.colors.muted }}>
            {categories.isSuccess && products.isSuccess && categories.data.length === 0 && products.data.length === 0 ? <Alert severity="info" action={canManageMenu ? <Button component={Link} to="/food-menu">Create Restaurant Menu</Button> : undefined}>{canManageMenu ? 'No Restaurant Menu has been configured for this store.' : 'No Restaurant Menu has been configured for this store. Ask a manager to configure the Restaurant Menu.'}</Alert> : null}
            <Box aria-label="Food products" sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(138px, 1fr))', gap: 1.25 }}>
              {visibleProducts.map((product) => {
                const hasVariants = Boolean(product.variants?.length);
                const availableVariants = product.variants?.filter(variant => variant.available) ?? [];
                const available = product.available && (!hasVariants || availableVariants.length > 0);
                return <Card key={product.id} variant="outlined" sx={{ minWidth: 0, opacity: available ? 1 : .62, borderRadius: 2, overflow: 'hidden' }}><CardActionArea disabled={!available || busy} onClick={() => add(product)} sx={{ minHeight: 128, height: '100%', display: 'flex', alignItems: 'stretch', flexDirection: 'column', textAlign: 'left' }}>{product.imageUrl ? <Box component="img" src={product.imageUrl} alt="" sx={{ width: '100%', height: 58, objectFit: 'cover' }} /> : null}<CardContent sx={{ width: '100%', p: '14px !important', display: 'flex', flexDirection: 'column', flex: 1 }}><Typography fontWeight={850} sx={{ lineHeight: 1.25, overflowWrap: 'anywhere' }}>{product.displayName}</Typography><Box sx={{ flex: 1 }} />{product.description ? <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: '-webkit-box', WebkitLineClamp: 1, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{product.description}</Typography> : null}<Stack direction="row" alignItems="center" justifyContent="space-between" spacing={0.5} sx={{ mt: 0.75 }}><Typography color="primary" fontWeight={900}>{availableVariants.length ? `From ${money(Math.min(...availableVariants.map(variant => variant.price)), store?.currencyCode)}` : money(product.price, store?.currencyCode)}</Typography>{!available ? <Chip label="Sold Out" color="warning" size="small" /> : null}</Stack></CardContent></CardActionArea></Card>;
              })}
            </Box>
            {products.isSuccess && visibleProducts.length === 0 && activeCategories.length > 0 ? <Typography color="text.secondary" textAlign="center" sx={{ py: 6 }}>{search ? 'No menu items match your search.' : 'No menu items are available in this category.'}</Typography> : null}
          </Box>
        </Paper>
      </Box>
      <Dialog open={Boolean(configuring)} onClose={() => setConfiguring(null)} fullWidth maxWidth="sm">
        <DialogTitle>Customize {configuring?.displayName}</DialogTitle>
        <DialogContent><Stack spacing={2} sx={{pt:1}}>
          {(configuring?.variants?.length??0)>0?<Box><Typography fontWeight={800} mb={1}>Choose a variant</Typography><Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">{configuring?.variants?.filter(value=>value.available).map(value=><Button key={value.id} variant={chosenVariant===value.id?'contained':'outlined'} onClick={()=>setChosenVariant(value.id)} sx={{minHeight:52}}>{value.name} · {money(value.price,store?.currencyCode)}</Button>)}</Stack></Box>:null}
          {(configuring?.components?.filter(value=>value.active&&value.includedByDefault).length??0)>0?<Box><Typography fontWeight={800}>Included</Typography><Stack spacing={1} mt={1}>{configuring?.components?.filter(value=>value.active&&value.includedByDefault).map(component=><Box key={component.id} sx={{display:'flex',alignItems:'center',justifyContent:'space-between',gap:1}}><Typography>{component.name}</Typography>{component.allowExtra?<Stack direction="row" spacing={0.5}><Button size="small" variant={!chosenComponents[component.id]?'contained':'outlined'} onClick={()=>setChosenComponents(value=>({...value,[component.id]:undefined}))}>Normal</Button>{component.removable?<Button size="small" color="error" variant={chosenComponents[component.id]==='REMOVED'?'contained':'outlined'} onClick={()=>setChosenComponents(value=>({...value,[component.id]:'REMOVED'}))}>No</Button>:null}<Button size="small" variant={chosenComponents[component.id]==='EXTRA'?'contained':'outlined'} onClick={()=>setChosenComponents(value=>({...value,[component.id]:'EXTRA'}))}>Extra +{money(component.extraPrice,store?.currencyCode)}</Button></Stack>:<FormControlLabel control={<Checkbox checked={chosenComponents[component.id]!=='REMOVED'} disabled={!component.removable} onChange={(_,checked)=>setChosenComponents(value=>({...value,[component.id]:checked?undefined:'REMOVED'}))}/>} label={component.removable?'Included':'Always included'}/>}</Box>)}</Stack></Box>:null}
          {[...(configuring?.modifierGroups??[])].sort((left,right)=>left.displayOrder-right.displayOrder).map(group=>{const selectedCount=group.options.filter(option=>chosenModifiers.includes(option.id)).length;return <Box key={group.id}><Typography fontWeight={800}>{group.name}{group.minimumSelections>0?' *':''}</Typography><Typography variant="caption" color="text.secondary">{group.minimumSelections===group.maximumSelections?`Choose ${group.maximumSelections}`:`Choose ${group.minimumSelections}–${group.maximumSelections}`}</Typography><Stack>{group.options.filter(option=>option.available).map(option=><FormControlLabel key={option.id} control={<Checkbox checked={chosenModifiers.includes(option.id)} disabled={!chosenModifiers.includes(option.id)&&selectedCount>=group.maximumSelections&&group.maximumSelections>1} onChange={(_,checked)=>selectModifier(group,option.id,checked)}/>} label={`${option.name}${option.priceAdjustment?` +${money(option.priceAdjustment,store?.currencyCode)}`:''}`}/>)}</Stack></Box>})}
          <TextField label="Kitchen notes (optional)" value={preparationInstructions} onChange={event=>setPreparationInstructions(event.target.value)} inputProps={{maxLength:255}} multiline minRows={2}/>
          {configuring?<Typography fontWeight={800}>Item total: {money((configuring.variants?.find(value=>value.id===chosenVariant)?.price??configuring.price)+(configuring.modifierGroups??[]).flatMap(group=>group.options).filter(option=>chosenModifiers.includes(option.id)).reduce((sum,option)=>sum+option.priceAdjustment,0)+(configuring.components??[]).filter(component=>chosenComponents[component.id]==='EXTRA').reduce((sum,component)=>sum+component.extraPrice,0),store?.currencyCode)}</Typography>:null}
        </Stack></DialogContent>
        <DialogActions><Button onClick={()=>setConfiguring(null)}>Cancel</Button><Button variant="contained" disabled={!configuring||Boolean(configuring.variants?.length&&!chosenVariant)||Boolean(configuring.modifierGroups?.some(group=>{const count=group.options.filter(option=>chosenModifiers.includes(option.id)).length;return count<group.minimumSelections||count>group.maximumSelections;}))} onClick={()=>configuring&&addConfigured(configuring)}>Add to Order</Button></DialogActions>
      </Dialog>
      <Dialog open={Boolean(customTaxTreatment)} onClose={()=>setCustomTaxTreatment(null)} maxWidth={false} slotProps={{paper:{sx:{width:430,maxWidth:'calc(100vw - 32px)',m:{xs:1.5,sm:2},height:'auto',borderRadius:2.5}}}}>
        <DialogTitle sx={{display:'flex',alignItems:'center',justifyContent:'space-between',gap:1,p:'18px 20px 10px',fontWeight:850}}>{customTaxTreatment==='NON_TAXABLE'?'Non-Taxable Item':'Taxable Item'}<IconButton aria-label="Close custom item" onClick={()=>setCustomTaxTreatment(null)} size="small"><CloseIcon/></IconButton></DialogTitle>
        <Box component="form" onSubmit={event=>{event.preventDefault();if(Number.isFinite(Number(customAmount))&&Number(customAmount)>0)addCustomItem();}}>
          <DialogContent sx={{px:2.5,pt:'8px !important',pb:2}}><Stack spacing={2}>
            <TextField autoFocus fullWidth label="Amount" type="number" placeholder="0.00" value={customAmount} onChange={event=>setCustomAmount(event.target.value)} inputProps={{min:0.01,step:0.01,inputMode:'decimal'}} InputProps={{startAdornment:<InputAdornment position="start">{currencySymbol(store?.currencyCode)}</InputAdornment>}} />
            <Box><Typography fontWeight={700} mb={0.75}>Quantity</Typography><Stack direction="row" alignItems="center" spacing={1}><IconButton aria-label="Decrease custom item quantity" disabled={customQuantity<=1} onClick={()=>setCustomQuantity(value=>Math.max(1,value-1))} sx={{border:'1px solid',borderColor:'divider',width:44,height:44}}><RemoveIcon/></IconButton><Typography aria-label="Custom item quantity" fontWeight={900} sx={{minWidth:40,textAlign:'center'}}>{customQuantity}</Typography><IconButton aria-label="Increase custom item quantity" onClick={()=>setCustomQuantity(value=>value+1)} sx={{border:'1px solid',borderColor:'divider',width:44,height:44}}><AddIcon/></IconButton></Stack></Box>
          </Stack></DialogContent>
          <Divider/>
          <DialogActions sx={{px:2.5,py:1.75,gap:1}}><Button variant="outlined" onClick={()=>setCustomTaxTreatment(null)}>Cancel</Button><Button type="submit" variant="contained" disabled={!Number.isFinite(Number(customAmount))||Number(customAmount)<=0}>Add to Order</Button></DialogActions>
        </Box>
      </Dialog>
      <PaymentDialog open={paymentOpen} sale={sale} busy={payment.isPending} onClose={() => setPaymentOpen(false)} onSubmit={(value) => payment.mutate(value)} />
      <DiscountDialog open={discountOpen} initial={discount} currencyCode={store?.currencyCode ?? 'USD'} onClose={() => setDiscountOpen(false)} onApply={(value) => { setDiscount(value); setDiscountOpen(false); setSale(null); setPaymentOpen(false); }} />
      <Dialog open={phoneOrderOpen} onClose={confirmPhone.isPending ? undefined : () => setPhoneOrderOpen(false)} fullWidth maxWidth="xs">
        <Box component="form" onSubmit={(event) => { event.preventDefault(); if (phoneCustomerName.trim() && (pickupAsap || pickupLocalDateTime)) confirmPhone.mutate(); }}>
          <DialogTitle>Phone Order</DialogTitle>
          <DialogContent><Stack spacing={1.5} sx={{ pt: 1 }}>
            <TextField autoFocus required label="Customer Name" value={phoneCustomerName} onChange={(event) => setPhoneCustomerName(event.target.value)} inputProps={{ maxLength: 120 }} />
            <TextField label="Phone Number" value={phoneNumber} onChange={(event) => setPhoneNumber(event.target.value)} inputProps={{ maxLength: 40 }} />
            <TextField select label="Pickup" value={pickupAsap ? 'ASAP' : 'SCHEDULED'} onChange={(event) => setPickupAsap(event.target.value === 'ASAP')}><MenuItem value="ASAP">ASAP</MenuItem><MenuItem value="SCHEDULED">Scheduled Time</MenuItem></TextField>
            {!pickupAsap ? <TextField required label="Scheduled Pickup" type="datetime-local" value={pickupLocalDateTime} onChange={(event) => setPickupLocalDateTime(event.target.value)} InputLabelProps={{ shrink: true }} /> : null}
            <TextField label="Order Notes" value={orderNotes} onChange={(event) => setOrderNotes(event.target.value)} multiline minRows={2} inputProps={{ maxLength: 1000 }} />
            {confirmPhone.isError ? <Alert severity="error">The phone order could not be confirmed. Check the pickup time and try again.</Alert> : null}
            <Alert severity="info">No payment will be recorded. The order will be sent to the kitchen immediately.</Alert>
          </Stack></DialogContent>
          <DialogActions><Button onClick={() => setPhoneOrderOpen(false)} disabled={confirmPhone.isPending}>Cancel</Button><Button type="submit" variant="contained" disabled={confirmPhone.isPending || !phoneCustomerName.trim() || (!pickupAsap && !pickupLocalDateTime)}>{confirmPhone.isPending ? 'Confirming…' : 'Confirm Phone Order'}</Button></DialogActions>
        </Box>
      </Dialog>
      <Dialog open={pickupOrdersOpen} onClose={() => { if (!claimPhone.isPending) { setPickupOrdersOpen(false); setSelectedPickupOrder(null); } }} fullWidth maxWidth="md">
        <DialogTitle>Pickup Orders</DialogTitle>
        <Tabs value={pickupOrdersView} onChange={(_event, value: 'active' | 'history') => { setPickupOrdersView(value); setSelectedPickupOrder(null); }} aria-label="Pickup order views" sx={{ px: 3 }}>
          <Tab value="active" label="Active" />
          <Tab value="history" label="Today’s History" />
        </Tabs>
        <DialogContent dividers>
          {!selectedPickupOrder ? <Stack spacing={1}>
            {(pickupOrdersView === 'active' ? pickupOrders : pickupHistory).isLoading ? <CircularProgress aria-label="Loading pickup orders" /> : null}
            {pickupOrdersView === 'active' && pickupOrders.isSuccess && pickupOrders.data.length === 0 ? <Alert severity="info">There are no active pickup orders.</Alert> : null}
            {pickupOrdersView === 'history' && pickupHistory.isSuccess && pickupHistory.data.length === 0 ? <Alert severity="info">No pickup orders were completed during the current BusinessDay.</Alert> : null}
            {(pickupOrdersView === 'active' ? pickupOrders.data ?? [] : pickupHistory.data ?? []).map((order) => { const job=kitchenPrintJobs.data?.find(value=>value.orderId===order.id); return <Paper key={order.id} variant="outlined" sx={{ p: 1.5 }}><Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2}><Box><Typography fontWeight={850}>{order.pickupAsap ? 'ASAP' : new Date(order.pickupAt ?? '').toLocaleTimeString([], { hour: 'numeric', minute: '2-digit', timeZone: store?.timezone })} · {order.phoneCustomerName} · #{order.foodOrderToken}</Typography><Typography variant="body2">{money(order.totalAmount, order.currencyCode)} · <strong>{kitchenStatusLabel(order)}</strong> · {paymentStatusLabel(order)}</Typography>{job?<Typography variant="caption" color={job.status==='FAILED'?'error.main':'text.secondary'}>Kitchen print: {job.status==='SCHEDULED'?new Date(job.scheduledAt).toLocaleTimeString([], {hour:'numeric',minute:'2-digit',timeZone:store?.timezone}):job.status==='PRINTED'?'Printed':job.status==='FAILED'?'Print failed':job.status==='DISPATCHED'?'Sending to kitchen':job.status}</Typography>:null}</Box><Button variant={pickupOrdersView === 'active' ? 'contained' : 'outlined'} onClick={() => setSelectedPickupOrder(order)}>Open</Button></Stack></Paper>; })}
          </Stack> : <Stack spacing={2}>
            <Box><Typography variant="h6" fontWeight={900}>Order #{selectedPickupOrder.foodOrderToken}</Typography><Typography>{selectedPickupOrder.phoneCustomerName}{selectedPickupOrder.phoneNumber ? ` · ${selectedPickupOrder.phoneNumber}` : ''}</Typography><Typography>Pickup: {selectedPickupOrder.pickupAsap ? 'ASAP' : new Date(selectedPickupOrder.pickupAt ?? '').toLocaleString([], { timeZone: store?.timezone })}</Typography><Typography>Kitchen: {kitchenStatusLabel(selectedPickupOrder)} · Payment: {paymentStatusLabel(selectedPickupOrder)}</Typography>{(() => { const job=kitchenPrintJobs.data?.find(value=>value.orderId===selectedPickupOrder.id); return job?<Typography color={job.status==='FAILED'?'error.main':'text.secondary'}>Print: {job.status}{job.status==='SCHEDULED'?` at ${new Date(job.scheduledAt).toLocaleTimeString([], {hour:'numeric',minute:'2-digit',timeZone:store?.timezone})}`:''}{job.lastError?` — ${job.lastError}`:''}</Typography>:null; })()}</Box>
            <Divider />
            {selectedPickupOrder.items.map(item => <Box key={item.id}><Stack direction="row" justifyContent="space-between"><Typography fontWeight={800}>{item.quantity} × {item.productName}{item.foodMenuItemVariantName ? ` — ${item.foodMenuItemVariantName}` : ''}</Typography><Typography>{money(item.lineTotal, selectedPickupOrder.currencyCode)}</Typography></Stack>{item.foodMenuModifiers?.map(value => <Typography key={value} variant="caption" display="block">{value}</Typography>)}{item.foodMenuComponents?.map(value => <Typography key={value.componentId} variant="caption" display="block">{value.state === 'REMOVED' ? 'NO' : 'EXTRA'} {value.name}</Typography>)}</Box>)}
            <Divider /><MoneyRow label="Subtotal" value={money(selectedPickupOrder.subtotalAmount, selectedPickupOrder.currencyCode)} /><MoneyRow label="Discount" value={money(-selectedPickupOrder.discountAmount, selectedPickupOrder.currencyCode)} /><MoneyRow label="Tax" value={money(selectedPickupOrder.estimatedTaxAmount, selectedPickupOrder.currencyCode)} /><MoneyRow strong label="Total" value={money(selectedPickupOrder.totalAmount, selectedPickupOrder.currencyCode)} />
            {selectedPickupOrder.orderNotes ? <Alert severity="info">Order Notes: {selectedPickupOrder.orderNotes}</Alert> : null}
            {pickupOrdersView === 'active' ? <Alert severity="info">To change a confirmed order safely, cancel and rebuild it. The kitchen receives a cancellation ticket before the replacement is confirmed.</Alert> : null}
            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap"><Button onClick={() => setSelectedPickupOrder(null)}>Back</Button>{pickupOrdersView==='active'&&!kitchenPrintJobs.data?.some(value=>value.orderId===selectedPickupOrder.id&&['PRINTED','DISPATCHED','CANCELLED'].includes(value.status))?<Button variant="outlined" onClick={()=>printNow.mutate(selectedPickupOrder)} disabled={printNow.isPending}>{kitchenPrintJobs.data?.some(value=>value.orderId===selectedPickupOrder.id&&value.status==='FAILED')?'Retry Print':'Print Now'}</Button>:null}{pickupOrdersView === 'active' && selectedPickupOrder.kitchenStatus === 'PENDING' ? <Button variant="outlined" onClick={() => kitchenProgress.mutate({ order: selectedPickupOrder, status: 'IN_PROGRESS' })}>Start Preparing</Button> : null}{pickupOrdersView === 'active' && selectedPickupOrder.kitchenStatus !== 'READY' ? <Button variant="outlined" color="success" onClick={() => kitchenProgress.mutate({ order: selectedPickupOrder, status: 'READY' })}>Mark Ready</Button> : null}{pickupOrdersView === 'active' && paymentStatusLabel(selectedPickupOrder) !== 'PAID' ? <><Button color="error" onClick={() => cancelPhone.mutate({ order: selectedPickupOrder, rebuild: false })} disabled={cancelPhone.isPending}>Cancel Order</Button><Button color="warning" onClick={() => cancelPhone.mutate({ order: selectedPickupOrder, rebuild: true })} disabled={cancelPhone.isPending}>Cancel &amp; Rebuild</Button><Button variant="contained" onClick={() => claimPhone.mutate(selectedPickupOrder)} disabled={claimPhone.isPending}>Pay Order</Button></> : null}{pickupOrdersView === 'active' && selectedPickupOrder.kitchenStatus === 'READY' && paymentStatusLabel(selectedPickupOrder) === 'PAID' ? <Button variant="contained" color="success" onClick={() => kitchenProgress.mutate({ order: selectedPickupOrder, status: 'COMPLETED' })} disabled={kitchenProgress.isPending}>Mark Completed</Button> : null}</Stack>
          </Stack>}
        </DialogContent>
        <DialogActions><Button onClick={() => { setPickupOrdersOpen(false); setSelectedPickupOrder(null); }}>Close</Button></DialogActions>
      </Dialog>
      <Dialog open={printSettingsOpen} onClose={()=>!savePrintSettings.isPending&&setPrintSettingsOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Scheduled Kitchen Printing</DialogTitle>
        <DialogContent dividers><Stack spacing={2}>
          <FormControlLabel control={<Checkbox checked={autoPrintAsap} onChange={event=>setAutoPrintAsap(event.target.checked)}/>} label="ASAP orders: Print kitchen ticket immediately"/>
          <FormControlLabel control={<Checkbox checked={autoPrintScheduled} onChange={event=>setAutoPrintScheduled(event.target.checked)}/>} label="Automatically print scheduled orders"/>
          <TextField type="number" label="Print before pickup (minutes)" value={printLeadMinutes} disabled={!autoPrintScheduled} inputProps={{min:0,max:1440}} onChange={event=>setPrintLeadMinutes(Number(event.target.value))}/>
          <Typography variant="caption" color="text.secondary">Times use the store timezone: {configuration.data?.timezone}. The server persists and releases due jobs; this screen does not control the schedule.</Typography>
          {savePrintSettings.isError?<Alert severity="error">Kitchen print settings could not be saved.</Alert>:null}
        </Stack></DialogContent>
        <DialogActions><Button onClick={()=>setPrintSettingsOpen(false)}>Cancel</Button><Button variant="contained" disabled={savePrintSettings.isPending||printLeadMinutes<0||printLeadMinutes>1440||!Number.isInteger(printLeadMinutes)} onClick={()=>savePrintSettings.mutate()}>Save</Button></DialogActions>
      </Dialog>
      <Dialog open={heldOrdersOpen} onClose={() => !resume.isPending && setHeldOrdersOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Held Orders</DialogTitle>
        <DialogContent dividers><Stack spacing={1.25}>
          {heldOrders.isLoading ? <Box sx={{ display: 'grid', placeItems: 'center', py: 4 }}><CircularProgress aria-label="Loading held orders" /></Box> : null}
          {heldOrders.isError ? <Alert severity="error">Held orders could not be loaded.</Alert> : null}
          {heldOrders.isSuccess && restaurantHeldOrders.length === 0 ? <Alert severity="info">There are no held restaurant orders for this register.</Alert> : null}
          {restaurantHeldOrders.map(held => <Paper key={held.id} variant="outlined" sx={{ p: 1.5 }}><Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2}><Box><Typography fontWeight={800}>{held.items.reduce((sum, item) => sum + item.quantity, 0)} items · {money(held.totalAmount, held.currencyCode)}</Typography><Typography variant="caption" color="text.secondary">Held {held.heldAt ? new Date(held.heldAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'recently'}</Typography></Box><Button variant="contained" startIcon={<RestoreIcon />} disabled={resume.isPending || cart.length > 0} onClick={() => resume.mutate(held.id)}>Resume</Button></Stack></Paper>)}
          {cart.length > 0 && restaurantHeldOrders.length > 0 ? <Alert severity="info">Hold or clear the current order before resuming another order.</Alert> : null}
        </Stack></DialogContent>
        <DialogActions><Button onClick={() => setHeldOrdersOpen(false)} disabled={resume.isPending}>Close</Button></DialogActions>
      </Dialog>
    </Box>
  );
}

function MoneyRow({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return <Stack direction="row" justifyContent="space-between" spacing={2}><Typography fontWeight={strong ? 900 : 500} variant={strong ? 'h6' : 'body2'}>{label}</Typography><Typography fontWeight={strong ? 900 : 700} variant={strong ? 'h6' : 'body2'} noWrap>{value}</Typography></Stack>;
}

function paymentStatusLabel(order: Sale) {
  if (order.paymentStatus) return order.paymentStatus.replace('_', ' ');
  if (order.paymentComplete || order.balanceDue <= 0) return 'PAID';
  return order.paidAmount > 0 ? 'PARTIALLY PAID' : 'UNPAID';
}

function kitchenStatusLabel(order: Sale) {
  if (order.kitchenStatus === 'IN_PROGRESS') return 'PREPARING';
  if (order.kitchenStatus === 'PENDING') return 'CONFIRMED';
  return order.kitchenStatus ?? 'CONFIRMED';
}

function PrintState({ label, value }: { label: string; value: { status: FoodPrintStatus; error?: string } }) {
  return <Alert severity={value.status === 'FAILED' ? 'error' : value.status === 'PRINTED' ? 'success' : 'info'}>
    {label}: {value.status === 'FAILED' ? 'Order saved, but this document could not be sent for printing. Use the reprint action to try again.' : value.status.charAt(0) + value.status.slice(1).toLowerCase()}
  </Alert>;
}
