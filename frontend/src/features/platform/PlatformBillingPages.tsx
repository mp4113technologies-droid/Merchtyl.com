import AddIcon from '@mui/icons-material/Add';
import DownloadIcon from '@mui/icons-material/Download';
import SendIcon from '@mui/icons-material/Send';
import {
  Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControl, FormControlLabel,
  Grid, InputLabel, MenuItem, Paper, Select, Stack, Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, TextField, Typography
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { Link } from 'react-router-dom';
import {
  assignPlatformBillingSubscription, createPlatformPricingPlan, downloadPlatformInvoicePdf,
  generatePlatformInvoice, getPlatformBillingOverview, getPlatformBillingSettings,
  listPlatformBillingCapabilities, listPlatformInvoices, listPlatformPricingHistory, listPlatformPricingPlans, listPlatformTenants, recordPlatformInvoicePayment,
  schedulePlatformPricingVersion, sendPlatformInvoice, cancelPlatformPricingVersion, updatePlatformBillingSettings, updatePlatformPricingPlan, voidPlatformInvoice
} from '../../api/client';
import type { CapabilityDefinition, CapabilityPrice, PlatformBillingSettings, PlatformInvoice, PricingPlan } from '../../api/types';
import { useSession } from '../../app/session';

function money(value: number, currency: string) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value);
}
const billingUnitLabels:Record<string,string>={PER_MERCHANT:'Per Merchant',PER_STORE:'Per Store',PER_USER:'Per User',PER_REGISTER:'Per Register'};
const billingUnitSuffix:Record<string,string>={PER_MERCHANT:'merchant',PER_STORE:'store',PER_USER:'user',PER_REGISTER:'register'};
const planFieldLabels:Record<string,string>={basePrice:'Base Monthly Price',includedStores:'Included Stores',additionalStorePrice:'Additional Store Price',includedRegisters:'Included Registers Per Store',additionalRegisterPrice:'Additional Register Price',includedUsers:'Included Users',additionalUserPrice:'Additional User Price',oneTimeOnboardingFee:'One-Time Onboarding Fee'};

function useToken() { return useSession().getValidAccessToken; }

export function PlatformBillingOverviewPage() {
  const token = useToken();
  const query = useQuery({ queryKey: ['platform-billing-overview'], queryFn: async () => getPlatformBillingOverview(await token()) });
  if (query.error) return <Alert severity="error">{query.error.message}</Alert>;
  const data = query.data;
  return <Stack spacing={3}>
    <Header title="Merchtyl Billing" />
    <Grid container spacing={2}>
      {data ? [
        ['Monthly Recurring Revenue', money(data.monthlyRecurringRevenue, data.currency)], ['Active Subscriptions', data.activeSubscriptions],
        ['Trial Subscriptions', data.trialSubscriptions], ['Invoices This Month', data.invoicesThisMonth],
        ['Outstanding Balance', money(data.outstandingBalance, data.currency)], ['Past Due Invoices', data.pastDueInvoices],
        ['Paid This Month', money(data.paidThisMonth, data.currency)], ['Subscriptions Cancelling', data.subscriptionsCancelling]
      ].map(([label, value]) => <Grid item xs={12} sm={6} md={3} key={label}><Paper variant="outlined" sx={{ p: 2 }}><Typography color="text.secondary">{label}</Typography><Typography variant="h5">{value}</Typography></Paper></Grid>) : null}
    </Grid>
  </Stack>;
}

const emptyPlan = { code: '', name: '', description: '', status: 'DRAFT', billingInterval: 'MONTHLY', basePrice: 0, oneTimeOnboardingFee: 0, currency: 'CAD', trialDays: 0, includedStores: 1, includedRegisters: 1, includedUsers: 5, additionalStorePrice: 0, additionalRegisterPrice: 0, additionalUserPrice: 0, capabilityPrices: [] as CapabilityPrice[], taxBehavior: 'EXCLUSIVE', effectiveFrom: new Date().toISOString().slice(0, 10), effectiveTo: null };

function capabilityRows(definitions: CapabilityDefinition[], existing: CapabilityPrice[] = []) {
  return definitions.map(definition => existing.find(value => value.capability === definition.capability) ?? { capability: definition.capability, inclusionType: 'NOT_AVAILABLE' as const, billingUnit: null, monthlyPricePerStore: null });
}

export function PlatformPricingPlansPage() {
  const token = useToken(); const client = useQueryClient();
  const {currentUser,session}=useSession(); const permissions=currentUser?.permissions??[]; const roles=currentUser?.roles??session?.roles??[]; const canEdit=roles.includes('PLATFORM_SUPER_ADMIN')||permissions.includes('PLATFORM_PRICING_PLAN_VERSION_SCHEDULE');
  const [open, setOpen] = React.useState(false); const [form, setForm] = React.useState<Record<string, unknown>>(emptyPlan);
  const [selected, setSelected] = React.useState<PricingPlan | null>(null); const [effectivePolicy,setEffectivePolicy]=React.useState('NEXT_BILLING_CYCLE'); const [effectiveDate, setEffectiveDate] = React.useState(new Date(Date.now()+86400000).toISOString().slice(0,10)); const [subscriberPolicy,setSubscriberPolicy]=React.useState('NEW_SUBSCRIPTIONS_ONLY'); const [confirmRemoval,setConfirmRemoval]=React.useState(false);
  const capabilityPrices = form.capabilityPrices as CapabilityPrice[];
  const plans = useQuery({ queryKey: ['platform-pricing-plans'], queryFn: async () => listPlatformPricingPlans(await token()) });
  const capabilities = useQuery({ queryKey: ['platform-billing-capabilities'], queryFn: async () => listPlatformBillingCapabilities(await token()) });
  const history = useQuery({ queryKey: ['platform-pricing-history',selected?.id], enabled: Boolean(selected), queryFn: async () => listPlatformPricingHistory(await token(), selected!.id) });
  const save = useMutation({ mutationFn: async () => createPlatformPricingPlan(await token(), form), onSuccess: async () => { setOpen(false); setForm(emptyPlan); await client.invalidateQueries({ queryKey: ['platform-pricing-plans'] }); } });
  const update = useMutation({ mutationFn: async () => updatePlatformPricingPlan(await token(), selected!.id, form), onSuccess: async () => { setOpen(false); await client.invalidateQueries({queryKey:['platform-pricing-history']}); await client.invalidateQueries({queryKey:['platform-pricing-plans']}); await client.invalidateQueries({queryKey:['platform-pricing-plan-options']}); } });
  const schedule = useMutation({ mutationFn: async () => schedulePlatformPricingVersion(await token(), selected!.id, { pricing: form, effectivePolicy, effectiveDate:effectivePolicy==='SPECIFIC_FUTURE_DATE'?effectiveDate:null, existingSubscriberPolicy: subscriberPolicy, confirmCapabilityRemoval: confirmRemoval, expectedPlanVersion: selected!.version }), onSuccess: async () => { setOpen(false); await client.invalidateQueries({queryKey:['platform-pricing-history']}); await client.invalidateQueries({queryKey:['platform-pricing-plans']}); } });
  const cancel = useMutation({ mutationFn: async (versionId:string) => cancelPlatformPricingVersion(await token(),selected!.id,versionId), onSuccess:()=>client.invalidateQueries({queryKey:['platform-pricing-history']}) });
  function openCreate(){setSelected(null);setForm({...emptyPlan,capabilityPrices:capabilityRows(capabilities.data??[])});setOpen(true);}
  function openEdit(plan:PricingPlan){setSelected(plan);setForm({...plan,capabilityPrices:capabilityRows(capabilities.data??[],plan.capabilityPrices)});setOpen(true);}
  function updateCapability(index:number, values:Partial<CapabilityPrice>){const next=[...capabilityPrices];next[index]={...next[index],...values};if(next[index].inclusionType!=='PAID_ADD_ON')next[index]={...next[index],billingUnit:null,monthlyPricePerStore:null};setForm({...form,capabilityPrices:next});}
  const invalidRegisterPricing=Number(form.includedRegisters)<0||Number(form.additionalRegisterPrice)<0;
  return <Stack spacing={3}><Header title="Pricing Plans" action={<Button startIcon={<AddIcon />} variant="contained" onClick={openCreate}>New Plan</Button>} />
    {save.error || update.error || schedule.error ? <Alert severity="error">{(save.error??update.error??schedule.error)?.message}</Alert> : null}
    <TableContainer component={Paper} variant="outlined"><Table><TableHead><TableRow>{['Plan','Monthly Base','Onboarding Fee','Included Stores','Additional Store','Billing','Status','Active Merchants'].map(x => <TableCell key={x}>{x}</TableCell>)}</TableRow></TableHead><TableBody>
      {plans.data?.content.map(plan => <TableRow hover key={plan.id} onClick={()=>openEdit(plan)} sx={{cursor:'pointer'}}><TableCell>{plan.name}<Typography variant="caption" display="block">{plan.code}</Typography></TableCell><TableCell>{money(plan.basePrice, plan.currency)}</TableCell><TableCell>{money(plan.oneTimeOnboardingFee, plan.currency)}</TableCell><TableCell>{plan.includedStores ?? 'Unlimited'}</TableCell><TableCell>{money(plan.additionalStorePrice ?? 0, plan.currency)}<Typography variant="caption" display="block">Food Service: {money(plan.capabilityPrices?.find(price=>price.capability==='FOOD_SERVICE')?.monthlyPricePerStore??0,plan.currency)}</Typography></TableCell><TableCell>{plan.billingInterval}</TableCell><TableCell><Chip size="small" label={plan.status} /></TableCell><TableCell>{plan.activeMerchants}</TableCell></TableRow>)}
    </TableBody></Table></TableContainer>
    <Paper variant="outlined" sx={{p:{xs:1.5,sm:2}}}><Typography variant="h6" gutterBottom>Plan Comparison</Typography><TableContainer><Table size="small"><TableHead><TableRow><TableCell>Capability</TableCell>{plans.data?.content.map(plan=><TableCell key={plan.id}>{plan.name}</TableCell>)}</TableRow></TableHead><TableBody>{(capabilities.data??[]).map(definition=><TableRow key={definition.capability}><TableCell>{definition.displayName}</TableCell>{plans.data?.content.map(plan=>{const configured=plan.capabilityPrices.find(value=>value.capability===definition.capability);return <TableCell key={plan.id}><Chip size="small" label={configured?.inclusionType==='INCLUDED'?'Included':configured?.inclusionType==='PAID_ADD_ON'?`Add-on · ${money(configured.monthlyPricePerStore??0,plan.currency)} / ${billingUnitSuffix[configured.billingUnit??'PER_MERCHANT']} / month`:'—'} /></TableCell>;})}</TableRow>)}</TableBody></Table></TableContainer></Paper>
    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="md"><DialogTitle>{selected?'Edit Pricing':'Create Pricing Plan'}</DialogTitle><DialogContent><Grid container spacing={2} sx={{ mt: 0 }}>
      {['name','code','description','basePrice','oneTimeOnboardingFee','currency','trialDays','includedStores','additionalStorePrice','includedRegisters','additionalRegisterPrice','includedUsers','additionalUserPrice'].map(field => <Grid item xs={12} sm={field === 'description' ? 12 : 6} key={field}><TextField fullWidth label={planFieldLabels[field]??field.replace(/([A-Z])/g, ' $1')} type={field.toLowerCase().includes('price') || field.toLowerCase().includes('fee') || field.toLowerCase().includes('included') || field === 'trialDays' ? 'number' : 'text'} inputProps={{min:0}} value={form[field] ?? ''} onChange={event => setForm({ ...form, [field]: event.target.type === 'number' ? Number(event.target.value) : event.target.value })} /></Grid>)}
      <Grid item xs={12}><Alert severity="info">Each store includes {String(form.includedRegisters??0)} registers. Registers above this allowance are billed at {money(Number(form.additionalRegisterPrice??0),String(form.currency))} per register per month.</Alert></Grid>
      <Grid item xs={12} sm={6}><SelectField label="Billing Frequency" value={String(form.billingInterval)} values={['MONTHLY','YEARLY']} onChange={value => setForm({ ...form, billingInterval: value })} /></Grid>
      <Grid item xs={12} sm={6}><SelectField label="Status" value={String(form.status)} values={['DRAFT','ACTIVE','INACTIVE','ARCHIVED']} onChange={value => setForm({ ...form, status: value })} /></Grid>
      <Grid item xs={12}><Typography variant="h6">Included Features &amp; Add-ons</Typography></Grid>
      {capabilityPrices.map((price,index)=><React.Fragment key={price.capability}><Grid item xs={12} md={4}><Typography sx={{pt:{md:2}}}>{(capabilities.data??[]).find(value=>value.capability===price.capability)?.displayName??price.capability}</Typography></Grid><Grid item xs={12} md={4}><SelectField label="Type" value={price.inclusionType} values={['INCLUDED','PAID_ADD_ON','NOT_AVAILABLE']} onChange={value=>updateCapability(index,{inclusionType:value as CapabilityPrice['inclusionType']})}/></Grid><Grid item xs={12} sm={6} md={2}>{price.inclusionType==='PAID_ADD_ON'?<TextField fullWidth label="Price" type="number" inputProps={{min:0}} value={price.monthlyPricePerStore??''} onChange={e=>updateCapability(index,{monthlyPricePerStore:Number(e.target.value)})}/>:null}</Grid><Grid item xs={12} sm={6} md={2}>{price.inclusionType==='PAID_ADD_ON'?<SelectField label="Billing Unit *" value={price.billingUnit??''} values={(capabilities.data??[]).find(value=>value.capability===price.capability)?.supportedBillingUnits??[]} labels={((capabilities.data??[]).find(value=>value.capability===price.capability)?.supportedBillingUnits??[]).map(value=>billingUnitLabels[value])} onChange={value=>updateCapability(index,{billingUnit:value as CapabilityPrice['billingUnit']})}/>:null}</Grid></React.Fragment>)}
      {selected?<><Grid item xs={12}><Typography variant="h6">Schedule Pricing Change</Typography></Grid><Grid item xs={6}><SelectField label="Effective" value={effectivePolicy} values={['NEXT_BILLING_CYCLE','SPECIFIC_FUTURE_DATE']} onChange={setEffectivePolicy}/></Grid><Grid item xs={6}>{effectivePolicy==='SPECIFIC_FUTURE_DATE'?<TextField fullWidth label="Effective Date" type="date" InputLabelProps={{shrink:true}} value={effectiveDate} onChange={e=>setEffectiveDate(e.target.value)}/>:null}</Grid><Grid item xs={12}><SelectField label="Existing Subscribers" value={subscriberPolicy} values={['NEW_SUBSCRIPTIONS_ONLY','APPLY_NEXT_BILLING_CYCLE']} onChange={setSubscriberPolicy}/></Grid><Grid item xs={12}><Alert severity="info">Pricing Change Summary: Base subscription {money(selected.basePrice,selected.currency)} → {money(Number(form.basePrice),selected.currency)}; additional store {money(selected.additionalStorePrice??0,selected.currency)} → {money(Number(form.additionalStorePrice??0),selected.currency)}; registers/store {selected.includedRegisters??0} → {Number(form.includedRegisters??0)}; additional register {money(selected.additionalRegisterPrice??0,selected.currency)} → {money(Number(form.additionalRegisterPrice??0),selected.currency)}. Affected active subscriptions: {selected.activeMerchants}.</Alert></Grid><Grid item xs={12}><FormControlLabel control={<Checkbox checked={confirmRemoval} onChange={e=>setConfirmRemoval(e.target.checked)}/>} label="Confirm capability removals for affected subscriptions at their next billing boundary" /></Grid><Grid item xs={12}><Typography variant="h6">Pricing History</Typography></Grid>{history.data?.map(version=><Grid item xs={12} key={version.id}><Paper variant="outlined" sx={{p:1}}><Stack direction="row" justifyContent="space-between"><Box><Typography>Version {version.versionNumber} · {version.status} · effective {version.effectiveFrom}</Typography><Typography variant="caption">Base {money(version.pricing.basePrice,version.pricing.currency)} · additional store {money(version.pricing.additionalStorePrice??0,version.pricing.currency)} · {version.pricing.includedRegisters??0} registers/store · additional register {money(version.pricing.additionalRegisterPrice??0,version.pricing.currency)} · {version.subscriberPolicy.replaceAll('_',' ')}</Typography></Box>{version.status==='SCHEDULED'&&canEdit?<Button color="error" onClick={()=>cancel.mutate(version.id)}>Cancel scheduled change</Button>:null}</Stack></Paper></Grid>)}</>:null}
    </Grid></DialogContent><DialogActions><Button onClick={() => setOpen(false)}>Cancel</Button>{(!selected||canEdit)?<Button variant="contained" disabled={invalidRegisterPricing} onClick={() => selected ? (selected.status !== 'ACTIVE' || form.status !== selected.status ? update.mutate() : schedule.mutate()) : save.mutate()}>{selected?'Save Pricing Changes':'Create'}</Button>:null}</DialogActions></Dialog>
  </Stack>;
}

export function PlatformSubscriptionsPage() {
  const token = useToken(); const client = useQueryClient();
  const { currentUser, session } = useSession();
  const canCustomizePricing = (currentUser?.permissions ?? []).includes('PLATFORM_SUBSCRIPTION_UPDATE')
    || (currentUser?.roles ?? session?.roles ?? []).includes('PLATFORM_SUPER_ADMIN');
  const tenants = useQuery({ queryKey: ['platform-tenants-billing'], queryFn: async () => listPlatformTenants(await token(), { size: 100 }) });
  const plans = useQuery({ queryKey: ['platform-pricing-plans'], queryFn: async () => listPlatformPricingPlans(await token()) });
  const [tenantId, setTenantId] = React.useState(''); const [planId, setPlanId] = React.useState('');
  const [customizePricing, setCustomizePricing] = React.useState(false);
  const [customPrice, setCustomPrice] = React.useState(''); const [customOnboardingFee, setCustomOnboardingFee] = React.useState('');
  const [customAdditionalStorePrice, setCustomAdditionalStorePrice] = React.useState('');
  const assign = useMutation({ mutationFn: async () => assignPlatformBillingSubscription(await token(), tenantId, {
    pricingPlanId: planId,
    status: 'ACTIVE',
    billingInterval: plans.data?.content.find(p => p.id === planId)?.billingInterval ?? 'MONTHLY',
    startDate: new Date().toISOString().slice(0,10),
    customBasePrice: customizePricing && customPrice ? Number(customPrice) : null,
    customOnboardingFee: customizePricing && customOnboardingFee ? Number(customOnboardingFee) : null,
    customAdditionalStorePrice: customizePricing && customAdditionalStorePrice ? Number(customAdditionalStorePrice) : null
  }), onSuccess: () => client.invalidateQueries({ queryKey: ['platform-tenants-billing'] }) });
  return <Stack spacing={3}><Header title="Merchant Subscriptions" /><Paper variant="outlined" sx={{ p: 3 }}><Stack spacing={2}>
    <Typography variant="h6">Assign or change plan</Typography><SelectField label="Merchant" value={tenantId} values={(tenants.data?.content ?? []).map(t => t.id)} labels={(tenants.data?.content ?? []).map(t => t.displayName)} onChange={setTenantId} />
    <SelectField label="Pricing Plan" value={planId} values={(plans.data?.content ?? []).filter(p => p.status === 'ACTIVE').map(p => p.id)} labels={(plans.data?.content ?? []).filter(p => p.status === 'ACTIVE').map(p => `${p.name} — ${money(p.basePrice,p.currency)}`)} onChange={setPlanId} />
    {canCustomizePricing ? <>
      <Button variant={customizePricing ? 'contained' : 'outlined'} onClick={() => setCustomizePricing(value => !value)}>Customize Pricing</Button>
      {customizePricing ? <Grid container spacing={2}>
        <Grid item xs={12} md={4}><TextField fullWidth label="Custom monthly base price" type="number" inputProps={{ min: 0 }} value={customPrice} onChange={e => setCustomPrice(e.target.value)} /></Grid>
        <Grid item xs={12} md={4}><TextField fullWidth label="Custom onboarding fee" type="number" inputProps={{ min: 0 }} value={customOnboardingFee} onChange={e => setCustomOnboardingFee(e.target.value)} /></Grid>
        <Grid item xs={12} md={4}><TextField fullWidth label="Custom additional store price" type="number" inputProps={{ min: 0 }} value={customAdditionalStorePrice} onChange={e => setCustomAdditionalStorePrice(e.target.value)} /></Grid>
      </Grid> : null}
    </> : null}
    <Alert severity="info">Plan changes take effect on the next billing period. Immediate proration is not implemented.</Alert>
    <Button variant="contained" disabled={!tenantId || !planId || assign.isPending} onClick={() => assign.mutate()}>Activate Subscription</Button>
    {assign.error ? <Alert severity="error">{assign.error.message}</Alert> : null}
  </Stack></Paper></Stack>;
}

export function PlatformInvoicesPage() {
  const token = useToken(); const client = useQueryClient(); const [search, setSearch] = React.useState('');
  const invoices = useQuery({ queryKey: ['platform-invoices', search], queryFn: async () => listPlatformInvoices(await token(), new URLSearchParams({ search }).toString()) });
  const refresh = () => client.invalidateQueries({ queryKey: ['platform-invoices'] });
  const send = useMutation({ mutationFn: async (id: string) => sendPlatformInvoice(await token(), id), onSuccess: refresh });
  const payment = useMutation({ mutationFn: async (invoice: PlatformInvoice) => recordPlatformInvoicePayment(await token(), invoice.id, { amount: invoice.amountOutstanding, paymentDate: new Date().toISOString().slice(0,10), paymentMethod: 'E_TRANSFER' }), onSuccess: refresh });
  const voidInvoice = useMutation({ mutationFn: async (id: string) => voidPlatformInvoice(await token(), id, 'Voided by platform administrator'), onSuccess: refresh });
  async function download(invoice: PlatformInvoice) { const blob = await downloadPlatformInvoicePdf(await token(), invoice.id); const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${invoice.invoiceNumber}.pdf`; anchor.click(); URL.revokeObjectURL(url); }
  return <Stack spacing={3}><Header title="Invoices" /><TextField label="Search invoice or merchant" value={search} onChange={e => setSearch(e.target.value)} />
    <TableContainer component={Paper} variant="outlined"><Table><TableHead><TableRow>{['Invoice','Merchant','Period','Due','Amount','Paid','Outstanding','Status','Actions'].map(x => <TableCell key={x}>{x}</TableCell>)}</TableRow></TableHead><TableBody>{invoices.data?.content.map(invoice => <TableRow key={invoice.id}><TableCell>{invoice.invoiceNumber}</TableCell><TableCell>{invoice.merchantName}</TableCell><TableCell>{invoice.billingPeriodStart} – {invoice.billingPeriodEnd}</TableCell><TableCell>{invoice.dueDate}</TableCell><TableCell>{money(invoice.total,invoice.currency)}</TableCell><TableCell>{money(invoice.amountPaid,invoice.currency)}</TableCell><TableCell>{money(invoice.amountOutstanding,invoice.currency)}</TableCell><TableCell><Chip size="small" label={invoice.status} /></TableCell><TableCell><Stack direction="row" useFlexGap flexWrap="wrap"><Button aria-label="Send invoice" onClick={() => send.mutate(invoice.id)}><SendIcon /></Button><Button aria-label="Download invoice" onClick={() => void download(invoice)}><DownloadIcon /></Button><Button disabled={invoice.amountOutstanding <= 0} onClick={() => payment.mutate(invoice)}>Record Payment</Button><Button color="error" disabled={invoice.amountPaid > 0} onClick={() => voidInvoice.mutate(invoice.id)}>Void</Button></Stack></TableCell></TableRow>)}</TableBody></Table></TableContainer>
  </Stack>;
}

export function PlatformBillingSettingsPage() {
  const token = useToken(); const client = useQueryClient(); const query = useQuery({ queryKey: ['platform-billing-settings'], queryFn: async () => getPlatformBillingSettings(await token()) });
  const [form, setForm] = React.useState<PlatformBillingSettings | null>(null); React.useEffect(() => { if (query.data) setForm(query.data); }, [query.data]);
  const save = useMutation({ mutationFn: async () => updatePlatformBillingSettings(await token(), form as unknown as Record<string, unknown>), onSuccess: data => { setForm(data); client.setQueryData(['platform-billing-settings'], data); } });
  if (!form) return <Typography>Loading billing settings…</Typography>;
  return <Stack spacing={3}><Header title="Billing Settings" /><Paper variant="outlined" sx={{ p: 3 }}><Grid container spacing={2}>{['legalName','billingAddress','supportEmail','invoiceSenderEmail','defaultCurrency','defaultPaymentTermsDays','invoicePrefix','taxRegistrationNumber','invoiceFooter','paymentInstructions'].map(field => <Grid item xs={12} sm={6} key={field}><TextField fullWidth label={field.replace(/([A-Z])/g,' $1')} value={(form as unknown as Record<string, unknown>)[field] ?? ''} onChange={e => setForm({ ...form, [field]: field === 'defaultPaymentTermsDays' ? Number(e.target.value) : e.target.value })} /></Grid>)}</Grid><Button sx={{ mt: 2 }} variant="contained" onClick={() => save.mutate()}>Save Billing Settings</Button></Paper></Stack>;
}

function Header({ title, action }: { title: string; action?: React.ReactNode }) { return <Stack direction={{xs:'column',sm:'row'}} spacing={2} justifyContent="space-between" alignItems={{xs:'stretch',sm:'flex-start'}}><Box sx={{minWidth:0}}><Typography variant="h5">{title}</Typography><Stack direction="row" spacing={1} useFlexGap flexWrap="wrap"><Button component={Link} to="/platform/billing">Overview</Button><Button component={Link} to="/platform/billing/plans">Pricing Plans</Button><Button component={Link} to="/platform/billing/subscriptions">Subscriptions</Button><Button component={Link} to="/platform/billing/invoices">Invoices</Button><Button component={Link} to="/platform/billing/settings">Settings</Button></Stack></Box>{action}</Stack>; }
function SelectField({ label, value, values, labels, onChange }: { label: string; value: string; values: string[]; labels?: string[]; onChange: (value: string) => void }) { const id=React.useId();return <FormControl fullWidth><InputLabel id={id}>{label}</InputLabel><Select labelId={id} label={label} value={value} onChange={e => onChange(String(e.target.value))}>{values.map((item,index) => <MenuItem key={item} value={item}>{labels?.[index] ?? item}</MenuItem>)}</Select></FormControl>; }
