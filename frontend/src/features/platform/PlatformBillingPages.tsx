import AddIcon from '@mui/icons-material/Add';
import DownloadIcon from '@mui/icons-material/Download';
import SendIcon from '@mui/icons-material/Send';
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControl,
  Grid, InputLabel, MenuItem, Paper, Select, Stack, Table, TableBody, TableCell, TableHead,
  TableRow, TextField, Typography
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as React from 'react';
import { Link } from 'react-router-dom';
import {
  assignPlatformBillingSubscription, createPlatformPricingPlan, downloadPlatformInvoicePdf,
  generatePlatformInvoice, getPlatformBillingOverview, getPlatformBillingSettings,
  listPlatformInvoices, listPlatformPricingPlans, listPlatformTenants, recordPlatformInvoicePayment,
  sendPlatformInvoice, updatePlatformBillingSettings, voidPlatformInvoice
} from '../../api/client';
import type { PlatformBillingSettings, PlatformInvoice, PricingPlan } from '../../api/types';
import { useSession } from '../../app/session';

function money(value: number, currency: string) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value);
}

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

const emptyPlan = { code: '', name: '', description: '', status: 'DRAFT', billingInterval: 'MONTHLY', basePrice: 0, oneTimeOnboardingFee: 0, currency: 'CAD', trialDays: 0, includedStores: 1, includedRegisters: 1, includedUsers: 5, additionalStorePrice: 0, additionalRegisterPrice: 0, additionalUserPrice: 0, taxBehavior: 'EXCLUSIVE', effectiveFrom: new Date().toISOString().slice(0, 10), effectiveTo: null };

export function PlatformPricingPlansPage() {
  const token = useToken(); const client = useQueryClient();
  const [open, setOpen] = React.useState(false); const [form, setForm] = React.useState<Record<string, unknown>>(emptyPlan);
  const plans = useQuery({ queryKey: ['platform-pricing-plans'], queryFn: async () => listPlatformPricingPlans(await token()) });
  const save = useMutation({ mutationFn: async () => createPlatformPricingPlan(await token(), form), onSuccess: async () => { setOpen(false); setForm(emptyPlan); await client.invalidateQueries({ queryKey: ['platform-pricing-plans'] }); } });
  return <Stack spacing={3}><Header title="Pricing Plans" action={<Button startIcon={<AddIcon />} variant="contained" onClick={() => setOpen(true)}>New Plan</Button>} />
    {save.error ? <Alert severity="error">{save.error.message}</Alert> : null}
    <Paper variant="outlined"><Table><TableHead><TableRow>{['Plan','Monthly Base','Onboarding Fee','Included Stores','Additional Store','Billing','Status','Active Merchants'].map(x => <TableCell key={x}>{x}</TableCell>)}</TableRow></TableHead><TableBody>
      {plans.data?.content.map(plan => <TableRow key={plan.id}><TableCell>{plan.name}<Typography variant="caption" display="block">{plan.code}</Typography></TableCell><TableCell>{money(plan.basePrice, plan.currency)}</TableCell><TableCell>{money(plan.oneTimeOnboardingFee, plan.currency)}</TableCell><TableCell>{plan.includedStores ?? 'Unlimited'}</TableCell><TableCell>{money(plan.additionalStorePrice ?? 0, plan.currency)}</TableCell><TableCell>{plan.billingInterval}</TableCell><TableCell><Chip size="small" label={plan.status} /></TableCell><TableCell>{plan.activeMerchants}</TableCell></TableRow>)}
    </TableBody></Table></Paper>
    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="md"><DialogTitle>Create Pricing Plan</DialogTitle><DialogContent><Grid container spacing={2} sx={{ mt: 0 }}>
      {['name','code','description','basePrice','oneTimeOnboardingFee','currency','trialDays','includedStores','includedRegisters','includedUsers','additionalStorePrice','additionalRegisterPrice','additionalUserPrice'].map(field => <Grid item xs={12} sm={field === 'description' ? 12 : 6} key={field}><TextField fullWidth label={field.replace(/([A-Z])/g, ' $1')} type={field.toLowerCase().includes('price') || field.toLowerCase().includes('fee') || field.toLowerCase().includes('included') || field === 'trialDays' ? 'number' : 'text'} value={form[field] ?? ''} onChange={event => setForm({ ...form, [field]: event.target.type === 'number' ? Number(event.target.value) : event.target.value })} /></Grid>)}
      <Grid item xs={6}><SelectField label="Billing Frequency" value={String(form.billingInterval)} values={['MONTHLY','YEARLY']} onChange={value => setForm({ ...form, billingInterval: value })} /></Grid>
      <Grid item xs={6}><SelectField label="Status" value={String(form.status)} values={['DRAFT','ACTIVE','INACTIVE','ARCHIVED']} onChange={value => setForm({ ...form, status: value })} /></Grid>
    </Grid></DialogContent><DialogActions><Button onClick={() => setOpen(false)}>Cancel</Button><Button variant="contained" onClick={() => save.mutate()}>Create</Button></DialogActions></Dialog>
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
    <Paper variant="outlined"><Table><TableHead><TableRow>{['Invoice','Merchant','Period','Due','Amount','Paid','Outstanding','Status','Actions'].map(x => <TableCell key={x}>{x}</TableCell>)}</TableRow></TableHead><TableBody>{invoices.data?.content.map(invoice => <TableRow key={invoice.id}><TableCell>{invoice.invoiceNumber}</TableCell><TableCell>{invoice.merchantName}</TableCell><TableCell>{invoice.billingPeriodStart} – {invoice.billingPeriodEnd}</TableCell><TableCell>{invoice.dueDate}</TableCell><TableCell>{money(invoice.total,invoice.currency)}</TableCell><TableCell>{money(invoice.amountPaid,invoice.currency)}</TableCell><TableCell>{money(invoice.amountOutstanding,invoice.currency)}</TableCell><TableCell><Chip size="small" label={invoice.status} /></TableCell><TableCell><Stack direction="row"><Button aria-label="Send invoice" onClick={() => send.mutate(invoice.id)}><SendIcon /></Button><Button aria-label="Download invoice" onClick={() => void download(invoice)}><DownloadIcon /></Button><Button disabled={invoice.amountOutstanding <= 0} onClick={() => payment.mutate(invoice)}>Record Payment</Button><Button color="error" disabled={invoice.amountPaid > 0} onClick={() => voidInvoice.mutate(invoice.id)}>Void</Button></Stack></TableCell></TableRow>)}</TableBody></Table></Paper>
  </Stack>;
}

export function PlatformBillingSettingsPage() {
  const token = useToken(); const client = useQueryClient(); const query = useQuery({ queryKey: ['platform-billing-settings'], queryFn: async () => getPlatformBillingSettings(await token()) });
  const [form, setForm] = React.useState<PlatformBillingSettings | null>(null); React.useEffect(() => { if (query.data) setForm(query.data); }, [query.data]);
  const save = useMutation({ mutationFn: async () => updatePlatformBillingSettings(await token(), form as unknown as Record<string, unknown>), onSuccess: data => { setForm(data); client.setQueryData(['platform-billing-settings'], data); } });
  if (!form) return <Typography>Loading billing settings…</Typography>;
  return <Stack spacing={3}><Header title="Billing Settings" /><Paper variant="outlined" sx={{ p: 3 }}><Grid container spacing={2}>{['legalName','billingAddress','supportEmail','invoiceSenderEmail','defaultCurrency','defaultPaymentTermsDays','invoicePrefix','taxRegistrationNumber','invoiceFooter','paymentInstructions'].map(field => <Grid item xs={12} sm={6} key={field}><TextField fullWidth label={field.replace(/([A-Z])/g,' $1')} value={(form as unknown as Record<string, unknown>)[field] ?? ''} onChange={e => setForm({ ...form, [field]: field === 'defaultPaymentTermsDays' ? Number(e.target.value) : e.target.value })} /></Grid>)}</Grid><Button sx={{ mt: 2 }} variant="contained" onClick={() => save.mutate()}>Save Billing Settings</Button></Paper></Stack>;
}

function Header({ title, action }: { title: string; action?: React.ReactNode }) { return <Stack direction="row" justifyContent="space-between"><Box><Typography variant="h5">{title}</Typography><Stack direction="row" spacing={1}><Button component={Link} to="/platform/billing">Overview</Button><Button component={Link} to="/platform/billing/plans">Pricing Plans</Button><Button component={Link} to="/platform/billing/subscriptions">Subscriptions</Button><Button component={Link} to="/platform/billing/invoices">Invoices</Button><Button component={Link} to="/platform/billing/settings">Settings</Button></Stack></Box>{action}</Stack>; }
function SelectField({ label, value, values, labels, onChange }: { label: string; value: string; values: string[]; labels?: string[]; onChange: (value: string) => void }) { return <FormControl fullWidth><InputLabel>{label}</InputLabel><Select label={label} value={value} onChange={e => onChange(String(e.target.value))}>{values.map((item,index) => <MenuItem key={item} value={item}>{labels?.[index] ?? item}</MenuItem>)}</Select></FormControl>; }
