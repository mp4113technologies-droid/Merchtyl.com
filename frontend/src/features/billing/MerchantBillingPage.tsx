import DownloadIcon from '@mui/icons-material/Download';
import { Button, Chip, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { downloadMerchantInvoicePdf, getMerchantBillingSubscription, listMerchantBillingInvoices } from '../../api/client';
import type { PlatformInvoice } from '../../api/types';
import { useSession } from '../../app/session';

export function MerchantBillingPage() {
  const { getValidAccessToken } = useSession();
  const subscription = useQuery({ queryKey: ['merchant-billing-subscription'], queryFn: async () => getMerchantBillingSubscription(await getValidAccessToken()) });
  const invoices = useQuery({ queryKey: ['merchant-billing-invoices'], queryFn: async () => listMerchantBillingInvoices(await getValidAccessToken()) });
  async function download(invoice: PlatformInvoice) { const blob = await downloadMerchantInvoicePdf(await getValidAccessToken(), invoice.id); const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${invoice.invoiceNumber}.pdf`; anchor.click(); URL.revokeObjectURL(url); }
  return <Stack spacing={3}><Typography variant="h5">Subscription & Billing</Typography>{subscription.data ? <Paper variant="outlined" sx={{ p: 3 }}><Typography variant="h6">{subscription.data.planName}</Typography><Typography>{subscription.data.status} · {new Intl.NumberFormat(undefined,{style:'currency',currency:subscription.data.currency}).format(subscription.data.merchantBasePrice)} / {subscription.data.billingInterval.toLowerCase()}</Typography><Typography color="text.secondary">Next invoice {subscription.data.nextBillingDate}</Typography></Paper> : null}
    <Paper variant="outlined"><Table><TableHead><TableRow><TableCell>Invoice</TableCell><TableCell>Date</TableCell><TableCell>Amount</TableCell><TableCell>Status</TableCell><TableCell /></TableRow></TableHead><TableBody>{invoices.data?.content.map(invoice => <TableRow key={invoice.id}><TableCell>{invoice.invoiceNumber}</TableCell><TableCell>{invoice.issueDate}</TableCell><TableCell>{new Intl.NumberFormat(undefined,{style:'currency',currency:invoice.currency}).format(invoice.total)}</TableCell><TableCell><Chip size="small" label={invoice.status} /></TableCell><TableCell><Button startIcon={<DownloadIcon />} onClick={() => void download(invoice)}>Download PDF</Button></TableCell></TableRow>)}</TableBody></Table></Paper>
  </Stack>;
}
